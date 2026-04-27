import os
import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from env_loader import load_tool_server_env

load_tool_server_env()

app = FastAPI()
ALPHAVANTAGE_API_KEY = os.getenv("ALPHAVANTAGE_API_KEY")

class StockRequest(BaseModel):
    symbol: str

@app.post("/execute")
async def execute(req: StockRequest):
    if not ALPHAVANTAGE_API_KEY:
        raise HTTPException(status_code=500, detail="ALPHAVANTAGE_API_KEY is not set (tool-server env)")
    async with httpx.AsyncClient() as client:
        response = await client.get(
            "https://www.alphavantage.co/query",
            params={
                "function": "GLOBAL_QUOTE",
                "symbol": req.symbol,
                "apikey": ALPHAVANTAGE_API_KEY
            }
        )
        if response.status_code != 200:
            raise HTTPException(status_code=502, detail="API error")
        data = response.json().get("Global Quote", {})
        if not data:
            raise HTTPException(status_code=404, detail="Stock data not found")
        return {
            "symbol": data.get("01. symbol"),
            "price": data.get("05. price")
        }

@app.get("/health")
async def health():
    return {"ok": True}
