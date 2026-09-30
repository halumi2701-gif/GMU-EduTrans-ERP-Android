\set ON_ERROR_STOP on
begin;
select set_config(
  'request.jwt.claim.sub',
  (
    select id::text
    from public.profiles
    where is_active=true
      and role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')
    order by case role::text
      when 'Owner' then 1
      when 'Director' then 2
      when 'Direktur' then 3
      when 'Manager' then 4
      else 5
    end
    limit 1
  ),
  true
);

with customer as (
  select id
  from public.customers
  order by created_at asc nulls last
  limit 1
),
created as (
  select public.create_sales_booking_v226(
    (select id from customer),
    'STATION-PROF-2026',
    current_date + 30,
    20,
    'DIRECT_PUBLIC',
    null,
    'PRIVATE',
    20,
    'Lead',
    'SYSTEM E2E GOOGLE DRIVE TEST',
    'CI TEST ONLY',
    null
  ) as j
)
select
  j->>'booking_id',
  j->>'booking_no',
  (
    select coalesce(nullif(trim(c.name),''),'CUSTOMER')
    from public.customers c
    join public.bookings b on b.customer_id=c.id
    where b.id=(j->>'booking_id')::uuid
  ),
  (current_date + 30)::text
from created;
commit;
