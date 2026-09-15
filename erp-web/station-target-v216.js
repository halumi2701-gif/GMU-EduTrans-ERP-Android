(() => {
  'use strict';

  const VERSION = 'v21.6-station-target-200-pricing';
  const ALLOWED = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales']);
  const DEFAULT = Object.freeze({
    programKey: 'EDU_STATION',
    periodMonth: '2026-09-01',
    periodLabel: 'September 2026',
    bepPax: 60,
    productivePax: 100,
    targetPax: 200,
    stretchPax: 300,
    outstandingPax: 400,
    minimumMarginPct: 35,
    salesRetainer: 600_000,
    salesFeePerPax: 2_500,
    targetBonus: 250_000,
    priceTiers: [
      { label: 'Reguler', pricePerPax: 55_000, minimumPax: 20 },
      { label: 'Volume', pricePerPax: 46_000, minimumPax: 30 },
    ],
  });

  let policy = { ...DEFAULT, priceTiers: DEFAULT.priceTiers.map(x => ({ ...x })) };
  const q = (s, root = document) => root.querySelector(s);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  const integer = v => Number(v || 0).toLocaleString('id-ID');

  function role() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }

  function allowed() { return ALLOWED.has(role()); }

  function computed() {
    const prices = policy.priceTiers.map(x => Number(x.pricePerPax || 0)).filter(Boolean);
    const minPrice = Math.min(...prices);
    const maxPrice = Math.max(...prices);
    const minRevenue = policy.targetPax * minPrice;
    const maxRevenue = policy.targetPax * maxPrice;
    const feeAtTarget = policy.targetPax * policy.salesFeePerPax;
    const salesIncome = policy.salesRetainer + feeAtTarget + policy.targetBonus;
    const minContribution = Math.round(minRevenue * policy.minimumMarginPct / 100);
    const maxContribution = Math.round(maxRevenue * policy.minimumMarginPct / 100);
    return { minRevenue, maxRevenue, feeAtTarget, salesIncome, minContribution, maxContribution };
  }

  function tierText() {
    return policy.priceTiers
      .map(t => `${money(t.pricePerPax)}/pax • min ${integer(t.minimumPax)} pax`)
      .join(' | ');
  }

  function renderSection() {
    if (!allowed()) return;
    const section = q('#gmuStationTargetSection');
    if (!section || section.dataset.v216 === 'true') return;
    const calc = computed();
    section.dataset.v216 = 'true';
    section.innerHTML = `
      <div class="head">
        <div>
          <h3>Target Program • Edukasi Profesi & Lingkungan Stasiun</h3>
          <p>${policy.periodLabel} • target dihitung dari paid pax dan cash-in.</p>
        </div>
        <span class="badge ok">TARGET 200 PAX</span>
      </div>
      <div class="gmu-target-grid">
        <div class="gmu-target-card"><small>2 pilihan harga aktif</small><b>${money(55_000)} / ${money(46_000)}</b><span>${tierText()}</span></div>
        <div class="gmu-target-card"><small>Target bulan ini</small><b>${integer(policy.targetPax)} paid pax</b><span>Omzet target ${money(calc.minRevenue)}–${money(calc.maxRevenue)} sesuai komposisi tier.</span></div>
        <div class="gmu-target-card"><small>Margin minimum</small><b>≥ ${policy.minimumMarginPct}%</b><span>Quotation di bawah margin wajib approval Manager/Owner.</span></div>
        <div class="gmu-target-card"><small>Fee Sales / paid pax</small><b>${money(policy.salesFeePerPax)}</b><span>= Rp50.000 per 20 paid pax.</span></div>
        <div class="gmu-target-card"><small>Penghasilan Sales di 200 pax</small><b>${money(calc.salesIncome)}</b><span>Retainer ${money(policy.salesRetainer)} + fee ${money(calc.feeAtTarget)} + bonus target ${money(policy.targetBonus)}.</span></div>
        <div class="gmu-target-card"><small>Kontribusi minimum target 35%</small><b>${money(calc.minContribution)}–${money(calc.maxContribution)}</b><span>Target kontribusi sebelum overhead korporat mengikuti tier harga.</span></div>
      </div>
      <div class="gmu-station-levels">
        <div class="gmu-station-level"><small>BEP</small><b>${integer(policy.bepPax)} pax</b><span>Titik aman awal.</span></div>
        <div class="gmu-station-level"><small>PRODUKTIF</small><b>${integer(policy.productivePax)} pax</b><span>Mulai produktif.</span></div>
        <div class="gmu-station-level current"><small>TARGET UTAMA</small><b>${integer(policy.targetPax)} pax</b><span>Target resmi bulan ini.</span></div>
        <div class="gmu-station-level"><small>STRETCH</small><b>${integer(policy.stretchPax)} pax</b><span>Target akselerasi.</span></div>
        <div class="gmu-station-level"><small>OUTSTANDING</small><b>${integer(policy.outstandingPax)} pax</b><span>Pencapaian luar biasa.</span></div>
      </div>
      <div class="gmu-target-note" style="margin-top:10px">
        <b>Guardrail:</b> Sales hanya dihitung dari <b>paid pax</b>. Bonus Rp250.000 cair saat 200 paid pax tercapai, cash-in masuk, tidak ada diskon tanpa approval, dan margin minimum 35% tetap terjaga.
      </div>`;
  }

  function patchMonthlyCard() {
    if (!allowed()) return;
    document.querySelectorAll('.gmu-target-actions .gmu-target-card').forEach(card => {
      if (card.querySelector('small')?.textContent?.trim() !== 'Bulanan Program') return;
      const b = card.querySelector('b');
      const span = card.querySelector('span');
      if (b) b.textContent = `${integer(policy.targetPax)} paid pax`;
      if (span) span.textContent = `BEP ${integer(policy.bepPax)} • produktif ${integer(policy.productivePax)} • stretch ${integer(policy.stretchPax)} • outstanding ${integer(policy.outstandingPax)} pax.`;
    });
  }

  async function loadPolicy() {
    if (typeof sb === 'undefined' || !sb) return;
    try {
      const response = await sb
        .from('program_sales_targets')
        .select('bep_pax,productive_pax,target_pax,next_target_pax,sales_retainer,sales_fee_per_pax,target_bonus,minimum_margin_pct,price_tiers')
        .eq('program_key', policy.programKey)
        .eq('period_month', policy.periodMonth)
        .eq('status', 'ACTIVE')
        .maybeSingle();
      if (response.error || !response.data) return;
      const row = response.data;
      policy = {
        ...policy,
        bepPax: Number(row.bep_pax || policy.bepPax),
        productivePax: Number(row.productive_pax || policy.productivePax),
        targetPax: Number(row.target_pax || policy.targetPax),
        stretchPax: Number(row.next_target_pax || policy.stretchPax),
        salesRetainer: Number(row.sales_retainer || policy.salesRetainer),
        salesFeePerPax: Number(row.sales_fee_per_pax || policy.salesFeePerPax),
        targetBonus: Number(row.target_bonus || policy.targetBonus),
        minimumMarginPct: Number(row.minimum_margin_pct || policy.minimumMarginPct),
        priceTiers: Array.isArray(row.price_tiers) && row.price_tiers.length ? row.price_tiers.map(t => ({
          label: t.label || 'Tier',
          pricePerPax: Number(t.price_per_pax || t.pricePerPax || 0),
          minimumPax: Number(t.minimum_pax || t.minimumPax || 0),
        })) : policy.priceTiers,
      };
    } catch (_) {
      // Fallback policy stays active until database migration is available.
    }
  }

  function apply() {
    renderSection();
    patchMonthlyCard();
  }

  function init() {
    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      if (!allowed()) return;
      loadPolicy().finally(apply);
      const observer = new MutationObserver(apply);
      observer.observe(document.body, { childList: true, subtree: true });
      apply();
    }, 250);

    window.GmuStationTargetV216 = Object.freeze({ version: VERSION, policy: DEFAULT });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
