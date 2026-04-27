import os
import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()
API_KEY = os.getenv("OPENWEATHERMAP_API_KEY")


class Request(BaseModel):
    lat: float
    lon: float


@app.post("/execute")
async def execute(req: Request):
    url = (
        f"https://api.openweathermap.org/data/2.5/weather"
        f"?lat={req.lat}&lon={req.lon}&appid={API_KEY}&units=metric"
    )
    async with httpx.AsyncClient() as client:
        resp = await client.get(url)
        data = resp.json()
    if resp.status_code != 200:
        raise HTTPException(status_code=502, detail=f"OpenWeatherMap error: {data.get('message')}")
    return {
        "temperature": data["main"]["temp"],
        "humidity": data["main"]["humidity"],
        "condition": data["weather"][0]["description"],
    }
