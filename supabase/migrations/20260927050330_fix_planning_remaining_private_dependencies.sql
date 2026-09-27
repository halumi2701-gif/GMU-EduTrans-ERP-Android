-- Complete service_role execution chain for Planning & Business Control.
-- These helpers are read/calculation dependencies reached by internal-planning-control.

grant execute on function private.treasury_account_balance(uuid,date) to service_role;
grant execute on function private.treasury_account_opening_balance(uuid,date) to service_role;
grant execute on function private.bank_ledger_matched_amount(uuid) to service_role;
grant execute on function private.gl_trial_balance(date) to service_role;
