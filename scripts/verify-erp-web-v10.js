const fs = require('fs');
const vm = require('vm');

const files = {
  privacy:'erp-web/role-privacy-v99.js',packages:'erp-web/package-master-v97.js',media:'erp-web/media-master-v96.js',agent:'erp-web/manager-ops-agent-v98.js',company:'erp-web/company-operating-system-v100.js',market:'erp-web/market-intelligence-v101.js',salesTarget:'erp-web/sales-target-engine-v103.js',systemCenter:'erp-web/company-system-center-v104.js',domains:'erp-web/management-domains-v105.js',navGuard:'erp-web/role-navigation-v106.js',detail:'erp-web/company-detail-controls-v107.js',playbook:'erp-web/role-playbook-v108.js',execution:'erp-web/execution-control-v109.js',ui:'erp-web/ui-focus-shell-v110.js',automation:'erp-web/automation-orchestrator-v111.js',automationFix:'erp-web/automation-orchestrator-fix-v111.js',activation:'erp-web/crm-finance-notification-v112.js',loader:'erp-web/unified-v10-loader.js'
};
const src=Object.fromEntries(Object.entries(files).map(([k,f])=>{const t=fs.readFileSync(f,'utf8');new vm.Script(t,{filename:f});return[k,t];}));
function must(k,t,l){if(!src[k].includes(t))throw new Error(`Missing ERP contract [${k}]: ${l}`)}
function mustNot(k,t,l){if(src[k].includes(t))throw new Error(`Forbidden ERP content [${k}]: ${l}`)}

must('privacy',"const STRATEGIC_FINANCE_ROLES = new Set(['Owner', 'Director', 'Direktur'])",'strategic finance restricted');
must('privacy','canSeeOperationalFinance','operational finance guard');must('privacy','canSeeStrategicFinance','strategic finance guard');
mustNot('privacy',"const FULL_FINANCE_ROLES = new Set(['Owner', 'Manager'",'legacy Manager full finance access');
must('packages',"sb.from('program_packages')",'program package source');must('packages','PKG-GMU-00008','station package');
must('media','const MAX_BYTES = 8 * 1024 * 1024','media limit');
for(const a of ['Siapkan Trip','Buat Rundown','Cek Kesiapan','Susun Crew','Cek Vendor','Analisis RAB','Buat Operation Sheet','Buat Laporan'])must('agent',`'${a}'`,`ops action ${a}`);
must('company','targetNetProfitMonthly: 15_000_000','profit target');must('company','managerPlannedRabLimit: 1_000_000','Manager RAB limit');must('company','managerUnplannedLimit: 250_000','unplanned limit');must('company','managerEmergencyLimit: 500_000','emergency limit');must('company','managerMaxDiscountPct: 5','discount limit');
must('market',"const REGIONS = ['Cianjur','Sukabumi']",'priority market');
must('salesTarget','prospectsMonthly: 200','prospect target');must('salesTarget','qualifiedLeadsMonthly: 20','qualified lead target');must('salesTarget','quotationsMonthly: 12','quotation target');must('salesTarget','minimumBookingsMonthly: 3','booking target');
for(const l of ['Sistem Operasi Perusahaan GMU EduTrans','Kendali Direktur','Pusat Kendali Manager','Tugas Saya','Target & Kinerja','Kas & Likuiditas','SDM & Personalia','Mutu & Pelanggan','Tata Kelola'])must('systemCenter',l,`system center ${l}`);
for(const l of ['Kas & Likuiditas','SDM & Personalia','Mutu & Pelanggan','Pertumbuhan & Ekspansi','Tata Kelola','Pusat AI'])must('domains',l,`domain ${l}`);

must('navGuard',"const VERSION = 'v10.9-role-navigation-guard'",'role navigation version');must('navGuard','window.applyRole','legacy role wrapper');
must('detail',"const VERSION = 'v10.7-company-detail-controls'",'detail version');must('detail','Ringkasan Aktual Bulan Ini','live executive summary');must('detail','Realisasi vs Target','live target comparison');
must('playbook',"const VERSION = 'v10.8-role-playbook'",'role playbook version');
for(const s of ['Tugas Harian','Tugas Mingguan','Tugas Bulanan','KPI / Ukuran Kinerja','Wewenang','Larangan / Batasan','Kapan Harus Eskalasi','Output Wajib','SOP Inti Jabatan','Penghasilan & Imbalan'])must('playbook',s,`playbook ${s}`);
must('execution',"const VERSION = 'v10.9-execution-control-layer'",'execution version');
for(const l of ['Tugas Otomatis','Penggajian & Fee','Rekrutmen & Onboarding','Keluhan & CAPA','Perkiraan Kas','Perencanaan SDM','Daftar Risiko','Urutan Kerja Saya Hari Ini'])must('execution',l,`execution ${l}`);

