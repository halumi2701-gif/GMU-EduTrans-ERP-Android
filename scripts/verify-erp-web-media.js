const fs = require('fs');
const vm = require('vm');

const file = 'erp-web/media-master-v96.js';
const src = fs.readFileSync(file, 'utf8');

new vm.Script(src, { filename: file });

function must(text, label) {
  if (!src.includes(text)) throw new Error(`Missing ERP Media contract: ${label}`);
}
function mustNot(text, label) {
  if (src.includes(text)) throw new Error(`Forbidden ERP Media content: ${label}`);
}

must("const BUCKET = 'edutrans-media'", 'official media bucket');
must("const INTERNAL_FN = 'internal-media-master'", 'internal media function');
must('const MAX_BYTES = 8 * 1024 * 1024', '8 MiB file limit');
must('const MAX_GALLERY = 5', 'gallery max 5');
must("image/jpeg", 'JPEG');
must("image/png", 'PNG');
must("image/webp", 'WebP');
for (const role of ['Owner', 'Director', 'Direktur', 'Manager', 'Manager EduTrans', 'Admin']) {
  must(`'${role}'`, `role ${role}`);
}
must("action: 'catalog'", 'catalog action');
must("action: 'load'", 'load action');
must("action: 'save'", 'save action');
must("action: 'discard'", 'discard cleanup');
must("upsert: false", 'no overwrite upload');
must('Jadikan Cover', 'make cover action');
must('Galeri ${state.gallery.length}/${MAX_GALLERY}', 'gallery status');
must('Tidak memuat HPP, fee, profit, margin', 'finance privacy notice');

// Media Master must not query or render internal finance values.
for (const key of ['base_cost', 'manager_fee', 'sales_fee', 'mitra_fee', 'partner_fee', 'target_margin_pct', 'floor_margin_pct']) {
  mustNot(key, key);
}

console.log('GMU ERP Web Media Master contract passed.');
console.log('Role guard | 8MiB | JPG/PNG/WebP | cover + gallery<=5 | save/discard | finance privacy');
