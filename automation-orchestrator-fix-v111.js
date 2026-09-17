(() => {
  'use strict';
  const VERSION = 'v11.1-automation-runtime-stabilizer';

  function stabilize() {
    try {
      const obs = document.body?.__gmuV111Obs;
      if (obs && typeof obs.disconnect === 'function') {
        obs.disconnect();
        delete document.body.__gmuV111Obs;
      }
    } catch (_) {}

    document.addEventListener('click', event => {
      const btn = event.target.closest?.('[data-g111-capa-edit]');
      if (!btn) return;
      queueMicrotask(() => {
        try {
          const id = btn.dataset.g111CapaEdit;
          const rows = window.GmuAutomationOrchestrator?.state?.capa || [];
          const row = rows.find(x => String(x.id) === String(id));
          const select = document.querySelector('.g111-modal select[name="status"]');
          if (row && select) select.value = row.status || 'OPEN';
        } catch (_) {}
      });
    });

    window.GmuAutomationRuntimeStabilizer = Object.freeze({ version: VERSION, active: true });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', stabilize, { once: true });
  else stabilize();
})();
