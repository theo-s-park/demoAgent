import os
import datetime as dt
from collections import Counter, defaultdict

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import Optional

from env_loader import load_tool_server_env

load_tool_server_env()

app = FastAPI()
API_KEY = os.getenv("OPENWEATHERMAP_API_KEY")

@app.get("/health")
async def health():
    return {"ok": True}


class Request(BaseModel):
    # Backward-compatible (current weather)
    lat: Optional[float] = None
    lon: Optional[float] = None

    # New (city + date-range forecast)
    query: Optional[str] = Field(default=None, description="City name, e.g. 제주시 / 서귀포시")
    start_date: Optional[str] = Field(default=None, description="YYYY-MM-DD")
    end_date: Optional[str] = Field(default=None, description="YYYY-MM-DD")


def _parse_date(s: str) -> dt.date:
    try:
        return dt.date.fromisoformat(s)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid date: {s} (expected YYYY-MM-DD)") from e


def _local_date_from_utc_ts(utc_ts: int, tz_offset_seconds: int) -> dt.date:
    return dt.datetime.fromtimestamp(utc_ts + tz_offset_seconds, tz=dt.timezone.utc).date()

def _validate_forecast_window(start: dt.date, end: dt.date) -> None:
    # OpenWeather free 5-day / 3-hour forecast is only available for a limited window.
    # Keep the API surface simple: reject ranges longer than 5 days, or that extend beyond today+5.
    today = dt.date.today()
    max_day = today + dt.timedelta(days=5)

    if (end - start).days > 5:
        raise HTTPException(status_code=400, detail="날씨 예보는 최대 5일 범위만 조회할 수 있어요.")
    if start < today or end > max_day:
        raise HTTPException(status_code=400, detail="날씨 예보는 오늘부터 최대 5일 이내 날짜만 조회할 수 있어요.")


async def _geocode_city(client: httpx.AsyncClient, query: str):
    url = "https://api.openweathermap.org/geo/1.0/direct"
    try:
        resp = await client.get(url, params={"q": query, "limit": 1, "appid": API_KEY})
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502,
            detail=f"OpenWeatherMap geocoding network error: {type(e).__name__}: {e!r}",
        ) from e
    try:
        data = resp.json()
    except Exception as e:
        raise HTTPException(status_code=502, detail="OpenWeatherMap geocoding returned invalid JSON") from e
    if resp.status_code != 200:
        raise HTTPException(status_code=502, detail=f"OpenWeatherMap geocoding error: {data.get('message')}")
    if not data:
        raise HTTPException(status_code=404, detail=f"City not found: {query}")
    best = data[0]
    return {
        "name": best.get("name") or query,
        "lat": float(best["lat"]),
        "lon": float(best["lon"]),
        "country": best.get("country"),
    }


async def _current_weather(client: httpx.AsyncClient, lat: float, lon: float):
    url = "https://api.openweathermap.org/data/2.5/weather"
    try:
        resp = await client.get(url, params={"lat": lat, "lon": lon, "appid": API_KEY, "units": "metric"})
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502,
            detail=f"OpenWeatherMap network error: {type(e).__name__}: {e!r}",
        ) from e
    try:
        data = resp.json()
    except Exception as e:
        raise HTTPException(status_code=502, detail="OpenWeatherMap returned invalid JSON") from e
    if resp.status_code != 200:
        raise HTTPException(status_code=502, detail=f"OpenWeatherMap error: {data.get('message')}")
    return {
        "temperature": data["main"]["temp"],
        "humidity": data["main"]["humidity"],
        "condition": data["weather"][0]["description"],
    }


async def _forecast_range(client: httpx.AsyncClient, lat: float, lon: float, start: dt.date, end: dt.date):
    # OpenWeather 5-day / 3-hour forecast
    url = "https://api.openweathermap.org/data/2.5/forecast"
    try:
        resp = await client.get(url, params={"lat": lat, "lon": lon, "appid": API_KEY, "units": "metric"})
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502,
            detail=f"OpenWeatherMap forecast network error: {type(e).__name__}: {e!r}",
        ) from e
    try:
        data = resp.json()
    except Exception as e:
        raise HTTPException(status_code=502, detail="OpenWeatherMap forecast returned invalid JSON") from e
    if resp.status_code != 200:
        raise HTTPException(status_code=502, detail=f"OpenWeatherMap forecast error: {data.get('message')}")

    tz_offset = int(data.get("city", {}).get("timezone", 0))
    buckets = defaultdict(list)
    for item in data.get("list", []):
        local_day = _local_date_from_utc_ts(int(item["dt"]), tz_offset)
        if local_day < start or local_day > end:
            continue
        buckets[local_day].append(item)

    days = []
    for day in sorted(buckets.keys()):
        items = buckets[day]
        temps_min = [x["main"]["temp_min"] for x in items if "main" in x]
        temps_max = [x["main"]["temp_max"] for x in items if "main" in x]
        humidities = [x["main"]["humidity"] for x in items if "main" in x and "humidity" in x["main"]]
        conditions = [
            x.get("weather", [{}])[0].get("description", "")
            for x in items
            if x.get("weather")
        ]
        condition = Counter([c for c in conditions if c]).most_common(1)[0][0] if any(conditions) else ""

        days.append(
            {
                "date": day.isoformat(),
                "temp_min": min(temps_min) if temps_min else None,
                "temp_max": max(temps_max) if temps_max else None,
                "humidity_avg": round(sum(humidities) / len(humidities)) if humidities else None,
                "condition": condition,
            }
        )

    return {"days": days, "timezone_offset_seconds": tz_offset}


@app.post("/execute")
async def execute(req: Request):
    if not API_KEY:
        raise HTTPException(status_code=500, detail="OPENWEATHERMAP_API_KEY is not set (tool-server env)")

    # trust_env=True enables HTTP(S)_PROXY / NO_PROXY environment variables (common in corp networks)
    async with httpx.AsyncClient(timeout=20, trust_env=True) as client:
        # Forecast mode (city + date range)
        if req.query and (req.start_date or req.end_date):
            start = _parse_date(req.start_date) if req.start_date else _parse_date(req.end_date)
            end = _parse_date(req.end_date) if req.end_date else _parse_date(req.start_date)
            if start > end:
                start, end = end, start
            _validate_forecast_window(start, end)

            location = await _geocode_city(client, req.query)
            forecast = await _forecast_range(client, location["lat"], location["lon"], start, end)
            if not forecast["days"]:
                raise HTTPException(status_code=400, detail="요청한 날짜 범위는 현재 제공 가능한 예보(최대 5일) 범위를 벗어났어요.")
            return {
                "location": location,
                "start_date": start.isoformat(),
                "end_date": end.isoformat(),
                "days": forecast["days"],
                "note": "OpenWeather 5-day forecast (3-hour intervals) aggregated by local date.",
            }

        # Current weather mode (lat/lon), also supports query without dates
        if req.lat is None or req.lon is None:
            if not req.query:
                raise HTTPException(
                    status_code=400,
                    detail="Provide either lat/lon for current weather, or query + start_date/end_date for forecast.",
                )
            location = await _geocode_city(client, req.query)
            current = await _current_weather(client, location["lat"], location["lon"])
            return {"location": location, **current}

        return await _current_weather(client, float(req.lat), float(req.lon))
