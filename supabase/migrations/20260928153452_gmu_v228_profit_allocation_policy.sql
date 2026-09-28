-- GMU EduTrans ERP v22.8 — Profit Allocation, Recovery & Internal Cost Policy
-- Business rule: target 400 paid pax/month; after all booked costs, positive net profit is split 50% recovery, 30% company cash, 20% Owner.

insert into public.company_control_settings(setting_key,numeric_value,text_value,description)
values
  ('MONTHLY_PAX_TARGET',400,'PAID_PAX','Target utama GMU EduTrans: 400 paid pax per bulan lintas seluruh program'),
  ('LOSS_RECOVERY_TARGET',50000000,'IDR','Target pemulihan kerugian/utang GMU EduTrans'),
  ('PROFIT_SPLIT_RECOVERY_PCT',50,'PCT','50% laba bersih siap dibagi dialokasikan untuk bayar utang/recovery'),
  ('PROFIT_SPLIT_COMPANY_CASH_PCT',30,'PCT','30% laba bersih siap dibagi ditahan sebagai kas perusahaan'),
  ('PROFIT_SPLIT_OWNER_PCT',20,'PCT','20% laba bersih siap dibagi menjadi hak Owner'),
  ('FIELD_CREW_MEAL_PER_PERSON',12000,'IDR_PER_PERSON_PER_TRIP','Uang makan crew lapangan per orang yang benar-benar hadir per kegiatan'),
  ('INTERNAL_MANAGER_FEE_PER_TRIP',130000,'IDR_PER_TRIP','Fee Manager EduTrans per kegiatan'),
  ('INTERNAL_TL_MC_FEE_PER_TRIP',110000,'IDR_PER_TRIP','Fee TL/MC per kegiatan'),
  ('INTERNAL_OPS_DOC_FEE_PER_TRIP',55000,'IDR_PER_TRIP','Fee Ops + Dokumentasi per kegiatan'),
  ('SALES_FIXED_MONTHLY',600000,'IDR_PER_MONTH','Fixed Sales bulanan'),
  ('SALES_COMMISSION_PER_20_PAX',50000,'IDR_PER_20_PAX','Komisi Sales Rp50.000 per 20 pax'),
  ('ADMIN_PART_TIME_MONTHLY',450000,'IDR_PER_MONTH','Honor Admin part-time bulanan'),
  ('FINANCE_PART_TIME_MONTHLY',550000,'IDR_PER_MONTH','Honor Finance part-time bulanan'),
  ('OWNER_FIXED_SALARY',0,'IDR_PER_MONTH','Owner tidak memakai gaji tetap; hak Owner berasal dari 20% laba bersih siap dibagi')
on conflict (setting_key) do update
set numeric_value=excluded.numeric_value,
    text_value=excluded.text_value,
    description=excluded.description,
    updated_at=now();

insert into public.company_control_settings(setting_key,numeric_value,text_value,description)
values ('LOSS_RECOVERY_PAID_TO_DATE',0,'IDR','Akumulasi recovery/utang yang sudah benar-benar dibayar; tidak otomatis menimpa nilai lama')
on conflict (setting_key) do nothing;

update public.sales_portfolio_targets
set target_paid_pax=400,
    stretch_paid_pax=500,
    outstanding_paid_pax=600,
    notes='Target utama GMU EduTrans 400 paid pax/bulan lintas seluruh program. BEP 60, produktif 100; stretch 500, outstanding 600. Sales retainer dan fee per pax tetap mengikuti policy aktif.',
    updated_at=now()
where period_month=date '2026-09-01'
  and target_key='ALL_PROGRAMS';

update public.planning_targets
set target_pax=400,
    notes=concat_ws(' • ', nullif(notes,''), '[2026-09-28] Target utama perusahaan dikunci 400 pax/bulan'),
    updated_at=now()
where period_month=date '2026-09-01'
  and scope_type='COMPANY'
  and sales_id is null
  and program_id is null;
