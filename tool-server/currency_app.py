import os
import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field, ConfigDict

app = FastAPI()
API_KEY = os.getenv("EXCHANGERATE_API_KEY")


class Request(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    amount: float
    from_currency: str = Field(alias="from")
    to: str


@app.post("/execute")
async def execute(req: Request):
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
