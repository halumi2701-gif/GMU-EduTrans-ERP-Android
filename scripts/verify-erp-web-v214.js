const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const fail = [];
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const exists = rel => fs.existsSync(path.join(root, rel));
const mustExist = rel => { if (!exists(rel)) fail.push(`Missing: ${rel}`); };
const mustContain = (rel, marker) => {
  if (!exists(rel)) { fail.push(`Missing: ${rel}`); return; }
  if (!read(rel).includes(marker)) fail.push(`Missing marker in ${rel}: ${marker}`);
};

[
  'erp-web/role-privacy-v99.js','erp-web/package-master-v97.js','erp-web/media-master-v96.js','erp-web/manager-ops-agent-v98.js',
  'erp-web/company-operating-system-v100.js','erp-web/market-intelligence-v101.js','erp-web/sales-target-engine-v103.js',
  'erp-web/company-system-center-v104.js','erp-web/management-domains-v105.js','erp-web/role-navigation-v106.js',
  'erp-web/company-detail-controls-v107.js','erp-web/role-playbook-v108.js','erp-web/execution-control-v109.js',
  'erp-web/ui-focus-shell-v110.js','erp-web/automation-orchestrator-v111.js','erp-web/automation-orchestrator-fix-v111.js',
  'erp-web/crm-finance-notification-v112.js','erp-web/enterprise-os-v200.js','erp-web/business-priority-v201.js',
  'erp-web/recovery-command-v202.js','erp-web/company-autopilot-v210.js','erp-web/target-cascade-v212.js',
  'erp-web/compensation-autopilot-v213.js','erp-web/company-control-v214.js','erp-web/unified-v10-loader.js'
].forEach(mustExist);

mustContain('erp-web/unified-v10-loader.js', 'v21.4-company-control-center');
mustContain('erp-web/unified-v10-loader.js', 'compensation-autopilot-v213.js');
mustContain('erp-web/unified-v10-loader.js', 'company-control-v214.js');
mustContain('erp-web/company-control-v214.js', "const VERSION = 'v21.4-performance-payroll-workforce-capacity'");
mustContain('erp-web/company-control-v214.js', 'internal_performance_payroll_v214_status');
mustContain('erp-web/company-control-v214.js', 'Target Direktur Rp50 jt');
mustContain('erp-web/company-control-v214.js', 'Recovery Gap');
mustContain('erp-web/company-control-v214.js', 'Recalculate Gaji / Fee');
mustContain('erp-web/company-control-v214.js', 'WhatsApp automation tetap dilewati');
mustContain('erp-web/company-control-v214.js', 'payment_requests');
mustContain('erp-web/company-control-v214.js', 'compensation_accruals');
mustContain('erp-web/company-control-v214.js', 'recruitment_cases');

const baseMigration = 'supabase/migrations/20260915070000_gmu_v214_company_control_finance_hr.sql';
const engineMigration = 'supabase/migrations/20260915071000_gmu_v214_performance_payroll_workforce_capacity.sql';
mustExist(baseMigration);
mustExist(engineMigration);
mustContain(baseMigration, "('MONTHLY_REVENUE_TARGET',50000000");
mustContain(baseMigration, "('HEALTHY_MARGIN_FLOOR_PCT',25");
mustContain(baseMigration, 'create table if not exists public.payment_requests');
mustContain(baseMigration, 'create table if not exists public.compensation_accruals');
mustContain(baseMigration, 'create table if not exists public.recruitment_candidates');
mustContain(baseMigration, 'create or replace view public.v_company_command_center');
mustContain(baseMigration, 'with (security_invoker=true)');

mustContain(engineMigration, 'Performance-Based Payroll & Workforce Capacity');
mustContain(engineMigration, 'WhatsApp is intentionally NOT part of this automation layer');
mustContain(engineMigration, 'create table if not exists public.performance_payroll_snapshots');
mustContain(engineMigration, 'create table if not exists public.workforce_capacity_reviews');
mustContain(engineMigration, 'alter table public.payroll_entries add column if not exists source_accrual_id');
mustContain(engineMigration, 'perform private.gmu_v212_refresh_target_cascade()');
mustContain(engineMigration, 'perform private.gmu_v213_refresh_compensation()');
mustContain(engineMigration, "state='PAYABLE'");
mustContain(engineMigration, "state='EARNED'");
mustContain(engineMigration, "state='APPROVED'");
mustContain(engineMigration, "'NEED_REVIEW'");
mustContain(engineMigration, 'internal_performance_payroll_v214_status');
mustContain(engineMigration, "'whatsapp_automation',false");
mustContain(engineMigration, 'gmu_v214_performance_payroll_workforce');

// Previous releases remain explicitly protected.
mustContain('erp-web/compensation-autopilot-v213.js', 'v21.3-compensation-capacity-autopilot');
mustContain('erp-web/target-cascade-v212.js', 'v21.2-target-cascade-workforce-autopilot');
mustContain('erp-web/company-autopilot-v210.js', 'v21-company-autopilot');
mustContain('scripts/build-erp-web-v10-release.js', 'baseline-v95');
mustContain('scripts/build-erp-web-v10-release.js', 'company-control-v214.js');
mustContain('scripts/build-erp-web-v10-release.js', 'Performance-Based Payroll & Workforce Capacity v21.4');

if (fail.length) {
  console.error('GMU EduTrans v21.4 verification FAILED');
  fail.forEach(x => console.error(`- ${x}`));
  process.exit(1);
}

console.log('GMU EduTrans v21.4 verification PASS');
console.log('Chain: Director target -> profit target -> revenue need -> SDM targets/tasks -> execution -> closing -> actual profit -> recovery gap -> target recalc -> payroll/workforce capacity recalc.');
console.log('No-regression: all prior ERP modules remain mandatory. WhatsApp automation remains deferred.');