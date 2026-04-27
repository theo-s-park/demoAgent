$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot

if (-not (Test-Path ".venv")) {
  Write-Host "[tool-server] .venv not found. Creating venv with Python 3.12 (py -3.12)..."
  py -3.12 -m venv .venv
}

& .\.venv\Scripts\Activate.ps1

Write-Host "[tool-server] Using python:"
python -V

Write-Host "[tool-server] Installing requirements..."
python -m pip install -q -U pip
python -m pip install -q -r requirements.txt

Write-Host "[tool-server] Starting tools:"
Write-Host "  - random  : http://127.0.0.1:8081/execute"
Write-Host "  - currency: http://127.0.0.1:8082/execute"
Write-Host "  - weather : http://127.0.0.1:8083/execute"
if (Test-Path "stock_price_app.py") {
  Write-Host "  - stock_price: http://127.0.0.1:8090/execute"
}
Write-Host ""
Write-Host "Press Ctrl+C to stop all."

$p1 = Start-Process -PassThru -NoNewWindow python -ArgumentList @("-m","uvicorn","random_app:app","--host","127.0.0.1","--port","8081")
$p2 = Start-Process -PassThru -NoNewWindow python -ArgumentList @("-m","uvicorn","currency_app:app","--host","127.0.0.1","--port","8082")
$p3 = Start-Process -PassThru -NoNewWindow python -ArgumentList @("-m","uvicorn","weather_app:app","--host","127.0.0.1","--port","8083")
$p4 = $null
if (Test-Path "stock_price_app.py") {
  $p4 = Start-Process -PassThru -NoNewWindow python -ArgumentList @("-m","uvicorn","stock_price_app:app","--host","127.0.0.1","--port","8090")
}

try {
  $ids = @($p1.Id, $p2.Id, $p3.Id)
  if ($p4) { $ids += $p4.Id }
  Wait-Process -Id $ids
} finally {
  $procs = @($p1, $p2, $p3)
  if ($p4) { $procs += $p4 }
  foreach ($p in $procs) {
    try { if (!$p.HasExited) { Stop-Process -Id $p.Id -Force } } catch {}
  }
}

