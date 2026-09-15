(() => {
  'use strict';

  const VERSION = 'v21.7-sales-target-200-dashboard';
  const ALLOWED = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales']);
  const TARGET = Object.freeze({
    periodLabel: 'September 2026',
    programName: 'Edukasi Profesi & Lingkungan Stasiun',
    paidPaxMonthly: 200,
    paidPaxWeekly: 50,
    leadsMonthly: 100,
    followupsMonthly: 200,
    quotationsMonthly: 20,
    bookingsMonthly: 4,
    minimumCashIn: 9_200_000,
    maximumCashIn: 11_000_000,
    salesRetainer: 600_000,
    salesFeePerPaidPax: 2_500,
    targetBonus: 250_000,
    minimumMarginPct: 35,
  });

  const q = (s, root = document) => root.querySelector(s);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  const integer = v => Number(v || 0).toLocaleString('id-ID');

  function role() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }

  function allowed() { return ALLOWED.has(role()); }

  function computed() {
    const variableFee = TARGET.paidPaxMonthly * TARGET.salesFeePerPaidPax;
    const incomeAtTarget = TARGET.salesRetainer + variableFee + TARGET.targetBonus;
    return { variableFee, incomeAtTarget };
  }

  function render() {
    if (!allowed() || q('#gmuSales200Target')) return;
    const station = q('#gmuStationTargetSection');
    if (!station) return;
    const calc = computed();
    const section = document.createElement('div');
    section.id = 'gmuSales200Target';
    section.className = 'card section';
    section.innerHTML = `
      <div class="head">
        <div>
          <h3>Target Sales • ${TARGET.programName}</h3>
          <p>${TARGET.periodLabel} • pencapaian utama berbasis paid pax, cash-in, dan margin sehat.</p>
        </div>
        <span class="badge ok">SALES 200 PAX</span>
      </div>
      <div class="gmu-target-grid">
        <div class="gmu-target-card"><small>Target paid pax / bulan</small><b>${integer(TARGET.paidPaxMonthly)} pax</b><span>Target resmi Sales bulan ini.</span></div>
        <div class="gmu-target-card"><small>Target paid pax / minggu</small><b>${integer(TARGET.paidPaxWeekly)} pax</b><span>Patokan ritme untuk mencapai 200 pax/bulan.</span></div>
        <div class="gmu-target-card"><small>Target lead / bulan</small><b>${integer(TARGET.leadsMonthly)} lead</b><span>Lead baru wajib masuk CRM/ERP.</span></div>
        <div class="gmu-target-card"><small>Target follow-up / bulan</small><b>${integer(TARGET.followupsMonthly)}</b><span>Setiap follow-up harus tercatat.</span></div>
        <div class="gmu-target-card"><small>Target quotation / bulan</small><b>${integer(TARGET.quotationsMonthly)}</b><span>Quotation hanya memakai harga resmi dan guardrail margin.</span></div>
        <div class="gmu-target-card"><small>Target booking / bulan</small><b>${integer(TARGET.bookingsMonthly)}</b><span>Booking dinilai sehat setelah pembayaran masuk.</span></div>
        <div class="gmu-target-card"><small>Target cash-in</small><b>${money(TARGET.minimumCashIn)}–${money(TARGET.maximumCashIn)}</b><span>Tergantung komposisi harga Rp46.000 dan Rp55.000/pax.</span></div>
        <div class="gmu-target-card"><small>Penghasilan Sales saat 200 pax</small><b>${money(calc.incomeAtTarget)}</b><span>Retainer ${money(TARGET.salesRetainer)} + fee ${money(calc.variableFee)} + bonus ${money(TARGET.targetBonus)}.</span></div>
      </div>
      <div class="gmu-target-note" style="margin-top:10px">
        <b>Aturan Sales:</b> fee hanya dari <b>paid pax</b>. Bonus target Rp250.000 cair saat 200 paid pax tercapai, cash-in tercatat, tidak ada diskon tanpa approval, dan margin program tetap <b>≥ ${TARGET.minimumMarginPct}%</b>.
      </div>`;
    station.insertAdjacentElement('afterend', section);
  }

  function init() {
    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      if (!allowed()) return;
      render();
      const observer = new MutationObserver(render);
      observer.observe(document.body, { childList: true, subtree: true });
    }, 250);

    window.GmuSalesTargetV217 = Object.freeze({ version: VERSION, target: TARGET });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
