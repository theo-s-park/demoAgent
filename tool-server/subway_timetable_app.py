import json
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()

class Request(BaseModel):
    station: str
    direction: str

# Sample data for demonstration
# In a real system, this would likely come from a database or external API
TIMETABLE = {
    "StationA": {
        "North": ["08:00", "09:00", "10:00"],
        "South": ["08:30", "09:30", "10:30"]
    },
    "StationB": {
        "East": ["07:15", "08:15", "09:15"],
        "West": ["07:45", "08:45", "09:45"]
    }
}

@app.post("/execute")
async def execute(req: Request):
    station_schedule = TIMETABLE.get(req.station)
    if not station_schedule:
        raise HTTPException(status_code=404, detail="Station not found")

    direction_schedule = station_schedule.get(req.direction)
    if not direction_schedule:
        raise HTTPException(status_code=404, detail="Direction not found for the station")

    return {"times": direction_schedule}

@app.get("/health")
async def health():
    return {"ok": True}