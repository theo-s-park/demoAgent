import os
import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()
API_KEY = os.getenv("ALPHAVANTAGE_API_KEY")

KR_SUFFIXES = {".KSC", ".KSQ", ".KS", ".KQ"}


class StockRequest(BaseModel):
    query: str  # 회사명(한글/영문) 또는 티커 심볼


def _is_kr_symbol(symbol: str) -> bool:
    return any(symbol.upper().endswith(s) for s in KR_SUFFIXES)


async def _resolve_symbol(query: str, client: httpx.AsyncClient) -> str:
    resp = await client.get(
        "https://www.alphavantage.co/query",
        params={"function": "SYMBOL_SEARCH", "keywords": query, "apikey": API_KEY},
    )
    if resp.status_code == 200:
        matches = resp.json().get("bestMatches", [])
        if matches:
            return matches[0].get("1. symbol", query)
    return query


async def _usd_to_krw(client: httpx.AsyncClient) -> float:
    try:
        resp = await client.get(
            "https://www.alphavantage.co/query",
            params={
                "function": "CURRENCY_EXCHANGE_RATE",
                "from_currency": "USD",
                "to_currency": "KRW",
                "apikey": API_KEY,
            },
        )
        if resp.status_code == 200:
            rate_str = (
                resp.json()
                .get("Realtime Currency Exchange Rate", {})
                .get("5. Exchange Rate")
            )
            if rate_str:
                return float(rate_str)
    except Exception:
        pass
    return 1380.0


@app.post("/execute")
async def get_stock_price(req: StockRequest):
    async with httpx.AsyncClient(timeout=15.0) as client:
        symbol = await _resolve_symbol(req.query, client)

        resp = await client.get(
            "https://www.alphavantage.co/query",
            params={"function": "GLOBAL_QUOTE", "symbol": symbol, "apikey": API_KEY},
        )
        if resp.status_code != 200:
            raise HTTPException(status_code=502, detail="Alpha Vantage API 오류")

        data = resp.json()
        note = data.get("Note") or data.get("Information")
        if note:
            raise HTTPException(status_code=429, detail=f"API 요청 한도 초과: {note}")

        gq = data.get("Global Quote", {})
        if not gq or not gq.get("05. price"):
            raise HTTPException(status_code=404, detail=f"주가 데이터를 찾을 수 없습니다: {symbol}")

        price = float(gq["05. price"])

        if _is_kr_symbol(symbol):
            price_krw = price
            currency = "KRW"
        else:
            rate = await _usd_to_krw(client)
            price_krw = price * rate
            currency = "USD"

        return {
            "query": req.query,
            "symbol": gq.get("01. symbol", symbol),
            "price_original": round(price, 4),
            "currency_original": currency,
            "price_krw": int(round(price_krw, 0)),
            "change_percent": gq.get("10. change percent", "N/A"),
            "date": gq.get("07. latest trading day"),
        }


@app.get("/health")
async def health():
    return {"ok": True}
