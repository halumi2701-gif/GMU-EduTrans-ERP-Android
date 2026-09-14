const fs = require('fs');
const vm = require('vm');

const required = [
  'erp-web/role-privacy-v99.js',
  'erp-web/package-master-v97.js',
  'erp-web/media-master-v96.js',
  'erp-web/manager-ops-agent-v98.js',
  'erp-web/company-operating-system-v100.js',
  'erp-web/market-intelligence-v101.js',
  'erp-web/unified-v10-loader.js',
];

for (const file of required) {
  const src = fs.readFileSync(file, 'utf8');
  new vm.Script(src, { filename: file });
}

const company = fs.readFileSync('erp-web/company-operating-system-v100.js', 'utf8');
const market = fs.readFileSync('erp-web/market-intelligence-v101.js', 'utf8');
const loader = fs.readFileSync('erp-web/unified-v10-loader.js', 'utf8');

for (const token of ['targetNetProfitMonthly: 15_000_000','managerPlannedRabLimit: 1_000_000','managerUnplannedLimit: 250_000','managerEmergencyLimit: 500_000','managerMaxDiscountPct: 5',"priorityRegions: ['Cianjur', 'Sukabumi']"]) {
  if (!company.includes(token)) throw new Error('Kontrak Company OS hilang: ' + token);
}
for (const token of ["const REGIONS = ['Cianjur','Sukabumi']","sb.from('market_targets')",'Intelijen Pasar','potential_score']) {
  if (!market.includes(token)) throw new Error('Kontrak Intelijen Pasar hilang: ' + token);
}
for (const file of ['role-privacy-v99.js','package-master-v97.js','media-master-v96.js','manager-ops-agent-v98.js','company-operating-system-v100.js','market-intelligence-v101.js']) {
  if (!loader.includes(`'${file}'`)) throw new Error('Loader belum memuat: ' + file);
}
if (!loader.includes('ERP utama tetap aktif')) throw new Error('Fallback ERP baseline hilang.');

console.log('GMU ERP Web v10.2 contract passed.');
