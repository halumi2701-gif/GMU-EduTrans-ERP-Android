(() => {
  'use strict';

  const VERSION = 'v21.8-station-operating-policy';
  const ALLOWED = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales']);
  const POLICY = Object.freeze({
    programName: 'Edukasi Profesi & Lingkungan Stasiun',
    minimumMarginPct: 35,
    workforce: [
      ['Manager EduTrans', 'Rp1.000.000/bulan + Rp30.000/sesi'],
      ['Sales', 'Rp600.000/bulan + Rp2.500/paid pax + bonus Rp250.000 di 200 pax'],
      ['Admin / CS', 'Rp300.000/bulan'],
      ['Finance', 'Rp300.000/bulan'],
      ['Ops + Dokumentasi', 'Rp40.000/sesi'],
      ['TL / MC', 'Rp110.000/sesi'],
      ['Kepala Stasiun', 'Rp50.000/sesi'],
      ['6 Narasumber', 'Rp120.000/sesi total'],
    ],
    directCosts: [
      ['Mitra', 'Rp2.500/paid pax'],
      ['Makan crew', 'Rp40.000/sesi'],
      ['Snack', 'Rp2.500/pax'],
      ['Sertifikat', 'Rp500/pax'],
      ['Worksheet', 'Rp500/pax'],
    ],
    targetLevels: [
      ['BEP', '60 pax'],
      ['Produktif', '100 pax'],
      ['Target Utama', '200 pax'],
      ['Stretch', '300 pax'],
      ['Outstanding', '400 pax'],
    ],
  });

  const q = (s, root = document) => root.querySelector(s);
  function role() { try { return String(profile?.role || ''); } catch (_) { return ''; } }
  function allowed() { return ALLOWED.has(role()); }

  function rows(items) {
    return items.map(([name, rule]) => `<tr><td><b>${name}</b></td><td>${rule}</td></tr>`).join('');
  }

  function patchHealthyMargin() {
    document.querySelectorAll('.gmu-target-card').forEach(card => {
      const label = card.querySelector('small')?.textContent?.trim();
      if (label !== 'Margin kegiatan sehat') return;
      const value = card.querySelector('b');
      const note = card.querySelector('span');
      if (value) value.textContent = '≥ 35%';
      if (note) note.textContent = 'Quotation di bawah 35% wajib approval Manager/Owner.';
    });
  }

  function render() {
    if (!allowed() || q('#gmuStationOperatingPolicy')) return;
    const anchor = q('#gmuSales200Target') || q('#gmuStationTargetSection');
    if (!anchor) return;

    const section = document.createElement('div');
    section.id = 'gmuStationOperatingPolicy';
    section.className = 'card section';
    section.innerHTML = `
      <div class="head">
        <div>
          <h3>Struktur SDM & Biaya Program</h3>
          <p>${POLICY.programName} • policy internal untuk pricing, RAB, quotation, dan kontrol margin.</p>
        </div>
        <span class="badge info">MARGIN ≥ ${POLICY.minimumMarginPct}%</span>
      </div>
      <div class="gmu-target-grid">
        ${POLICY.targetLevels.map(([label,value]) => `<div class="gmu-target-card"><small>${label}</small><b>${value}</b><span>Level target program.</span></div>`).join('')}
      </div>
      <div class="wrap" style="margin-top:12px">
        <table>
          <thead><tr><th>SDM / Fungsi</th><th>Skema Fee</th></tr></thead>
          <tbody>${rows(POLICY.workforce)}</tbody>
        </table>
      </div>
      <div class="wrap" style="margin-top:12px">
        <table>
          <thead><tr><th>Biaya Langsung</th><th>Skema</th></tr></thead>
          <tbody>${rows(POLICY.directCosts)}</tbody>
        </table>
      </div>
      <div class="gmu-target-note" style="margin-top:10px">
        <b>Guardrail sistem:</b> harga aktif Rp55.000/pax minimum 20 pax dan Rp46.000/pax minimum 30 pax. Fee Sales hanya dari paid pax. Bonus Sales hanya cair saat target 200 paid pax tercapai, cash-in masuk, tidak ada diskon tanpa approval, dan projected/actual margin tetap ≥35%. Jika quotation di bawah 35%, sistem harus meminta approval Manager/Owner.
      </div>`;
    anchor.insertAdjacentElement('afterend', section);
  }

  function apply() {
    patchHealthyMargin();
    render();
  }

  function init() {
    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      if (!allowed()) return;
      apply();
      const observer = new MutationObserver(apply);
      observer.observe(document.body, { childList: true, subtree: true });
    }, 250);

    window.GmuStationOperatingPolicyV218 = Object.freeze({ version: VERSION, policy: POLICY });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
