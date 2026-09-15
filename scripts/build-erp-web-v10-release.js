const fs = require('fs');
const path = require('path');

const VERIFIED_BASELINE_URL = process.env.ERP_BASELINE_URL || 'https://raw.githubusercontent.com/halumi2701-gif/GMU-EduTrans-ERP-Android/f6a28c609b1d82e4afee2988a09876f03452b3a7/baseline-v95.html';
const OUT_DIR = process.env.ERP_V10_OUT_DIR || 'dist/erp-web-v10';
const MODULES = [
  'role-privacy-v99.js','package-master-v97.js','media-master-v96.js','manager-ops-agent-v98.js','company-operating-system-v100.js','market-intelligence-v101.js','sales-target-engine-v103.js','company-system-center-v104.js','management-domains-v105.js','role-navigation-v106.js','company-detail-controls-v107.js','role-playbook-v108.js','execution-control-v109.js','ui-focus-shell-v110.js','automation-orchestrator-v111.js','automation-orchestrator-fix-v111.js','crm-finance-notification-v112.js','enterprise-os-v200.js','unified-v10-loader.js',
];

function assertIncludes(source, needle, label) {
  if (!source.includes(needle)) throw new Error(`ERP baseline mismatch: ${label}`);
}

function applyV20Branding(source) {
  let output = source.replaceAll('v9.5', 'v20');
  output = output.replace('ERP v20 — Online Multi-User','Enterprise OS v20 — Company Operating System');
  output = output.replace(
    'v20 memakai database online dengan Trip Folder, private document upload, approval, Trip Archive, Financial Privacy Guard, dan dukungan 3 bahasa. Perubahan antar user akan disinkronkan ulang melalui Supabase Realtime.',
    'GMU EduTrans Enterprise OS v20 aktif • Sales, CRM, Booking, Operasional, Finance, Accounting, SDM/HRIS, Payroll, Vendor, Customer Quality, Risk, Governance, Notification, AI dan Automation terhubung dalam satu sistem perusahaan role-first dengan Enterprise Control Tower, People OS dan Exception Center.'
  );
  return output;
}

