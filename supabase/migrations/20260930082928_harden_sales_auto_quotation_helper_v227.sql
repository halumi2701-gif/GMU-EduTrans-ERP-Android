-- Harden private Sales auto-quotation helper
-- Production migration: 20260930082928
revoke all on function private.gmu_auto_quotation_from_booking(text) from public, anon, authenticated;
