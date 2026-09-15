(() => {
  'use strict';

  const VERSION = 'v21-company-autopilot';
  const ENTERPRISE_BASELINE_VERSION = 'v20-enterprise-operating-system';
  const PRIORITY_VERSION = 'v20.1-priority-command-layer';
  const RECOVERY_VERSION = 'v20.2-recovery-accountability-layer';
  const AUTOPILOT_VERSION = 'v21-company-autopilot-layer';
  const LEGACY_CONTRACT_MARKERS = 'GMU EduTrans Enterprise OS v20 aktif • Recovery & Accountability v20.2';
  const RELEASE_CHANNEL = 'production';
  const MODULES = [
    'role-privacy-v99.js',
    'package-master-v97.js',
    'media-master-v96.js',
    'manager-ops-agent-v98.js',
    'company-operating-system-v100.js',
    'market-intelligence-v101.js',
    'sales-target-engine-v103.js',
    'company-system-center-v104.js',
    'management-domains-v105.js',
    'role-navigation-v106.js',
    'company-detail-controls-v107.js',
    'role-playbook-v108.js',
    'execution-control-v109.js',
    'ui-focus-shell-v110.js',
    'automation-orchestrator-v111.js',
    'automation-orchestrator-fix-v111.js',
    'crm-finance-notification-v112.js',
    'enterprise-os-v200.js',
    'business-priority-v201.js',
    'recovery-command-v202.js',
    'company-autopilot-v210.js',
  ];

  function scriptBase() {
    const current = document.currentScript?.src || '';
    if (!current) return './';
    return current.slice(0, current.lastIndexOf('/') + 1);
  }

  function notice(message, bad = false) {
    const content = document.querySelector('.content');
    if (!content) return;
    let el = document.getElementById('gmuV10LoaderNotice');
    if (!el) {
      el = document.createElement('div');
      el.id = 'gmuV10LoaderNotice';
      el.className = 'notice';
      content.prepend(el);
    }
    el.textContent = message;
    if (bad) {
      el.style.borderColor = '#efc5c5';
      el.style.background = '#fff4f4';
      el.style.color = '#9f3434';
    }
  }

  function loadScript(src) {
    return new Promise((resolve, reject) => {
      const existing = [...document.scripts].find(s => s.src === src);
      if (existing) {
        if (existing.dataset.gmuLoaded === 'true') resolve();
        else {
          existing.addEventListener('load', resolve, { once: true });
          existing.addEventListener('error', () => reject(new Error(`Gagal memuat ${src}`)), { once: true });
        }
        return;
      }
      const script = document.createElement('script');
      script.src = src;
      script.defer = true;
      script.dataset.gmuV10Module = 'true';
      script.addEventListener('load', () => { script.dataset.gmuLoaded = 'true'; resolve(); }, { once: true });
      script.addEventListener('error', () => reject(new Error(`Gagal memuat ${src}`)), { once: true });
      document.head.appendChild(script);
    });
  }

  async function boot() {
    if (window.__GMU_ERP_WEB_V10__?.booted) return;
    const state = window.__GMU_ERP_WEB_V10__ = {
      version: VERSION,
      enterpriseBaselineVersion: ENTERPRISE_BASELINE_VERSION,
      priorityVersion: PRIORITY_VERSION,
      recoveryVersion: RECOVERY_VERSION,
      autopilotVersion: AUTOPILOT_VERSION,
      legacyContractMarkers: LEGACY_CONTRACT_MARKERS,
      releaseChannel: RELEASE_CHANNEL,
      booted: false,
      modules: [],
      failedModule: null,
    };

    const base = scriptBase();
    try {
      for (const file of MODULES) {
        await loadScript(base + file);
        state.modules.push(file);
      }
      state.booted = true;
      notice('GMU EduTrans Company Autopilot v21 aktif. Lead → Follow-up → Quotation → Payment → Booking → Operasional → Crew/Vendor → Trip → Finance → Payroll → Feedback → Repeat Order terhubung dalam automation-first operating system. Recovery v20.2 tetap aktif dan exception hanya ditutup setelah data sumber kembali sehat. Aksi sensitif tetap melalui approval manusia.');
    } catch (error) {
      state.failedModule = MODULES[state.modules.length] || 'unknown';
      console.error('GMU EduTrans Company Autopilot v21 loader', error);
      notice(`ERP utama tetap aktif. Modul tambahan gagal dimuat: ${state.failedModule}.`, true);
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();