async function main() {
  const response = await fetch(VERIFIED_BASELINE_URL, {
    headers: { accept: 'text/html,application/xhtml+xml', 'user-agent': 'GMU-EduTrans-Enterprise-OS-v20-release-builder/5.0' },
    redirect: 'follow',
  });
  if (!response.ok) throw new Error(`Verified ERP baseline fetch failed: HTTP ${response.status}`);

  const baseline = await response.text();
  assertIncludes(baseline, '<title>GMU EduTrans ERP v9.5', 'v9.5 title');
  assertIncludes(baseline, 'id="loginScreen"', 'login screen');
  assertIncludes(baseline, 'data-page="tripfolder"', 'Trip Folder navigation');
  assertIncludes(baseline, 'id="folderDocumentCenter"', 'Document Upload Center');
  assertIncludes(baseline, 'id="tripArchiveBanner"', 'Trip Archive');
  assertIncludes(baseline, 'id="bookingForm"', 'Booking form');
  assertIncludes(baseline, 'id="operationForm"', 'Operation Sheet');
  assertIncludes(baseline, 'Supabase Auth', 'Supabase authentication marker');

  const brandedBaseline = applyV20Branding(baseline);
  assertIncludes(brandedBaseline, '<title>GMU EduTrans ERP v20', 'v20 browser title');
  assertIncludes(brandedBaseline, 'Enterprise OS v20 — Company Operating System', 'v20 login branding');
  assertIncludes(brandedBaseline, 'ERP v20 Online', 'v20 sidebar branding');
  if (brandedBaseline.includes('ERP v9.5')) throw new Error('Legacy ERP v9.5 branding remains in production output.');

  const closeBodyIndex = brandedBaseline.lastIndexOf('</body>');
  const lastBaselineScriptIndex = brandedBaseline.lastIndexOf('</script>');
  if (closeBodyIndex < 0) throw new Error('Verified ERP baseline has no final </body> marker.');
  if (closeBodyIndex <= lastBaselineScriptIndex) throw new Error('Final </body> is not after the baseline application scripts.');
  if (brandedBaseline.includes('unified-v10-loader.js')) throw new Error('Verified baseline already contains the additive loader; refusing double injection.');

  fs.rmSync(OUT_DIR, { recursive: true, force: true });
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const loaderTag = '\n<script src="./unified-v10-loader.js" defer></script>\n<!-- gmu-enterprise-os-v20-additive -->\n';
  const index = brandedBaseline.slice(0, closeBodyIndex) + loaderTag + brandedBaseline.slice(closeBodyIndex);
  const injectedLoaderIndex = index.lastIndexOf('src="./unified-v10-loader.js"');
  if (injectedLoaderIndex <= lastBaselineScriptIndex || injectedLoaderIndex >= index.lastIndexOf('</body>')) throw new Error('Additive loader injection is not in the main document tail.');

  fs.writeFileSync(path.join(OUT_DIR, 'index.html'), index, 'utf8');
  fs.writeFileSync(path.join(OUT_DIR, 'baseline-v95.html'), baseline, 'utf8');

  for (const file of MODULES) {
    const src = path.join('erp-web', file);
    if (!fs.existsSync(src)) throw new Error(`Missing module: ${src}`);
    fs.copyFileSync(src, path.join(OUT_DIR, file));
  }

  const loaderSource = fs.readFileSync(path.join('erp-web', 'unified-v10-loader.js'), 'utf8');
  const enterpriseSource = fs.readFileSync(path.join('erp-web', 'enterprise-os-v200.js'), 'utf8');
  const activationSource = fs.readFileSync(path.join('erp-web', 'crm-finance-notification-v112.js'), 'utf8');
  assertIncludes(loaderSource, 'v20-enterprise-operating-system', 'v20 loader marker');
  assertIncludes(loaderSource, 'enterprise-os-v200.js', 'v20 enterprise module');
  assertIncludes(enterpriseSource, 'Enterprise Control Tower', 'enterprise control tower');
  assertIncludes(enterpriseSource, 'People OS', 'enterprise people os');
  assertIncludes(enterpriseSource, 'Exception Center', 'enterprise exception center');
  assertIncludes(enterpriseSource, 'Automation Map', 'enterprise automation map');
  assertIncludes(enterpriseSource, 'internal_enterprise_control_tower', 'enterprise backend RPC');
  assertIncludes(activationSource, 'internal_gl_profit_loss', 'existing accounting activation preserved');

  fs.writeFileSync(path.join(OUT_DIR, 'vercel.json'), JSON.stringify({
    cleanUrls: true,
    trailingSlash: false,
    headers: [{ source: '/(.*)', headers: [
      { key: 'X-GMU-ERP-Release', value: 'v20-enterprise-operating-system' },
      { key: 'Cache-Control', value: 'no-store, max-age=0' },
    ] }],
  }, null, 2) + '\n', 'utf8');

  const manifest = {
    release: 'GMU EduTrans Enterprise Operating System v20',
    strategy: 'additive-enterprise-control-on-verified-pinned-v9.5-baseline',
    baselineUrl: VERIFIED_BASELINE_URL,
    baselineBytes: Buffer.byteLength(baseline),
    generatedAt: new Date().toISOString(),
    modules: MODULES,
    visibleBranding: 'v20',
    backendProjectRef: 'gtgnwasijweewmaubvyg',
    enterpriseFlow: ['Lead','Quotation','DP','Booking','Operations','Crew/Vendor','Trip','Actual Cost','Payroll/Fee','Finance Closing','Feedback/CAPA','Repeat Order'],
    enterpriseDomains: ['Sales & CRM','Booking','Operations & Safety','Finance & Accounting','People & HRIS','Payroll','Vendor & Procurement','Customer Quality','Risk & Governance','Notification','AI & Automation'],
    hrisLifecycle: ['Recruitment','Contract','Onboarding','Probation 30/60/90','Attendance','Leave Balance','KPI','Performance Review','Payroll/Fee','Training','Warning/PIP','Asset & Access Handover','Offboarding'],
    enterpriseCapabilities: ['Enterprise Control Tower','Exception Center','People OS','Automation Map','Role-first Workspace','Progressive Disclosure','Cross-role Tasks','Approval Matrix','Audit Trail','Notification Delivery','Accounting Statements','CRM Recovery','Risk Register','CAPA','Workforce Planning','AI Action Drafts'],
    backendV20: ['staff_probation_checkpoints','staff_leave_balances','staff_compensation_profiles','payroll_periods','staff_asset_access_handover','enterprise_automation_policies','enterprise_exception_queue','internal_enterprise_control_tower'],
    uiPrinciples: ['role-first','progressive-disclosure','grouped-navigation','menu-search','compact-by-default','responsive','exception-driven-management'],
    preservedMarkers: ['loginScreen','tripfolder','folderDocumentCenter','tripArchiveBanner','bookingForm','operationForm'],
  };
  fs.writeFileSync(path.join(OUT_DIR, 'release-manifest.json'), JSON.stringify(manifest, null, 2) + '\n', 'utf8');

  console.log(`GMU EduTrans Enterprise OS v20 release built: ${OUT_DIR}`);
  console.log(`Verified v9.5 rollback baseline preserved: ${manifest.baselineBytes} bytes`);
  console.log('Visible production branding: v20');
  console.log(`Additive modules: ${MODULES.join(', ')}`);
}

main().catch(error => { console.error(error.stack || error.message || error); process.exit(1); });
