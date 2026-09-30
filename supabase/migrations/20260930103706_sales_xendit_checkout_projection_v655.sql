create or replace function public.gmu_sales_my_quotations_v3()
returns table(
  id uuid,
  quotation_no text,
  booking_request_id uuid,
  booking_code text,
  access_token uuid,
  institution_name text,
  pic_name text,
  whatsapp text,
  program_name text,
  pax integer,
  status text,
  total numeric,
  valid_until date,
  created_at timestamptz,
  customer_decision text,
  decision_status text,
  invoice_id uuid,
  invoice_no text,
  invoice_status text,
  invoice_total numeric,
  dp_percent numeric,
  payment_order_no text,
  payment_status text,
  payment_checkout_url text,
  payment_expires_at timestamptz
)
language plpgsql
security definer
set search_path='public','auth'
as $$
declare
  v_uid uuid:=auth.uid();
  v_role text;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  select p.role::text into v_role
  from public.profiles p
  where p.id=v_uid and p.is_active=true;
  if v_role is distinct from 'Sales' then raise exception 'Sales role required'; end if;

  return query
  select
    q.id,q.quotation_no,br.id,br.booking_code,br.access_token,
    br.institution_name,br.pic_name,br.whatsapp,
    coalesce(nullif(br.custom_program,''),pr.name,'Program GMU EduTrans')::text,
    br.pax,q.status,q.total,q.valid_until,q.created_at,
    d.decision,d.status,
    inv.id,inv.invoice_no,inv.status,inv.total,inv.dp_percent,
    pg.order_no,pg.status,pg.checkout_url,pg.expires_at
  from public.quotations q
  join public.booking_requests br on br.id=q.booking_request_id
  left join public.programs pr on pr.id=br.program_id
  left join public.customer_quotation_decisions d on d.quotation_id=q.id
  left join lateral(
    select i.id,i.invoice_no,i.status,i.total,i.dp_percent
    from public.invoices i
    where i.booking_request_id=br.id and i.quotation_id=q.id
    order by i.created_at desc
    limit 1
  ) inv on true
  left join lateral(
    select o.order_no,o.status,o.checkout_url,o.expires_at
    from public.payment_gateway_orders o
    where o.invoice_id=inv.id and o.provider='XENDIT'
    order by o.created_at desc
    limit 1
  ) pg on true
  where br.assigned_sales=v_uid
  order by q.created_at desc;
end;
$$;

revoke all on function public.gmu_sales_my_quotations_v3() from public,anon;
grant execute on function public.gmu_sales_my_quotations_v3() to authenticated;
