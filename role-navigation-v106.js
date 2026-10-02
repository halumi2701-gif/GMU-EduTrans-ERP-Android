(() => {
  'use strict';

  const VERSION = 'v10.9-role-navigation-guard';
  const V10_PAGES = new Set([
    'companyControl','marketIntelligence','salesTargetControl','companySystemCenter','roleWorkspace','myTasksHub','treasuryControl','peopleControl','qualityControl','growthControl','governanceControl','aiCenter','rolePlaybook',
    'taskAutomationControl','payrollControl','recruitmentControl','serviceRecoveryControl','cashForecastControl','workforcePlanningControl','riskRegisterControl',
  ]);

  const q = (s, root = document) => root.querySelector(s);

  function currentRole() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }

  function syncV10Navigation() {
    if (!currentRole()) return;
    for (const page of V10_PAGES) {
      const button = q(`#nav [data-page="${page}"]`);
      const view = q(`#${page}`);
      if (!button) continue;
      button.classList.toggle('hidden', !view);
    }
  }

  function wrapLegacyApplyRole() {
    const original = window.applyRole;
    if (typeof original !== 'function' || original.__gmuV10Wrapped) return;
    const wrapped = function(...args) {
      const result = original.apply(this, args);
      queueMicrotask(syncV10Navigation);
      setTimeout(syncV10Navigation, 0);
      setTimeout(syncV10Navigation, 100);
      return result;
    };
    wrapped.__gmuV10Wrapped = true;
    wrapped.__gmuV10Original = original;
    window.applyRole = wrapped;
  }

  function installObserver() {
    const nav = q('#nav');
    if (!nav || nav.__gmuV10Observer) return;
    let queued = false;
    const observer = new MutationObserver(() => {
      if (queued) return;
      queued = true;
      queueMicrotask(() => {
        queued = false;
        syncV10Navigation();
      });
    });
    observer.observe(nav, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
    nav.__gmuV10Observer = observer;
  }

  function init() {
    wrapLegacyApplyRole();
    installObserver();
    const timer = setInterval(() => {
      wrapLegacyApplyRole();
      installObserver();
      syncV10Navigation();
      if (currentRole() && q('#rolePlaybook') && q('#taskAutomationControl')) clearInterval(timer);
    }, 200);
    setTimeout(() => clearInterval(timer), 25000);
    window.GmuRoleNavigationGuard = Object.freeze({ version: VERSION, sync: syncV10Navigation });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
