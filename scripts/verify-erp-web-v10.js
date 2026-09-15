const fs = require('fs');
const vm = require('vm');

const files = {
  privacy: 'erp-web/role-privacy-v99.js', packages: 'erp-web/package-master-v97.js', media: 'erp-web/media-master-v96.js', agent: 'erp-web/manager-ops-agent-v98.js', company: 'erp-web/company-operating-system-v100.js', market: 'erp-web/market-intelligence-v101.js', salesTarget: 'erp-web/sales-target-engine-v103.js', systemCenter: 'erp-web/company-system-center-v104.js', domains: 'erp-web/management-domains-v105.js', navGuard: 'erp-web/role-navigation-v106.js', detail: 'erp-web/company-detail-controls-v107.js', playbook: 'erp-web/role-playbook-v108.js', execution: 'erp-web/execution-control-v109.js', loader: 'erp-web/unified-v10-loader.js',
};
const src = Object.fromEntries(Object.entries(files).map(([key,file])=>{const text=fs.readFileSync(file,'utf8');new vm.Script(text,{filename:file});return [key,text];}));
function must(key,text,label){if(!src[key].includes(text)) throw new Error(`Missing ERP v10 contract [${key}]: ${label}`);}
function mustNot(key,text,label){if(src[key].includes(text)) throw new Error(`Forbidden ERP v10 content [${key}]: ${label}`);}

must('privacy', "const STRATEGIC_FINANCE_ROLES = new Set(['Owner', 'Director', 'Direktur'])", 'strategic finance restricted to Owner/Director');
must('privacy', "'Manager EduTrans'", 'Manager EduTrans role support');
must('privacy', 'canSeeOperationalFinance', 'operational finance guard');
must('privacy', 'canSeeStrategicFinance', 'strategic finance guard');
mustNot('privacy', "const FULL_FINANCE_ROLES = new Set(['Owner', 'Manager'", 'legacy Manager full-company finance access');

must('packages', "sb.from('program_packages')", 'program_packages source');
must('packages', 'PKG-GMU-00008', 'official station package');
must('media', 'const MAX_BYTES = 8 * 1024 * 1024', '8 MiB media limit');
for (const action of ['Siapkan Trip','Buat Rundown','Cek Kesiapan','Susun Crew','Cek Vendor','Analisis RAB','Buat Operation Sheet','Buat Laporan']) must('agent', `'${action}'`, `Ops Agent action ${action}`);

must('company', 'targetNetProfitMonthly: 15_000_000', 'Rp15m monthly net-profit target');
must('company', 'managerPlannedRabLimit: 1_000_000', 'Rp1m planned RAB Manager limit');
must('company', 'managerUnplannedLimit: 250_000', 'Rp250k unplanned Manager limit');
must('company', 'managerEmergencyLimit: 500_000', 'Rp500k emergency Manager limit');
must('company', 'managerMaxDiscountPct: 5', '5 percent Manager discount limit');
must('company', "priorityRegions: ['Cianjur', 'Sukabumi']", 'priority regions');

must('market', "const REGIONS = ['Cianjur','Sukabumi']", 'Cianjur and Sukabumi market regions');
must('market', 'Intelijen Pasar', 'market intelligence UI');
must('salesTarget', 'prospectsMonthly: 200', '200 monthly prospects baseline');
must('salesTarget', 'qualifiedLeadsMonthly: 20', '20 qualified leads baseline');
must('salesTarget', 'quotationsMonthly: 12', '12 quotations baseline');
must('salesTarget', 'minimumBookingsMonthly: 3', '3 minimum bookings baseline');
must('salesTarget', 'quotationToBookingPct: 25', '25 percent quotation conversion baseline');

for (const label of ['Sistem Operasi Perusahaan GMU EduTrans','Kendali Direktur','Pusat Kendali Manager','Pusat Penjualan Saya','Pekerjaan Operasional Saya','Pekerjaan Keuangan Saya','Tugas Saya','Target & Kinerja','Kas & Likuiditas','SDM & Personalia','Mutu & Pelanggan','Tata Kelola']) must('systemCenter',label,`system center label ${label}`);
for (const label of ['Kas & Likuiditas','SDM & Personalia','Mutu & Pelanggan','Pertumbuhan & Ekspansi','Tata Kelola','Pusat AI']) must('domains',label,`management domain ${label}`);

