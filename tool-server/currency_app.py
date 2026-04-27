import os
import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field, ConfigDict

from env_loader import load_tool_server_env

load_tool_server_env()

app = FastAPI()
API_KEY = os.getenv("EXCHANGERATE_API_KEY")

@app.get("/health")
async def health():
    return {"ok": True}


class Request(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    amount: float
    from_currency: str = Field(alias="from")
    to: str


@app.post("/execute")
async def execute(req: Request):
    if not API_KEY:
        raise HTTPException(status_code=500, detail="EXCHANGERATE_API_KEY is not set (tool-server env)")
    url = f"https://v6.exchangerate-api.com/v6/{API_KEY}/pair/{req.from_currency}/{req.to}/{req.amount}"
    async with httpx.AsyncClient() as client:
        resp = await client.get(url)
        data = resp.json()
    if data.get("result") != "success":
        raise HTTPException(status_code=502, detail=f"ExchangeRate API error: {data.get('error-type')}")
    return {
        "amount": req.amount,
        "from": req.from_currency,
        "to": req.to,
        "converted": data["conversion_result"],
    }
