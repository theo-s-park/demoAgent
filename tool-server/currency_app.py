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
    also_to_krw: bool = False


@app.post("/execute")
async def execute(req: Request):
    if not API_KEY:
        raise HTTPException(status_code=500, detail="EXCHANGERATE_API_KEY is not set (tool-server env)")

    async with httpx.AsyncClient() as client:
        url = f"https://v6.exchangerate-api.com/v6/{API_KEY}/pair/{req.from_currency}/{req.to}/{req.amount}"
        resp = await client.get(url)
        data = resp.json()
        if data.get("result") != "success":
            raise HTTPException(status_code=502, detail=f"ExchangeRate API error: {data.get('error-type')}")

        result = {
            "amount": req.amount,
            "from": req.from_currency,
            "to": req.to,
            "converted": data["conversion_result"],
        }

        if req.also_to_krw and req.to.upper() != "KRW":
            krw_url = f"https://v6.exchangerate-api.com/v6/{API_KEY}/pair/{req.from_currency}/KRW/{req.amount}"
            krw_resp = await client.get(krw_url)
            krw_data = krw_resp.json()
            if krw_data.get("result") == "success":
                result["converted_krw"] = krw_data["conversion_result"]

    return result
