-- Fix Planning & Business Control permission chain
-- internal-planning-control executes through service_role and reaches this private helper.
grant execute on function private.vendor_booking_settlement_summary(text) to service_role;
