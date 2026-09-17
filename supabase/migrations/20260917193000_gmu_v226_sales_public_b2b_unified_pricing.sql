-- GMU EduTrans v22.6 — SUPERSEDED MIGRATION MARKER
-- STATION-PROF-2026
--
-- The original monolithic draft did not match production package status/activation guards.
-- It must never recreate the obsolete draft logic.
-- Production-aligned v22.6 is split into the exact migrations below:
--   20260917171320_gmu_v226_station_package_cost_master.sql
--   20260917171422_gmu_v226_sales_public_b2b_runtime.sql
--   20260917171535_gmu_v226_sales_rpc_acl_hardening.sql
--
-- This marker intentionally performs no business-data mutation. It is retained only
-- because earlier release validation checks referenced this historical filename.

select 1;