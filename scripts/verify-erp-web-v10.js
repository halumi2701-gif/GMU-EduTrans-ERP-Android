const fs = require('fs');
const vm = require('vm');

const files = {
  privacy: 'erp-web/role-privacy-v99.js',
  packages: 'erp-web/package-master-v97.js',
  media: 'erp-web/media-master-v96.js',
  agent: 'erp-web/manager-ops-agent-v98.js',
  company: 'erp-web/company-operating-system-v100.js',
  market: 'erp-web/market-intelligence-v101.js',
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

// Role/privacy contract:
// - Owner/Director keep strategic/full-company finance.
// - Manager EduTrans and Finance may see operational unit finance.
must('privacy', "const STRATEGIC_FINANCE_ROLES = new Set(['Owner', 'Director', 'Direktur'])", 'strategic finance restricted to Owner/Director');
must('privacy', "'Manager EduTrans'", 'Manager EduTrans role support');
must('privacy', 'const OPERATIONAL_FINANCE_ROLES', 'operational finance role set');
must('privacy', 'canSeeOperationalFinance', 'operational finance guard');
must('privacy', 'canSeeStrategicFinance', 'strategic finance guard');
must('privacy', "page === 'finance' && !canSeeOperationalFinance()", 'finance navigation guard');
must('privacy', 'Terkunci sesuai hak akses', 'Trip Folder finance lock');
mustNot('privacy', "const FULL_FINANCE_ROLES = new Set(['Owner', 'Manager'", 'legacy Manager full-company finance access');

// Package Master: selling catalog only, with station contract reference.
must('packages', "sb.from('program_packages')", 'program_packages source');
must('packages', 'price_per_pax', 'selling price');
must('packages', 'min_pax', 'minimum pax');
must('packages', 'facilities', 'public facilities');
must('packages', 'PKG-GMU-00008', 'official station package');
must('packages', 'Pilih dari Master Paket', 'booking package selector');

// Internal finance terms may appear in explanatory copy, but must never be queried
// from Supabase or written as payload keys by Package Master.
const forbiddenFinanceFields = [
  'hpp',
  'base_cost',
  'manager_fee',
  'sales_fee',
  'mitra_fee',
  'partner_fee',
  'profit',
  'margin',
  'margin_pct',
  'pricing_policy',
];
const packageSelects = [...src.packages.matchAll(/\.select\(\s*(['"`])([\s\S]*?)\1\s*\)/g)]
  .map(match => match[2].toLowerCase().split(',').map(x => x.trim()).filter(Boolean));
for (const field of forbiddenFinanceFields) {
  if (packageSelects.some(fields => fields.includes(field))) {
    throw new Error(`Forbidden Package Master database projection: ${field}`);
  }
  const payloadKey = new RegExp(`(^|[,{\\n\\r])\\s*${field}\\s*:`, 'i');
  if (payloadKey.test(src.packages)) {
    throw new Error(`Forbidden Package Master write payload field: ${field}`);
  }
}

// Media contract remains exact.
must('media', 'const MAX_BYTES = 8 * 1024 * 1024', '8 MiB media limit');
must('media', 'const MAX_GALLERY = 5', 'gallery max 5');
must('media', "const INTERNAL_FN = 'internal-media-master'", 'internal media API');

// Existing Ops Agent actions remain available.
for (const action of ['Siapkan Trip','Buat Rundown','Cek Kesiapan','Susun Crew','Cek Vendor','Analisis RAB','Buat Operation Sheet','Buat Laporan']) {
  must('agent', `'${action}'`, `Ops Agent action ${action}`);
}
must('agent', "'DIRECTOR_APPROVAL'", 'Director approval authority');
must('agent', "'MANAGER_CONFIRMATION'", 'Manager confirmation authority');
must('agent', "queryOptional('trip_costs'", 'operational RAB read');

// Final Company Operating System policy supersedes the legacy generic Ops-Agent limit.
must('company', 'targetNetProfitMonthly: 15_000_000', 'Rp15m monthly net-profit target');
must('company', 'healthyMarginPct: 25', '25 percent healthy margin');
must('company', 'criticalMarginPct: 20', '20 percent critical margin');
must('company', 'managerPlannedRabLimit: 1_000_000', 'Rp1m planned RAB Manager limit');
must('company', 'managerUnplannedLimit: 250_000', 'Rp250k unplanned Manager limit');
must('company', 'managerEmergencyLimit: 500_000', 'Rp500k emergency Manager limit');
must('company', 'managerMaxDiscountPct: 5', '5 percent Manager discount limit');
must('company', "priorityRegions: ['Cianjur', 'Sukabumi']", 'Cianjur and Sukabumi priority regions');
must('company', "'AI & Otomatisasi'", 'AI operating-cost category');
must('company', "event.stopImmediatePropagation()", 'legacy Ops-Agent authority interception');

// Market Intelligence v10.2 contract.
must('market', "const VERSION = 'v10.1-market-intelligence'", 'market intelligence module version');
must('market', "const REGIONS = ['Cianjur','Sukabumi']", 'Cianjur and Sukabumi market regions');
must('market', "sb.from('market_targets')", 'market_targets source');
must('market', 'Intelijen Pasar', 'market intelligence UI');

// Unified loader is additive and fail-safe: baseline remains when a module fails.
for (const moduleFile of ['role-privacy-v99.js','package-master-v97.js','media-master-v96.js','manager-ops-agent-v98.js','company-operating-system-v100.js','market-intelligence-v101.js']) {
  must('loader', `'${moduleFile}'`, `loader module ${moduleFile}`);
}
must('loader', 'ERP utama tetap aktif', 'safe fallback to baseline');
must('loader', 'v10.2-company-operating-system-market-intelligence', 'current version marker');

console.log('GMU ERP Web v10.2 contract passed.');
console.log('Additive loader | Package Master | Media Master | Ops Agent | Company Operating System | Market Intelligence | strategic finance privacy');
