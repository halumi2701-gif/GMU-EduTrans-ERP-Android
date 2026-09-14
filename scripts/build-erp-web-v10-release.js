const fs = require('fs');
const path = require('path');

const LIVE_URL = process.env.ERP_LIVE_URL || 'https://erp.edutrans.garsyanimultiusaha.site/';
const OUT_DIR = process.env.ERP_V10_OUT_DIR || 'dist/erp-web-v10';
const MODULES = [
  'role-privacy-v99.js',
  'package-master-v97.js',
  'media-master-v96.js',
  'manager-ops-agent-v98.js',
  'company-operating-system-v100.js',
  'market-intelligence-v101.js',
  'unified-v10-loader.js',
];

function assertIncludes(source, needle, label) {
  if (!source.includes(needle)) throw new Error(`Live ERP baseline mismatch: ${label}`);
}

async function main() {
  const response = await fetch(LIVE_URL, {
    headers: {
      accept: 'text/html,application/xhtml+xml',
      'user-agent': 'GMU-EduTrans-ERP-v10-release-builder/1.0',
    },
    redirect: 'follow',
  });
  if (!response.ok) throw new Error(`Live ERP fetch failed: HTTP ${response.status}`);

  const baseline = await response.text();
  assertIncludes(baseline, '<title>GMU EduTrans ERP v9.5', 'v9.5 title');
  assertIncludes(baseline, 'id="loginScreen"', 'login screen');
  assertIncludes(baseline, 'data-page="tripfolder"', 'Trip Folder navigation');
  assertIncludes(baseline, 'id="folderDocumentCenter"', 'Document Upload Center');
  assertIncludes(baseline, 'id="tripArchiveBanner"', 'Trip Archive');
  assertIncludes(baseline, 'id="bookingForm"', 'Booking form');
  assertIncludes(baseline, 'id="operationForm"', 'Operation Sheet');
  assertIncludes(baseline, 'Supabase Auth', 'Supabase authentication marker');

  if (!baseline.includes('</body>')) throw new Error('Live ERP baseline has no </body> marker.');
  if (baseline.includes('unified-v10-loader.js')) throw new Error('Live ERP already contains the v10 loader; refusing double injection.');

  fs.rmSync(OUT_DIR, { recursive: true, force: true });
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const loaderTag = '\n<script src="./unified-v10-loader.js" defer></script>\n<!-- gmu-erp-v10-additive -->\n';
  const index = baseline.replace('</body>', `${loaderTag}</body>`);
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
    headers: [{ source: '/(.*)', headers: [{ key: 'X-GMU-ERP-Release', value: 'v10.2-additive' }] }],
  }, null, 2) + '\n', 'utf8');

  const manifest = {
    release: 'GMU EduTrans ERP Web v10.2 — Company Operating System & Market Intelligence',
    strategy: 'additive-on-live-v9.5',
    baselineUrl: LIVE_URL,
    baselineBytes: Buffer.byteLength(baseline),
    generatedAt: new Date().toISOString(),
    modules: MODULES,
    preservedMarkers: [
      'loginScreen',
      'tripfolder',
      'folderDocumentCenter',
      'tripArchiveBanner',
      'bookingForm',
      'operationForm',
    ],
  };
  fs.writeFileSync(path.join(OUT_DIR, 'release-manifest.json'), JSON.stringify(manifest, null, 2) + '\n', 'utf8');

  console.log(`ERP Web v10.2 release bundle built: ${OUT_DIR}`);
  console.log(`Live v9.5 baseline preserved: ${manifest.baselineBytes} bytes`);
  console.log(`Additive modules: ${MODULES.join(', ')}`);
}

main().catch(error => {
  console.error(error.stack || error.message || error);
  process.exit(1);
});
