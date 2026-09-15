const fs = require('fs');
const path = require('path');

const VERIFIED_BASELINE_URL = process.env.ERP_BASELINE_URL || 'https://raw.githubusercontent.com/halumi2701-gif/GMU-EduTrans-ERP-Android/f6a28c609b1d82e4afee2988a09876f03452b3a7/baseline-v95.html';
const OUT_DIR = process.env.ERP_V10_OUT_DIR || 'dist/erp-web-v10';
const MODULES = [
  'role-privacy-v99.js','package-master-v97.js','media-master-v96.js','manager-ops-agent-v98.js','company-operating-system-v100.js','market-intelligence-v101.js','sales-target-engine-v103.js','company-system-center-v104.js','management-domains-v105.js','role-navigation-v106.js','company-detail-controls-v107.js','role-playbook-v108.js','execution-control-v109.js','ui-focus-shell-v110.js','automation-orchestrator-v111.js','automation-orchestrator-fix-v111.js','unified-v10-loader.js',
];

function assertIncludes(source, needle, label) {
  if (!source.includes(needle)) throw new Error(`ERP baseline mismatch: ${label}`);
}

function applyV111Branding(source) {
  let output = source.replaceAll('v9.5', 'v11.1');
  output = output.replace('ERP v11.1 — Online Multi-User','ERP v11.1 — Transactional Automation');
  output = output.replace(
    'v11.1 memakai database online dengan Trip Folder, private document upload, approval, Trip Archive, Financial Privacy Guard, dan dukungan 3 bahasa. Perubahan antar user akan disinkronkan ulang melalui Supabase Realtime.',
    'ERP Web v11.1 aktif • Transactional Automation: Sales → Booking → Finance → Operasional → SDM → Mutu → Closing → Repeat Order terhubung oleh event, tugas otomatis, audit trail, CRM activity, payroll/fee, CAPA, workforce, risk register dan draft tindakan AI; UI tetap ringkas dan role-first.'
  );
  return output;
}

async function main() {
  const response = await fetch(VERIFIED_BASELINE_URL, {
    headers: { accept: 'text/html,application/xhtml+xml', 'user-agent': 'GMU-EduTrans-ERP-v11.1-release-builder/4.1' },
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

  const brandedBaseline = applyV111Branding(baseline);
  assertIncludes(brandedBaseline, '<title>GMU EduTrans ERP v11.1', 'v11.1 browser title');
  assertIncludes(brandedBaseline, 'ERP v11.1 — Transactional Automation', 'v11.1 login branding');
  assertIncludes(brandedBaseline, 'ERP v11.1 Online', 'v11.1 sidebar branding');
  assertIncludes(brandedBaseline, 'Tugas Otomatis', 'task automation visible notice');
  assertIncludes(brandedBaseline, 'Target & Kinerja', 'target visible notice');
  if (brandedBaseline.includes('ERP v9.5')) throw new Error('Legacy ERP v9.5 branding remains in production output.');

  const closeBodyIndex = brandedBaseline.lastIndexOf('</body>');
  const lastBaselineScriptIndex = brandedBaseline.lastIndexOf('</script>');
  if (closeBodyIndex < 0) throw new Error('Verified ERP baseline has no final </body> marker.');
  if (closeBodyIndex <= lastBaselineScriptIndex) throw new Error('Final </body> is not after the baseline application scripts.');
  if (brandedBaseline.includes('unified-v10-loader.js')) throw new Error('Verified baseline already contains the v10 loader; refusing double injection.');

  fs.rmSync(OUT_DIR, { recursive: true, force: true });
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const loaderTag = '\n<script src="./unified-v10-loader.js" defer></script>\n<!-- gmu-erp-v10-additive -->\n';
  const index = brandedBaseline.slice(0, closeBodyIndex) + loaderTag + brandedBaseline.slice(closeBodyIndex);
  const injectedLoaderIndex = index.lastIndexOf('src="./unified-v10-loader.js"');
  if (injectedLoaderIndex <= lastBaselineScriptIndex || injectedLoaderIndex >= index.lastIndexOf('</body>')) throw new Error('v10 loader injection is not in the main document tail.');

  fs.writeFileSync(path.join(OUT_DIR, 'index.html'), index, 'utf8');
  fs.writeFileSync(path.join(OUT_DIR, 'baseline-v95.html'), baseline, 'utf8');

  for (const file of MODULES) {
    const src = path.join('erp-web', file);
    if (!fs.existsSync(src)) throw new Error(`Missing module: ${src}`);
    fs.copyFileSync(src, path.join(OUT_DIR, file));
  }

  fs.writeFileSync(path.join(OUT_DIR, 'vercel.json'), JSON.stringify({
    cleanUrls: true,
    trailingSlash: false,
    headers: [{ source: '/(.*)', headers: [
      { key: 'X-GMU-ERP-Release', value: 'v11.1-transactional-automation' },
      { key: 'Cache-Control', value: 'no-store, max-age=0' },
    ] }],
  }, null, 2) + '\n', 'utf8');

  const manifest = {
    release: 'GMU EduTrans ERP Web v11.1 — Transactional Automation',
    strategy: 'additive-on-verified-pinned-v9.5-baseline',
    baselineUrl: VERIFIED_BASELINE_URL,
    baselineBytes: Buffer.byteLength(baseline),
    generatedAt: new Date().toISOString(),
    modules: MODULES,
    visibleBranding: 'v11.1',
    backendProjectRef: 'gtgnwasijweewmaubvyg',
    automationFlow: ['Sales/Quotation','Booking','Invoice/DP','Operation H-7/H-3/H-1','Trip','Actual Cost','Closing','Feedback/CAPA','Repeat Order'],
    transactionalTables: ['automation_events','automation_tasks','approval_details','payroll_entries','recruitment_cases','capa_cases','workforce_plans','risk_register','crm_activities','ai_action_drafts'],
    uiPrinciples: ['role-first','progressive-disclosure','grouped-navigation','menu-search','compact-by-default','responsive'],
    navigationGroups: ['Utama','Kerja Saya','Penjualan','Operasional','Keuangan','SDM & Mutu','Sistem & AI','Semua'],
    systemModules: ['Sistem Perusahaan','Workspace per-role','Tugas Saya','Target & Kinerja','Intelijen Pasar','Kas & Likuiditas','SDM & Personalia','Mutu & Pelanggan','Pertumbuhan','Tata Kelola','Pusat AI','Jobdesk & SOP per Jabatan','Tugas Otomatis','Penggajian & Fee','Rekrutmen & Onboarding','Keluhan & CAPA','Perkiraan Kas','Perencanaan SDM','Daftar Risiko','Focused UI Shell','Transactional Automation Orchestrator'],
    preservedMarkers: ['loginScreen','tripfolder','folderDocumentCenter','tripArchiveBanner','bookingForm','operationForm'],
  };
  fs.writeFileSync(path.join(OUT_DIR, 'release-manifest.json'), JSON.stringify(manifest, null, 2) + '\n', 'utf8');

  console.log(`ERP Web v11.1 transactional automation release built: ${OUT_DIR}`);
  console.log(`Verified v9.5 rollback baseline preserved: ${manifest.baselineBytes} bytes`);
  console.log('Visible production branding: v11.1');
  console.log(`Additive modules: ${MODULES.join(', ')}`);
}

main().catch(error => { console.error(error.stack || error.message || error); process.exit(1); });
