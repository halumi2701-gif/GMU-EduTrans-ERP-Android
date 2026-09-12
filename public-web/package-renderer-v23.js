/* GMU EduTrans Public Web — Package & Price Renderer v23
 * Source of truth: public-package-catalog
 * Public data only: selling price, min pax, facilities, description, media, estimated total.
 * Never render internal HPP, manager/sales/partner fees, profit, or margin.
 */

(function () {
  const rupiah = (value) => `Rp ${Number(value || 0).toLocaleString('id-ID')}`;
  const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[char]));
  const safeMediaUrl = (value) => {
    const raw = String(value || '').trim();
    if (!raw) return '';
    if (raw.startsWith('/')) return raw;
    try {
      const u = new URL(raw, location.origin);
      return u.protocol === 'https:' ? u.href : '';
    } catch (_) { return ''; }
  };

  function durationLabel(facilities) {
    const item = (facilities || []).find((x) => /jam|durasi/i.test(String(x)));
    return item ? String(item).replace(/^kegiatan\s+selama\s+/i, '') : null;
  }

  function mediaBlock(pkg) {
    const cover = safeMediaUrl(pkg.cover_image_url || pkg.image_url || pkg.program_cover_image_url);
    const galleryRaw = Array.isArray(pkg.gallery_urls) ? pkg.gallery_urls : (Array.isArray(pkg.images) ? pkg.images : []);
    const gallery = galleryRaw.map(safeMediaUrl).filter(Boolean).filter((x) => x !== cover).slice(0, 4);
    if (!cover && !gallery.length) return '';
    const main = cover || gallery[0];
    const thumbs = [main, ...gallery.filter((x) => x !== main)].slice(0, 4);
    return `<div class="pkg-media">
      <img class="pkg-cover" src="${esc(main)}" alt="${esc(pkg.name || 'Paket GMU EduTrans')}" loading="lazy" decoding="async">
      ${thumbs.length > 1 ? `<div class="pkg-gallery">${thumbs.map((src) => `<img src="${esc(src)}" alt="" loading="lazy" decoding="async">`).join('')}</div>` : ''}
    </div>`;
  }

  function packageCard(pkg) {
    const facilities = Array.isArray(pkg.facilities) ? pkg.facilities : [];
    const duration = durationLabel(facilities);
    const checklist = facilities.length
      ? `<div class="pkg-includes"><strong>Isi Paket</strong><ul>${facilities.map((item) => `<li>✓ ${esc(item)}</li>`).join('')}</ul></div>`
      : '';
    const total = pkg.estimated_total != null
      ? `<div class="pkg-total">Estimasi total: <strong>${rupiah(pkg.estimated_total)}</strong></div>`
      : '';
    const durationChip = duration ? `<span>${esc(duration)}</span>` : '';

    return `<article class="card pkg pkg-v23">
      ${mediaBlock(pkg)}
      <span class="badge">${esc(pkg.program_name || 'EduTrip')}</span>
      <h3>${esc(pkg.name || 'Paket GMU EduTrans')}</h3>
      <p class="muted">${esc(pkg.description || '')}</p>
      <div class="price">${rupiah(pkg.price_per_pax)} <small>/ pax</small></div>
      <div class="pkg-meta"><span>Minimum ${Number(pkg.min_pax || 1)} peserta</span>${durationChip}</div>
      ${checklist}
      ${total}
      <button class="btn btn-g" style="width:100%" data-package-id="${esc(pkg.id)}">Pilih Paket</button>
    </article>`;
  }

  function renderPackageCatalog(container, packages, onChoose) {
    if (!container) return;
    if (!Array.isArray(packages) || packages.length === 0) {
      container.innerHTML = '<div class="card" style="grid-column:1/-1"><h3>Belum ada paket aktif</h3><p class="muted">Silakan pilih program lain atau ajukan kebutuhan custom.</p></div>';
      return;
    }
    container.innerHTML = packages.map(packageCard).join('');
    container.querySelectorAll('[data-package-id]').forEach((button) => {
      button.addEventListener('click', () => onChoose?.(button.dataset.packageId));
    });
  }

  window.GMU_PACKAGE_RENDERER_V23 = { packageCard, renderPackageCatalog };
})();
