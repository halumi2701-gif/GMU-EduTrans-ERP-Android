-- GMU EduTrans ERP v22.8 — Restore final Sales commission policy
-- Final Sales commission: Rp5.000 per paid pax.
-- Package Stasiun pricing/HPP is intentionally untouched.

update public.sales_portfolio_targets
set sales_fee_per_paid_pax=5000,
    updated_at=now(),
    notes=concat_ws(' • ',nullif(notes,''),'Komisi Sales final Rp5.000 per paid pax')
where target_key='ALL_PROGRAMS' and status='ACTIVE';

insert into public.company_control_settings(setting_key,numeric_value,text_value,description)
values
  ('SALES_COMMISSION_PER_PAX',5000,'IDR_PER_PAX','Komisi Sales final Rp5.000 per paid pax'),
  ('SALES_COMMISSION_PER_20_PAX',100000,'IDR_PER_20_PAX','Nilai ekuivalen backward compatibility: Rp5.000/pax × 20 pax')
on conflict (setting_key) do update
set numeric_value=excluded.numeric_value,
    text_value=excluded.text_value,
    description=excluded.description,
    updated_at=now();
