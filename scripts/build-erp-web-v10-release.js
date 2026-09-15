const fs = require('fs');
const path = require('path');

const VERIFIED_BASELINE_URL = process.env.ERP_BASELINE_URL || 'https://raw.githubusercontent.com/halumi2701-gif/GMU-EduTrans-ERP-Android/f6a28c609b1d82e4afee2988a09876f03452b3a7/baseline-v95.html';
const OUT_DIR = process.env.ERP_V10_OUT_DIR || 'dist/erp-web-v10';
const MODULES = [
  'role-privacy-v99.js','package-master-v97.js','media-master-v96.js','manager-ops-agent-v98.js','company-operating-system-v100.js','market-intelligence-v101.js','sales-target-engine-v103.js','company-system-center-v104.js','management-domains-v105.js','role-navigation-v106.js','company-detail-controls-v107.js','role-playbook-v108.js','execution-control-v109.js','ui-focus-shell-v110.js','automation-orchestrator-v111.js','automation-orchestrator-fix-v111.js','crm-finance-notification-v112.js','unified-v10-loader.js',
];

function assertIncludes(source, needle, label) {
  if (!source.includes(needle)) throw new Error(`ERP baseline mismatch: ${label}`);
}

function applyV112Branding(source) {
  let output = source.replaceAll('v9.5', 'v11.2');
  output = output.replace('ERP v11.2 — Online Multi-User','ERP v11.2 — CRM, Accounting & Notification');
  output = output.replace(
    'v11.2 memakai database online dengan Trip Folder, private document upload, approval, Trip Archive, Financial Privacy Guard, dan dukungan 3 bahasa. Perubahan antar user akan disinkronkan ulang melalui Supabase Realtime.',
    'ERP Web v11.2 aktif • CRM, Accounting & Notification: lead assignment, follow-up, lost reason, conversion, recovery, AR/AP Aging, Trial Balance, Laba Rugi, Neraca, Arus Kas, finance period dan delivery log notifikasi terhubung ke backend production; UI tetap role-first dan audit-ready.'
  );
  return output;
}

