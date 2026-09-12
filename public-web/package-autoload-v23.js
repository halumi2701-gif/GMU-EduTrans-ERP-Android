/* GMU EduTrans Public Web — automatic active package catalog v23
 * Shows ACTIVE packages immediately when the public page opens.
 * Program/date/pax filters remain available through searchPackages().
 */

(function () {
  async function loadActivePackagesOnOpen() {
    const cards = document.getElementById('packageCards');
    const health = document.getElementById('packageHealth');
    if (!cards || !window.GMU_PACKAGE_RENDERER_V23 || typeof window.api !== 'function') return;

    cards.innerHTML = '<div class="card" style="grid-column:1/-1">Memuat paket aktif...</div>';
    if (health) health.textContent = 'SYNC...';

    try {
      const paxInput = document.getElementById('tripPax');
      const dateInput = document.getElementById('tripDate');
      const pax = Number(paxInput?.value || 35);
      const date = dateInput?.value || '';
      const qs = new URLSearchParams();
      if (date) qs.set('trip_date', date);
      if (pax > 0) qs.set('pax', String(pax));

      const d = await window.api(`public-package-catalog?${qs.toString()}`);
      window.packages = Array.isArray(d.items) ? d.items : [];
      if (health) health.textContent = `${window.packages.length} ACTIVE`;
      window.GMU_PACKAGE_RENDERER_V23.renderPackageCatalog(cards, window.packages, (id) => {
        if (typeof window.choosePackage === 'function') window.choosePackage(id);
      });

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
