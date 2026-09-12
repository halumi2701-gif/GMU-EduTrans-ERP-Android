-- GMU EduTrans Public Catalog Media v24
-- Apply to Supabase production when database connection is available.
-- Purpose: ERP-managed program/package images automatically consumed by public web.

alter table public.programs
  add column if not exists cover_image_url text,
  add column if not exists gallery_urls text[] not null default '{}';

alter table public.program_packages
  add column if not exists cover_image_url text,
  add column if not exists gallery_urls text[] not null default '{}';

comment on column public.programs.cover_image_url is 'Public cover image URL managed from ERP.';
comment on column public.programs.gallery_urls is 'Public gallery image URLs managed from ERP.';
comment on column public.program_packages.cover_image_url is 'Public package cover image URL managed from ERP.';
comment on column public.program_packages.gallery_urls is 'Public package gallery image URLs managed from ERP.';

-- Recommended storage bucket:
-- name: public-catalog-media
-- public read: yes
-- authenticated write: Owner / Manager / Admin only
-- max file: 8 MB
-- MIME: image/jpeg, image/png, image/webp
-- path convention:
--   programs/<program_id>/cover.<ext>
--   programs/<program_id>/gallery/<uuid>.<ext>
--   packages/<package_id>/cover.<ext>
--   packages/<package_id>/gallery/<uuid>.<ext>

-- Public catalog API must expose ONLY these media fields plus other customer-safe fields.
-- Never expose internal HPP, manager/sales/partner fees, margin, cost templates, or profit.
