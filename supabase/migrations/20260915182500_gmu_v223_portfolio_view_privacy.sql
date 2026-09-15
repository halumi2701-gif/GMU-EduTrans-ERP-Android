-- GMU EduTrans v22.3 — Finance privacy hardening for portfolio target
-- Dashboard access must go through safe RPCs. Raw attribution/cash views are not directly exposed.

revoke all on public.v_sales_paid_booking_attribution from public, anon, authenticated;
revoke all on public.v_sales_portfolio_program_monthly from public, anon, authenticated;
revoke all on public.v_sales_portfolio_monthly from public, anon, authenticated;
revoke all on public.v_sales_portfolio_by_sales_monthly from public, anon, authenticated;

-- Ticket base cost + handling split is internal finance information.
drop policy if exists program_package_addons_management_read on public.program_package_addons;
create policy program_package_addons_management_read on public.program_package_addons
for select to authenticated using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid()) and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')
  )
);

comment on view public.v_sales_paid_booking_attribution is 'Internal raw paid-pax attribution. Direct authenticated access revoked; use safe RPCs.';
comment on view public.v_sales_portfolio_monthly is 'Internal aggregate source. Direct authenticated access revoked; ERP uses gmu_sales_portfolio_summary().' ;
