const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('public-web/package-renderer-v23.js', 'utf8');
const sandbox = {
  window: {},
  location: { origin: 'https://edutrans.garsyanimultiusaha.site' },
  URL,
  console,
  Number,
  String,
  Array,
};
vm.createContext(sandbox);
vm.runInContext(source, sandbox, { filename: 'package-renderer-v23.js' });

const renderer = sandbox.window.GMU_PACKAGE_RENDERER_V23;
assert(renderer && typeof renderer.packageCard === 'function', 'packageCard renderer must be exported');

const facilities = [
  'Edukasi stasiun & perkeretaapian',
  '6 sesi profesi / narasumber',
  'Observasi keberangkatan kereta',
  'Worksheet',
  'Sertifikat',
  'Snack',
  'Dokumentasi',
  'Pendampingan TL / MC',
  'Pendampingan staf operasional',
  'Kegiatan selama ±2 jam',
];
const programCover = 'https://gtgnwasijweewmaubvyg.supabase.co/storage/v1/object/public/edutrans-media/program/program-1/cover.webp';
const gallery = Array.from({ length: 5 }, (_, i) =>
  `https://gtgnwasijweewmaubvyg.supabase.co/storage/v1/object/public/edutrans-media/package/package-1/gallery-${i + 1}.webp`
);

const html = renderer.packageCard({
  id: 'package-1',
  package_code: 'PKG-GMU-00008',
  program_name: 'Edukasi Lingkungan Stasiun',
  name: 'Paket Edukasi Lingkungan Stasiun',
  description: 'Aman <script>alert(1)</script>',
  price_per_pax: 46000,
  min_pax: 20,
  facilities,
  estimated_total: 920000,
  cover_image_url: null,
  program_cover_image_url: programCover,
  gallery_urls: [...gallery, 'http://invalid.example/image.jpg'],
  hpp: 455000,
  manager_fee: 130000,
  sales_fee: 50000,
  mitra_fee: 50000,
  profit: 235000,
  margin: 25.54,
});

assert(html.includes(programCover), 'package without cover must fall back to Program cover');
for (const url of gallery) assert(html.includes(url), `gallery image must render: ${url}`);
assert(!html.includes('http://invalid.example/image.jpg'), 'non-HTTPS gallery URL must be rejected');
assert.strictEqual((html.match(/data-media-src=/g) || []).length, 6, 'cover + all five gallery thumbnails must render');
assert.strictEqual((html.match(/<li>✓/g) || []).length, 10, 'all ten package facilities must render');
assert(html.includes('Minimum 20 peserta'), 'minimum pax must render');
assert(html.includes('±2 jam'), 'duration must render');
assert(html.includes('data-package-id="package-1"'), 'booking package id must remain wired');
assert(!html.includes('<script>alert(1)</script>'), 'description HTML must be escaped');
assert(html.includes('&lt;script&gt;alert(1)&lt;/script&gt;'), 'escaped description must remain readable');

for (const forbidden of ['455000', '130000', '235000', '25.54']) {
  assert(!html.includes(forbidden), `internal finance value must not render: ${forbidden}`);
}

// Booking invariant: Media Sync is presentation-only. Choosing a package must pass
// only the package ID into the existing booking flow, never the full package/media object.
assert(
  /onChoose\?\.\(button\.dataset\.packageId\)/.test(source),
  'Pilih Paket must pass only data-package-id to the existing booking callback'
);
assert(
  !/onChoose\?\.\((?:pkg|package|packages|window\.packages)/.test(source),
  'booking callback must never receive package/media objects'
);

const packageCover = 'https://gtgnwasijweewmaubvyg.supabase.co/storage/v1/object/public/edutrans-media/package/package-1/cover.webp';
const packageCoverHtml = renderer.packageCard({
  id: 'package-1',
  name: 'Paket Test',
  price_per_pax: 46000,
  min_pax: 20,
  facilities: [],
  cover_image_url: packageCover,
  program_cover_image_url: programCover,
  gallery_urls: [],
});
assert(packageCoverHtml.includes(packageCover), 'package cover must override Program fallback');
assert(!packageCoverHtml.includes(programCover), 'Program fallback must not replace an explicit package cover');

console.log('GMU public web media renderer contract passed.');
console.log('Program fallback | 5 gallery | 10 facilities | package-id-only booking | safe HTML | finance fields hidden');
