-- GMU EduTrans Sales App v2.1 — safe quotation sent flow, DP state, automatic CRM handover.

create or replace function public.gmu_sales_mark_quotation_sent(p_quotation_id uuid)
returns table(quotation_no text, status text)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_role text;
  v_quote public.quotations%rowtype;
  v_request public.booking_requests%rowtype;
  v_was_sent boolean := false;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
  if v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;

  select q.* into v_quote
  from public.quotations q
  join public.booking_requests br on br.id=q.booking_request_id
  where q.id=p_quotation_id and br.assigned_sales=v_uid;
  if not found then raise exception 'Quotation not found or not assigned to this Sales'; end if;
  if v_quote.status not in ('DRAFT','SENT') then raise exception 'Quotation status % cannot be marked sent',v_quote.status; end if;

  v_was_sent := v_quote.status='SENT';
  select * into v_request from public.booking_requests br where br.id=v_quote.booking_request_id;

  update public.quotations q
  set status='SENT',sent_at=coalesce(q.sent_at,now()),sent_by=coalesce(q.sent_by,v_uid),updated_at=now()
  where q.id=v_quote.id;

  update public.booking_requests br
  set status=case when br.status in ('NEW_REQUEST','VERIFICATION','QUOTATION') then 'WAITING_DP' else br.status end,
      updated_at=now()
  where br.id=v_quote.booking_request_id;

  insert into public.crm_lead_controls(
    booking_request_id,stage,owner_id,lead_source,probability_pct,last_contact_at,next_follow_up_at,created_by,updated_by
  ) values (
    v_quote.booking_request_id,'WAITING_DP',v_uid,'Sales App',85,now(),now()+interval '3 days',v_uid,v_uid
  )
  on conflict (booking_request_id) do update
    set stage=case when public.crm_lead_controls.stage='WON' then 'WON' else 'WAITING_DP' end,
        probability_pct=case when public.crm_lead_controls.stage='WON' then 100 else greatest(public.crm_lead_controls.probability_pct,85) end,
        owner_id=v_uid,last_contact_at=now(),
        next_follow_up_at=case when public.crm_lead_controls.stage='WON' then null else now()+interval '3 days' end,
        updated_by=v_uid,updated_at=now();

  if not v_was_sent then
    insert into public.crm_activities(
      booking_request_id,sales_id,channel,activity_type,outcome,lead_source,notes,next_follow_up_at,created_by
    ) values (
      v_quote.booking_request_id,v_uid,'WHATSAPP','QUOTATION_SENT',
      'Quotation ditandai terkirim; customer menunggu DP','Sales App',v_quote.quotation_no,now()+interval '3 days',v_uid
    );
  end if;
  return query select v_quote.quotation_no,'SENT'::text;
end;
$$;
revoke all on function public.gmu_sales_mark_quotation_sent(uuid) from public,anon;
grant execute on function public.gmu_sales_mark_quotation_sent(uuid) to authenticated;

create or replace function public.gmu_sales_booking_payment_states()
returns table(booking_id text,payment_state text,verified_at timestamptz)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_role text;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  select p.role::text into v_role from public.profiles p where p.id=v_uid and p.is_active=true;
  if v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;

  return query
  select b.id,
    case
      when exists(select 1 from public.payments p where p.booking_id=b.id and coalesce(p.amount,0)>0 and (p.verified_at is not null or p.verified_by is not null)) then 'DP_TERVERIFIKASI'
      when exists(select 1 from public.payments p where p.booking_id=b.id and coalesce(p.amount,0)>0) then 'MENUNGGU_VERIFIKASI'
      else 'BELUM_ADA_PEMBAYARAN'
    end::text,
    (select max(coalesce(p.verified_at,p.created_at)) from public.payments p where p.booking_id=b.id and coalesce(p.amount,0)>0 and (p.verified_at is not null or p.verified_by is not null))
  from public.bookings b
  where b.sales_id=v_uid
  order by b.created_at desc;
end;
$$;
revoke all on function public.gmu_sales_booking_payment_states() from public,anon;
grant execute on function public.gmu_sales_booking_payment_states() to authenticated;

create or replace function private.gmu_sales_sync_handover_after_verified_payment()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_new_verified boolean;
  v_old_verified boolean := false;
  v_request_id uuid;
  v_sales_id uuid;
  v_prev_stage text;
begin
  v_new_verified := coalesce(new.amount,0)>0 and (new.verified_at is not null or new.verified_by is not null);
  if tg_op='UPDATE' then
    v_old_verified := coalesce(old.amount,0)>0 and (old.verified_at is not null or old.verified_by is not null);
  end if;
  if not v_new_verified or v_old_verified then return new; end if;

  select br.id,br.assigned_sales into v_request_id,v_sales_id
  from public.booking_requests br
  where br.assigned_sales is not null and (
    br.converted_booking_id=new.booking_id or br.erp_lead_booking_id=new.booking_id
    or exists(select 1 from public.quotations q where q.booking_request_id=br.id and q.booking_id=new.booking_id)
  )
  order by br.updated_at desc limit 1;
  if v_request_id is null then return new; end if;

  select c.stage into v_prev_stage from public.crm_lead_controls c where c.booking_request_id=v_request_id;
  update public.booking_requests br
  set status=case when br.status in ('NEW_REQUEST','VERIFICATION','QUOTATION','WAITING_DP') then 'CONFIRMED' else br.status end,
      updated_at=now()
  where br.id=v_request_id;

  insert into public.crm_lead_controls(
    booking_request_id,stage,owner_id,lead_source,probability_pct,last_contact_at,next_follow_up_at,won_at,created_by,updated_by
  ) values (
    v_request_id,'WON',v_sales_id,'Sales App',100,now(),null,now(),v_sales_id,v_sales_id
  )
  on conflict (booking_request_id) do update
    set stage='WON',probability_pct=100,owner_id=v_sales_id,last_contact_at=now(),next_follow_up_at=null,
        won_at=coalesce(public.crm_lead_controls.won_at,now()),updated_by=v_sales_id,updated_at=now();

  if v_prev_stage is distinct from 'WON' then
    insert into public.crm_activities(
      booking_request_id,sales_id,channel,activity_type,outcome,lead_source,notes,created_by
    ) values (
      v_request_id,v_sales_id,'LAINNYA','DP_VERIFIED_HANDOVER',
      'DP/pembayaran terverifikasi Finance; lead WON dan siap handover ke Admin/Manager Ops','Sales App',new.booking_id,v_sales_id
    );
  end if;
  return new;
end;
$$;
revoke all on function private.gmu_sales_sync_handover_after_verified_payment() from public,anon,authenticated;

drop trigger if exists trg_sales_handover_after_verified_payment on public.payments;
create trigger trg_sales_handover_after_verified_payment
after insert or update of booking_id,amount,verified_at,verified_by on public.payments
for each row execute function private.gmu_sales_sync_handover_after_verified_payment();

notify pgrst,'reload schema';
