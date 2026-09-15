const fs = require('fs');
const path = require('path');

const VERIFIED_BASELINE_URL = process.env.ERP_BASELINE_URL || 'https://raw.githubusercontent.com/halumi2701-gif/GMU-EduTrans-ERP-Android/f6a28c609b1d82e4afee2988a09876f03452b3a7/baseline-v95.html';
const OUT_DIR = process.env.ERP_V10_OUT_DIR || 'dist/erp-web-v10';
const MODULES = [
  'role-privacy-v99.js','package-master-v97.js','media-master-v96.js','manager-ops-agent-v98.js','company-operating-system-v100.js','market-intelligence-v101.js','sales-target-engine-v103.js','company-system-center-v104.js','management-domains-v105.js','role-navigation-v106.js','company-detail-controls-v107.js','role-playbook-v108.js','execution-control-v109.js','ui-focus-shell-v110.js','automation-orchestrator-v111.js','automation-orchestrator-fix-v111.js','crm-finance-notification-v112.js','enterprise-os-v200.js','business-priority-v201.js','recovery-command-v202.js','company-autopilot-v210.js','target-cascade-v212.js','unified-v10-loader.js',
];

function assertIncludes(source, needle, label) {
  if (!source.includes(needle)) throw new Error(`ERP baseline mismatch: ${label}`);
}

function applyV21Branding(source) {
  let output = source.replaceAll('v9.5', 'v21');
  output = output.replace('ERP v21 — Online Multi-User','Company Autopilot v21 — Enterprise Operating System');
  output = output.replace(
    'v21 memakai database online dengan Trip Folder, private document upload, approval, Trip Archive, Financial Privacy Guard, dan dukungan 3 bahasa. Perubahan antar user akan disinkronkan ulang melalui Supabase Realtime.',
    'GMU EduTrans Company Autopilot v21.2 aktif • Target Direktur menggerakkan Lead, Sales, Quotation, Payment, Booking, Operasional, Finance, Payroll, Retention dan Workforce secara end-to-end dengan management by exception.'
  );
  return output;
}

