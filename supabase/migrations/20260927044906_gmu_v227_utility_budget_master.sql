-- GMU EduTrans ERP v22.7 — Utility Budget Master
-- Data-only migration: establishes the monthly utility ceiling and September 2026 planning budget.
-- Trip-specific costs remain authoritative in RAB/HPP and must not be duplicated here.

insert into public.company_control_settings(setting_key,numeric_value,text_value,description)
values
  ('MONTHLY_UTILITY_BUDGET',1755000,'GMU_EDUTRANS_UTILITIES','Plafon utilitas bulanan GMU EduTrans'),
  ('UTILITY_RECURRING_BUDGET',1155000,'RECURRING','Internet, listrik, kuota, ChatGPT Plus, CapCut Pro, Canva Pro, domain/email/sistem'),
  ('UTILITY_FLEXIBLE_BUDGET',600000,'FLEXIBLE','Admin bank, ATK, maintenance, kebersihan, perlengkapan kecil, transport umum, tak terduga')
on conflict (setting_key) do update
set numeric_value=excluded.numeric_value,
    text_value=excluded.text_value,
    description=excluded.description,
    updated_at=now();

insert into public.planning_budgets(period_month,budget_type,category,amount,notes)
select date '2026-09-01','OVERHEAD',x.category,x.amount,x.notes
from (values
  ('Utilitas - Internet/Wi-Fi',225000::numeric,'Recurring • budget bulanan'),
  ('Utilitas - Listrik',200000::numeric,'Recurring • budget bulanan'),
  ('Utilitas - Kuota/Paket Data',80000::numeric,'Recurring • budget bulanan'),
  ('Utilitas - ChatGPT Plus',350000::numeric,'Recurring • plafon termasuk ruang kurs/pajak'),
  ('Utilitas - CapCut Pro',130000::numeric,'Recurring • budget bulanan'),
  ('Utilitas - Canva Pro',95000::numeric,'Recurring • budget bulanan'),
  ('Utilitas - Domain, Email & Sistem Digital',75000::numeric,'Recurring • budget bulanan'),
  ('Utilitas - Administrasi Bank/Transaksi',50000::numeric,'Flexible • sesuai realisasi'),
  ('Utilitas - ATK & Cetak Umum',75000::numeric,'Flexible • sesuai realisasi'),
  ('Utilitas - Maintenance/Perawatan Alat',100000::numeric,'Flexible • sesuai realisasi'),
  ('Utilitas - Kebersihan & Kebutuhan Umum',50000::numeric,'Flexible • sesuai realisasi'),
  ('Utilitas - Perlengkapan Operasional Kecil',50000::numeric,'Flexible • sesuai realisasi'),
  ('Utilitas - BBM/Transport Operasional Umum',200000::numeric,'Flexible • bukan biaya trip tertentu'),
  ('Utilitas - Dana Tak Terduga',75000::numeric,'Flexible • sesuai realisasi')
) as x(category,amount,notes)
where not exists (
  select 1
  from public.planning_budgets b
  where b.period_month=date '2026-09-01'
    and b.budget_type='OVERHEAD'
    and b.category=x.category
);
