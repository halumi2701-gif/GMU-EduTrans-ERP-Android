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

// Role/privacy contract.
must('privacy', "const STRATEGIC_FINANCE_ROLES = new Set(['Owner', 'Director', 'Direktur'])", 'strategic finance restricted to Owner/Director');
must('privacy', "'Manager EduTrans'", 'Manager EduTrans role support');
must('privacy', 'const OPERATIONAL_FINANCE_ROLES', 'operational finance role set');
must('privacy', 'canSeeOperationalFinance', 'operational finance guard');
must('privacy', 'canSeeStrategicFinance', 'strategic finance guard');
must('privacy', "page === 'finance' && !canSeeOperationalFinance()", 'finance navigation guard');
must('privacy', 'Terkunci sesuai hak akses', 'Trip Folder finance lock');
mustNot('privacy', "const FULL_FINANCE_ROLES = new Set(['Owner', 'Manager'", 'legacy Manager full-company finance access');

// Package Master.
must('packages', "sb.from('program_packages')", 'program_packages source');
must('packages', 'price_per_pax', 'selling price');
must('packages', 'min_pax', 'minimum pax');
must('packages', 'facilities', 'public facilities');
must('packages', 'PKG-GMU-00008', 'official station package');
must('packages', 'Pilih dari Master Paket', 'booking package selector');

const forbiddenFinanceFields = ['hpp','base_cost','manager_fee','sales_fee','mitra_fee','partner_fee','profit','margin','margin_pct','pricing_policy'];
const packageSelects = [...src.packages.matchAll(/\.select\(\s*(['"`])([\s\S]*?)\1\s*\)/g)]
  .map(match => match[2].toLowerCase().split(',').map(x => x.trim()).filter(Boolean));
for (const field of forbiddenFinanceFields) {
  if (packageSelects.some(fields => fields.includes(field))) throw new Error(`Forbidden Package Master database projection: ${field}`);
  const payloadKey = new RegExp(`(^|[,{\\n\\r])\\s*${field}\\s*:`, 'i');
  if (payloadKey.test(src.packages)) throw new Error(`Forbidden Package Master write payload field: ${field}`);
}

// Media.
must('media', 'const MAX_BYTES = 8 * 1024 * 1024', '8 MiB media limit');
must('media', 'const MAX_GALLERY = 5', 'gallery max 5');
must('media', "const INTERNAL_FN = 'internal-media-master'", 'internal media API');

// Ops Agent.
for (const action of ['Siapkan Trip','Buat Rundown','Cek Kesiapan','Susun Crew','Cek Vendor','Analisis RAB','Buat Operation Sheet','Buat Laporan']) must('agent', `'${action}'`, `Ops Agent action ${action}`);
must('agent', "'DIRECTOR_APPROVAL'", 'Director approval authority');
must('agent', "'MANAGER_CONFIRMATION'", 'Manager confirmation authority');
must('agent', "queryOptional('trip_costs'", 'operational RAB read');

// Company policy.
must('company', 'targetNetProfitMonthly: 15_000_000', 'Rp15m monthly net-profit target');
must('company', 'healthyMarginPct: 25', '25 percent healthy margin');
must('company', 'criticalMarginPct: 20', '20 percent critical margin');
must('company', 'managerPlannedRabLimit: 1_000_000', 'Rp1m planned RAB Manager limit');
must('company', 'managerUnplannedLimit: 250_000', 'Rp250k unplanned Manager limit');
must('company', 'managerEmergencyLimit: 500_000', 'Rp500k emergency Manager limit');
must('company', 'managerMaxDiscountPct: 5', '5 percent Manager discount limit');
must('company', "priorityRegions: ['Cianjur', 'Sukabumi']", 'priority regions');
must('company', "'AI & Otomatisasi'", 'AI cost category');

// Market Intelligence.
must('market', "const REGIONS = ['Cianjur','Sukabumi']", 'Cianjur and Sukabumi market regions');
must('market', "sb.from('market_targets')", 'market_targets source');
must('market', 'Intelijen Pasar', 'market intelligence UI');

// Sales target engine must visibly expose baseline sales targets.
must('salesTarget', "const VERSION = 'v10.3-sales-target-engine'", 'sales target engine version');
must('salesTarget', 'prospectsMonthly: 200', '200 monthly prospects baseline');
must('salesTarget', 'qualifiedLeadsMonthly: 20', '20 qualified leads baseline');
must('salesTarget', 'quotationsMonthly: 12', '12 quotations baseline');
must('salesTarget', 'minimumBookingsMonthly: 3', '3 minimum bookings baseline');
must('salesTarget', 'quotationToBookingPct: 25', '25 percent quotation conversion baseline');
must('salesTarget', 'Target & Kinerja Penjualan', 'visible sales target page');
must('salesTarget', 'Target Saya', 'Sales personal target view');

// Company system center must expose workspaces and full blueprint.
for (const label of ['Sistem Operasi Perusahaan GMU EduTrans','Kendali Direktur','Pusat Kendali Manager','Pusat Penjualan Saya','Pekerjaan Operasional Saya','Pekerjaan Keuangan Saya','Tugas Saya','Target & Kinerja','Kas & Likuiditas','SDM & Personalia','Mutu & Pelanggan','Tata Kelola','Pusat AI']) {
  must('systemCenter', label, `system center label ${label}`);
}
must('systemCenter', '7 Hal yang Wajib Terlihat', 'role workspace contract');
must('systemCenter', 'Penghasilan Saya', 'income workspace item');

// Management domain pages.
for (const label of ['Kas & Likuiditas','SDM & Personalia','Mutu & Pelanggan','Pertumbuhan & Ekspansi','Tata Kelola','Pusat AI']) {
  must('domains', label, `management domain ${label}`);
}
must('domains', 'Cash waterfall', 'treasury waterfall');
must('domains', 'Growth Readiness', 'growth readiness');
must('domains', 'AI dilarang otomatis', 'AI guardrails');

// Unified loader includes all visible company-system modules and is fail-safe.
for (const moduleFile of [
  'role-privacy-v99.js','package-master-v97.js','media-master-v96.js','manager-ops-agent-v98.js','company-operating-system-v100.js','market-intelligence-v101.js','sales-target-engine-v103.js','company-system-center-v104.js','management-domains-v105.js'
]) must('loader', `'${moduleFile}'`, `loader module ${moduleFile}`);
must('loader', 'ERP utama tetap aktif', 'safe fallback to baseline');
must('loader', 'v10.5-full-company-operating-system', 'current full-system version marker');
must('loader', 'Sistem Perusahaan', 'visible company-system release notice');
must('loader', 'Kas & Likuiditas', 'visible treasury release notice');
must('loader', 'Pusat AI', 'visible AI center release notice');

console.log('GMU ERP Web full Company Operating System contract passed.');
console.log('Role workspaces | Sales Target | Market Intelligence | Treasury | People | Quality | Growth | Governance | AI | strategic finance privacy');