async function main() {
  const response = await fetch(VERIFIED_BASELINE_URL, {
    headers: { accept: 'text/html,application/xhtml+xml', 'user-agent': 'GMU-EduTrans-ERP-v11.2-release-builder/4.2' },
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

  const brandedBaseline = applyV112Branding(baseline);
  assertIncludes(brandedBaseline, '<title>GMU EduTrans ERP v11.2', 'v11.2 browser title');
  assertIncludes(brandedBaseline, 'ERP v11.2 — CRM, Accounting & Notification', 'v11.2 login branding');
  assertIncludes(brandedBaseline, 'ERP v11.2 Online', 'v11.2 sidebar branding');
  if (brandedBaseline.includes('ERP v9.5')) throw new Error('Legacy ERP v9.5 branding remains in production output.');

  const closeBodyIndex = brandedBaseline.lastIndexOf('</body>');
  const lastBaselineScriptIndex = brandedBaseline.lastIndexOf('</script>');
  if (closeBodyIndex < 0) throw new Error('Verified ERP baseline has no final </body> marker.');
  if (closeBodyIndex <= lastBaselineScriptIndex) throw new Error('Final </body> is not after the baseline application scripts.');
  if (brandedBaseline.includes('unified-v10-loader.js')) throw new Error('Verified baseline already contains the additive loader; refusing double injection.');

  fs.rmSync(OUT_DIR, { recursive: true, force: true });
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const loaderTag = '\n<script src="./unified-v10-loader.js" defer></script>\n<!-- gmu-erp-v10-additive -->\n';
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
  const automationSource = fs.readFileSync(path.join('erp-web', 'automation-orchestrator-v111.js'), 'utf8');
  const v112Source = fs.readFileSync(path.join('erp-web', 'crm-finance-notification-v112.js'), 'utf8');
  assertIncludes(loaderSource, 'v11.2-crm-finance-notification-activation', 'v11.2 loader marker');
  assertIncludes(loaderSource, 'crm-finance-notification-v112.js', 'v11.2 activation module');
  assertIncludes(automationSource, 'Tugas Otomatis Aktual', 'transactional task UI');
  assertIncludes(v112Source, 'crm_lead_controls', 'CRM lead controls');
  assertIncludes(v112Source, 'internal_crm_sales_scorecard', 'CRM sales scorecard');
  assertIncludes(v112Source, 'internal_management_ar_aging', 'AR aging');
  assertIncludes(v112Source, 'internal_management_ap_aging', 'AP aging');
  assertIncludes(v112Source, 'internal_gl_trial_balance', 'trial balance');
  assertIncludes(v112Source, 'internal_gl_profit_loss', 'profit and loss');
  assertIncludes(v112Source, 'internal_gl_balance_sheet', 'balance sheet');
  assertIncludes(v112Source, 'internal_gl_cash_flow', 'cash flow');
  assertIncludes(v112Source, 'customer_notification_deliveries', 'customer delivery log');
  assertIncludes(v112Source, 'internal_notification_deliveries', 'internal delivery log');

  fs.writeFileSync(path.join(OUT_DIR, 'vercel.json'), JSON.stringify({
    cleanUrls: true,
    trailingSlash: false,
    headers: [{ source: '/(.*)', headers: [
      { key: 'X-GMU-ERP-Release', value: 'v11.2-crm-finance-notification' },
      { key: 'Cache-Control', value: 'no-store, max-age=0' },
    ] }],
  }, null, 2) + '\n', 'utf8');

  const manifest = {
    release: 'GMU EduTrans ERP Web v11.2 — CRM, Accounting & Notification Activation',
    strategy: 'additive-on-verified-pinned-v9.5-baseline',
    baselineUrl: VERIFIED_BASELINE_URL,
    baselineBytes: Buffer.byteLength(baseline),
    generatedAt: new Date().toISOString(),
    modules: MODULES,
    visibleBranding: 'v11.2',
    backendProjectRef: 'gtgnwasijweewmaubvyg',
    automationFlow: ['Lead/CRM','Quotation','Booking','Invoice/DP','Operation H-7/H-3/H-1','Trip','Actual Cost','Payroll/Fee','Finance Closing','Feedback/CAPA','Repeat Order'],
    transactionalTables: ['automation_events','automation_tasks','approval_details','payroll_entries','recruitment_cases','capa_cases','workforce_plans','risk_register','crm_activities','crm_lead_controls','ai_action_drafts'],
    financeCapabilities: ['AR Aging','AP Aging','Trial Balance','Profit & Loss','Balance Sheet','Cash Flow','Finance Period Lock','Bank Reconciliation','Treasury Ledger'],
    notificationCapabilities: ['Customer Outbox','Customer Delivery Log','Internal Delivery Log','Retry/Attempts','Provider Message ID','Delivery Error'],
    crmCapabilities: ['Lead Assignment','Lead Stage','Lead Source','Next Follow-up','Overdue','Lost Reason','Recovery Plan','Sales Scorecard','Conversion','Weighted Pipeline'],
    uiPrinciples: ['role-first','progressive-disclosure','grouped-navigation','menu-search','compact-by-default','responsive'],
    navigationGroups: ['Utama','Kerja Saya','Penjualan','Operasional','Keuangan','SDM & Mutu','Sistem & AI','Semua'],
    systemModules: ['Sistem Perusahaan','Workspace per-role','Tugas Saya','Target & Kinerja','Intelijen Pasar','Kas & Likuiditas','Accounting & Laporan','Pusat Notifikasi','SDM & Personalia','Mutu & Pelanggan','Pertumbuhan','Tata Kelola','Pusat AI','Jobdesk & SOP per Jabatan','Tugas Otomatis','Penggajian & Fee','Rekrutmen & Onboarding','Keluhan & CAPA','Perkiraan Kas','Perencanaan SDM','Daftar Risiko','Focused UI Shell','Transactional Automation Orchestrator'],
    preservedMarkers: ['loginScreen','tripfolder','folderDocumentCenter','tripArchiveBanner','bookingForm','operationForm'],
  };
  fs.writeFileSync(path.join(OUT_DIR, 'release-manifest.json'), JSON.stringify(manifest, null, 2) + '\n', 'utf8');

  console.log(`ERP Web v11.2 CRM/accounting/notification release built: ${OUT_DIR}`);
  console.log(`Verified v9.5 rollback baseline preserved: ${manifest.baselineBytes} bytes`);
  console.log('Visible production branding: v11.2');
  console.log(`Additive modules: ${MODULES.join(', ')}`);
}

main().catch(error => { console.error(error.stack || error.message || error); process.exit(1); });
