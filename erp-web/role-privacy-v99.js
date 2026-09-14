(() => {
  'use strict';

  const VERSION = 'v9.9-role-privacy-sync-id';
  const STRATEGIC_FINANCE_ROLES = new Set(['Owner', 'Director', 'Direktur']);
  const OPERATIONAL_FINANCE_ROLES = new Set(['Owner', 'Director', 'Direktur', 'Manager', 'Manager EduTrans', 'Finance', 'Keuangan']);
  const MANAGER_ROLES = new Set(['Manager', 'Manager EduTrans']);
  const q = (sel, root = document) => root.querySelector(sel);
  const qa = (sel, root = document) => [...root.querySelectorAll(sel)];

  function currentRole() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }

  function canSeeStrategicFinance() {
    return STRATEGIC_FINANCE_ROLES.has(currentRole());
  }

  function canSeeOperationalFinance() {
    return OPERATIONAL_FINANCE_ROLES.has(currentRole());
  }

  // Backward-compatible name used by other web modules.
  function canSeeFullFinance() {
    return canSeeStrategicFinance();
  }

  function isManagerEduTrans() {
    return MANAGER_ROLES.has(currentRole());
  }

  function operationalMetrics() {
    let bs = [];
    try { bs = Array.isArray(data?.bookings) ? data.bookings : []; } catch (_) {}
    const now = new Date();
    now.setHours(0, 0, 0, 0);
    const pax = bs.reduce((sum, b) => sum + Number(b?.pax || 0), 0);
    const upcoming = bs.filter(b => {
      const d = new Date(String(b?.trip_date || '') + 'T00:00:00');
      return Number.isFinite(d.getTime()) && d >= now && !['Completed', 'Closed'].includes(String(b?.status || ''));
    }).length;
    let alerts = 0;
    try { alerts = typeof workflowAlerts === 'function' ? workflowAlerts().length : 0; } catch (_) {}
    return [
      ['Pemesanan', bs.length, 'tahapan aktif'],
      ['Peserta', pax, 'peserta'],
      ['Kegiatan Mendatang', upcoming, 'jadwal aktif'],
      ['Peringatan Proses', alerts, 'perlu perhatian'],
    ];
  }

  function sanitizeDashboard() {
    if (canSeeOperationalFinance()) return;
    const metrics = q('#metrics');
    if (!metrics) return;
    metrics.innerHTML = operationalMetrics().map((x, i) =>
      `<div class="card metric ${i === 2 ? 'bluetop' : i === 3 ? 'goldtop' : ''}"><span>${x[0]}</span><b>${x[1]}</b><span>${x[2]}</span></div>`
    ).join('');
  }

  function sanitizeFinanceUi() {
    const financeButton = q('#nav [data-page="finance"]');
    if (financeButton) financeButton.classList.toggle('hidden', !canSeeOperationalFinance());

    qa('.financial-only').forEach(el => el.classList.toggle('hidden', !canSeeOperationalFinance()));
    qa('.strategic-financial-only').forEach(el => el.classList.toggle('hidden', !canSeeStrategicFinance()));

    if (!canSeeOperationalFinance()) {
      const financePage = q('#finance');
      if (financePage) financePage.classList.remove('active');
      if (q('#title')?.textContent?.toLowerCase().includes('finance') || q('#title')?.textContent?.toLowerCase().includes('keuangan')) {
        try { if (typeof navTo === 'function') navTo('dashboard'); } catch (_) {}
      }
    }
  }

  function patchApplyRole() {
    if (typeof applyRole !== 'function' || applyRole.__gmuPrivacyPatched) return;
    const original = applyRole;
    const patched = function (...args) {
      const result = original.apply(this, args);
      sanitizeFinanceUi();
      sanitizeDashboard();
      return result;
    };
    patched.__gmuPrivacyPatched = true;
    applyRole = patched;
  }

  function patchNav() {
    if (typeof navTo !== 'function' || navTo.__gmuPrivacyPatched) return;
    const original = navTo;
    const patched = function (page, ...rest) {
      if (page === 'finance' && !canSeeOperationalFinance()) page = 'dashboard';
      return original.call(this, page, ...rest);
    };
    patched.__gmuPrivacyPatched = true;
    navTo = patched;
  }

  function patchRender(name, after) {
    try {
      const fn = eval(name);
      if (typeof fn !== 'function' || fn.__gmuPrivacyPatched) return;
      const wrapped = function (...args) {
        const result = fn.apply(this, args);
        try { after(); } catch (e) { console.warn(`${VERSION} ${name}`, e); }
        return result;
      };
      wrapped.__gmuPrivacyPatched = true;
      eval(`${name} = wrapped`);
    } catch (_) {}
  }

  function patchTripFolderFinancialDocs() {
    try {
      if (typeof tripFolderState === 'function' && !tripFolderState.__gmuPrivacyPatched) {
        const original = tripFolderState;
        const wrapped = function (...args) {
          const result = original.apply(this, args);
          if (!canSeeOperationalFinance() && Array.isArray(result?.docs)) {
            result.docs = result.docs.map(doc => ['invoice', 'vendor-invoice'].includes(String(doc?.key || ''))
              ? { ...doc, ok: false, locked: true, meta: 'Terkunci sesuai hak akses' }
              : doc
            );
            const countable = result.docs.filter(x => !x.locked);
            result.done = countable.filter(x => x.ok).length;
            result.total = countable.length;
            result.score = Math.round(result.done / Math.max(1, result.total) * 100);
          }
          return result;
        };
        wrapped.__gmuPrivacyPatched = true;
        tripFolderState = wrapped;
      }
    } catch (_) {}

    try {
      if (typeof visibleTripDocCategories === 'function' && !visibleTripDocCategories.__gmuPrivacyPatched) {
        const original = visibleTripDocCategories;
        const wrapped = function (...args) {
          const list = original.apply(this, args) || [];
          if (canSeeOperationalFinance()) return list;
          return list.filter(item => !item?.finance && !['invoice', 'vendor-invoice'].includes(String(item?.key || '')));
        };
        wrapped.__gmuPrivacyPatched = true;
        visibleTripDocCategories = wrapped;
      }
    } catch (_) {}
  }

  function relabelManager() {
    if (!isManagerEduTrans()) return;
    const roleEl = q('#meRole');
    if (roleEl && roleEl.textContent !== 'Manager EduTrans') roleEl.textContent = 'Manager EduTrans';
  }

  function enforce() {
    sanitizeFinanceUi();
    sanitizeDashboard();
    patchTripFolderFinancialDocs();
    relabelManager();
  }

  function init() {
    patchApplyRole();
    patchNav();
    patchRender('renderDashboard', sanitizeDashboard);
    patchRender('renderBookings', sanitizeFinanceUi);
    patchRender('renderCustomers', sanitizeFinanceUi);
    patchRender('renderFinance', sanitizeFinanceUi);
    patchRender('renderTripFolder', sanitizeFinanceUi);

    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      enforce();
    }, 250);

    const observer = new MutationObserver(() => {
      if (typeof profile === 'undefined' || !profile) return;
      sanitizeFinanceUi();
      relabelManager();
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });

    window.GmuErpRolePrivacy = Object.freeze({
      version: VERSION,
      canSeeFullFinance,
      canSeeStrategicFinance,
      canSeeOperationalFinance,
      isManagerEduTrans,
      strategicFinanceRoles: [...STRATEGIC_FINANCE_ROLES],
      operationalFinanceRoles: [...OPERATIONAL_FINANCE_ROLES],
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
