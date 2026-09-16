(() => {
  'use strict';

  const VERSION = 'v22.1-station-pricing-policy';
  const ALLOWED = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales']);
  const DEFAULT = Object.freeze({
    programKey: 'EDU_STATION',
    minimumMarginPct: 35,
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

  function tierText() {
    return policy.priceTiers.map(t => `${t.label}: ${money(t.pricePerPax)}/pax • min ${integer(t.minimumPax)} pax`).join(' | ');
  }

  function renderSection() {
    if (!allowed()) return;
    const section = q('#gmuStationTargetSection');
    if (!section) return;
    section.dataset.v216 = 'true';
    section.innerHTML = `
      <div class="head">
        <div>
          <h3>Kebijakan Harga • Edukasi Profesi & Lingkungan Stasiun</h3>
          <p>Program Stasiun memiliki pricing sendiri. Target Sales 400 paid pax sekarang dihitung lintas seluruh program GMU EduTrans.</p>
        </div>
        <span class="badge info">PRICING POLICY</span>
      </div>
      <div class="gmu-target-grid">
        <div class="gmu-target-card"><small>Reguler</small><b>${money(policy.priceTiers[0]?.pricePerPax || 55_000)}</b><span>Minimum ${integer(policy.priceTiers[0]?.minimumPax || 20)} pax.</span></div>
        <div class="gmu-target-card"><small>Volume</small><b>${money(policy.priceTiers[1]?.pricePerPax || 46_000)}</b><span>Minimum ${integer(policy.priceTiers[1]?.minimumPax || 30)} pax.</span></div>
        <div class="gmu-target-card"><small>Margin guardrail</small><b>≥ ${Number(policy.minimumMarginPct || 35).toLocaleString('id-ID')}%</b><span>Quotation di bawah guardrail wajib approval Manager/Owner.</span></div>
        <div class="gmu-target-card"><small>Target Sales</small><b>Lintas Program</b><span>Tidak mengunci 400 pax khusus Stasiun.</span></div>
      </div>
      <div class="gmu-target-note" style="margin-top:10px"><b>Tier aktif:</b> ${tierText()}</div>`;
  }

  async function loadPolicy() {
    if (typeof sb === 'undefined' || !sb) return;
    try {
      const response = await sb
        .from('program_sales_targets')
        .select('minimum_margin_pct,price_tiers')
        .eq('program_key', policy.programKey)
        .eq('status', 'ACTIVE')
        .order('period_month', { ascending: false })
        .limit(1)
        .maybeSingle();
      if (response.error || !response.data) return;
      const row = response.data;
      policy = {
        ...policy,
        minimumMarginPct: Number(row.minimum_margin_pct || policy.minimumMarginPct),
        priceTiers: Array.isArray(row.price_tiers) && row.price_tiers.length ? row.price_tiers.map(t => ({
          label: t.label || 'Tier',
          pricePerPax: Number(t.price_per_pax || t.pricePerPax || 0),
          minimumPax: Number(t.minimum_pax || t.minimumPax || 0),
        })) : policy.priceTiers,
      };
    } catch (_) {
      // Fallback policy remains visible.
    }
  }

  function init() {
    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      if (!allowed()) return;
      loadPolicy().finally(renderSection);
      const observer = new MutationObserver(renderSection);
      observer.observe(document.body, { childList: true, subtree: true });
      renderSection();
    }, 250);
    window.GmuStationTargetV216 = Object.freeze({ version: VERSION, policy: DEFAULT });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();