-- Sales quotation WhatsApp SENT state v6.5.3
-- Mirrors the production RPC used after the Sales app successfully opens WhatsApp
-- with the official quotation PDF attached.

create or replace function public.gmu_sales_mark_quotation_sent(p_quotation_id uuid)
returns table(quotation_no text, status text)
language plpgsql
security definer
set search_path to ''
as $function$
declare
  v_uid uuid := (select auth.uid());
  v_role text;
  v_quote public.quotations%rowtype;
  v_was_sent boolean := false;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;

  select p.role::text into v_role
  from public.profiles p
  where p.id = v_uid and p.is_active = true;

  if v_role is distinct from 'Sales' then
    raise exception 'Sales role required';
  end if;

  select q.* into v_quote
  from public.quotations q
  join public.booking_requests br on br.id = q.booking_request_id
  where q.id = p_quotation_id
    and br.assigned_sales = v_uid;

  if not found then
    raise exception 'Quotation not found or not assigned to this Sales';
  end if;

  if v_quote.status not in ('DRAFT','SENT') then
    raise exception 'Quotation status % cannot be marked sent', v_quote.status;
  end if;

  v_was_sent := v_quote.status = 'SENT';

  update public.quotations q
  set status = 'SENT',
      sent_at = coalesce(q.sent_at, now()),
      sent_by = coalesce(q.sent_by, v_uid),
      updated_at = now()
  where q.id = v_quote.id;

  update public.documents d
  set status = 'Published',
      customer_visible = true,
      published_at = coalesce(d.published_at, now())
  where d.document_no = v_quote.quotation_no
    and lower(d.document_type) = 'quotation';

  update public.booking_requests br
  set status = case
      when br.status in ('NEW_REQUEST','VERIFICATION','WAITING_DP') then 'QUOTATION'
      else br.status
    end,
      updated_at = now()
  where br.id = v_quote.booking_request_id
    and br.converted_booking_id is null;

  insert into public.crm_lead_controls(
    booking_request_id,stage,owner_id,lead_source,probability_pct,
    last_contact_at,next_follow_up_at,created_by,updated_by
  ) values (
    v_quote.booking_request_id,'QUOTATION',v_uid,'Sales App',60,
    now(),now()+interval '2 days',v_uid,v_uid
  )
  on conflict (booking_request_id) do update
    set stage = case
          when public.crm_lead_controls.stage='WON' then 'WON'
          else 'QUOTATION'
        end,
        probability_pct = case
          when public.crm_lead_controls.stage='WON' then 100
          else greatest(public.crm_lead_controls.probability_pct,60)
        end,
        owner_id = v_uid,
        last_contact_at = now(),
        next_follow_up_at = case
          when public.crm_lead_controls.stage='WON' then null
          else now()+interval '2 days'
        end,
        updated_by = v_uid,
        updated_at = now();

  if not v_was_sent then
    insert into public.crm_activities(
      booking_request_id,sales_id,channel,activity_type,outcome,
      lead_source,notes,next_follow_up_at,created_by
    ) values (
      v_quote.booking_request_id,v_uid,'WHATSAPP','QUOTATION_SENT',
      'Quotation dikirim; menunggu keputusan customer',
      'Sales App',v_quote.quotation_no,now()+interval '2 days',v_uid
    );
  end if;

  return query
  select v_quote.quotation_no, 'SENT'::text;
end;
$function$;