must('navGuard', "const VERSION = 'v10.9-role-navigation-guard'", 'role navigation guard version');
for (const page of ['rolePlaybook','taskAutomationControl','payrollControl','recruitmentControl','serviceRecoveryControl','cashForecastControl','workforcePlanningControl','riskRegisterControl']) must('navGuard', `'${page}'`, `navigation page ${page}`);
must('navGuard','window.applyRole','legacy applyRole wrapper');

must('detail', "const VERSION = 'v10.7-company-detail-controls'", 'detail control version');
for (const label of ['Ringkasan Aktual Bulan Ini','Realisasi vs Target','Antrian Kerja Aktual','Posisi Kas Operasional dari Data ERP','Struktur SDM Aktual','Mutu Aktual dari Evaluasi Trip','Kesiapan Bertumbuh','Kontrol Tata Kelola Aktual','Aksi Asisten AI per Role','Direktori Modul Perusahaan','Kontrol Kerja Saya — Aktual']) must('detail',label,`detail control ${label}`);
must('detail','Laba bersih perusahaan final tetap memerlukan seluruh overhead','no fake net profit guardrail');

must('playbook', "const VERSION = 'v10.8-role-playbook'", 'role playbook version');
for (const roleLabel of ['Owner / Direktur','Manager GMU EduTrans','Sales / Business Development','Admin / Customer Service','Finance / Accounting','Admin Ops / Trip Coordinator','Tour Leader / PIC Lapangan','MC / Fasilitator / Narasumber','Dokumentasi','Crew / Freelancer']) must('playbook',roleLabel,`jobdesk role ${roleLabel}`);
for (const section of ['Tugas Harian','Tugas Mingguan','Tugas Bulanan','KPI / Ukuran Kinerja','Wewenang','Larangan / Batasan','Kapan Harus Eskalasi','Output Wajib','SOP Inti Jabatan','Penghasilan & Imbalan']) must('playbook',section,`jobdesk section ${section}`);

must('execution', "const VERSION = 'v10.9-execution-control-layer'", 'execution control version');
for (const label of ['Tugas Otomatis','Penggajian & Fee','Rekrutmen & Onboarding','Keluhan & CAPA','Perkiraan Kas','Perencanaan SDM','Daftar Risiko','Urutan Kerja Saya Hari Ini']) must('execution',label,`execution UI ${label}`);
for (const stage of ['H-7','H-3','H-1','H+1','H+3']) must('execution',stage,`trip lifecycle ${stage}`);
must('execution','Pending → Earned → Paid','commission lifecycle');
must('execution','Freelancer First','workforce hiring rule');
must('execution','Root cause analysis','CAPA root cause');
must('execution','Runway = Available Cash / fixed cost bulanan','cash runway');
must('execution','Capacity → Workload → Staffing Gap → Cost Impact → Recommendation','workforce planning chain');
must('execution','Safety / Legal','critical complaint rule');
must('execution','Direktur tidak menjadi PIC tugas operasional rutin','director release rule');

for (const moduleFile of ['role-privacy-v99.js','package-master-v97.js','media-master-v96.js','manager-ops-agent-v98.js','company-operating-system-v100.js','market-intelligence-v101.js','sales-target-engine-v103.js','company-system-center-v104.js','management-domains-v105.js','role-navigation-v106.js','company-detail-controls-v107.js','role-playbook-v108.js','execution-control-v109.js']) must('loader',`'${moduleFile}'`,`loader module ${moduleFile}`);
must('loader','v10.9-execution-control-system','current execution system marker');
must('loader','Tugas Otomatis','task automation release notice');
must('loader','Penggajian & Fee','payroll release notice');
must('loader','Daftar Risiko','risk register release notice');

console.log('GMU ERP Web v10.9 Execution & Control System contract passed.');
console.log('Role Playbooks | Task Automation | Payroll | Recruitment | CAPA | Cash Forecast | Workforce Planning | Risk Register | Sales | Market | Treasury | Governance | AI');
