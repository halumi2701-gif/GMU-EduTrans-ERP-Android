-- GMU EduTrans v23.3.6 — trusted recruitment intake wrapper for first-party public form
create or replace function public.gmu_recruitment_form_ingest_trusted(p_payload jsonb)
returns jsonb
language sql
security definer
set search_path = ''
as $$
  select public.gmu_recruitment_form_ingest(
    (select decrypted_secret from vault.decrypted_secrets where name='gmu_recruitment_form_webhook_secret' limit 1),
    p_payload
  );
$$;

revoke all on function public.gmu_recruitment_form_ingest_trusted(jsonb) from public, anon, authenticated;
grant execute on function public.gmu_recruitment_form_ingest_trusted(jsonb) to service_role;

comment on function public.gmu_recruitment_form_ingest_trusted(jsonb) is
  'First-party backend wrapper for GMU EduTrans public recruitment form; service_role only.';
