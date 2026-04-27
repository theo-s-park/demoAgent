#!/bin/bash
# 코드 업데이트 후 재배포할 때마다 실행
set -e

cd ~/demoAgent

echo "=== [1/3] 코드 업데이트 ==="
git pull origin main

echo "=== [2/3] 빌드 & 실행 ==="
docker compose -f docker-compose.prod.yml up --build -d

echo "=== [3/3] 상태 확인 ==="
sleep 3
docker compose -f docker-compose.prod.yml ps

echo ""
echo "=== 배포 완료 ==="
echo "접속: http://$(curl -s ifconfig.me):8080"
echo "로그: docker compose -f docker-compose.prod.yml logs -f agent-server"
