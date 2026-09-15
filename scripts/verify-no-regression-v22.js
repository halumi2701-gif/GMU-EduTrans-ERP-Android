const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const failures = [];

function mustExist(rel) {
  const p = path.join(root, rel);
  if (!fs.existsSync(p)) failures.push(`MISSING FILE: ${rel}`);
  return p;
}

function mustContain(rel, marker) {
  const p = mustExist(rel);
  if (!fs.existsSync(p)) return;
  const text = fs.readFileSync(p, 'utf8');
  if (!text.includes(marker)) failures.push(`MISSING CONTRACT: ${rel} -> ${marker}`);
}

// Existing ERP web modules are locked as additive baseline.
[
  'erp-web/role-privacy-v99.js',
  'erp-web/package-master-v97.js',
  'erp-web/media-master-v96.js',
  'erp-web/manager-ops-agent-v98.js',
  'erp-web/company-operating-system-v100.js',
  'erp-web/market-intelligence-v101.js',
  'erp-web/sales-target-engine-v103.js',
  'erp-web/company-system-center-v104.js',
  'erp-web/management-domains-v105.js',
  'erp-web/role-navigation-v106.js',
  'erp-web/company-detail-controls-v107.js',
  'erp-web/role-playbook-v108.js',
  'erp-web/execution-control-v109.js',
  'erp-web/ui-focus-shell-v110.js',
  'erp-web/automation-orchestrator-v111.js',
  'erp-web/automation-orchestrator-fix-v111.js',
  'erp-web/crm-finance-notification-v112.js',
  'erp-web/enterprise-os-v200.js',
  'erp-web/business-priority-v201.js',
  'erp-web/recovery-command-v202.js',
  'erp-web/company-autopilot-v210.js',
  'erp-web/target-cascade-v212.js',
  'erp-web/company-control-v213.js',
  'erp-web/unified-v10-loader.js'
].forEach(mustExist);

// Native ERP core must remain available.
mustExist('app/src/main/java/com/garsyanimultiusaha/gmuedutrans/erp/CompanyOperatingSystemV1.kt');

// Critical database foundations already used by production workflows.
[
  'supabase/migrations/20260915023020_gmu_v111_automation_core.sql',
  'supabase/migrations/20260915023153_gmu_v111_event_orchestrator.sql',
  'supabase/migrations/20260915023403_gmu_v111_performance_policy_hardening.sql',
  'supabase/migrations/20260915030638_gmu_v112_crm_control_and_time_automation.sql',
  'supabase/migrations/20260915030730_gmu_v112_crm_rls_and_scorecard.sql',
  'supabase/migrations/20260915030823_gmu_v112_unassigned_lead_escalation.sql',
  'supabase/migrations/20260915133000_gmu_v20_company_control_finance_hr.sql'
].forEach(mustExist);

// Functional contracts: do not remove these older capabilities while reorganizing UI.
mustContain('erp-web/manager-ops-agent-v98.js', 'Manager');
mustContain('erp-web/role-privacy-v99.js', 'role');
mustContain('erp-web/sales-target-engine-v103.js', 'target');
mustContain('erp-web/automation-orchestrator-v111.js', 'automation');
mustContain('erp-web/crm-finance-notification-v112.js', 'finance');
mustContain('erp-web/enterprise-os-v200.js', 'enterprise');
mustContain('erp-web/company-autopilot-v210.js', 'autopilot');
mustContain('erp-web/target-cascade-v212.js', 'internal_target_cascade_status');
mustContain('erp-web/company-control-v213.js', 'v_company_command_center');
mustContain('erp-web/company-control-v213.js', 'payment_requests');
mustContain('erp-web/company-control-v213.js', 'compensation_accruals');
mustContain('erp-web/company-control-v213.js', 'recruitment_cases');
mustContain('app/src/main/java/com/garsyanimultiusaha/gmuedutrans/erp/CompanyOperatingSystemV1.kt', 'TARGET_MARGIN_SEHAT_PCT');
mustContain('supabase/migrations/20260915023020_gmu_v111_automation_core.sql', 'recruitment_cases');
mustContain('supabase/migrations/20260915023020_gmu_v111_automation_core.sql', 'payroll_entries');
mustContain('supabase/migrations/20260915023020_gmu_v111_automation_core.sql', 'workforce_plans');
mustContain('supabase/migrations/20260915133000_gmu_v20_company_control_finance_hr.sql', '50000000');
mustContain('supabase/migrations/20260915133000_gmu_v20_company_control_finance_hr.sql', 'payment_requests');

// Existing release builder must preserve the old pinned baseline for rollback/regression comparison.
mustContain('scripts/build-erp-web-v10-release.js', 'baseline-v95');
mustContain('scripts/build-erp-web-v10-release.js', 'company-control-v213.js');
mustContain('.github/workflows/validate-erp-web-v10.yml', 'Verify preserved baseline');

if (failures.length) {
  console.error('GMU EduTrans ERP NO-REGRESSION GUARD: FAILED');
  failures.forEach((f) => console.error(`- ${f}`));
  process.exit(1);
}

console.log('GMU EduTrans ERP NO-REGRESSION GUARD: PASS');
console.log('Rule: upgrades are additive; existing modules and critical business flows must remain available.');