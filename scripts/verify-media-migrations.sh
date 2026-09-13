#!/usr/bin/env bash
set -euo pipefail

schema="supabase/migrations/20260912153500_add_program_package_media.sql"
storage="supabase/migrations/20260912153800_create_edutrans_media_bucket.sql"
deploy_workflow=".github/workflows/deploy-supabase-media.yml"

for file in "$schema" "$storage" "$deploy_workflow"; do
  test -f "$file" || { echo "Missing Media Sync file: $file"; exit 1; }
done

# Media metadata contract.
grep -Fq "cover_image_url text" "$schema"
grep -Fq "gallery_urls jsonb not null default '[]'::jsonb" "$schema"
grep -Fq "jsonb_typeof(gallery_urls) = 'array'" "$schema"
grep -Fq "jsonb_array_length(gallery_urls) <= 5" "$schema"
grep -Fq "cover_image_url is null or cover_image_url like 'https://%'" "$schema"

# Both Program and Package tables must be covered.
grep -Fq "alter table public.programs" "$schema"
grep -Fq "alter table public.program_packages" "$schema"

# Storage contract: public-read bucket, 8 MiB, image MIME types only.
grep -Fq "'edutrans-media'" "$storage"
grep -Fq "8388608" "$storage"
grep -Fq "array['image/jpeg','image/png','image/webp']" "$storage"
grep -Fq "for insert" "$storage"
grep -Fq "to authenticated" "$storage"

# Role guard required for internal uploads.
for role in "Owner" "Director" "Direktur" "Manager" "Manager EduTrans" "Admin"; do
  grep -Fq "'$role'" "$storage" || { echo "Missing storage role: $role"; exit 1; }
done

# Production deployment safety contract.
grep -Fq 'confirm_production == '\''DEPLOY'\''' "$deploy_workflow"
grep -Fq 'gmu-edutrans-supabase-production' "$deploy_workflow"
grep -Fq 'version: v2.117.0' "$deploy_workflow"
grep -Fq 'Capture and validate pre-deploy customer baseline' "$deploy_workflow"
grep -Fq 'gmu-media-sync-predeploy-customer-snapshot' "$deploy_workflow"
grep -Fq 'supabase db push --dry-run' "$deploy_workflow"
grep -Fq 'Verify direct production Media Catalog contract' "$deploy_workflow"
grep -Fq 'Verify customer proxy Media Catalog contract' "$deploy_workflow"
grep -Fq 'https://edutrans.garsyanimultiusaha.site/api/proxy?slug=public-package-catalog' "$deploy_workflow"

echo "GMU Media migration + deployment contract passed."
echo "Program+Package media | gallery<=5 | HTTPS cover | 8MiB | JPG/PNG/WebP | authenticated role upload"
echo "Production guard | DEPLOY confirmation | CLI v2.117.0 | pre-snapshot | dry-run | direct+customer verification"
