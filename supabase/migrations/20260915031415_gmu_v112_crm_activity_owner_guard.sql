create or replace function private.gmu_v112_crm_activity_rollup()
returns trigger language plpgsql security definer set search_path=public,private,pg_temp as $$
declare
  v_request_id uuid := new.booking_request_id;
  v_sales_owner uuid;
begin
  if v_request_id is null and new.booking_id is not null then
    select br.id into v_request_id from public.booking_requests br where br.converted_booking_id=new.booking_id order by br.updated_at desc limit 1;
  end if;
  if v_request_id is null and new.customer_id is not null then
    select br.id into v_request_id from public.booking_requests br where br.converted_customer_id=new.customer_id or br.erp_lead_customer_id=new.customer_id order by br.updated_at desc limit 1;
  end if;
  if v_request_id is null then return new; end if;

  select p.id into v_sales_owner from public.profiles p where p.id=new.sales_id and p.is_active=true and p.role::text='Sales' limit 1;

  insert into public.crm_lead_controls(booking_request_id,stage,owner_id,lead_source,last_contact_at,next_follow_up_at,lost_reason,lost_at,probability_pct,created_by,updated_by)
  select v_request_id,
         case when nullif(new.lost_reason,'') is not null then 'LOST' else 'CONTACTED' end,
         coalesce(v_sales_owner,br.assigned_sales),coalesce(new.lead_source,br.source),new.created_at,new.next_follow_up_at,nullif(new.lost_reason,''),
         case when nullif(new.lost_reason,'') is not null then new.created_at end,
         case when nullif(new.lost_reason,'') is not null then 0 else 20 end,
         coalesce(new.created_by,new.sales_id),(select auth.uid())
  from public.booking_requests br where br.id=v_request_id
  on conflict (booking_request_id) do update set
    owner_id=coalesce(excluded.owner_id,public.crm_lead_controls.owner_id),
    lead_source=coalesce(excluded.lead_source,public.crm_lead_controls.lead_source),
    last_contact_at=greatest(coalesce(public.crm_lead_controls.last_contact_at,'epoch'::timestamptz),excluded.last_contact_at),
    next_follow_up_at=coalesce(excluded.next_follow_up_at,public.crm_lead_controls.next_follow_up_at),
    lost_reason=coalesce(excluded.lost_reason,public.crm_lead_controls.lost_reason),
    lost_at=coalesce(excluded.lost_at,public.crm_lead_controls.lost_at),
    stage=case when excluded.lost_reason is not null then 'LOST' when public.crm_lead_controls.stage='NEW' then 'CONTACTED' else public.crm_lead_controls.stage end,
    probability_pct=case when excluded.lost_reason is not null then 0 when public.crm_lead_controls.stage='NEW' then 20 else public.crm_lead_controls.probability_pct end,
    updated_by=(select auth.uid()),updated_at=now();
  return new;
end;
$$;
revoke all on function private.gmu_v112_crm_activity_rollup() from public, anon, authenticated;
