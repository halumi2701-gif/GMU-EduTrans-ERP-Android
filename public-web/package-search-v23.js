/* GMU EduTrans Public Web — package search integration v23
 * Requires package-renderer-v23.js loaded first.
 * Keeps existing booking/account flow intact.
 */

(function () {
  async function searchPackagesV23() {
    const programSel = document.getElementById('programSel');
    const tripDate = document.getElementById('tripDate');
    const tripPax = document.getElementById('tripPax');
    const cards = document.getElementById('packageCards');
    const health = document.getElementById('packageHealth');
    if (!programSel || !tripDate || !tripPax || !cards) return;

    const pid = programSel.value;
    const date = tripDate.value;
    const pax = Number(tripPax.value || 1);

    const bookingProgram = document.getElementById('bookingProgram');
    const bookingDate = document.getElementById('bookingDate');
    const bookingPax = document.getElementById('bookingPax');
    const packageId = document.getElementById('packageId');
    if (bookingProgram) bookingProgram.value = pid;
    if (bookingDate) bookingDate.value = date;
    if (bookingPax) bookingPax.value = pax;
    if (packageId) packageId.value = '';

    cards.innerHTML = '<div class="card" style="grid-column:1/-1">Memuat paket...</div>';
    if (health) health.textContent = 'SYNC...';

    try {
      const qs = new URLSearchParams({ program_id: pid, trip_date: date, pax: String(pax) });
      const d = await window.api(`public-package-catalog?${qs.toString()}`);
      window.packages = Array.isArray(d.items) ? d.items : [];
      if (health) health.textContent = `${window.packages.length} ACTIVE`;
      window.GMU_PACKAGE_RENDERER_V23.renderPackageCatalog(cards, window.packages, (id) => {
        if (typeof window.choosePackage === 'function') window.choosePackage(id);
      });
    } catch (error) {
      if (health) health.textContent = 'ON DEMAND';
      cards.innerHTML = '<div class="card" style="grid-column:1/-1"><h3>Paket sedang sinkron ulang</h3><p class="muted">Program Master tetap aktif. Silakan coba lagi atau ajukan booking.</p></div>';
    } finally {
      document.getElementById('paket')?.scrollIntoView({ behavior: 'smooth' });
    }
  }

  window.searchPackages = searchPackagesV23;
})();
