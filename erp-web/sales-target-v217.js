(() => {
  'use strict';

  const VERSION = 'v22.1-sales-portfolio-target-automatic';
  const ALLOWED = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales']);
  const FALLBACK = Object.freeze({
    paidPaxMonthly: 200,
    bepPax: 60,
    productivePax: 100,
    stretchPax: 300,
    outstandingPax: 400,
    leadsMonthly: 100,
    followupsMonthly: 200,
    quotationsMonthly: 20,
    bookingsMonthly: 4,
    salesRetainer: 600_000,
    salesFeePerPaidPax: 2_500,
    targetBonus: 250_000,
  });

  const state = {
    summary: null,
    programs: [],
    loading: false,
    error: null,
  };

  const q = (s, root = document) => root.querySelector(s);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  const integer = v => Number(v || 0).toLocaleString('id-ID');
  const pct = v => Number(v || 0).toLocaleString('id-ID', { maximumFractionDigits: 2 });

  function role() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }
  function allowed() { return ALLOWED.has(role()); }
  function db() { try { return typeof sb !== 'undefined' && sb?.from ? sb : null; } catch (_) { return null; } }

  function periodMonth() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
  }

  function periodLabel() {
    return new Date(periodMonth() + 'T00:00:00').toLocaleDateString('id-ID', { month: 'long', year: 'numeric' });
  }

  function normalized() {
    const r = state.summary || {};
    const target = Number(r.target_paid_pax || FALLBACK.paidPaxMonthly);
    const paidPax = Number(r.paid_pax || 0);
    const feePerPax = Number(r.sales_fee_per_paid_pax || FALLBACK.salesFeePerPaidPax);
    const retainer = Number(r.sales_retainer || FALLBACK.salesRetainer);
    const targetBonus = Number(r.target_bonus_earned ?? (paidPax >= target ? FALLBACK.targetBonus : 0));
    const achievement = r.achievement_pct == null ? (target ? paidPax / target * 100 : 0) : Number(r.achievement_pct);
    return {
      target,
      paidPax,
      paidBookings: Number(r.paid_bookings || 0),
      cashIn: Number(r.cash_in || 0),
      bookedValue: Number(r.booked_value || 0),
      bep: Number(r.bep_paid_pax || FALLBACK.bepPax),
      productive: Number(r.productive_paid_pax || FALLBACK.productivePax),
      stretch: Number(r.stretch_paid_pax || FALLBACK.stretchPax),
      outstanding: Number(r.outstanding_paid_pax || FALLBACK.outstandingPax),
      achievement,
      above: Math.max(paidPax - target, 0),
      level: String(r.achievement_level || (paidPax >= target ? 'TARGET_TERCAPAI' : 'PROSES')),
      retainer,
      feePerPax,
      variableFee: Number(r.variable_sales_fee ?? paidPax * feePerPax),
      targetBonus,
      modeledIncome: Number(r.modeled_sales_income ?? (retainer + paidPax * feePerPax + targetBonus)),
    };
  }

  function statusLabel(level) {
    return ({
      DI_BAWAH_BEP: 'DI BAWAH BEP',
      BEP: 'BEP',
      PRODUKTIF: 'PRODUKTIF',
      TARGET_TERCAPAI: 'TARGET TERCAPAI',
      STRETCH: 'STRETCH',
      OUTSTANDING: 'OUTSTANDING',
    })[level] || level.replaceAll('_', ' ');
  }

  function programBreakdown() {
    if (!state.programs.length) return '<div class="gmu-target-note">Belum ada paid pax bulan ini. Booking baru akan masuk target setelah ada pembayaran positif terverifikasi.</div>';
    return `<div class="gmu-target-flow">${state.programs.slice(0, 8).map(x => `
      <div><small>${String(x.program_name || 'Program')}</small><b>${integer(x.paid_pax)} pax</b><small>${integer(x.paid_bookings)} booking • ${money(x.booked_value)}</small></div>`).join('')}</div>`;
  }

  function render() {
    if (!allowed()) return;
    const anchor = q('#gmuStationTargetSection') || q('#salesTargetControl') || q('.content');
    if (!anchor) return;
    let section = q('#gmuSales200Target');
    if (!section) {
      section = document.createElement('div');
      section.id = 'gmuSales200Target';
      section.className = 'card section';
      if (anchor.id === 'gmuStationTargetSection') anchor.insertAdjacentElement('afterend', section);
      else if (anchor.id === 'salesTargetControl') anchor.prepend(section);
      else anchor.appendChild(section);
    }

    const s = normalized();
    const statusClass = s.paidPax >= s.target ? 'ok' : 'info';
    const errorNote = state.error ? `<div class="gmu-target-note" style="margin-top:10px;color:var(--bad)">Data otomatis belum dapat dimuat: ${String(state.error)}</div>` : '';

    section.innerHTML = `
      <div class="head">
        <div>
          <h3>Target Sales GMU EduTrans • Seluruh Program</h3>
          <p>${periodLabel()} • 200 paid pax adalah gabungan semua program, bukan 200 pax per program.</p>
        </div>
        <span class="badge ${statusClass}">${statusLabel(s.level)}</span>
      </div>
      <div class="gmu-target-grid">
        <div class="gmu-target-card"><small>Target paid pax / bulan</small><b>${integer(s.target)} pax</b><span>Lintas Edukasi Kereta, Stasiun, Pandanwangi, Membatik, Factory Visit dan Custom EduTrip.</span></div>
        <div class="gmu-target-card"><small>Aktual paid pax otomatis</small><b>${integer(s.paidPax)} pax</b><span>${integer(s.paidBookings)} booking sudah memiliki pembayaran positif terverifikasi.</span></div>
        <div class="gmu-target-card"><small>Achievement</small><b>${pct(s.achievement)}%</b><span>${s.above > 0 ? `Lebih target +${integer(s.above)} pax.` : `Kurang ${integer(Math.max(s.target - s.paidPax,0))} pax menuju target.`}</span></div>
        <div class="gmu-target-card"><small>Cash-in booking terkait</small><b>${money(s.cashIn)}</b><span>Diambil dari pembayaran terverifikasi pada bulan berjalan.</span></div>
        <div class="gmu-target-card"><small>Nilai booking paid-pax</small><b>${money(s.bookedValue)}</b><span>Total nilai booking yang pertama kali menjadi paid pada bulan ini.</span></div>
        <div class="gmu-target-card"><small>Fee Sales aktual</small><b>${money(s.variableFee)}</b><span>${money(s.feePerPax)} × ${integer(s.paidPax)} paid pax.</span></div>
        <div class="gmu-target-card"><small>Bonus target</small><b>${money(s.targetBonus)}</b><span>Aktif otomatis saat paid pax ≥ ${integer(s.target)}.</span></div>
        <div class="gmu-target-card"><small>Model penghasilan Sales</small><b>${money(s.modeledIncome)}</b><span>Retainer ${money(s.retainer)} + fee paid pax + bonus target bila tercapai.</span></div>
      </div>
      <div class="gmu-station-levels" style="margin-top:10px">
        <div class="gmu-station-level"><small>BEP</small><b>${integer(s.bep)} pax</b></div>
        <div class="gmu-station-level"><small>PRODUKTIF</small><b>${integer(s.productive)} pax</b></div>
        <div class="gmu-station-level current"><small>TARGET UTAMA</small><b>${integer(s.target)} pax</b></div>
        <div class="gmu-station-level"><small>STRETCH</small><b>${integer(s.stretch)} pax</b></div>
        <div class="gmu-station-level"><small>OUTSTANDING</small><b>${integer(s.outstanding)} pax</b></div>
      </div>
      <div style="margin-top:12px"><div class="head"><div><h3 style="font-size:13px">Breakdown Paid Pax per Program</h3><p>Semua program dijumlahkan ke target yang sama.</p></div><span class="badge neutral">AUTO SUM</span></div>${programBreakdown()}</div>
      <div class="gmu-target-note" style="margin-top:10px">
        <b>Mesin hitung:</b> input booking tetap terpisah per program. Saat pembayaran positif diverifikasi, seluruh pax booking masuk sebagai <b>paid pax satu kali</b> pada bulan pembayaran pertama. DP + pelunasan tidak menggandakan pax. Contoh 38 + 37 + 20 + 60 + 99 + 27 + 20 = <b>301 pax</b> → achievement <b>150,5%</b> terhadap target 200.
      </div>
      ${errorNote}`;
  }

  async function load() {
    if (!allowed() || !db() || state.loading) return;
    state.loading = true;
    state.error = null;
    try {
      const month = periodMonth();
      const [summaryResponse, programResponse] = await Promise.all([
        db().from('v_sales_portfolio_monthly').select('*').eq('period_month', month).eq('target_key','ALL_PROGRAMS').maybeSingle(),
        db().from('v_sales_portfolio_program_monthly').select('period_month,program_name,paid_bookings,paid_pax,booked_value').eq('period_month', month).order('paid_pax',{ascending:false}),
      ]);
      if (summaryResponse.error) throw summaryResponse.error;
      if (programResponse.error) throw programResponse.error;
      state.summary = summaryResponse.data || null;
      state.programs = Array.isArray(programResponse.data) ? programResponse.data : [];
    } catch (e) {
      state.error = e?.message || String(e);
    } finally {
      state.loading = false;
      render();
    }
  }

  function init() {
    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      if (!allowed()) return;
      render();
      load();
      const observer = new MutationObserver(() => { if (!q('#gmuSales200Target')) render(); });
      observer.observe(document.body, { childList: true, subtree: true });
      setInterval(load, 60_000);
    }, 250);
    window.GmuSalesTargetV217 = Object.freeze({ version: VERSION, refresh: load, target: FALLBACK });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
