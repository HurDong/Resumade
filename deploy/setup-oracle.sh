#!/bin/bash
# Oracle Cloud A1 (ARM64 Ubuntu 22.04) 초기 서버 세팅 스크립트
# 실행: chmod +x setup-oracle.sh && sudo ./setup-oracle.sh
set -e

DEPLOY_DIR="/home/ubuntu/resumade"

echo "=========================================="
echo " RESUMADE Oracle A1 Server Setup"
echo "=========================================="

# ── 1. 시스템 업데이트 ──────────────────────────────
apt-get update && apt-get upgrade -y
apt-get install -y curl gnupg lsb-release ca-certificates iptables-persistent netfilter-persistent

# ── 2. Docker 설치 (ARM64) ──────────────────────────
if ! command -v docker &> /dev/null; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=arm64 signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    | tee /etc/apt/sources.list.d/docker.list > /dev/null
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
  usermod -aG docker ubuntu
  echo "✓ Docker 설치 완료"
else
  echo "✓ Docker 이미 설치됨"
fi

# ── 3. 방화벽 설정 ──────────────────────────────────
# Oracle Cloud는 기본적으로 iptables 사용 (ufw 아님)
# OCI 콘솔 Security List에서도 80/443/22 인바운드 규칙 추가 필요
iptables -I INPUT 6 -m state --state NEW -p tcp --dport 22  -j ACCEPT
iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80  -j ACCEPT
iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
netfilter-persistent save
echo "✓ 방화벽 포트 22/80/443 오픈"

# ── 4. 디렉토리 구성 ────────────────────────────────
mkdir -p "$DEPLOY_DIR/nginx/certs"
chown -R ubuntu:ubuntu "$DEPLOY_DIR"
echo "✓ 디렉토리 생성: $DEPLOY_DIR"

# ── 5. Swap 설정 (ES/MySQL 메모리 안정성) ────────────
# A1 24GB이면 swap 불필요하지만 소형 플랜 사용 시 권장
# fallocate -l 2G /swapfile
# chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
# echo '/swapfile none swap sw 0 0' >> /etc/fstab

echo ""
echo "=========================================="
echo " 설치 완료! 다음 수동 작업을 진행하세요:"
echo "=========================================="
echo ""
echo "[ OCI 콘솔 필수 작업 ]"
echo "  1. Networking > VCN > Security List 에서 인바운드 규칙 추가:"
echo "     - TCP 22  (SSH)"
echo "     - TCP 80  (HTTP)"
echo "     - TCP 443 (HTTPS)"
echo ""
echo "[ 서버 수동 작업 ]"
echo "  2. .env 파일 생성:"
echo "     nano $DEPLOY_DIR/.env"
echo "     (.env.example 참고)"
echo ""
echo "  3. tessdata 업로드 (최초 1회):"
echo "     scp -r Backend/tessdata/ ubuntu@SERVER_IP:$DEPLOY_DIR/tessdata/"
echo ""
echo "  4. SSL 인증서 발급 (도메인 있을 때):"
echo "     apt-get install -y certbot"
echo "     certbot certonly --standalone -d YOUR_DOMAIN.com"
echo "     cp /etc/letsencrypt/live/YOUR_DOMAIN.com/fullchain.pem $DEPLOY_DIR/nginx/certs/"
echo "     cp /etc/letsencrypt/live/YOUR_DOMAIN.com/privkey.pem  $DEPLOY_DIR/nginx/certs/"
echo ""
echo "  5. 도메인 없이 HTTP만 테스트하려면:"
echo "     deploy/nginx/nginx.conf 에서 HTTP only 모드 사용"
echo ""
echo "[ GitHub Secrets 등록 (repo Settings > Secrets) ]"
echo "  SERVER_HOST  = $(curl -s ifconfig.me 2>/dev/null || echo 'YOUR_SERVER_IP')"
echo "  SERVER_USER  = ubuntu"
echo "  SSH_PRIVATE_KEY = (로컬 ~/.ssh/id_rsa 내용)"
echo ""
echo "[ GitHub Actions push → 자동 배포 시작 ]"
