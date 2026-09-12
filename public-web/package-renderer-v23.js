/* GMU EduTrans Public Web — Package & Price Renderer v23
 * Source of truth: public-package-catalog
 * Public data only: selling price, min pax, facilities, description, estimated total.
 * Never render internal HPP, manager/sales/partner fees, profit, or margin.
 */

(function () {
  const rupiah = (value) => `Rp ${Number(value || 0).toLocaleString('id-ID')}`;
  const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[char]));

  function packageCard(pkg) {
    const facilities = Array.isArray(pkg.facilities) ? pkg.facilities : [];
    const checklist = facilities.length
      ? `<div class="pkg-includes"><strong>Isi Paket</strong><ul>${facilities.map((item) => `<li>✓ ${esc(item)}</li>`).join('')}</ul></div>`
      : '';
    const total = pkg.estimated_total != null
      ? `<div class="pkg-total">Estimasi total: <strong>${rupiah(pkg.estimated_total)}</strong></div>`
      : '';

    return `<article class="card pkg pkg-v23">
      <span class="badge">${esc(pkg.program_name || 'EduTrip')}</span>
      <h3>${esc(pkg.name || 'Paket GMU EduTrans')}</h3>
      <p class="muted">${esc(pkg.description || '')}</p>
      <div class="price">${rupiah(pkg.price_per_pax)} <small>/ pax</small></div>
      <div class="pkg-meta"><span>Minimum ${Number(pkg.min_pax || 1)} peserta</span><span>±2 jam</span></div>
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
