(() => {
  'use strict';

  const VERSION = 'v11.0-focused-ui-shell';
  const STORAGE_GROUP = 'gmu.erp.ui.navGroup';
  const STORAGE_COMPACT = 'gmu.erp.ui.compact';
  const q = (s, r = document) => r.querySelector(s);
  const qa = (s, r = document) => [...r.querySelectorAll(s)];

  const GROUPS = Object.freeze({
    utama: {
      label: 'Utama',
      pages: new Set(['dashboard','roleWorkspace','myTasksHub','rolePlaybook','companyControl','salesTargetControl','taskAutomationControl','bookings','operation','finance'])
    },
    kerja: {
      label: 'Kerja Saya',
      pages: new Set(['roleWorkspace','myTasksHub','rolePlaybook','taskAutomationControl','workflow','tripcontrol'])
    },
    sales: {
      label: 'Penjualan',
      pages: new Set(['customers','bookings','salesTargetControl','marketIntelligence','packages','packageMaster','mediaMaster'])
    },
    ops: {
      label: 'Operasional',
      pages: new Set(['operation','tripcontrol','tripfolder','vendor','managerOpsAgent','serviceRecoveryControl'])
    },
    finance: {
      label: 'Keuangan',
      pages: new Set(['finance','treasuryControl','cashForecastControl','payrollControl','riskRegisterControl'])
    },
    people: {
      label: 'SDM & Mutu',
      pages: new Set(['peopleControl','workforcePlanningControl','recruitmentControl','qualityControl','serviceRecoveryControl','growthControl'])
    },
    system: {
      label: 'Sistem & AI',
      pages: new Set(['companySystemCenter','companyControl','governanceControl','aiCenter','riskRegisterControl','users'])
    },
    semua: { label: 'Semua', pages: null }
  });

  const DETAIL_PAGES = new Set([
    'companyControl','salesTargetControl','myTasksHub','treasuryControl','peopleControl','qualityControl','growthControl','governanceControl','aiCenter',
    'taskAutomationControl','payrollControl','recruitmentControl','serviceRecoveryControl','cashForecastControl','workforcePlanningControl','riskRegisterControl','companySystemCenter'
  ]);

  function currentRole() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }

  function installStyle() {
    if (q('#gmu110Style')) return;
    const s = document.createElement('style');
    s.id = 'gmu110Style';
    s.textContent = `
      :root{--gmu110-radius:14px;--gmu110-soft:#f7faf8;--gmu110-line:#e5eee8}
      body.gmu110-ui .content{max-width:1480px;margin:0 auto;padding-bottom:36px}
      body.gmu110-ui .page.active{animation:gmu110fade .16s ease-out}
      @keyframes gmu110fade{from{opacity:.55;transform:translateY(2px)}to{opacity:1;transform:none}}
      #gmu110NavControls{padding:8px 7px 10px;margin:0 0 6px;border-bottom:1px solid var(--line)}
      #gmu110NavSearch{width:100%;box-sizing:border-box;border:1px solid var(--line);background:#fff;border-radius:10px;padding:8px 10px;font:inherit;font-size:9px;outline:none}
      #gmu110NavSearch:focus{border-color:var(--g)}
      .gmu110-chips{display:flex;gap:5px;flex-wrap:wrap;margin-top:7px}
      .gmu110-chip{border:1px solid var(--line);background:#fff;color:var(--muted);border-radius:999px;padding:5px 8px;font:inherit;font-size:8px;cursor:pointer;line-height:1.1}
      .gmu110-chip.active{background:var(--g);border-color:var(--g);color:#fff}
      #nav [data-page].gmu110-filtered{display:none!important}
      #gmu110PageBar{display:flex;align-items:center;justify-content:space-between;gap:10px;margin:0 0 12px;padding:9px 11px;border:1px solid var(--gmu110-line);background:rgba(255,255,255,.92);backdrop-filter:blur(8px);border-radius:12px;position:sticky;top:6px;z-index:12}
      .gmu110-context{min-width:0}.gmu110-context b{display:block;color:var(--gd);font-size:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.gmu110-context span{font-size:8px;color:var(--muted)}
      .gmu110-bar-actions{display:flex;align-items:center;gap:6px;flex-wrap:wrap;justify-content:flex-end}
      .gmu110-action{border:1px solid var(--line);background:#fff;color:var(--gd);border-radius:9px;padding:6px 8px;font:inherit;font-size:8px;cursor:pointer}.gmu110-action:hover{background:var(--gmu110-soft)}
      body.gmu110-ui .card{border-radius:var(--gmu110-radius)!important;box-shadow:0 2px 12px rgba(22,61,39,.035)}
      body.gmu110-ui .section{margin-bottom:10px!important}
      body.gmu110-ui .kpi-grid,body.gmu110-ui .grid{gap:10px!important}
      body.gmu110-ui table{font-size:9px}
      body.gmu110-ui .notice{border-radius:12px}
      body.gmu110-compact .gmu110-secondary{display:none!important}
      body.gmu110-compact .gmu110-page-expanded .gmu110-secondary{display:block!important}
      .gmu110-expand{display:none;margin:7px 0 12px}
      body.gmu110-compact .gmu110-has-secondary .gmu110-expand{display:flex}
      .gmu110-expand button{width:100%;border:1px dashed var(--line);background:#fbfdfc;color:var(--muted);border-radius:11px;padding:8px;font:inherit;font-size:8px;cursor:pointer}
      .gmu110-expand button:hover{background:#f5faf7;color:var(--gd)}
      body.gmu110-ui .gmu107-grid,body.gmu110-ui .gmu109-grid{grid-template-columns:repeat(auto-fit,minmax(190px,1fr))!important}
      body.gmu110-ui .gmu107-card,body.gmu110-ui .gmu109-card{min-height:auto!important}
      @media(max-width:920px){#gmu110PageBar{position:static}.gmu110-bar-actions{justify-content:flex-start}.gmu110-context{width:100%}#gmu110PageBar{flex-wrap:wrap}}
      @media(max-width:620px){body.gmu110-ui .content{padding-left:10px!important;padding-right:10px!important}#gmu110PageBar{margin-bottom:9px}.gmu110-action{padding:7px 8px}.gmu110-chips{overflow-x:auto;flex-wrap:nowrap;padding-bottom:2px}.gmu110-chip{flex:0 0 auto}}
    `;
    document.head.appendChild(s);
    document.body.classList.add('gmu110-ui');
  }

  function navItems() { return qa('#nav [data-page]'); }

  function inferGroup(page) {
    for (const [key, cfg] of Object.entries(GROUPS)) {
      if (key === 'semua' || key === 'utama') continue;
      if (cfg.pages?.has(page)) return key;
    }
    return 'system';
  }

  function installNavControls() {
    const nav = q('#nav');
    if (!nav || q('#gmu110NavControls', nav)) return;
    const wrap = document.createElement('div');
    wrap.id = 'gmu110NavControls';
    wrap.innerHTML = `<input id="gmu110NavSearch" type="search" placeholder="Cari menu…" autocomplete="off" aria-label="Cari menu ERP"><div class="gmu110-chips" id="gmu110NavChips"></div>`;
    nav.prepend(wrap);
    const chips = q('#gmu110NavChips', wrap);
    for (const [key,cfg] of Object.entries(GROUPS)) {
      const b = document.createElement('button');
      b.type = 'button'; b.className = 'gmu110-chip'; b.dataset.group = key; b.textContent = cfg.label;
      chips.appendChild(b);
    }
    q('#gmu110NavSearch', wrap).addEventListener('input', applyNavFilter);
    chips.addEventListener('click', e => {
      const b = e.target.closest('[data-group]'); if (!b) return;
      localStorage.setItem(STORAGE_GROUP, b.dataset.group);
      applyNavFilter();
    });
  }

  function activeGroup() {
    const saved = localStorage.getItem(STORAGE_GROUP);
    return GROUPS[saved] ? saved : 'utama';
  }

  function applyNavFilter() {
    const group = activeGroup();
    const text = (q('#gmu110NavSearch')?.value || '').trim().toLowerCase();
    qa('#gmu110NavChips [data-group]').forEach(b => b.classList.toggle('active', b.dataset.group === group));
    for (const item of navItems()) {
      const page = item.dataset.page || '';
      item.dataset.gmu110Group = inferGroup(page);
      const label = (item.textContent || '').trim().toLowerCase();
      const groupMatch = group === 'semua' || GROUPS[group]?.pages?.has(page) || (group === 'utama' && GROUPS.utama.pages.has(page));
      const textMatch = !text || label.includes(text) || page.toLowerCase().includes(text);
      item.classList.toggle('gmu110-filtered', !(textMatch && (text ? true : groupMatch)));
    }
  }

  function installPageBar() {
    const content = q('.content');
    if (!content || q('#gmu110PageBar', content)) return;
    const bar = document.createElement('div');
    bar.id = 'gmu110PageBar';
    bar.innerHTML = `<div class="gmu110-context"><b id="gmu110PageName">Pusat Kerja</b><span id="gmu110RoleName">Fokus pada pekerjaan yang perlu ditindaklanjuti.</span></div><div class="gmu110-bar-actions"><button class="gmu110-action" id="gmu110CompactToggle" type="button">Mode Ringkas</button><button class="gmu110-action" data-gmu110-go="myTasksHub" type="button">Tugas Saya</button><button class="gmu110-action" data-gmu110-go="rolePlaybook" type="button">Jobdesk & SOP</button><button class="gmu110-action" data-gmu110-all type="button">Semua Modul</button></div>`;
    content.prepend(bar);
    bar.addEventListener('click', e => {
      const go = e.target.closest('[data-gmu110-go]');
      if (go) return navigate(go.dataset.gmu110Go);
      if (e.target.closest('[data-gmu110-all]')) {
        localStorage.setItem(STORAGE_GROUP, 'semua');
        applyNavFilter();
        q('#gmu110NavSearch')?.focus();
      }
    });
    q('#gmu110CompactToggle', bar).addEventListener('click', toggleCompact);
  }

  function navigate(page) {
    const btn = q(`#nav [data-page="${CSS.escape(page)}"]`);
    if (btn) { btn.click(); return; }
    try { if (typeof navTo === 'function') navTo(page); } catch (_) {}
  }

  function compactEnabled() {
    const saved = localStorage.getItem(STORAGE_COMPACT);
    return saved === null ? true : saved === '1';
  }

  function applyCompact() {
    const compact = compactEnabled();
    document.body.classList.toggle('gmu110-compact', compact);
    const b = q('#gmu110CompactToggle');
    if (b) b.textContent = compact ? 'Mode Ringkas ✓' : 'Mode Lengkap';
    markSecondarySections();
  }

  function toggleCompact() {
    localStorage.setItem(STORAGE_COMPACT, compactEnabled() ? '0' : '1');
    applyCompact();
  }

  function markSecondarySections() {
    for (const pageId of DETAIL_PAGES) {
      const page = q(`#${CSS.escape(pageId)}`); if (!page) continue;
      const sections = qa(':scope > .card.section, :scope > .section.card', page);
      if (sections.length <= 3) continue;
      page.classList.add('gmu110-has-secondary');
      sections.forEach((s,i) => s.classList.toggle('gmu110-secondary', i >= 3));
      let exp = q(':scope > .gmu110-expand', page);
      if (!exp) {
        exp = document.createElement('div'); exp.className = 'gmu110-expand';
        exp.innerHTML = '<button type="button">Tampilkan detail tambahan</button>';
        const third = sections[2];
        third?.insertAdjacentElement('afterend', exp);
        exp.addEventListener('click', () => {
          page.classList.toggle('gmu110-page-expanded');
          const open = page.classList.contains('gmu110-page-expanded');
          q('button', exp).textContent = open ? 'Sembunyikan detail tambahan' : 'Tampilkan detail tambahan';
        });
      }
    }
  }

  function updateContext() {
    const active = q('.page.active');
    if (!active) return;
    const page = active.id || '';
    const btn = q(`#nav [data-page="${CSS.escape(page)}"]`);
    const title = (btn?.textContent || q('#title')?.textContent || 'Pusat Kerja').trim();
    const role = currentRole();
    const t = q('#gmu110PageName'); if (t) t.textContent = title;
    const r = q('#gmu110RoleName'); if (r) r.textContent = role ? `${role} • hanya informasi dan aksi yang relevan.` : 'Fokus pada pekerjaan yang perlu ditindaklanjuti.';
  }

  function installObserver() {
    if (document.body.__gmu110Observer) return;
    let queued = false;
    const obs = new MutationObserver(() => {
      if (queued) return; queued = true;
      requestAnimationFrame(() => {
        queued = false;
        installNavControls(); installPageBar(); applyNavFilter(); markSecondarySections(); updateContext();
      });
    });
    obs.observe(document.body, {subtree:true, childList:true, attributes:true, attributeFilter:['class']});
    document.body.__gmu110Observer = obs;
  }

  function init() {
    installStyle(); installNavControls(); installPageBar(); applyNavFilter(); applyCompact(); updateContext(); installObserver();
    window.GmuFocusedUIShell = Object.freeze({version: VERSION, refresh(){applyNavFilter();applyCompact();updateContext();}});
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, {once:true}); else init();
})();
