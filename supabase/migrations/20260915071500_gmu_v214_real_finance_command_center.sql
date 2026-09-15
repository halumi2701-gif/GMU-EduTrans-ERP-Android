-- GMU EduTrans v21.4 — real-source Company Control summary
-- Revenue comes from Trip Closing, cash from verified payments, pipeline from CRM.

create or replace view public.v_company_command_center
with (security_invoker=true)
as
select
  date_trunc('month',now())::date as period_month,
  (select numeric_value from public.company_control_settings where setting_key='MONTHLY_REVENUE_TARGET') as revenue_target,
  (select numeric_value from public.company_control_settings where setting_key='HEALTHY_MARGIN_FLOOR_PCT') as healthy_margin_floor_pct,
  (select numeric_value from public.company_control_settings where setting_key='PIPELINE_COVERAGE_TARGET_X') as pipeline_coverage_target_x,
  coalesce((select sum(tc.contract_revenue) from public.trip_closings tc where tc.closed_at>=date_trunc('month',now()) and tc.closed_at<date_trunc('month',now())+interval '1 month'),0) as revenue_realized,
  coalesce((select sum(p.amount) from public.payments p where p.verified_at is not null and p.verified_at>=date_trunc('month',now()) and p.verified_at<date_trunc('month',now())+interval '1 month'),0) as cash_collected,
  coalesce((select sum(c.estimated_value*c.probability_pct/100) from public.crm_lead_controls c where c.stage not in ('WON','LOST')),0) as pipeline_value,
  coalesce((select count(*) from public.payment_requests where approval_state='SUBMITTED'),0) as payments_waiting_approval,
  coalesce((select count(*) from public.payment_requests where payment_state='UNPAID' and due_date<current_date),0) as overdue_payments,
  coalesce((select count(*) from public.recruitment_cases where status in ('NEED_REVIEW','APPROVED','SOURCING','INTERVIEW','OFFER','ONBOARDING')),0) as open_recruitments,
  coalesce((select count(*) from public.compensation_accruals where state='ON_HOLD'),0) as compensation_on_hold;

grant select on public.v_company_command_center to authenticated;
