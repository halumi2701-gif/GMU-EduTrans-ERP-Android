-- Xendit Payment Session gateway v6.5.5
alter table public.payment_gateway_channels
  drop constraint if exists payment_gateway_channels_integration_mode_check;

alter table public.payment_gateway_channels
  add constraint payment_gateway_channels_integration_mode_check
  check (integration_mode = any (array['CORE_V2'::text,'SNAP_RESTRICTED'::text,'XENDIT_SESSION'::text]));

insert into public.payment_gateway_channels(
  code,provider,display_name,method_type,provider_channel_code,
  is_enabled,sort_order,expiry_minutes,requires_phone,integration_mode,
  brand_group,notes,updated_at
)
values(
  'XENDIT','XENDIT','Bayar via Xendit','OTHER','PAYMENT_SESSION',
  true,1,30,false,'XENDIT_SESSION','HOSTED_CHECKOUT',
  'Xendit Payment Session Hosted Checkout. Metode yang tampil mengikuti channel yang aktif pada akun Xendit GMU.',
  now()
)
on conflict (code) do update set
  provider='XENDIT',display_name='Bayar via Xendit',method_type='OTHER',
  provider_channel_code='PAYMENT_SESSION',is_enabled=true,sort_order=1,
  expiry_minutes=30,requires_phone=false,integration_mode='XENDIT_SESSION',
  brand_group='HOSTED_CHECKOUT',
  notes='Xendit Payment Session Hosted Checkout. Metode yang tampil mengikuti channel yang aktif pada akun Xendit GMU.',
  updated_at=now();

update public.payment_gateway_channels set is_enabled=false,updated_at=now() where provider='MIDTRANS';

do $$
begin
  if not exists (select 1 from vault.secrets where name='gmu_internal_xendit_worker_token') then
    perform vault.create_secret(
      encode(gen_random_bytes(32),'hex'),
      'gmu_internal_xendit_worker_token',
      'Internal token for invoice-to-Xendit checkout worker'
    );
  end if;
end $$;

create or replace function public.gmu_verify_internal_xendit_worker_token(p_token text)
returns boolean
language sql
security definer
set search_path='vault','public','pg_temp'
as $$
  select exists(
    select 1 from vault.decrypted_secrets
    where name='gmu_internal_xendit_worker_token'
      and decrypted_secret=p_token
  );
$$;
revoke all on function public.gmu_verify_internal_xendit_worker_token(text) from public,anon,authenticated;
grant execute on function public.gmu_verify_internal_xendit_worker_token(text) to service_role;

create or replace function public.gmu_xendit_backend_config()
returns jsonb
language sql
security definer
set search_path='vault','public','pg_temp'
as $$
  select jsonb_build_object(
    'secret_key',(select decrypted_secret from vault.decrypted_secrets where name='xendit_secret_key' limit 1),
    'webhook_token',(select decrypted_secret from vault.decrypted_secrets where name='xendit_webhook_token' limit 1)
  );
$$;
revoke all on function public.gmu_xendit_backend_config() from public,anon,authenticated;
grant execute on function public.gmu_xendit_backend_config() to service_role;

create or replace function private.queue_xendit_checkout_after_invoice()
returns trigger
language plpgsql
security definer
set search_path='pg_catalog','public','private','vault','net','pg_temp'
as $$
declare
  v_token text;
  v_request_id bigint;
begin
  if new.invoice_type not in ('DP','PELUNASAN')
     or new.status not in ('ISSUED','PARTIAL','OVERDUE') then
    return new;
  end if;
  if tg_op='UPDATE'
     and old.status is not distinct from new.status
     and old.total is not distinct from new.total then
    return new;
  end if;

  select decrypted_secret into v_token
  from vault.decrypted_secrets
  where name='gmu_internal_xendit_worker_token'
  limit 1;

  if nullif(v_token,'') is null then return new; end if;

  begin
    select net.http_post(
      url := 'https://gtgnwasijweewmaubvyg.supabase.co/functions/v1/public-customer-portal',
      headers := jsonb_build_object('Content-Type','application/json'),
      body := jsonb_build_object(
        'action','xendit_create_internal',
        'invoice_id',new.id::text,
        'internal_token',v_token
      )
    ) into v_request_id;
  exception when others then
    null;
  end;
  return new;
end;
$$;
revoke all on function private.queue_xendit_checkout_after_invoice() from public,anon,authenticated;

drop trigger if exists trg_auto_xendit_checkout_after_invoice on public.invoices;
create trigger trg_auto_xendit_checkout_after_invoice
after insert or update of status,total on public.invoices
for each row execute function private.queue_xendit_checkout_after_invoice();
