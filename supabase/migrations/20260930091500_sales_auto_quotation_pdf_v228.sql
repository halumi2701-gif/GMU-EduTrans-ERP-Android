-- GMU EduTrans Sales Auto Quotation PDF v2.28
-- Creates a Vault-backed internal token and queues PDF generation asynchronously
-- whenever a quotation assigned to Sales is inserted.

do $$
begin
  if not exists (select 1 from vault.secrets where name='gmu_internal_pdf_token') then
    perform vault.create_secret(
      encode(gen_random_bytes(32),'hex'),
      'gmu_internal_pdf_token',
      'Internal token for async auto quotation PDF generation from Postgres pg_net'
    );
  end if;
end $$;

create or replace function public.gmu_verify_internal_pdf_token(p_token text)
returns boolean
language sql
security definer
set search_path = 'vault','public','pg_temp'
as $$
  select exists (
    select 1
    from vault.decrypted_secrets
    where name='gmu_internal_pdf_token'
      and decrypted_secret = p_token
  );
$$;

revoke all on function public.gmu_verify_internal_pdf_token(text) from public, anon, authenticated;
grant execute on function public.gmu_verify_internal_pdf_token(text) to service_role;

create or replace function private.queue_sales_quotation_pdf()
returns trigger
language plpgsql
security definer
set search_path = 'pg_catalog','public','private','vault','net','pg_temp'
as $$
declare
  v_sales uuid;
  v_token text;
  v_request_id bigint;
begin
  select br.assigned_sales
    into v_sales
  from public.booking_requests br
  where br.id = new.booking_request_id;

  if v_sales is null then
    return new;
  end if;

  select decrypted_secret
    into v_token
  from vault.decrypted_secrets
  where name='gmu_internal_pdf_token'
  limit 1;

  if nullif(v_token,'') is null then
    return new;
  end if;

  begin
    select net.http_post(
      url := 'https://gtgnwasijweewmaubvyg.supabase.co/functions/v1/internal-commercial-pdf',
      headers := jsonb_build_object('Content-Type','application/json'),
      body := jsonb_build_object(
        'action','quotation',
        'id',new.id::text,
        'mode','auto',
        'internal_token',v_token
      )
    ) into v_request_id;
  exception when others then
    -- PDF generation is asynchronous and must never roll back the booking.
    null;
  end;

  return new;
end;
$$;

revoke all on function private.queue_sales_quotation_pdf() from public, anon, authenticated;

drop trigger if exists trg_auto_sales_quotation_pdf on public.quotations;
create trigger trg_auto_sales_quotation_pdf
after insert on public.quotations
for each row
execute function private.queue_sales_quotation_pdf();
