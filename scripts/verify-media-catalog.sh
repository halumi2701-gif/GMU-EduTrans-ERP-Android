#!/usr/bin/env bash
set -euo pipefail

endpoint="${1:?Usage: verify-media-catalog.sh <public-package-catalog-endpoint>}"
program_id="8d9c065b-124b-4e56-9180-23600018b04e"
package_code="PKG-GMU-00008"
trip_date="2026-09-12"

curl --fail --silent --show-error \
  "$endpoint?program_id=$program_id&trip_date=$trip_date&pax=20" \
  -o /tmp/gmu-catalog20.json

jq -e --arg code "$package_code" --arg trip_date "$trip_date" '
  .version == "v10-media-sync" and
  .catalog_status == "AVAILABLE" and
  .catalog_scope == "PROGRAM" and
  .custom_trip_available == true and
  .as_of == $trip_date and
  .trip_date == $trip_date and
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
    ($p.gallery_urls | length) <= 5 and
    ([
      $p.cover_image_url,
      $p.program_cover_image_url,
      ($p.gallery_urls[]? // null)
    ] | all(. == null or (type == "string" and startswith("https://"))))
  ) and
  (.programs | type) == "array" and
  (.programs | all(
    has("sort_order") and
    has("cover_image_url") and
    has("gallery_urls") and
    (.gallery_urls | type) == "array" and
    (.gallery_urls | length) <= 5 and
    ([.cover_image_url, (.gallery_urls[]? // null)] |
      all(. == null or (type == "string" and startswith("https://"))))
  ))
' /tmp/gmu-catalog20.json

# Security invariant: forbidden internal-pricing keys must not occur anywhere
# in the public response, including future nested structures.
jq -e '
  [
    .. | objects | keys[]? |
    ascii_downcase |
    select(
      . == "price_note" or
      . == "hpp" or
      . == "base_cost" or
      . == "manager_fee" or
      . == "sales_fee" or
      . == "mitra_fee" or
      . == "partner_fee" or
      . == "profit" or
      . == "margin" or
      . == "margin_pct" or
      . == "target_margin_pct" or
      . == "floor_margin_pct" or
      . == "cost_templates" or
      . == "pricing_policy"
    )
  ] | length == 0
' /tmp/gmu-catalog20.json

curl --fail --silent --show-error \
  "$endpoint?program_id=$program_id&trip_date=$trip_date&pax=19" \
  -o /tmp/gmu-catalog19.json

jq -e --arg code "$package_code" '
  (.items | map(select(.package_code == $code)) | length) == 0
' /tmp/gmu-catalog19.json

echo "GMU EduTrans Media Catalog verification passed."
echo "v9-compatible | Rp46.000/pax | min 20 | 10 facilities | HTTPS media <=5 | recursive finance leak guard"
