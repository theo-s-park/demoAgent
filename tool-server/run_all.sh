#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -d ".venv" ]]; then
  echo "[tool-server] .venv not found. Creating venv with Python 3.12 (py -3.12)..."
  py -3.12 -m venv .venv
fi

# shellcheck disable=SC1091
source ".venv/Scripts/activate"

echo "[tool-server] Using python: $(which python)"
python -V

echo "[tool-server] Installing requirements..."
python -m pip install -q -U pip
python -m pip install -q -r requirements.txt

echo "[tool-server] Starting tools:"
echo "  - random  : http://127.0.0.1:8081/execute"
echo "  - currency: http://127.0.0.1:8082/execute"
echo "  - weather : http://127.0.0.1:8083/execute"
echo ""
echo "Press Ctrl+C to stop all."

python -m uvicorn random_app:app   --host 127.0.0.1 --port 8081 &
python -m uvicorn currency_app:app --host 127.0.0.1 --port 8082 &
python -m uvicorn weather_app:app  --host 127.0.0.1 --port 8083 &

wait

