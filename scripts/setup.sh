#!/bin/bash
# EC2 최초 1회 실행
set -e

echo "=== [1/4] Docker 설치 ==="
if ! command -v docker &>/dev/null; then
  sudo dnf install -y docker
  sudo systemctl enable --now docker
  sudo usermod -aG docker ec2-user
  echo "Docker 설치 완료. 스크립트 재실행 전에 'exit' 후 재접속하세요."
  echo "재접속 후: cd ~/demoAgent && bash scripts/setup.sh"
  exit 0
else
  echo "Docker 이미 설치됨"
fi

echo "=== [2/4] 레포 clone ==="
if [ ! -d ~/demoAgent ]; then
  git clone https://github.com/theo-s-park/demoAgent.git ~/demoAgent
else
  echo "이미 clone됨"
fi
cd ~/demoAgent

echo "=== [3/4] .env 파일 생성 ==="
if [ ! -f .env ]; then
  read -p "OPENAI_API_KEY: " OPENAI_API_KEY
  read -p "EXCHANGERATE_API_KEY: " EXCHANGERATE_API_KEY
  read -p "OPENWEATHERMAP_API_KEY: " OPENWEATHERMAP_API_KEY

  cat > .env << EOF
OPENAI_API_KEY=${OPENAI_API_KEY}
EXCHANGERATE_API_KEY=${EXCHANGERATE_API_KEY}
OPENWEATHERMAP_API_KEY=${OPENWEATHERMAP_API_KEY}
EOF
  echo ".env 생성 완료"
else
  echo ".env 이미 존재함 (덮어쓰려면 rm .env 후 재실행)"
fi

echo "=== [4/4] 최초 배포 ==="
bash scripts/deploy.sh

echo ""
echo "=== 완료 ==="
echo "접속: http://$(curl -s ifconfig.me):8080"
