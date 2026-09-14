(() => {
  'use strict';

  const VERSION = 'v9.9-role-privacy-sync';
  const FULL_FINANCE_ROLES = new Set(['Owner', 'Director', 'Direktur']);
  const MANAGER_ROLES = new Set(['Manager', 'Manager EduTrans']);
  const q = (sel, root = document) => root.querySelector(sel);
  const qa = (sel, root = document) => [...root.querySelectorAll(sel)];

  function currentRole() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }

  function canSeeFullFinance() {
    return FULL_FINANCE_ROLES.has(currentRole());
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
      ['Booking', bs.length, 'pipeline'],
      ['Pax', pax, 'peserta'],
      ['Trip Mendatang', upcoming, 'jadwal aktif'],
      ['Workflow Alert', alerts, 'perlu perhatian'],
    ];
  }

  function sanitizeDashboard() {
    if (canSeeFullFinance()) return;
    const metrics = q('#metrics');
    if (!metrics) return;
    metrics.innerHTML = operationalMetrics().map((x, i) =>
      `<div class="card metric ${i === 2 ? 'bluetop' : i === 3 ? 'goldtop' : ''}"><span>${x[0]}</span><b>${x[1]}</b><span>${x[2]}</span></div>`
    ).join('');
  }

  function sanitizeFinanceUi() {
    const financeButton = q('#nav [data-page="finance"]');
    if (financeButton) financeButton.classList.toggle('hidden', !canSeeFullFinance());
    qa('.financial-only').forEach(el => el.classList.toggle('hidden', !canSeeFullFinance()));
    if (!canSeeFullFinance()) {
      const financePage = q('#finance');
      if (financePage) financePage.classList.remove('active');
      if (q('#title')?.textContent?.toLowerCase().includes('finance')) {
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
      if (page === 'finance' && !canSeeFullFinance()) page = 'dashboard';
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
          if (!canSeeFullFinance() && Array.isArray(result?.docs)) {
            result.docs = result.docs.map(doc => doc?.key === 'invoice'
              ? { ...doc, ok: false, locked: true, meta: 'Terkunci Owner/Director' }
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
          if (canSeeFullFinance()) return list;
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
      isManagerEduTrans,
      fullFinanceRoles: [...FULL_FINANCE_ROLES],
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
