-- GMU EduTrans ERP v22.8 — Align Sales commission to final policy
-- Rp50.000 per 20 paid pax = Rp2.500 per paid pax.

update public.sales_portfolio_targets
set sales_fee_per_paid_pax=2500,
    updated_at=now(),
    notes=concat_ws(' • ',nullif(notes,''),'Komisi Sales final Rp50.000 per 20 paid pax = Rp2.500/pax')
where target_key='ALL_PROGRAMS' and status='ACTIVE';
