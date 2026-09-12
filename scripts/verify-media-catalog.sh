#!/usr/bin/env bash
set -euo pipefail

endpoint="${1:?Usage: verify-media-catalog.sh <public-package-catalog-endpoint>}"
program_id="8d9c065b-124b-4e56-9180-23600018b04e"
package_code="PKG-GMU-00008"
trip_date="2026-09-12"

curl --fail --silent --show-error \
  "$endpoint?program_id=$program_id&trip_date=$trip_date&pax=20" \
  -o /tmp/gmu-catalog20.json

jq -e --arg code "$package_code" '
  .version == "v10-media-sync" and
  .catalog_status == "AVAILABLE" and
  ((.items | map(select(.package_code == $code)) | first) as $p |
    $p != null and
    $p.price_per_pax == 46000 and
    $p.min_pax == 20 and
    ($p.facilities | length) == 10 and
    $p.estimated_total == 920000 and
    ($p | has("cover_image_url")) and
    ($p | has("program_cover_image_url")) and
    ($p | has("gallery_urls")) and
    (($p.gallery_urls | type) == "array") and
    ($p | has("price_note") | not) and
    ($p | has("hpp") | not) and
    ($p | has("base_cost") | not) and
    ($p | has("manager_fee") | not) and
    ($p | has("sales_fee") | not) and
    ($p | has("mitra_fee") | not) and
    ($p | has("profit") | not) and
    ($p | has("margin") | not)
  )
' /tmp/gmu-catalog20.json

curl --fail --silent --show-error \
  "$endpoint?program_id=$program_id&trip_date=$trip_date&pax=19" \
  -o /tmp/gmu-catalog19.json

jq -e --arg code "$package_code" '
  (.items | map(select(.package_code == $code)) | length) == 0
' /tmp/gmu-catalog19.json

echo "GMU EduTrans Media Catalog verification passed."
echo "Rp46.000/pax | min 20 | 10 facilities | media contract safe"
