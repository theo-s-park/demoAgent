import random
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()


class Request(BaseModel):
    min_val: int
    max_val: int


@app.post("/execute")
async def execute(req: Request):
    if req.min_val > req.max_val:
        raise HTTPException(status_code=400, detail="min_val must be <= max_val")
    return {"value": random.randint(req.min_val, req.max_val)}
