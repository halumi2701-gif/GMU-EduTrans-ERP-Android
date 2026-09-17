-- GMU EduTrans Sales App v6.5
-- Align September 2026 portfolio commission with the locked commercial rule:
-- Sales commission = Rp5.000 per eligible paid pax.
-- Target and bonus semantics are intentionally left unchanged.

update public.sales_portfolio_targets
set sales_fee_per_paid_pax = 5000.00,
    notes = 'Target Sales App: 400 paid pax/bulan. Komisi Sales Rp5.000/pax. Bonus target tidak dihitung otomatis sampai kebijakan bonus profit-funded disetujui.',
    updated_at = now()
where period_month = date '2026-09-01'
  and target_key = 'ALL_PROGRAMS'
  and status = 'ACTIVE'
  and sales_fee_per_paid_pax is distinct from 5000.00;
