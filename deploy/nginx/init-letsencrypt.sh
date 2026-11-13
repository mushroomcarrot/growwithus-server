#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."

DOMAIN="grow-api.yuns.kr"
EMAIL="yunsol267@gmail.com"
ACTIVE=deploy/nginx/active/app.conf
FULL=deploy/nginx/app.conf.full

if sudo docker compose run --rm --no-deps --entrypoint test certbot -f "/etc/letsencrypt/live/$DOMAIN/fullchain.pem"; then
  echo "Let's Encrypt certificate for $DOMAIN already exists"
  exit 0
fi

restore() {
  if [ -f "$FULL" ]; then
    mv "$FULL" "$ACTIVE"
  fi
  sudo docker compose restart nginx > /dev/null 2>&1 || true
}
trap restore EXIT

echo "Issuing Let's Encrypt certificate for $DOMAIN"
cp "$ACTIVE" "$FULL"
cp deploy/nginx/bootstrap.conf "$ACTIVE"
sudo docker compose up -d --no-deps nginx
sudo docker compose restart nginx

if [ -n "$EMAIL" ]; then
  ACCOUNT="--email $EMAIL --no-eff-email"
else
  ACCOUNT="--register-unsafely-without-email"
fi

sudo docker compose run --rm --no-deps --entrypoint certbot certbot certonly \
  --webroot -w /var/www/certbot -d "$DOMAIN" $ACCOUNT --agree-tos --non-interactive

echo "Certificate issued for $DOMAIN"
