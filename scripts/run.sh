#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
read -r -p 'Shop subdomain: ' shop_name
read -r -p 'Shopify client ID: ' client_id
read -r -s -p 'Shopify client secret: ' client_secret
printf '\n'
exec java "-DSHOPIFY_SHOP=$shop_name" "-DSHOPIFY_CLIENT_ID=$client_id" \
  "-DSHOPIFY_CLIENT_SECRET=$client_secret" "-DSHOPIFY_API_VERSION=2026-07" \
  -jar "$project_dir/backend/target/shopify-dashboard.jar"
