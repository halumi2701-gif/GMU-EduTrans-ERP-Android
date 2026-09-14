const fs = require('fs');
const vm = require('vm');

const files = {
  privacy: 'erp-web/role-privacy-v99.js',
  packages: 'erp-web/package-master-v97.js',
  media: 'erp-web/media-master-v96.js',
  agent: 'erp-web/manager-ops-agent-v98.js',
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

// Role/privacy: full-company finance is Owner/Director only.
must('privacy', "const FULL_FINANCE_ROLES = new Set(['Owner', 'Director', 'Direktur'])", 'Owner/Director-only full finance');
must('privacy', "page === 'finance' && !canSeeFullFinance()", 'finance navigation guard');
must('privacy', "Terkunci Owner/Director", 'Trip Folder finance lock');
mustNot('privacy', "FULL_FINANCE_ROLES = new Set(['Owner', 'Manager'", 'legacy Manager finance access');

// Package Master: selling catalog only, with station contract reference.
must('packages', "sb.from('program_packages')", 'program_packages source');
must('packages', 'price_per_pax', 'selling price');
must('packages', 'min_pax', 'minimum pax');
must('packages', 'facilities', 'public facilities');
must('packages', "PKG-GMU-00008", 'official station package');
must('packages', 'Pilih dari Master Paket', 'booking package selector');
for (const forbidden of ['base_cost', 'manager_fee', 'sales_fee', 'mitra_fee', 'partner_fee', 'profit', 'margin_pct']) {
  mustNot('packages', forbidden, forbidden);
}

// Media contract remains exact.
must('media', "const MAX_BYTES = 8 * 1024 * 1024", '8 MiB media limit');
must('media', 'const MAX_GALLERY = 5', 'gallery max 5');
must('media', "const INTERNAL_FN = 'internal-media-master'", 'internal media API');

// Ops Agent mirrors Android policy and actions.
for (const action of ['Siapkan Trip','Buat Rundown','Cek Kesiapan','Susun Crew','Cek Vendor','Analisis RAB','Buat Operation Sheet','Buat Laporan']) {
  must('agent', `'${action}'`, `Ops Agent action ${action}`);
}
must('agent', 'const DIRECTOR_APPROVAL_LIMIT = 2_000_000', 'Rp2m Manager approval limit');
must('agent', "'DIRECTOR_APPROVAL'", 'Director approval authority');
must('agent', "'MANAGER_CONFIRMATION'", 'Manager confirmation authority');
must('agent', "queryOptional('trip_costs'", 'operational RAB read');

// Unified loader is additive and fail-safe: v9.5 remains when a module fails.
for (const moduleFile of ['role-privacy-v99.js','package-master-v97.js','media-master-v96.js','manager-ops-agent-v98.js']) {
  must('loader', `'${moduleFile}'`, `loader module ${moduleFile}`);
}
must('loader', 'ERP v9.5 tetap aktif', 'safe fallback to baseline');
must('loader', 'v10-unified-operations-media', 'v10 version marker');

console.log('GMU ERP Web v10 contract passed.');
console.log('Additive loader | Package Master | Media Master | Ops Agent | Owner/Director finance privacy');
