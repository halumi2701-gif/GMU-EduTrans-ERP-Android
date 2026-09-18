-- Sales App v6.5 production release health.
-- Public-safe: returns readiness booleans only; no customer data, HPP, profit, margin, or credentials.

create or replace function public.gmu_sales_release_health_v65()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_e2e_ok boolean := false;
  v_catalog_ok boolean := false;
  v_price jsonb := '{}'::jsonb;
  v_pricing_ok boolean := false;
begin
  begin
    select coalesce(bool_and(healthy), false)
      into v_e2e_ok
      from public.gmu_sales_e2e_health();
  exception when others then
    v_e2e_ok := false;
  end;

  select exists (
    select 1
    from public.v_sales_commercial_catalog_v226
    where package_code = 'STATION-PROF-2026'
    limit 1
  ) into v_catalog_ok;

  begin
    v_price := public.resolve_sales_price_v226(
      'STATION-PROF-2026',
      20,
      'DIRECT_PUBLIC',
      null,
      'PRIVATE',
      20,
      null
    );
    v_pricing_ok :=
      coalesce((v_price->>'eligible')::boolean, false)
      and coalesce(v_price->>'status','') = 'SAFE'
      and coalesce((v_price->>'customer_price_per_pax')::numeric,0) = 65000
      and coalesce((v_price->>'sales_commission_per_pax')::numeric,0) = 5000;
  exception when others then
    v_pricing_ok := false;
  end;

  return jsonb_build_object(
    'healthy', (v_e2e_ok and v_catalog_ok and v_pricing_ok),
    'e2e_healthy', v_e2e_ok,
    'catalog_ready', v_catalog_ok,
    'pricing_contract_ready', v_pricing_ok,
    'contract_version', 'sales-v6.5/v22.6'
  );
exception when others then
  return jsonb_build_object(
    'healthy', false,
    'e2e_healthy', false,
    'catalog_ready', false,
    'pricing_contract_ready', false,
    'contract_version', 'sales-v6.5/v22.6'
  );
end;
$$;

revoke all on function public.gmu_sales_release_health_v65() from public;
grant execute on function public.gmu_sales_release_health_v65() to anon, authenticated;
