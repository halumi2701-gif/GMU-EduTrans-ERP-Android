-- Fix Planning & Business Control dependency permissions for service_role.
grant execute on function private.vendor_po_effective_commitment(public.vendor_pos) to service_role;
grant execute on function private.vendor_po_settlement_summary(text) to service_role;

grant execute on function private.treasury_ar_calendar(date,date) to service_role;
grant execute on function private.treasury_ap_calendar(date,date) to service_role;
grant execute on function private.treasury_forecast_series(integer) to service_role;
grant execute on function private.treasury_current_cash(date) to service_role;
grant execute on function private.treasury_minimum_reserve() to service_role;

grant execute on function private.bank_import_summary(uuid) to service_role;
grant execute on function private.gl_profit_loss(date,date) to service_role;
grant execute on function private.gl_balance_sheet(date) to service_role;
grant execute on function private.gl_cash_flow(date,date) to service_role;
grant execute on function private.gl_accounting_integrity(date,date) to service_role;
grant execute on function private.executive_finance_dashboard(date,date,date) to service_role;
