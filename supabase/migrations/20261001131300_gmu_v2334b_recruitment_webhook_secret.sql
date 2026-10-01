-- GMU EduTrans v23.3.4b — ensure recruitment webhook secret exists in Supabase Vault
select vault.create_secret(
  encode(extensions.gen_random_bytes(32),'hex'),
  'gmu_recruitment_form_webhook_secret',
  'GMU EduTrans Recruitment Form webhook v1'
)
where not exists (
  select 1 from vault.secrets where name='gmu_recruitment_form_webhook_secret'
);
