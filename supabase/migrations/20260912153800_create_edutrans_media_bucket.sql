-- GMU EduTrans Media Sync v1 storage bucket
-- Public read is intentional for customer-facing Program/Package media.
-- Upload remains restricted through storage.objects RLS.

insert into storage.buckets (
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types
)
values (
    'edutrans-media',
    'edutrans-media',
    true,
    8388608,
    array['image/jpeg','image/png','image/webp']
)
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "gmu_edutrans_media_insert" on storage.objects;
create policy "gmu_edutrans_media_insert"
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'edutrans-media'
    and exists (
        select 1
        from public.profiles p
        where p.id = (select auth.uid())
          and p.is_active = true
          and p.role in ('Owner','Director','Direktur','Manager','Manager EduTrans','Admin')
    )
);
