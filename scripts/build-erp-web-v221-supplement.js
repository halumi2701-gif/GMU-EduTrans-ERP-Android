const fs = require('fs');
const path = require('path');

const OUT_DIR = process.env.ERP_V10_OUT_DIR || 'dist/erp-web-v10';
const SUPPLEMENTAL_MODULES = [
  'station-target-v216.js',
  'sales-target-v217.js',
  'station-operating-policy-v218.js',
  'customer-account-crm-v220.js',
  'train-package-policy-v221.js',
];

function assertIncludes(source, needle, label) {
  if (!source.includes(needle)) throw new Error(`GMU v22.1 release mismatch: ${label}`);
}

function main() {
  if (!fs.existsSync(path.join(OUT_DIR, 'index.html'))) {
    throw new Error(`Base release belum dibangun: ${OUT_DIR}/index.html`);
  }

  for (const file of SUPPLEMENTAL_MODULES) {
    const src = path.join('erp-web', file);
    if (!fs.existsSync(src)) throw new Error(`Missing supplemental module: ${src}`);
    fs.copyFileSync(src, path.join(OUT_DIR, file));
  }

  const loader = fs.readFileSync(path.join(OUT_DIR, 'unified-v10-loader.js'), 'utf8');
  assertIncludes(loader, 'station-target-v216.js', 'station pricing module in loader');
  assertIncludes(loader, 'sales-target-v217.js', 'portfolio target module in loader');
  assertIncludes(loader, 'customer-account-crm-v220.js', 'customer account CRM module in loader');
  assertIncludes(loader, 'train-package-policy-v221.js', 'train package policy module in loader');

  const sales = fs.readFileSync(path.join(OUT_DIR, 'sales-target-v217.js'), 'utf8');
  const station = fs.readFileSync(path.join(OUT_DIR, 'station-target-v216.js'), 'utf8');
  const customer = fs.readFileSync(path.join(OUT_DIR, 'customer-account-crm-v220.js'), 'utf8');
  const train = fs.readFileSync(path.join(OUT_DIR, 'train-package-policy-v221.js'), 'utf8');
  assertIncludes(sales, "v22.2-sales-portfolio-target-safe", 'safe portfolio target version');
  assertIncludes(sales, 'Seluruh Program', 'portfolio-wide target label');
  assertIncludes(sales, 'gmu_sales_portfolio_summary', 'safe automatic paid-pax RPC');
  assertIncludes(sales, 'role Sales tidak menerima omzet', 'Sales finance privacy marker');
  assertIncludes(station, 'Tidak lagi mengunci 200 pax khusus Stasiun', 'station target correction');
  assertIncludes(customer, 'Customer Account & Prospek Web', 'customer prospect CRM');
  assertIncludes(train, 'HPP fasilitas • LOCK', 'train locked-HPP policy');
  assertIncludes(train, 'Tiket Tambahan di Luar Program', 'extra ticket policy');

  const manifestPath = path.join(OUT_DIR, 'release-manifest.json');
  if (fs.existsSync(manifestPath)) {
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    manifest.additiveRelease = 'GMU EduTrans Portfolio Target & Package Policy v22.1 + Safe Target v22.2';
    manifest.additiveModules = SUPPLEMENTAL_MODULES;
    manifest.portfolioSalesTarget = {
      scope: 'ALL_PROGRAMS',
      paidPaxMonthly: 200,
      rule: 'One booking contributes pax once, in the month of its first verified positive payment',
      salesFinancePrivacy: 'Sales receives paid-pax/fee/bonus only; company revenue, cash-in, HPP, profit and margin remain management-only',
    };
    manifest.trainPackagePolicy = {
      packages: ['TRAIN-HEMAT-39','TRAIN-REGULER-60','TRAIN-LENGKAP-75'],
      mainTicketIncluded: true,
      extraTicketingOutsideProgramOnly: true,
    };
    fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n', 'utf8');
  }

  console.log(`GMU EduTrans v22.1/v22.2 supplemental release copied: ${SUPPLEMENTAL_MODULES.join(', ')}`);
}

main();
