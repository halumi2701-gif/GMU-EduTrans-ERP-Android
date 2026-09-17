-- GMU EduTrans v22.6 — Explicit RPC ACL hardening
-- PUBLIC/anon must never execute internal Sales pricing/booking RPCs.

revoke all on function public.resolve_sales_price_v226(text,integer,text,uuid,text,integer,date) from public;
revoke all on function public.resolve_sales_price_v226(text,integer,text,uuid,text,integer,date) from anon;
grant execute on function public.resolve_sales_price_v226(text,integer,text,uuid,text,integer,date) to authenticated;

revoke all on function public.create_sales_booking_v226(uuid,text,date,integer,text,uuid,text,integer,text,text,text,text) from public;
revoke all on function public.create_sales_booking_v226(uuid,text,date,integer,text,uuid,text,integer,text,text,text,text) from anon;
grant execute on function public.create_sales_booking_v226(uuid,text,date,integer,text,uuid,text,integer,text,text,text,text) to authenticated;