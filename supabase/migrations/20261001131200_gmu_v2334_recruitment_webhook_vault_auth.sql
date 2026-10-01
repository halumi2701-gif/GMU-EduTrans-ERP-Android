-- GMU EduTrans v23.3.4 — validate recruitment webhook against Supabase Vault

create or replace function public.gmu_validate_recruitment_form_secret(p_secret text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from vault.decrypted_secrets s
    where s.name='gmu_recruitment_form_webhook_secret'
      and s.decrypted_secret=p_secret
  );
$$;

revoke all on function public.gmu_validate_recruitment_form_secret(text) from public, anon, authenticated;
grant execute on function public.gmu_validate_recruitment_form_secret(text) to service_role;