async function main() {
  const response = await fetch(VERIFIED_BASELINE_URL, {
    headers: { accept: 'text/html,application/xhtml+xml', 'user-agent': 'GMU-EduTrans-v21.2-release-builder/1.0' },
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

  const brandedBaseline = applyV21Branding(baseline);
  assertIncludes(brandedBaseline, '<title>GMU EduTrans ERP v21', 'v21 browser title');
  assertIncludes(brandedBaseline, 'Company Autopilot v21 — Enterprise Operating System', 'v21 login branding');
  assertIncludes(brandedBaseline, 'ERP v21 Online', 'v21 sidebar branding');
  if (brandedBaseline.includes('ERP v9.5')) throw new Error('Legacy ERP v9.5 branding remains in production output.');

  const closeBodyIndex = brandedBaseline.lastIndexOf('</body>');
  const lastBaselineScriptIndex = brandedBaseline.lastIndexOf('</script>');
  if (closeBodyIndex < 0) throw new Error('Verified ERP baseline has no final </body> marker.');
  if (closeBodyIndex <= lastBaselineScriptIndex) throw new Error('Final </body> is not after the baseline application scripts.');
  if (brandedBaseline.includes('unified-v10-loader.js')) throw new Error('Verified baseline already contains the additive loader; refusing double injection.');

  fs.rmSync(OUT_DIR, { recursive: true, force: true });
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const loaderTag = '\n<script src="./unified-v10-loader.js" defer></script>\n<!-- gmu-company-autopilot-v21-additive -->\n';
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
  const prioritySource = fs.readFileSync(path.join('erp-web', 'business-priority-v201.js'), 'utf8');
  const recoverySource = fs.readFileSync(path.join('erp-web', 'recovery-command-v202.js'), 'utf8');
  const autopilotSource = fs.readFileSync(path.join('erp-web', 'company-autopilot-v210.js'), 'utf8');
  const targetCascadeSource = fs.readFileSync(path.join('erp-web', 'target-cascade-v212.js'), 'utf8');
  assertIncludes(loaderSource, 'v21.2-target-cascade-workforce-autopilot', 'v21.2 loader marker');
  assertIncludes(loaderSource, 'company-autopilot-v210.js', 'v21 autopilot module');
  assertIncludes(loaderSource, 'target-cascade-v212.js', 'v21.2 target cascade module');
  assertIncludes(loaderSource, 'v21.2-target-cascade-workforce-autopilot-layer', 'v21.2 release layer');
  assertIncludes(enterpriseSource, 'Enterprise Control Tower', 'enterprise control tower');
  assertIncludes(activationSource, 'internal_gl_profit_loss', 'existing accounting activation preserved');
  assertIncludes(prioritySource, "const VERSION = 'v20.1-business-priority-command'", 'priority layer preserved');
  assertIncludes(recoverySource, "const VERSION = 'v20.2-recovery-accountability-command'", 'recovery layer preserved');
  assertIncludes(autopilotSource, "const VERSION = 'v21-company-autopilot'", 'autopilot UI version');
  assertIncludes(autopilotSource, 'internal_company_autopilot_v21', 'autopilot backend RPC');
  assertIncludes(targetCascadeSource, "const VERSION = 'v21.2-target-cascade-workforce-autopilot'", 'target cascade UI version');
  assertIncludes(targetCascadeSource, 'internal_target_cascade_status', 'target cascade status RPC');
  assertIncludes(targetCascadeSource, 'internal_set_executive_profit_target', 'director target command RPC');
  assertIncludes(targetCascadeSource, 'Workforce & Compensation Guardrail', 'workforce guardrail UI');

  fs.writeFileSync(path.join(OUT_DIR, 'vercel.json'), JSON.stringify({
    cleanUrls: true,
    trailingSlash: false,
    headers: [{ source: '/(.*)', headers: [
      { key: 'X-GMU-ERP-Release', value: 'v21.2-target-cascade-workforce-autopilot' },
      { key: 'X-GMU-ERP-Priority-Layer', value: 'v20.1-priority-command-layer' },
      { key: 'X-GMU-ERP-Recovery-Layer', value: 'v20.2-recovery-accountability-layer' },
      { key: 'X-GMU-ERP-Autopilot-Layer', value: 'v21-company-autopilot-layer' },
      { key: 'X-GMU-ERP-Target-Cascade-Layer', value: 'v21.2-target-cascade-workforce-autopilot-layer' },
      { key: 'Cache-Control', value: 'no-store, max-age=0' },
    ] }],
  }, null, 2) + '\n', 'utf8');

  const manifest = {
    release: 'GMU EduTrans Target Cascade & Workforce Autopilot v21.2',
    strategy: 'additive-target-cascade-on-company-autopilot-v21-and-verified-pinned-v9.5-baseline',
    baselineUrl: VERIFIED_BASELINE_URL,
    baselineBytes: Buffer.byteLength(baseline),
    generatedAt: new Date().toISOString(),
    modules: MODULES,
    visibleBranding: 'v21',
    backendProjectRef: 'gtgnwasijweewmaubvyg',
    enterpriseFlow: ['Director Target','Profit/Recovery Gap','Revenue Requirement','Manager Recovery','Sales Target','Admin SLA','Operations Capacity','Finance Closing','TL Execution','Actual Profit','Reforecast'],
    targetCascadeCapabilities: ['Rp50m cumulative recovery command','Rp15m monthly profit floor','25% healthy margin fallback','Fixed payroll + overhead guardrail','Sales target distribution','Role fallback to Manager','Forecast gap recovery tasks','Truthful booking estimate only with real closing history'],
    approvalOnlyActions: ['Money transfer','Refund','Payroll payment','Strategic price change','Margin below 20%','Reserve use','Permanent hiring/firing','Legal','Safety-critical decisions'],
    preservedMarkers: ['loginScreen','tripfolder','folderDocumentCenter','tripArchiveBanner','bookingForm','operationForm'],
  };
  fs.writeFileSync(path.join(OUT_DIR, 'release-manifest.json'), JSON.stringify(manifest, null, 2) + '\n', 'utf8');

  console.log(`GMU EduTrans v21.2 release built: ${OUT_DIR}`);
  console.log(`Verified v9.5 rollback baseline preserved: ${manifest.baselineBytes} bytes`);
  console.log('Visible production branding: v21');
  console.log(`Additive modules: ${MODULES.join(', ')}`);
}

main().catch(error => { console.error(error.stack || error.message || error); process.exit(1); });