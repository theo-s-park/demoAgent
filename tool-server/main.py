import os
import random

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field, ConfigDict

app = FastAPI()

EXCHANGERATE_API_KEY = os.getenv("EXCHANGERATE_API_KEY")
OPENWEATHERMAP_API_KEY = os.getenv("OPENWEATHERMAP_API_KEY")


class RandomRequest(BaseModel):
    min_val: int
    max_val: int


class CurrencyRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    amount: float
    from_currency: str = Field(alias="from")
    to: str


class WeatherRequest(BaseModel):
    lat: float
    lon: float


@app.post("/tools/random")
async def tool_random(req: RandomRequest):
    if req.min_val > req.max_val:
        raise HTTPException(status_code=400, detail="min_val must be <= max_val")
    return {"value": random.randint(req.min_val, req.max_val)}


@app.post("/tools/currency")
async def tool_currency(req: CurrencyRequest):
    url = (
        f"https://v6.exchangerate-api.com/v6/{EXCHANGERATE_API_KEY}"
        f"/pair/{req.from_currency}/{req.to}/{req.amount}"
    )
    async with httpx.AsyncClient() as client:
        response = await client.get(url)
        data = response.json()
    if data.get("result") != "success":
        raise HTTPException(status_code=502, detail=f"ExchangeRate API error: {data.get('error-type')}")
    return {
        "amount": req.amount,
        "from": req.from_currency,
        "to": req.to,
        "converted": data["conversion_result"],
    }


@app.post("/tools/weather")
async def tool_weather(req: WeatherRequest):
    url = (
        f"https://api.openweathermap.org/data/2.5/weather"
        f"?lat={req.lat}&lon={req.lon}&appid={OPENWEATHERMAP_API_KEY}&units=metric"
    )
    async with httpx.AsyncClient() as client:
        response = await client.get(url)
        data = response.json()
    if response.status_code != 200:
        raise HTTPException(status_code=502, detail=f"OpenWeatherMap error: {data.get('message')}")
    return {
        "temperature": data["main"]["temp"],
        "humidity": data["main"]["humidity"],
        "condition": data["weather"][0]["description"],
    }
