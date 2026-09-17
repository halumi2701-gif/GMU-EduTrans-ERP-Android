-- GMU EduTrans Sales v6.4 security hardening
-- Trigger-only SECURITY DEFINER functions must never be directly executable by anon/authenticated API roles.

revoke execute on function public.internal_recalc_invoice_from_payment() from public, anon, authenticated;
revoke execute on function public.internal_sales_sync_dp_handover() from public, anon, authenticated;
revoke execute on function public.internal_sync_sales_handover_from_booking_request() from public, anon, authenticated;

grant execute on function public.internal_recalc_invoice_from_payment() to service_role;
grant execute on function public.internal_sales_sync_dp_handover() to service_role;
grant execute on function public.internal_sync_sales_handover_from_booking_request() to service_role;