must('ui',"const VERSION = 'v11.0-focused-ui-shell'",'focused UI version');
for(const l of ['Utama','Kerja Saya','Penjualan','Operasional','Keuangan','SDM & Mutu','Sistem & AI','Semua'])must('ui',`label: '${l}'`,`navigation group ${l}`);
must('ui','Cari menu…','menu search');must('ui','Mode Ringkas','compact mode');must('ui','Tampilkan detail tambahan','progressive disclosure');must('ui','@media(max-width:620px)','mobile responsive contract');

must('automation',"const VERSION = 'v11.1-transactional-automation-orchestrator'",'transactional automation version');
for(const table of ['automation_tasks','automation_events','crm_activities','payroll_entries','recruitment_cases','capa_cases','workforce_plans','risk_register','ai_action_drafts','approval_details'])must('automation',`'${table}'`,`transactional backend table ${table}`);
for(const label of ['Tugas Otomatis Aktual','Aktivitas & Tindak Lanjut Sales','Penggajian & Fee Aktual','Kebutuhan SDM & Onboarding Aktual','Corrective & Preventive Action Aktual','Capacity → Workload → Staffing Gap','Risiko Perusahaan Aktual','Draft Tindakan AI','Automation Orchestrator v11.1'])must('automation',label,`transactional UI ${label}`);
must('automation',"data-g111-task-status=\"DONE\"",'task completion action');
must('automation',"data-g111-payroll=\"PAID\"",'payroll paid action');
must('automation',"data-g111-ai=\"APPROVED\"",'AI approval action');
must('automation','next_follow_up_at','CRM next follow-up');
must('automation','lost_reason','CRM lost reason');
must('automation','requires_approval','AI approval field');
must('automationFix',"const VERSION = 'v11.1-automation-runtime-stabilizer'",'runtime stabilizer version');
must('automationFix','obs.disconnect()','observer loop guard');must('automationFix','select.value = row.status','CAPA status preservation');

must('activation',"const VERSION = 'v11.2-crm-finance-notification-activation'",'v11.2 activation version');
for(const t of ['crm_lead_controls','booking_requests','crm_activities','finance_periods','customer_notification_outbox','customer_notification_deliveries','internal_notification_deliveries'])must('activation',`'${t}'`,`v11.2 backend ${t}`);
for(const f of ['internal_crm_sales_scorecard','internal_management_ar_aging','internal_management_ap_aging','internal_gl_trial_balance','internal_gl_profit_loss','internal_gl_balance_sheet','internal_gl_cash_flow'])must('activation',`'${f}'`,`v11.2 RPC ${f}`);
for(const l of ['Pipeline, Assignment & Conversion','Scorecard Sales Bulan Ini','Accounting & Financial Statements','Laporan Keuangan & Closing Control','AR Aging / Piutang','AP Aging / Hutang Vendor','Trial Balance','Pusat Notifikasi','Notification Delivery Center'])must('activation',l,`v11.2 UI ${l}`);
must('activation','setInterval(()=>{if(role()&&db()&&!document.hidden)refresh();},60000)','conservative 60-second refresh');
mustNot('activation','MutationObserver','no mutation observer loop');
must('activation','data-g112-activity','CRM activity action');must('activation','data-g112-lead','CRM lead control action');must('activation','Lost Reason wajib diisi','lost reason guard');

for(const m of ['role-privacy-v99.js','package-master-v97.js','media-master-v96.js','manager-ops-agent-v98.js','company-operating-system-v100.js','market-intelligence-v101.js','sales-target-engine-v103.js','company-system-center-v104.js','management-domains-v105.js','role-navigation-v106.js','company-detail-controls-v107.js','role-playbook-v108.js','execution-control-v109.js','ui-focus-shell-v110.js','automation-orchestrator-v111.js','automation-orchestrator-fix-v111.js','crm-finance-notification-v112.js'])must('loader',`'${m}'`,`loader module ${m}`);
must('loader','v11.2-crm-finance-notification-activation','v11.2 release marker');must('loader','CRM, Accounting & Notification Activation aktif','v11.2 activation notice');must('loader','delivery log notifikasi','delivery log notice');

console.log('GMU ERP Web v11.2 CRM, Accounting & Notification contract passed.');
console.log('CRM Assignment | Follow-up | Recovery | Sales Scorecard | AR/AP | Trial Balance | P&L | Balance Sheet | Cash Flow | Notification Delivery');
