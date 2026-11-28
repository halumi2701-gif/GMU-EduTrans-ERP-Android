/* GMU EduTrans public active-package loader.
 * Populate both the package cards and booking package selector from the active public catalog.
 */
(function () {
  async function loadActivePackagesOnOpen() {
    const cards = document.getElementById('packageCards');
    const health = document.getElementById('packageHealth');
    const choice = document.getElementById('bookingPackageChoice');
    if (!cards || !window.GMU_PACKAGE_RENDERER_V23 || typeof window.api !== 'function') return;
    cards.innerHTML = '<div class="card" style="grid-column:1/-1">Memuat paket aktif...</div>';
    if (health) health.textContent = 'SYNC...';
    try {
      const d = await window.api('public-package-catalog');
      const items = Array.isArray(d.items) ? d.items : [];
      window.packages = items;
      if (health) health.textContent = items.length + ' ACTIVE';
      window.GMU_PACKAGE_RENDERER_V23.renderPackageCatalog(cards, items, (id) => {
        if (typeof window.choosePackage === 'function') window.choosePackage(id);
      });
      if (choice) {
        const current = choice.value;
        choice.innerHTML = '<option value="">Pilih paket aktif</option>' + items.map(p =>
          '<option value="' + String(p.id || '').replace(/&/g,'&amp;').replace(/"/g,'&quot;') +
          '">' + String(p.package_code || 'PAKET').replace(/&/g,'&amp;').replace(/</g,'&lt;') +
          ' — ' + String(p.name || 'Paket GMU EduTrans').replace(/&/g,'&amp;').replace(/</g,'&lt;') +
          ' — ' + (p.quote_required ? 'Quotation' : 'Rp ' + Number(p.price_per_pax || 0).toLocaleString('id-ID')) +
          '/peserta</option>'
        ).join('');
        if (items.some(p => String(p.id) === current)) choice.value = current;
      }
      const headText = document.querySelector('#paket .head p');
      if (headText) headText.textContent = 'Paket aktif GMU EduTrans — tersinkron otomatis dari ERP.';
    } catch (error) {
      if (health) health.textContent = 'ON DEMAND';
      cards.innerHTML = '<div class="card" style="grid-column:1/-1"><h3>Paket siap dicari</h3><p class="muted">Pilih program, tanggal, dan jumlah peserta lalu klik Lihat Paket & Harga.</p></div>';
    }
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', loadActivePackagesOnOpen, { once: true });
  } else {
    loadActivePackagesOnOpen();
  }
  window.GMU_AUTOLOAD_PACKAGES_V23 = loadActivePackagesOnOpen;
})();
