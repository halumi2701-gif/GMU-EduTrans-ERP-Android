const fs = require('fs');
const vm = require('vm');

const files = {
  privacy: 'erp-web/role-privacy-v99.js',
  packages: 'erp-web/package-master-v97.js',
  media: 'erp-web/media-master-v96.js',
  agent: 'erp-web/manager-ops-agent-v98.js',
  company: 'erp-web/company-operating-system-v100.js',
  market: 'erp-web/market-intelligence-v101.js',
  salesTarget: 'erp-web/sales-target-engine-v103.js',
  systemCenter: 'erp-web/company-system-center-v104.js',
  domains: 'erp-web/management-domains-v105.js',
  navGuard: 'erp-web/role-navigation-v106.js',
  detail: 'erp-web/company-detail-controls-v107.js',
  playbook: 'erp-web/role-playbook-v108.js',
  loader: 'erp-web/unified-v10-loader.js',
};

const src = Object.fromEntries(Object.entries(files).map(([key, file]) => {
  const text = fs.readFileSync(file, 'utf8');
  new vm.Script(text, { filename: file });
  return [key, text];
}));

function must(key, text, label) {
  if (!src[key].includes(text)) throw new Error(`Missing ERP v10 contract [${key}]: ${label}`);
}
function mustNot(key, text, label) {
  if (src[key].includes(text)) throw new Error(`Forbidden ERP v10 content [${key}]: ${label}`);
}

must('privacy', "const STRATEGIC_FINANCE_ROLES = new Set(['Owner', 'Director', 'Direktur'])", 'strategic finance restricted to Owner/Director');
must('privacy', "'Manager EduTrans'", 'Manager EduTrans role support');
must('privacy', 'const OPERATIONAL_FINANCE_ROLES', 'operational finance role set');
must('privacy', 'canSeeOperationalFinance', 'operational finance guard');
must('privacy', 'canSeeStrategicFinance', 'strategic finance guard');
must('privacy', "page === 'finance' && !canSeeOperationalFinance()", 'finance navigation guard');
must('privacy', 'Terkunci sesuai hak akses', 'Trip Folder finance lock');
mustNot('privacy', "const FULL_FINANCE_ROLES = new Set(['Owner', 'Manager'", 'legacy Manager full-company finance access');

must('packages', "sb.from('program_packages')", 'program_packages source');
must('packages', 'price_per_pax', 'selling price');
must('packages', 'min_pax', 'minimum pax');
must('packages', 'PKG-GMU-00008', 'official station package');
must('packages', 'Pilih dari Master Paket', 'booking package selector');
must('media', 'const MAX_BYTES = 8 * 1024 * 1024', '8 MiB media limit');
must('media', 'const MAX_GALLERY = 5', 'gallery max 5');

for (const action of ['Siapkan Trip','Buat Rundown','Cek Kesiapan','Susun Crew','Cek Vendor','Analisis RAB','Buat Operation Sheet','Buat Laporan']) must('agent', `'${action}'`, `Ops Agent action ${action}`);
must('agent', "'DIRECTOR_APPROVAL'", 'Director approval authority');
must('agent', "'MANAGER_CONFIRMATION'", 'Manager confirmation authority');

must('company', 'targetNetProfitMonthly: 15_000_000', 'Rp15m monthly net-profit target');
must('company', 'healthyMarginPct: 25', '25 percent healthy margin');
must('company', 'criticalMarginPct: 20', '20 percent critical margin');
must('company', 'managerPlannedRabLimit: 1_000_000', 'Rp1m planned RAB Manager limit');
must('company', 'managerUnplannedLimit: 250_000', 'Rp250k unplanned Manager limit');
must('company', 'managerEmergencyLimit: 500_000', 'Rp500k emergency Manager limit');
must('company', 'managerMaxDiscountPct: 5', '5 percent Manager discount limit');
must('company', "priorityRegions: ['Cianjur', 'Sukabumi']", 'priority regions');

must('market', "const REGIONS = ['Cianjur','Sukabumi']", 'Cianjur and Sukabumi market regions');
must('market', "sb.from('market_targets')", 'market_targets source');
must('market', 'Intelijen Pasar', 'market intelligence UI');

