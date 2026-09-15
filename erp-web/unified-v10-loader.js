(() => {
  'use strict';

  const VERSION = 'v20-enterprise-operating-system';
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
      script.addEventListener('load', () => {
        script.dataset.gmuLoaded = 'true';
        resolve();
      }, { once: true });
      script.addEventListener('error', () => reject(new Error(`Gagal memuat ${src}`)), { once: true });
      document.head.appendChild(script);
    });
  }

  async function boot() {
    if (window.__GMU_ERP_WEB_V10__?.booted) return;
    const state = window.__GMU_ERP_WEB_V10__ = {
      version: VERSION,
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
      notice('GMU EduTrans Enterprise OS v20 aktif: Sales, CRM, Booking, Operasional, Finance, Accounting, SDM/HRIS, Payroll, Vendor, Customer Quality, Risk, Governance, Notification, AI dan Automation kini disatukan melalui Enterprise Control Tower, Exception Center, People OS dan policy lintas divisi. UI tetap role-first dan ringkas; keputusan sensitif tetap membutuhkan approval manusia.');
    } catch (error) {
      state.failedModule = MODULES[state.modules.length] || 'unknown';
      console.error('GMU EduTrans Enterprise OS v20 loader', error);
      notice(`ERP utama tetap aktif. Modul tambahan gagal dimuat: ${state.failedModule}.`, true);
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();