must('salesTarget', 'prospectsMonthly: 200', '200 monthly prospects baseline');
must('salesTarget', 'qualifiedLeadsMonthly: 20', '20 qualified leads baseline');
must('salesTarget', 'quotationsMonthly: 12', '12 quotations baseline');
must('salesTarget', 'minimumBookingsMonthly: 3', '3 minimum bookings baseline');
must('salesTarget', 'quotationToBookingPct: 25', '25 percent quotation conversion baseline');

for (const label of ['Sistem Operasi Perusahaan GMU EduTrans','Kendali Direktur','Pusat Kendali Manager','Pusat Penjualan Saya','Pekerjaan Operasional Saya','Pekerjaan Keuangan Saya','Tugas Saya','Target & Kinerja','Kas & Likuiditas','SDM & Personalia','Mutu & Pelanggan','Tata Kelola']) must('systemCenter', label, `system center label ${label}`);
for (const label of ['Kas & Likuiditas','SDM & Personalia','Mutu & Pelanggan','Pertumbuhan & Ekspansi','Tata Kelola','Pusat AI']) must('domains', label, `management domain ${label}`);

must('navGuard', "const VERSION = 'v10.8-role-navigation-guard'", 'role navigation guard version');
must('navGuard', "'rolePlaybook'", 'role playbook kept visible after legacy role render');
must('navGuard', 'window.applyRole', 'legacy applyRole wrapper');

must('detail', "const VERSION = 'v10.7-company-detail-controls'", 'detail control version');
for (const label of ['Ringkasan Aktual Bulan Ini','Realisasi vs Target','Antrian Kerja Aktual','Posisi Kas Operasional dari Data ERP','Struktur SDM Aktual','Mutu Aktual dari Evaluasi Trip','Kesiapan Bertumbuh','Kontrol Tata Kelola Aktual','Aksi Asisten AI per Role','Direktori Modul Perusahaan','Kontrol Kerja Saya — Aktual']) must('detail', label, `detail control ${label}`);
must('detail', 'bookingRevenue', 'live booking revenue calculation');
must('detail', 'bookingPaid', 'live payment calculation');
must('detail', 'bookingCost', 'live cost calculation');
must('detail', 'Laba bersih perusahaan final tetap memerlukan seluruh overhead', 'no fake net profit guardrail');

must('playbook', "const VERSION = 'v10.8-role-playbook'", 'role playbook version');
for (const roleLabel of ['Owner / Direktur','Manager GMU EduTrans','Sales / Business Development','Admin / Customer Service','Finance / Accounting','Admin Ops / Trip Coordinator','Tour Leader / PIC Lapangan','MC / Fasilitator / Narasumber','Dokumentasi','Crew / Freelancer']) must('playbook', roleLabel, `jobdesk role ${roleLabel}`);
for (const section of ['Tugas Harian','Tugas Mingguan','Tugas Bulanan','KPI / Ukuran Kinerja','Wewenang','Larangan / Batasan','Kapan Harus Eskalasi','Output Wajib','SOP Inti Jabatan','Penghasilan & Imbalan']) must('playbook', section, `jobdesk section ${section}`);
must('playbook', '10 prospek baru.', 'Sales daily prospect target');
must('playbook', 'Biaya dalam RAB ≤Rp1.000.000/transaksi.', 'Manager RAB authority');
must('playbook', 'Closing trip maksimal H+3.', 'Finance close SLA');
must('playbook', 'H-7: vendor/crew/rundown/RAB/documents.', 'Ops H-7 control');
must('playbook', '≥95% rundown compliance', 'TL rundown KPI');

for (const moduleFile of [
  'role-privacy-v99.js','package-master-v97.js','media-master-v96.js','manager-ops-agent-v98.js','company-operating-system-v100.js','market-intelligence-v101.js','sales-target-engine-v103.js','company-system-center-v104.js','management-domains-v105.js','role-navigation-v106.js','company-detail-controls-v107.js','role-playbook-v108.js'
]) must('loader', `'${moduleFile}'`, `loader module ${moduleFile}`);
must('loader', 'v10.8-full-company-operating-system', 'current detailed full-system marker');
must('loader', 'jobdesk & SOP per jabatan', 'role jobdesk visible release notice');
must('loader', 'kontrol aktual', 'detail controls visible release notice');

console.log('GMU ERP Web v10.8 detailed Company Operating System contract passed.');
console.log('Detailed controls | Role Playbooks | Jobdesk/SOP | Sales Target | Market Intelligence | Treasury | People | Quality | Growth | Governance | AI | Finance Privacy');
