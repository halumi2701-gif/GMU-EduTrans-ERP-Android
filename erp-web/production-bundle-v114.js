// GMU EduTrans ERP Web production bundle v11.4. Generated from the ordered additive modules.

/* ===== role-privacy-v99.js ===== */
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


/* ===== package-master-v97.js ===== */
(() => {
  'use strict';

  const VERSION = 'v9.7-package-master';
  const MANAGE_ROLES = new Set(['Owner', 'Director', 'Direktur', 'Admin']);
  const q = (sel, root = document) => root.querySelector(sel);
  const h = (value) => String(value ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
  })[c]);
  const rupiahLocal = (value) => 'Rp ' + Number(value || 0).toLocaleString('id-ID');

  let programs = [];
  let packages = [];
  let editing = null;

  function role() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }

  function canManage() {
    return MANAGE_ROLES.has(role());
  }

  function facilitiesArray(value) {
    if (Array.isArray(value)) return value.map(x => String(x || '').trim()).filter(Boolean);
    if (typeof value === 'string') {
      try {
        const parsed = JSON.parse(value);
        if (Array.isArray(parsed)) return parsed.map(x => String(x || '').trim()).filter(Boolean);
      } catch (_) {}
      return value.split(/\r?\n|\s*\|\s*/).map(x => x.replace(/^[-•]\s*/, '').trim()).filter(Boolean);
    }
    return [];
  }

  function setStatus(message, bad = false) {
    const el = q('#gmuPackageStatus');
    if (!el) return;
    el.textContent = message || '';
    el.style.color = bad ? 'var(--bad)' : 'var(--g)';
  }

  async function loadMaster() {
    if (typeof sb === 'undefined' || !sb) return;
    setStatus('Memuat Master Paket…');
    const [p1, p2] = await Promise.all([
      sb.from('programs')
        .select('id,slug,name,category,short_description,min_pax,is_active,sort_order')
        .order('sort_order', { ascending: true }),
      sb.from('program_packages')
        .select('id,package_code,program_id,name,description,price_per_pax,min_pax,facilities,effective_from,effective_until,status,is_active,sort_order')
        .order('sort_order', { ascending: true }),
    ]);
    if (p1.error) throw p1.error;
    if (p2.error) throw p2.error;
    programs = p1.data || [];
    packages = p2.data || [];
    if (canManage()) render();
    refreshBookingPackagePicker();
    setStatus('');
  }

  function programName(id) {
    return programs.find(x => String(x.id) === String(id))?.name || '-';
  }

  function render() {
    const body = q('#gmuPackageBody');
    const programBody = q('#gmuProgramBody');
    if (programBody) {
      programBody.innerHTML = programs.map(p => `
        <tr>
          <td><b>${h(p.name)}</b><br><small>${h(p.slug || '')}</small></td>
          <td>${h(p.category || '-')}</td>
          <td>${Number(p.min_pax || 0)}</td>
          <td>${p.is_active ? '<span class="badge ok">ACTIVE</span>' : '<span class="badge neutral">INACTIVE</span>'}</td>
          <td><button class="btn ghost" data-edit-program="${h(p.id)}">Edit</button></td>
        </tr>`).join('') || '<tr><td colspan="5">Belum ada Program.</td></tr>';
    }
    if (body) {
      body.innerHTML = packages.map(p => {
        const facilities = facilitiesArray(p.facilities);
        return `<tr>
          <td><b>${h(p.package_code || '-')}</b><br><small>${h(p.name || '-')}</small></td>
          <td>${h(programName(p.program_id))}</td>
          <td class="money">${rupiahLocal(p.price_per_pax)}</td>
          <td>${Number(p.min_pax || 0)}</td>
          <td>${facilities.length} item</td>
          <td>${h(p.status || '-')} ${p.is_active ? '<span class="badge ok">ON</span>' : '<span class="badge neutral">OFF</span>'}</td>
          <td><button class="btn ghost" data-edit-package="${h(p.id)}">Edit</button></td>
        </tr>`;
      }).join('') || '<tr><td colspan="7">Belum ada Paket.</td></tr>';
    }
    renderSummary();
  }

  function programOptions(selected = '') {
    return programs.map(p => `<option value="${h(p.id)}" ${String(p.id) === String(selected) ? 'selected' : ''}>${h(p.name)}</option>`).join('');
  }

  function openProgram(id = '') {
    const row = programs.find(x => String(x.id) === String(id)) || null;
    editing = { type: 'program', id: row?.id || '' };
    q('#gmuMasterModalTitle').textContent = row ? 'Edit Program' : 'Program Baru';
    q('#gmuMasterModalBody').innerHTML = `
      <form id="gmuProgramForm" class="formgrid">
        <div class="field"><label>Nama Program</label><input id="gpmName" required value="${h(row?.name || '')}"></div>
        <div class="field"><label>Slug</label><input id="gpmSlug" required value="${h(row?.slug || '')}"></div>
        <div class="field"><label>Kategori</label><input id="gpmCategory" value="${h(row?.category || '')}"></div>
        <div class="field"><label>Minimum Pax</label><input id="gpmMin" type="number" min="1" value="${Number(row?.min_pax || 1)}"></div>
        <div class="field full"><label>Deskripsi Singkat</label><textarea id="gpmDesc" rows="3">${h(row?.short_description || '')}</textarea></div>
        <div class="field"><label>Urutan</label><input id="gpmSort" type="number" value="${Number(row?.sort_order || 0)}"></div>
        <div class="field"><label>Status</label><select id="gpmActive"><option value="true" ${row?.is_active !== false ? 'selected' : ''}>Aktif</option><option value="false" ${row?.is_active === false ? 'selected' : ''}>Nonaktif</option></select></div>
        <div class="full"><button class="btn primary" type="submit">Simpan Program</button></div>
      </form>`;
    q('#gmuMasterModal').classList.add('open');
    q('#gmuProgramForm').addEventListener('submit', saveProgram, { once: true });
  }

  function openPackage(id = '') {
    const row = packages.find(x => String(x.id) === String(id)) || null;
    editing = { type: 'package', id: row?.id || '' };
    q('#gmuMasterModalTitle').textContent = row ? 'Edit Paket' : 'Paket Baru';
    q('#gmuMasterModalBody').innerHTML = `
      <form id="gmuPackageForm" class="formgrid">
        <div class="field"><label>Kode Paket</label><input id="gpkCode" required value="${h(row?.package_code || '')}"></div>
        <div class="field"><label>Program</label><select id="gpkProgram" required>${programOptions(row?.program_id || '')}</select></div>
        <div class="field"><label>Nama Paket</label><input id="gpkName" required value="${h(row?.name || '')}"></div>
        <div class="field"><label>Harga Jual / Pax</label><input id="gpkPrice" type="number" min="0" required value="${Number(row?.price_per_pax || 0)}"></div>
        <div class="field"><label>Minimum Pax</label><input id="gpkMin" type="number" min="1" required value="${Number(row?.min_pax || 1)}"></div>
        <div class="field"><label>Status</label><select id="gpkStatus"><option ${row?.status === 'ACTIVE' ? 'selected' : ''}>ACTIVE</option><option ${row?.status === 'DRAFT' ? 'selected' : ''}>DRAFT</option><option ${row?.status === 'INACTIVE' ? 'selected' : ''}>INACTIVE</option></select></div>
        <div class="field"><label>Berlaku Mulai</label><input id="gpkFrom" type="date" value="${h(row?.effective_from || '')}"></div>
        <div class="field"><label>Berlaku Sampai</label><input id="gpkUntil" type="date" value="${h(row?.effective_until || '')}"></div>
        <div class="field"><label>Urutan</label><input id="gpkSort" type="number" value="${Number(row?.sort_order || 0)}"></div>
        <div class="field"><label>Tampil</label><select id="gpkActive"><option value="true" ${row?.is_active !== false ? 'selected' : ''}>Aktif</option><option value="false" ${row?.is_active === false ? 'selected' : ''}>Nonaktif</option></select></div>
        <div class="field full"><label>Deskripsi</label><textarea id="gpkDesc" rows="3">${h(row?.description || '')}</textarea></div>
        <div class="field full"><label>Fasilitas — satu baris satu item</label><textarea id="gpkFacilities" rows="8">${h(facilitiesArray(row?.facilities).join('\n'))}</textarea></div>
        <div class="full"><p class="note">Master Paket hanya menyimpan informasi penjualan publik. HPP, fee, profit, margin dan pricing policy internal tidak disentuh modul ini.</p><button class="btn primary" type="submit">Simpan Paket</button></div>
      </form>`;
    q('#gmuMasterModal').classList.add('open');
    q('#gmuPackageForm').addEventListener('submit', savePackage, { once: true });
  }

  async function saveProgram(event) {
    event.preventDefault();
    const editId = editing?.id || '';
    const payload = {
      name: q('#gpmName').value.trim(),
      slug: q('#gpmSlug').value.trim(),
      category: q('#gpmCategory').value.trim() || null,
      min_pax: Number(q('#gpmMin').value || 1),
      short_description: q('#gpmDesc').value.trim() || null,
      sort_order: Number(q('#gpmSort').value || 0),
      is_active: q('#gpmActive').value === 'true',
    };
    setStatus('Menyimpan Program…');
    const response = editId
      ? await sb.from('programs').update(payload).eq('id', editId)
      : await sb.from('programs').insert(payload);
    if (response.error) { setStatus(response.error.message, true); return; }
    closeModalLocal();
    await safeAudit('UPDATE', 'programs', editId, `Master Program • ${payload.name}`);
    await loadMaster();
  }

  async function savePackage(event) {
    event.preventDefault();
    const editId = editing?.id || '';
    const payload = {
      package_code: q('#gpkCode').value.trim(),
      program_id: q('#gpkProgram').value,
      name: q('#gpkName').value.trim(),
      description: q('#gpkDesc').value.trim() || null,
      price_per_pax: Number(q('#gpkPrice').value || 0),
      min_pax: Number(q('#gpkMin').value || 1),
      facilities: q('#gpkFacilities').value.split(/\r?\n/).map(x => x.replace(/^[-•]\s*/, '').trim()).filter(Boolean),
      effective_from: q('#gpkFrom').value || null,
      effective_until: q('#gpkUntil').value || null,
      status: q('#gpkStatus').value,
      is_active: q('#gpkActive').value === 'true',
      sort_order: Number(q('#gpkSort').value || 0),
    };
    setStatus('Menyimpan Paket…');
    const response = editId
      ? await sb.from('program_packages').update(payload).eq('id', editId)
      : await sb.from('program_packages').insert(payload);
    if (response.error) { setStatus(response.error.message, true); return; }
    closeModalLocal();
    await safeAudit('UPDATE', 'program_packages', editId, `Master Paket • ${payload.package_code}`);
    await loadMaster();
  }

  async function safeAudit(action, table, id, message) {
    try { if (typeof audit === 'function') await audit(action, table, id, message); } catch (_) {}
  }

  function closeModalLocal() {
    editing = null;
    q('#gmuMasterModal')?.classList.remove('open');
  }

  function installStyles() {
    if (q('#gmuPackageMasterStyle')) return;
    const style = document.createElement('style');
    style.id = 'gmuPackageMasterStyle';
    style.textContent = `.gmu-master-summary{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-bottom:14px}.gmu-master-summary .metric{box-shadow:none}.gmu-master-actions{display:flex;gap:8px;flex-wrap:wrap}.gmu-master-status{font-size:9px;min-height:14px;font-weight:800;margin:8px 0}.gmu-booking-package-helper{border:1px solid #cfe3d7;background:#f4fbf7;border-radius:12px;padding:10px;margin:0 0 10px}.gmu-booking-package-helper label{display:block;font-size:9px;font-weight:900;color:var(--g);margin-bottom:5px}.gmu-booking-package-helper select{width:100%;border:1px solid var(--line);border-radius:10px;padding:9px;background:#fff}@media(max-width:760px){.gmu-master-summary{grid-template-columns:1fr}}`;
    document.head.appendChild(style);
  }

  function installPage() {
    if (q('#packageMaster')) return;
    const content = q('.content');
    if (!content) return;
    const page = document.createElement('section');
    page.id = 'packageMaster';
    page.className = 'page';
    page.innerHTML = `
      <div class="notice">Master Paket ${VERSION} • Sinkron dengan Program/Paket customer. Hanya harga jual/fasilitas publik — tidak memuat HPP, fee, profit, margin.</div>
      <div class="gmu-master-summary" id="gmuMasterSummary"></div>
      <div class="card section" style="margin-top:0"><div class="head"><div><h3>Program</h3><p>Master program edukasi GMU EduTrans</p></div><button id="gmuAddProgram" class="btn primary" type="button">+ Program</button></div><div class="wrap"><table><thead><tr><th>Program</th><th>Kategori</th><th>Min Pax</th><th>Status</th><th>Aksi</th></tr></thead><tbody id="gmuProgramBody"></tbody></table></div></div>
      <div class="card section"><div class="head"><div><h3>Package Master</h3><p>Harga jual, minimum peserta, fasilitas dan masa berlaku</p></div><div class="gmu-master-actions"><button id="gmuPackageRefresh" class="btn ghost" type="button">↻ Refresh</button><button id="gmuAddPackage" class="btn primary" type="button">+ Paket</button></div></div><div id="gmuPackageStatus" class="gmu-master-status"></div><div class="wrap"><table><thead><tr><th>Paket</th><th>Program</th><th>Harga/Pax</th><th>Min</th><th>Fasilitas</th><th>Status</th><th>Aksi</th></tr></thead><tbody id="gmuPackageBody"></tbody></table></div></div>`;
    content.appendChild(page);

    const modal = document.createElement('div');
    modal.id = 'gmuMasterModal';
    modal.className = 'modal';
    modal.innerHTML = `<div class="modalbox"><div class="mtitle"><h3 id="gmuMasterModalTitle">Master</h3><button id="gmuMasterClose" class="close" type="button">×</button></div><div id="gmuMasterModalBody"></div></div>`;
    document.body.appendChild(modal);
  }

  function renderSummary() {
    const el = q('#gmuMasterSummary');
    if (!el) return;
    const activePrograms = programs.filter(x => x.is_active).length;
    const activePackages = packages.filter(x => x.is_active && String(x.status).toUpperCase() === 'ACTIVE').length;
    const station = packages.find(x => x.package_code === 'PKG-GMU-00008');
    el.innerHTML = `<div class="card metric"><span>Program Aktif</span><b>${activePrograms}</b><span>catalog</span></div><div class="card metric bluetop"><span>Paket Aktif</span><b>${activePackages}</b><span>selling packages</span></div><div class="card metric goldtop"><span>Paket Stasiun</span><b>${station ? rupiahLocal(station.price_per_pax) : '-'}</b><span>${station ? `min ${Number(station.min_pax || 0)} peserta` : 'belum ditemukan'}</span></div>`;
  }

  function installNav() {
    if (!canManage() || q('#nav [data-page="packageMaster"]')) return;
    const nav = q('#nav');
    if (!nav) return;
    const button = document.createElement('button');
    button.dataset.page = 'packageMaster';
    button.innerHTML = '▦ &nbsp; Package Master';
    const media = q('#nav [data-page="mediaMaster"]');
    if (media) nav.insertBefore(button, media); else nav.appendChild(button);
  }

  function installBookingPackagePicker() {
    const form = q('#bookingForm');
    const programField = q('#bProgram')?.closest('.field');
    if (!form || !programField || q('#gmuBookingPackageHelper')) return;
    const helper = document.createElement('div');
    helper.id = 'gmuBookingPackageHelper';
    helper.className = 'full gmu-booking-package-helper';
    helper.innerHTML = `<label>Pilih dari Master Paket (opsional)</label><select id="gmuBookingPackageSelect"><option value="">— Input manual —</option></select>`;
    form.insertBefore(helper, programField);
    q('#gmuBookingPackageSelect').addEventListener('change', applyPackageToBooking);
  }

  function refreshBookingPackagePicker() {
    const select = q('#gmuBookingPackageSelect');
    if (!select) return;
    const active = packages.filter(p => p.is_active && String(p.status || '').toUpperCase() === 'ACTIVE');
    select.innerHTML = '<option value="">— Input manual —</option>' + active.map(p => `<option value="${h(p.id)}">${h(p.package_code)} • ${h(p.name)} • ${rupiahLocal(p.price_per_pax)} • min ${Number(p.min_pax || 0)}</option>`).join('');
  }

  function applyPackageToBooking(event) {
    const pkg = packages.find(p => String(p.id) === String(event.target.value));
    if (!pkg) return;
    const program = programs.find(p => String(p.id) === String(pkg.program_id));
    if (q('#bProgram')) q('#bProgram').value = program?.name || pkg.name || '';
    if (q('#bPrice')) q('#bPrice').value = Number(pkg.price_per_pax || 0);
    if (q('#bPax') && Number(q('#bPax').value || 0) < Number(pkg.min_pax || 1)) q('#bPax').value = Number(pkg.min_pax || 1);
    if (q('#bNotes')) {
      const facilities = facilitiesArray(pkg.facilities);
      const generated = `Paket: ${pkg.package_code} — ${pkg.name}\nFasilitas:\n${facilities.map(x => '- ' + x).join('\n')}`;
      if (!q('#bNotes').value.trim()) q('#bNotes').value = generated;
    }
  }

  function bind() {
    q('#gmuAddProgram')?.addEventListener('click', () => openProgram());
    q('#gmuAddPackage')?.addEventListener('click', () => openPackage());
    q('#gmuPackageRefresh')?.addEventListener('click', () => loadMaster().catch(e => setStatus(e.message, true)));
    q('#gmuMasterClose')?.addEventListener('click', closeModalLocal);
    q('#gmuProgramBody')?.addEventListener('click', e => { const btn = e.target.closest('[data-edit-program]'); if (btn) openProgram(btn.dataset.editProgram); });
    q('#gmuPackageBody')?.addEventListener('click', e => { const btn = e.target.closest('[data-edit-package]'); if (btn) openPackage(btn.dataset.editPackage); });
  }

  function init() {
    installStyles();
    installBookingPackagePicker();
    const timer = setInterval(() => {
      if (typeof sb === 'undefined' || !sb || typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      if (canManage()) { installPage(); installNav(); bind(); }
      loadMaster().catch(e => setStatus(e?.message || String(e), true));
    }, 250);

    window.GmuErpPackageMaster = Object.freeze({ version: VERSION, reload: () => loadMaster(), canManage });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();


/* ===== media-master-v96.js ===== */
(() => {
  'use strict';

  const VERSION = 'v9.6-media-master';
  const BUCKET = 'edutrans-media';
  const INTERNAL_FN = 'internal-media-master';
  const MAX_BYTES = 8 * 1024 * 1024;
  const MAX_GALLERY = 5;
  const ALLOWED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
  const ALLOWED_ROLES = new Set(['Owner', 'Director', 'Direktur', 'Manager', 'Manager EduTrans', 'Admin']);

  let catalog = { programs: [], packages: [] };
  let editor = null;

  const q = (sel, root = document) => root.querySelector(sel);
  const qa = (sel, root = document) => [...root.querySelectorAll(sel)];
  const h = (value) => String(value ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
  })[c]);

  function currentRole() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }

  function canManageMedia() {
    return ALLOWED_ROLES.has(currentRole());
  }

  async function token() {
    const { data, error } = await sb.auth.getSession();
    if (error) throw error;
    const access = data?.session?.access_token;
    if (!access) throw new Error('Sesi login tidak ditemukan. Silakan login ulang.');
    return access;
  }

  async function mediaApi(payload) {
    const access = await token();
    const cfgObj = typeof cfg === 'function' ? cfg() : { url: 'https://gtgnwasijweewmaubvyg.supabase.co', key: '' };
    const base = String(cfgObj.url || '').replace(/\/$/, '');
    const res = await fetch(`${base}/functions/v1/${INTERNAL_FN}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        apikey: cfgObj.key || '',
        Authorization: `Bearer ${access}`,
      },
      body: JSON.stringify(payload),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data?.error || `Media API gagal (${res.status}).`);
    return data;
  }

  function entityMeta(kind, row) {
    if (kind === 'program') {
      return { table: 'programs', type: 'program', id: String(row.id), title: String(row.name || 'Program') };
    }
    return { table: 'program_packages', type: 'package', id: String(row.id), title: String(row.name || row.package_code || 'Paket') };
  }

  function normalizeMedia(media) {
    const cover = String(media?.cover_image_url || '').startsWith('https://') ? String(media.cover_image_url) : null;
    const gallery = Array.isArray(media?.gallery_urls)
      ? media.gallery_urls.map(x => String(x || '').trim()).filter(x => x.startsWith('https://'))
          .filter((x, i, a) => a.indexOf(x) === i).filter(x => x !== cover).slice(0, MAX_GALLERY)
      : [];
    return { cover_image_url: cover, gallery_urls: gallery };
  }

  function publicUrl(path) {
    const cfgObj = typeof cfg === 'function' ? cfg() : { url: 'https://gtgnwasijweewmaubvyg.supabase.co' };
    return `${String(cfgObj.url).replace(/\/$/, '')}/storage/v1/object/public/${BUCKET}/${path.split('/').map(encodeURIComponent).join('/')}`;
  }

  function extension(file) {
    if (file.type === 'image/jpeg') return 'jpg';
    if (file.type === 'image/png') return 'png';
    return 'webp';
  }

  async function uploadFile(file, target) {
    if (!file) throw new Error('Pilih file gambar.');
    if (!ALLOWED_TYPES.has(file.type)) throw new Error('Format harus JPG, PNG, atau WebP.');
    if (file.size > MAX_BYTES) throw new Error('Ukuran maksimal 8 MB per gambar.');
    const path = `${target.type}/${target.id}/${crypto.randomUUID()}.${extension(file)}`;
    const { error } = await sb.storage.from(BUCKET).upload(path, file, {
      cacheControl: '3600',
      upsert: false,
      contentType: file.type,
    });
    if (error) throw error;
    return publicUrl(path);
  }

  async function loadCatalog() {
    if (!canManageMedia()) return;
    setStatus('Memuat Media Master…');
    const data = await mediaApi({ action: 'catalog' });
    catalog = {
      programs: Array.isArray(data.programs) ? data.programs : [],
      packages: Array.isArray(data.packages) ? data.packages : [],
    };
    renderLists();
    setStatus('');
  }

  function programCoverForPackage(pkg) {
    return catalog.programs.find(p => String(p.id) === String(pkg.program_id))?.cover_image_url || null;
  }

  function mediaState(row, fallback = null) {
    const own = normalizeMedia(row);
    return {
      cover: own.cover_image_url || fallback || null,
      ownCover: own.cover_image_url,
      gallery: own.gallery_urls,
    };
  }

  function card(kind, row) {
    const fallback = kind === 'package' ? programCoverForPackage(row) : null;
    const state = mediaState(row, fallback);
    const sub = kind === 'program'
      ? `${h(row.category || 'Program')}`
      : `${h(row.package_code || 'Paket')} • ${h(row.status || '-')}`;
    const coverLabel = state.ownCover ? 'Cover ✓' : (fallback ? 'Cover Program' : 'Cover —');
    return `<button type="button" class="gmu-media-card" data-kind="${kind}" data-id="${h(row.id)}">
      <div class="gmu-media-thumb">${state.cover ? `<img src="${h(state.cover)}" alt="">` : '<span>Belum ada cover</span>'}</div>
      <div class="gmu-media-card-main"><b>${h(row.name || row.package_code || '-')}</b><small>${sub}</small><em>${coverLabel} • Galeri ${state.gallery.length}/${MAX_GALLERY}</em></div>
      <span class="gmu-media-edit">Kelola</span>
    </button>`;
  }

  function renderLists() {
    const p = q('#gmuMediaPrograms');
    const k = q('#gmuMediaPackages');
    if (p) p.innerHTML = catalog.programs.map(row => card('program', row)).join('') || '<div class="gmu-media-empty">Belum ada Program aktif.</div>';
    if (k) k.innerHTML = catalog.packages.map(row => card('package', row)).join('') || '<div class="gmu-media-empty">Belum ada Paket.</div>';
  }

  async function openEditor(kind, id) {
    const rows = kind === 'program' ? catalog.programs : catalog.packages;
    const row = rows.find(x => String(x.id) === String(id));
    if (!row) return;
    const target = entityMeta(kind, row);
    setStatus('Memuat media…');
    const result = await mediaApi({ action: 'load', table: target.table, entity_id: target.id });
    const media = normalizeMedia(result.media);
    editor = {
      kind,
      row,
      target,
      original: normalizeMedia(media),
      media: normalizeMedia(media),
      uploadedThisSession: [],
      saving: false,
    };
    q('#gmuMediaEditorTitle').textContent = `Media • ${target.title}`;
    q('#gmuMediaEditor').classList.add('open');
    renderEditor();
    setStatus('');
  }

  function renderEditor() {
    if (!editor) return;
    const m = editor.media;
    q('#gmuMediaCoverPreview').innerHTML = m.cover_image_url
      ? `<img src="${h(m.cover_image_url)}" alt="Cover">`
      : '<div class="gmu-media-placeholder">Belum ada cover</div>';
    q('#gmuMediaGallery').innerHTML = m.gallery_urls.map((url, i) => `
      <div class="gmu-media-gallery-item" data-index="${i}">
        <img src="${h(url)}" alt="Galeri ${i + 1}">
        <div class="gmu-media-gallery-actions">
          <button type="button" data-action="cover" data-index="${i}">Jadikan Cover</button>
          <button type="button" data-action="up" data-index="${i}" ${i === 0 ? 'disabled' : ''}>↑</button>
          <button type="button" data-action="down" data-index="${i}" ${i === m.gallery_urls.length - 1 ? 'disabled' : ''}>↓</button>
          <button type="button" data-action="remove" data-index="${i}">Hapus</button>
        </div>
      </div>`).join('') || '<div class="gmu-media-empty">Belum ada foto galeri.</div>';
    q('#gmuMediaGalleryCount').textContent = `${m.gallery_urls.length}/${MAX_GALLERY}`;
    q('#gmuMediaAddGallery').disabled = m.gallery_urls.length >= MAX_GALLERY;
  }

  async function uploadCover(file) {
    if (!editor || !file) return;
    setEditorStatus('Mengupload cover…');
    const url = await uploadFile(file, editor.target);
    editor.uploadedThisSession.push(url);
    editor.media.cover_image_url = url;
    editor.media.gallery_urls = editor.media.gallery_urls.filter(x => x !== url);
    renderEditor();
    setEditorStatus('Cover siap disimpan.');
  }

  async function uploadGallery(files) {
    if (!editor) return;
    const chosen = [...(files || [])];
    if (!chosen.length) return;
    const room = MAX_GALLERY - editor.media.gallery_urls.length;
    if (room <= 0) throw new Error('Galeri sudah mencapai 5 foto.');
    if (chosen.length > room) throw new Error(`Sisa slot galeri hanya ${room}.`);
    for (const file of chosen) {
      setEditorStatus(`Mengupload galeri ${editor.media.gallery_urls.length + 1}/${MAX_GALLERY}…`);
      const url = await uploadFile(file, editor.target);
      editor.uploadedThisSession.push(url);
      if (url !== editor.media.cover_image_url && !editor.media.gallery_urls.includes(url)) editor.media.gallery_urls.push(url);
    }
    renderEditor();
    setEditorStatus('Galeri siap disimpan.');
  }

  function galleryAction(action, index) {
    if (!editor) return;
    const list = editor.media.gallery_urls;
    if (index < 0 || index >= list.length) return;
    if (action === 'cover') {
      const selected = list[index];
      const oldCover = editor.media.cover_image_url;
      editor.media.cover_image_url = selected;
      editor.media.gallery_urls = list.filter((_, i) => i !== index);
      if (oldCover && oldCover !== selected && !editor.media.gallery_urls.includes(oldCover)) {
        editor.media.gallery_urls.unshift(oldCover);
        editor.media.gallery_urls = editor.media.gallery_urls.slice(0, MAX_GALLERY);
      }
    } else if (action === 'remove') {
      editor.media.gallery_urls = list.filter((_, i) => i !== index);
    } else if (action === 'up' && index > 0) {
      [list[index - 1], list[index]] = [list[index], list[index - 1]];
    } else if (action === 'down' && index < list.length - 1) {
      [list[index + 1], list[index]] = [list[index], list[index + 1]];
    }
    renderEditor();
  }

  async function saveEditor() {
    if (!editor || editor.saving) return;
    editor.saving = true;
    setEditorStatus('Menyimpan media…');
    try {
      const result = await mediaApi({
        action: 'save',
        table: editor.target.table,
        entity_id: editor.target.id,
        media: editor.media,
      });
      editor.media = normalizeMedia(result.media);
      editor.original = normalizeMedia(result.media);
      editor.uploadedThisSession = [];
      setEditorStatus('Media tersimpan.');
      await loadCatalog();
      setTimeout(closeEditor, 250);
    } finally {
      if (editor) editor.saving = false;
    }
  }

  async function discardSessionUploads() {
    if (!editor?.uploadedThisSession?.length) return;
    const retained = new Set([
      editor.original.cover_image_url,
      ...editor.original.gallery_urls,
    ].filter(Boolean));
    const disposable = editor.uploadedThisSession.filter(url => !retained.has(url));
    if (!disposable.length) return;
    try {
      await mediaApi({
        action: 'discard',
        table: editor.target.table,
        entity_id: editor.target.id,
        urls: disposable,
      });
    } catch (e) {
      console.warn('Media discard cleanup gagal', e);
    }
  }

  async function closeEditor() {
    if (!editor) return;
    const current = editor;
    editor = null;
    q('#gmuMediaEditor')?.classList.remove('open');
    try {
      if (current.uploadedThisSession?.length) {
        editor = current;
        await discardSessionUploads();
        editor = null;
      }
    } finally {
      q('#gmuMediaEditorStatus').textContent = '';
      q('#gmuMediaCoverInput').value = '';
      q('#gmuMediaGalleryInput').value = '';
    }
  }

  function setStatus(msg) {
    const el = q('#gmuMediaStatus');
    if (el) el.textContent = msg || '';
  }

  function setEditorStatus(msg) {
    const el = q('#gmuMediaEditorStatus');
    if (el) el.textContent = msg || '';
  }

  function installStyle() {
    if (q('#gmuMediaMasterStyle')) return;
    const style = document.createElement('style');
    style.id = 'gmuMediaMasterStyle';
    style.textContent = `
      .gmu-media-layout{display:grid;grid-template-columns:1fr 1fr;gap:14px}.gmu-media-list{display:grid;gap:9px}.gmu-media-card{width:100%;display:grid;grid-template-columns:88px minmax(0,1fr) auto;gap:12px;align-items:center;text-align:left;border:1px solid var(--line);background:#fff;border-radius:14px;padding:9px;cursor:pointer;color:var(--text)}.gmu-media-card:hover{border-color:#9bc6ae;box-shadow:0 8px 22px rgba(6,68,46,.08)}.gmu-media-thumb{width:88px;aspect-ratio:16/10;border-radius:10px;background:#eef4f1;overflow:hidden;display:grid;place-items:center;color:var(--muted);font-size:8px}.gmu-media-thumb img{width:100%;height:100%;object-fit:cover}.gmu-media-card-main{min-width:0}.gmu-media-card-main b,.gmu-media-card-main small,.gmu-media-card-main em{display:block}.gmu-media-card-main b{font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.gmu-media-card-main small{font-size:9px;color:var(--muted);margin-top:3px}.gmu-media-card-main em{font-size:8px;color:var(--g);font-style:normal;font-weight:800;margin-top:4px}.gmu-media-edit{font-size:9px;font-weight:800;color:var(--g)}.gmu-media-empty{border:1px dashed var(--line);border-radius:12px;padding:15px;color:var(--muted);font-size:9px;text-align:center}.gmu-media-editor-cover{aspect-ratio:16/9;border-radius:14px;background:#eef4f1;overflow:hidden;display:grid;place-items:center}.gmu-media-editor-cover img{width:100%;height:100%;object-fit:cover}.gmu-media-placeholder{font-size:10px;color:var(--muted)}.gmu-media-upload-row{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin:10px 0 14px}.gmu-media-gallery{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.gmu-media-gallery-item{border:1px solid var(--line);border-radius:12px;padding:8px}.gmu-media-gallery-item img{width:100%;aspect-ratio:16/10;object-fit:cover;border-radius:9px;background:#eef4f1}.gmu-media-gallery-actions{display:flex;gap:5px;flex-wrap:wrap;margin-top:7px}.gmu-media-gallery-actions button{border:0;border-radius:8px;padding:6px 8px;background:#edf4f0;color:var(--gd);font-size:8px;font-weight:800;cursor:pointer}.gmu-media-gallery-actions button:disabled{opacity:.4;cursor:not-allowed}.gmu-media-toolbar{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:12px}.gmu-media-help{font-size:9px;color:var(--muted);line-height:1.5}.gmu-media-status{font-size:9px;color:var(--g);font-weight:800;min-height:14px}.gmu-media-editor-footer{display:flex;justify-content:flex-end;gap:8px;margin-top:16px}@media(max-width:900px){.gmu-media-layout{grid-template-columns:1fr}.gmu-media-gallery{grid-template-columns:1fr 1fr}}@media(max-width:560px){.gmu-media-card{grid-template-columns:72px minmax(0,1fr)}.gmu-media-thumb{width:72px}.gmu-media-edit{grid-column:2}.gmu-media-gallery{grid-template-columns:1fr}}
    `;
    document.head.appendChild(style);
  }

  function installPage() {
    if (q('#mediaMaster')) return;
    const content = q('.content');
    if (!content) return;
    const page = document.createElement('section');
    page.id = 'mediaMaster';
    page.className = 'page';
    page.innerHTML = `
      <div class="notice">Media Master ${VERSION} • Gambar publik Program/Paket saja. Tidak memuat HPP, fee, profit, margin, atau data keuangan.</div>
      <div class="gmu-media-toolbar"><div><b>Program & Package Media</b><div class="gmu-media-help">Cover + galeri maksimal 5 foto. JPG/PNG/WebP, maksimal 8 MB per file.</div></div><button class="btn ghost" type="button" id="gmuMediaRefresh">↻ Refresh</button></div>
      <div id="gmuMediaStatus" class="gmu-media-status"></div>
      <div class="gmu-media-layout">
        <div class="card section" style="margin-top:0"><div class="head"><div><h3>Program</h3><p>Cover dan galeri Program</p></div></div><div id="gmuMediaPrograms" class="gmu-media-list"></div></div>
        <div class="card section" style="margin-top:0"><div class="head"><div><h3>Paket</h3><p>Cover Paket; jika kosong customer memakai cover Program</p></div></div><div id="gmuMediaPackages" class="gmu-media-list"></div></div>
      </div>`;
    content.appendChild(page);

    const modal = document.createElement('div');
    modal.id = 'gmuMediaEditor';
    modal.className = 'modal';
    modal.innerHTML = `<div class="modalbox" style="width:min(820px,100%)">
      <div class="mtitle"><div><h3 id="gmuMediaEditorTitle">Media</h3><p class="note" style="margin:4px 0 0">Media publik customer. Simpan untuk menerapkan perubahan.</p></div><button class="close" type="button" id="gmuMediaClose">×</button></div>
      <div id="gmuMediaCoverPreview" class="gmu-media-editor-cover"></div>
      <div class="gmu-media-upload-row"><input id="gmuMediaCoverInput" type="file" accept="image/jpeg,image/png,image/webp"><button class="btn ghost" type="button" id="gmuMediaUploadCover">Upload / Ganti Cover</button><button class="btn danger" type="button" id="gmuMediaRemoveCover">Hapus Cover</button></div>
      <div class="head"><div><h3>Galeri</h3><p>Maksimal 5 foto • <span id="gmuMediaGalleryCount">0/5</span></p></div></div>
      <div id="gmuMediaGallery" class="gmu-media-gallery"></div>
      <div class="gmu-media-upload-row"><input id="gmuMediaGalleryInput" type="file" multiple accept="image/jpeg,image/png,image/webp"><button class="btn ghost" type="button" id="gmuMediaAddGallery">+ Upload Galeri</button></div>
      <div id="gmuMediaEditorStatus" class="gmu-media-status"></div>
      <div class="gmu-media-editor-footer"><button class="btn ghost" type="button" id="gmuMediaCancel">Batal</button><button class="btn primary" type="button" id="gmuMediaSave">Simpan Media</button></div>
    </div>`;
    document.body.appendChild(modal);
  }

  function installNav() {
    if (!canManageMedia() || q('#nav [data-page="mediaMaster"]')) return;
    const nav = q('#nav');
    if (!nav) return;
    const btn = document.createElement('button');
    btn.dataset.page = 'mediaMaster';
    btn.innerHTML = '▧ &nbsp; Media Master';
    nav.appendChild(btn);
    btn.addEventListener('click', async () => {
      qa('.page').forEach(x => x.classList.remove('active'));
      q('#mediaMaster')?.classList.add('active');
      qa('#nav button').forEach(x => x.classList.toggle('active', x === btn));
      if (q('#title')) q('#title').textContent = 'Media Master';
      q('#side')?.classList.remove('open');
      try { await loadCatalog(); } catch (e) { setStatus(`Gagal: ${e.message}`); }
    });
  }

  function bind() {
    q('#gmuMediaRefresh')?.addEventListener('click', () => loadCatalog().catch(e => setStatus(`Gagal: ${e.message}`)));
    q('#gmuMediaPrograms')?.addEventListener('click', e => {
      const c = e.target.closest('.gmu-media-card');
      if (c) openEditor(c.dataset.kind, c.dataset.id).catch(err => setStatus(`Gagal: ${err.message}`));
    });
    q('#gmuMediaPackages')?.addEventListener('click', e => {
      const c = e.target.closest('.gmu-media-card');
      if (c) openEditor(c.dataset.kind, c.dataset.id).catch(err => setStatus(`Gagal: ${err.message}`));
    });
    q('#gmuMediaUploadCover')?.addEventListener('click', () => uploadCover(q('#gmuMediaCoverInput')?.files?.[0]).catch(e => setEditorStatus(`Gagal: ${e.message}`)));
    q('#gmuMediaAddGallery')?.addEventListener('click', () => uploadGallery(q('#gmuMediaGalleryInput')?.files).catch(e => setEditorStatus(`Gagal: ${e.message}`)));
    q('#gmuMediaRemoveCover')?.addEventListener('click', () => { if (editor) { editor.media.cover_image_url = null; renderEditor(); } });
    q('#gmuMediaGallery')?.addEventListener('click', e => {
      const b = e.target.closest('button[data-action]');
      if (b) galleryAction(b.dataset.action, Number(b.dataset.index));
    });
    q('#gmuMediaSave')?.addEventListener('click', () => saveEditor().catch(e => setEditorStatus(`Gagal: ${e.message}`)));
    q('#gmuMediaCancel')?.addEventListener('click', () => closeEditor());
    q('#gmuMediaClose')?.addEventListener('click', () => closeEditor());
  }

  function init() {
    installStyle();
    installPage();
    bind();
    const timer = setInterval(() => {
      if (typeof sb === 'undefined' || !sb || typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      if (canManageMedia()) installNav();
    }, 300);
    setTimeout(() => clearInterval(timer), 30000);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();


/* ===== manager-ops-agent-v98.js ===== */
(() => {
  'use strict';

  const VERSION = 'v9.8-manager-ops-agent';
  const ALLOWED_ROLES = new Set(['Owner', 'Director', 'Direktur', 'Manager', 'Manager EduTrans']);
  const MANAGER_ROLES = new Set(['Manager', 'Manager EduTrans']);
  const MANAGER_PLANNED_RAB_LIMIT = 1_000_000;
  const MANAGER_UNPLANNED_LIMIT = 250_000;
  const MANAGER_EMERGENCY_LIMIT = 500_000;
  const MANAGER_MAX_DISCOUNT_PCT = 5;
  const CRITICAL_MARGIN_PCT = 20;
  const QUICK_ACTIONS = ['Siapkan Trip','Buat Rundown','Cek Kesiapan','Susun Crew','Cek Vendor','Analisis RAB','Buat Operation Sheet','Buat Laporan'];
  const q = (sel, root = document) => root.querySelector(sel);
  const h = (value) => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = (value) => 'Rp ' + Number(value || 0).toLocaleString('id-ID');

  let selectedBookingId = '';
  let extra = { vendorPos: [], documents: [], costs: [] };
  let loading = false;

  function currentRole(){ try{return String(profile?.role||'')}catch(_){return ''} }
  function allowed(){ return ALLOWED_ROLES.has(currentRole()) }
  function isManager(){ return MANAGER_ROLES.has(currentRole()) }
  function bookings(){ try{return Array.isArray(data?.bookings)?data.bookings:[]}catch(_){return []} }
  function nextTrip(){ const rows=bookings().filter(b=>!['Completed','Closed'].includes(String(b?.status||''))).slice().sort((a,b)=>String(a?.trip_date||'').localeCompare(String(b?.trip_date||'')));return rows[0]||null }
  function selectedTrip(){ return bookings().find(b=>String(b.id)===String(selectedBookingId))||nextTrip() }
  function customerName(booking){ try{return customer(booking?.customer_id)?.name||'-'}catch(_){return '-'} }
  function tripRow(id){ try{return data.trips?.find(x=>String(x.booking_id)===String(id))}catch(_){return null} }
  function operationSheet(id){ try{return data.operationSheets?.find(x=>String(x.booking_id)===String(id))}catch(_){return null} }

  async function queryOptional(table, bookingId){
    try{
      const {data:rows,error}=await sb.from(table).select('*').eq('booking_id',bookingId);
      if(error)throw error;
      return rows||[];
    }catch(e){console.warn(`${VERSION}: ${table}`,e?.message||e);return []}
  }

  async function loadExtras(bookingId){
    if(!bookingId||typeof sb==='undefined'||!sb){extra={vendorPos:[],documents:[],costs:[]};return}
    const [vendorPos,documents,costs]=await Promise.all([queryOptional('vendor_pos',bookingId),queryOptional('documents',bookingId),queryOptional('trip_costs',bookingId)]);
    extra={vendorPos,documents,costs};
  }

  function readiness(booking){
    if(!booking)return{score:0,missing:[],checks:[]};
    const id=booking.id,os=operationSheet(id);
    let rundown=false,manifest=false;
    try{rundown=data.rundownItems?.some(x=>String(x.booking_id)===String(id))}catch(_){}
    try{manifest=data.manifests?.some(x=>String(x.booking_id)===String(id))}catch(_){}
    const checks=[['Rundown',!!rundown],['Manifest',!!manifest],['Operation Sheet',!!os],['Vendor/PO',extra.vendorPos.length>0],['Documents',extra.documents.length>=5]];
    const completed=checks.filter(x=>x[1]).length;
    return{score:Math.round(completed*100/checks.length),missing:checks.filter(x=>!x[1]).map(x=>x[0]),checks};
  }

  function rabSnapshot(){const rab=extra.costs.reduce((s,r)=>s+Number(r?.rab_amount||0),0),actual=extra.costs.reduce((s,r)=>s+Number(r?.actual_amount||0),0);return{rab,actual,variance:rab-actual}}
  function authorityFor(action,amount=0){
    const n=String(action||'').toLowerCase(),value=Number(amount||0);
    if(['refund','closing','hapus piutang','ubah harga','rekening','jurnal','gunakan cadangan','reserve'].some(x=>n.includes(x)))return'DIRECTOR_APPROVAL';
    const discountMatch=n.match(/(?:diskon|discount)\s*(?:sebesar\s*)?([0-9]+(?:[.,][0-9]+)?)\s*%/i),discountPct=discountMatch?Number(discountMatch[1].replace(',','.')):0;
    if(discountPct>MANAGER_MAX_DISCOUNT_PCT)return'DIRECTOR_APPROVAL';
    if(discountPct>0)return'MANAGER_CONFIRMATION';
    const marginMatch=n.match(/margin\s*([0-9]+(?:[.,][0-9]+)?)\s*%/i),marginPct=marginMatch?Number(marginMatch[1].replace(',','.')):null;
    if(marginPct!==null&&marginPct<CRITICAL_MARGIN_PCT)return'DIRECTOR_APPROVAL';
    if(marginPct!==null&&marginPct<25)return'MANAGER_CONFIRMATION';
    if(['darurat','emergency'].some(x=>n.includes(x))){return value>MANAGER_EMERGENCY_LIMIT?'DIRECTOR_APPROVAL':'MANAGER_CONFIRMATION'}
    if(['di luar rab','diluar rab','unplanned','tidak ada di rab'].some(x=>n.includes(x))){return value>MANAGER_UNPLANNED_LIMIT?'DIRECTOR_APPROVAL':'MANAGER_CONFIRMATION'}
    if(['rab','biaya','vendor','pengeluaran','expense','cost'].some(x=>n.includes(x))&&value>0){return value>MANAGER_PLANNED_RAB_LIMIT?'DIRECTOR_APPROVAL':'MANAGER_CONFIRMATION'}
    if(value>MANAGER_PLANNED_RAB_LIMIT)return'DIRECTOR_APPROVAL';
    if(['assign','kirim','approve','ubah','final','konfirmasi vendor'].some(x=>n.includes(x)))return'MANAGER_CONFIRMATION';
    return'AUTO'
  }
  function statusText(a){return a==='DIRECTOR_APPROVAL'?'Perlu Approval Direktur':a==='MANAGER_CONFIRMATION'?'Perlu Konfirmasi Manager':'Assist / Auto Draft'}

  function renderTripSelect(){const select=q('#gmuOpsTripSelect');if(!select)return;const rows=bookings().filter(b=>String(b.status||'')!=='Closed');if(!selectedBookingId&&nextTrip())selectedBookingId=String(nextTrip().id);select.innerHTML=rows.map(b=>`<option value="${h(b.id)}" ${String(b.id)===String(selectedBookingId)?'selected':''}>${h(b.booking_no)} • ${h(b.program_name)} • ${h(b.trip_date)}</option>`).join('')||'<option value="">Belum ada trip</option>'}

  function renderPanel(){
    if(!allowed())return;
    renderTripSelect();
    const booking=selectedTrip(),ready=readiness(booking),rab=rabSnapshot(),summary=q('#gmuOpsBrief');
    if(summary){
      if(!booking)summary.innerHTML='<div class="gmu-ops-empty">Belum ada trip aktif atau mendatang.</div>';
      else{
        let pending=0;try{pending=data.approvals?.filter(x=>x.status==='Pending').length||0}catch(_){}
        summary.innerHTML=`<div class="gmu-ops-brief-head"><div><b>${h(booking.program_name)}</b><small>${h(customerName(booking))} • ${h(booking.trip_date)} • ${Number(booking.pax||0)} pax</small></div><span class="badge ${ready.score>=80?'ok':'warn'}">${ready.score}% READY</span></div><div class="gmu-ops-progress"><i style="width:${ready.score}%"></i></div><div class="gmu-ops-checks">${ready.checks.map(x=>`<span class="${x[1]?'done':'missing'}">${x[1]?'✓':'○'} ${h(x[0])}</span>`).join('')}</div>${ready.missing.length?`<p class="gmu-ops-attention">Perlu perhatian: ${h(ready.missing.join(', '))}</p>`:'<p class="gmu-ops-good">Dokumen inti trip sudah siap.</p>'}${pending?`<p class="gmu-ops-attention">${pending} approval sedang menunggu tindakan.</p>`:''}<div class="gmu-ops-rab"><span>RAB operasional</span><b>${rab.rab?money(rab.rab):'Belum diisi'}</b><span>Aktual ${money(rab.actual)}</span></div>`;
      }
    }
    const dock=q('#gmuOpsDockText');if(dock)dock.textContent=booking?`Next trip ${ready.score}% ready • ${booking.program_name}`:'Siap membantu operasional Manager';
  }

  function reply(message,tone=''){const el=q('#gmuOpsReply');if(!el)return;el.textContent=message;el.dataset.tone=tone}
  async function refresh(){if(loading||!allowed())return;loading=true;try{const b=selectedTrip();if(b){selectedBookingId=String(b.id);await loadExtras(b.id)}else extra={vendorPos:[],documents:[],costs:[]};renderPanel()}finally{loading=false}}

  function openTripFolderLocal(id){try{if(typeof openTripFolder==='function'){openTripFolder(id);return true}}catch(_){}try{if(typeof navTo==='function')navTo('tripfolder')}catch(_){}return false}
  function openOperationLocal(id){try{if(typeof openOperation==='function'){openOperation(id);return true}}catch(_){}try{if(typeof navTo==='function')navTo('operation')}catch(_){}return false}

  async function runAction(action){
    const booking=selectedTrip();if(!booking&&action!=='Cek Vendor'){reply('Belum ada trip yang dapat diproses.','warn');return}
    q('#gmuOpsAuthority').textContent=statusText(authorityFor(action));
    if(action==='Cek Kesiapan'){await refresh();const r=readiness(selectedTrip());reply(r.missing.length?`Kesiapan ${r.score}%. Lengkapi: ${r.missing.join(', ')}.`:`Kesiapan ${r.score}%. Trip inti siap.`,r.score>=80?'ok':'warn');return}
    if(action==='Analisis RAB'){await refresh();const r=rabSnapshot();if(r.rab<=0)reply('RAB operasional belum diisi untuk trip ini.','warn');else if(r.variance<0)reply(`RAB ${money(r.rab)} • Aktual ${money(r.actual)}. Aktual melewati RAB ${money(-r.variance)}.`,'warn');else reply(`RAB ${money(r.rab)} • Aktual ${money(r.actual)}. Sisa ${money(r.variance)}.`,'ok');return}
    if(action==='Cek Vendor'){try{navTo('vendors')}catch(_){}reply('Vendor Master dibuka. Konfirmasi vendor tetap memerlukan tindakan Manager.');return}
    if(action==='Buat Operation Sheet'||action==='Susun Crew'){openOperationLocal(booking.id);reply(action==='Susun Crew'?'Operation Sheet dibuka untuk penugasan TL/crew.':'Operation Sheet dibuka untuk disiapkan.');return}
    if(['Siapkan Trip','Buat Rundown','Buat Laporan'].includes(action)){openTripFolderLocal(booking.id);reply(`${action}: Trip Folder dibuka. Penyimpanan/finalisasi tetap dilakukan user berwenang.`);return}
  }

  async function routeCommand(command){
    const text=String(command||'').toLowerCase().trim();if(!text)return;
    const amountMatch=text.match(/(?:rp\s*)?([0-9][0-9.]{5,})/i),amount=amountMatch?Number(amountMatch[1].replace(/\./g,'')):0,authority=authorityFor(text,amount);
    q('#gmuOpsAuthority').textContent=statusText(authority);
    if(authority==='DIRECTOR_APPROVAL'){reply('Tindakan ini berada di luar kewenangan Manager sesuai matriks persetujuan GMU EduTrans dan harus dinaikkan ke Direktur.','warn');return}
    if(text.includes('rab')||text.includes('biaya')||text.includes('cost'))return runAction('Analisis RAB');
    if(text.includes('vendor')||text.includes(' po '))return runAction('Cek Vendor');
    if(text.includes('rundown'))return runAction('Buat Rundown');
    if(text.includes('manifest')||text.includes('dokumen')||text.includes('folder'))return runAction('Siapkan Trip');
    if(text.includes('crew')||text.includes('tl')||text.includes('tour leader'))return runAction('Susun Crew');
    if(text.includes('operation')||text.includes('opsheet'))return runAction('Buat Operation Sheet');
    if(text.includes('laporan')||text.includes('report'))return runAction('Buat Laporan');
    if(text.includes('siap')||text.includes('kesiapan')||text.includes('ready'))return runAction('Cek Kesiapan');
    reply('Saya bisa membantu: siapkan trip, rundown, kesiapan, crew/TL, vendor, RAB operasional, Operation Sheet, dan laporan.');
  }

  function installStyle(){
    if(q('#gmuOpsAgentStyle'))return;const style=document.createElement('style');style.id='gmuOpsAgentStyle';style.textContent=`.gmu-ops-shell{display:grid;grid-template-columns:1.15fr .85fr;gap:14px}.gmu-ops-brief-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.gmu-ops-brief-head b,.gmu-ops-brief-head small{display:block}.gmu-ops-brief-head small{font-size:9px;color:var(--muted);margin-top:3px}.gmu-ops-progress{height:9px;background:#e6eee9;border-radius:999px;overflow:hidden;margin:12px 0}.gmu-ops-progress i{display:block;height:100%;background:var(--g);border-radius:999px}.gmu-ops-checks{display:flex;gap:6px;flex-wrap:wrap}.gmu-ops-checks span{font-size:8px;font-weight:800;padding:5px 7px;border-radius:999px}.gmu-ops-checks .done{background:#e5f5ed;color:#0b6649}.gmu-ops-checks .missing{background:#fff4dc;color:#956300}.gmu-ops-attention{font-size:9px;color:#956300;font-weight:800}.gmu-ops-good{font-size:9px;color:var(--g);font-weight:800}.gmu-ops-rab{display:grid;grid-template-columns:1fr auto;gap:3px 10px;border-top:1px solid var(--line);margin-top:12px;padding-top:10px}.gmu-ops-rab span{font-size:8px;color:var(--muted)}.gmu-ops-rab span:last-child{grid-column:1/-1}.gmu-ops-actions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.gmu-ops-action{border:1px solid var(--line);background:#fff;border-radius:13px;padding:11px;text-align:left;cursor:pointer;color:var(--text)}.gmu-ops-action b{display:block;font-size:10px}.gmu-ops-action small{font-size:8px;color:var(--g)}.gmu-ops-command{display:grid;grid-template-columns:1fr auto;gap:8px}.gmu-ops-command input,.gmu-ops-select{border:1px solid var(--line);border-radius:10px;padding:10px;background:#fff;color:var(--text)}.gmu-ops-select{width:100%;margin-top:7px}.gmu-ops-reply{border-radius:12px;background:#f4f8f6;padding:11px;font-size:10px;line-height:1.5;margin:10px 0}.gmu-ops-reply[data-tone="warn"]{background:#fff7e8;color:#805c00}.gmu-ops-reply[data-tone="ok"]{background:#eaf7ef;color:#0b6649}.gmu-ops-policy{font-size:9px;line-height:1.55;color:var(--muted)}.gmu-ops-empty{font-size:10px;color:var(--muted)}.gmu-ops-dock{position:fixed;left:calc(270px + 24px);right:24px;bottom:18px;z-index:18;background:linear-gradient(135deg,var(--gd),var(--g));color:#fff;border:0;border-radius:16px;padding:11px 14px;box-shadow:0 14px 35px rgba(3,48,32,.22);display:flex;align-items:center;justify-content:space-between;gap:12px;cursor:pointer}.gmu-ops-dock b{display:block;font-size:11px}.gmu-ops-dock small{display:block;font-size:8px;color:#d6ebe1;margin-top:2px}.gmu-ops-dock span{font-size:9px;font-weight:900;background:#fff;color:var(--gd);padding:7px 10px;border-radius:999px}@media(max-width:900px){.gmu-ops-shell{grid-template-columns:1fr}}@media(max-width:760px){.gmu-ops-dock{left:13px;right:13px;bottom:12px}}`;
    document.head.appendChild(style);
  }

  function installPage(){
    if(q('#opsAgent'))return;const content=q('.content');if(!content)return;const page=document.createElement('section');page.id='opsAgent';page.className='page';page.innerHTML=`<div class="notice">GMU EduTrans Ops Agent ${VERSION} • Assistant operasional. Perubahan penting tetap memakai konfirmasi dan approval.</div><div class="gmu-ops-shell"><div><div class="card section" style="margin-top:0"><div class="head"><div><h3>Daily Ops Brief</h3><p>Kesiapan trip dan kebutuhan tindakan Manager</p></div><button id="gmuOpsRefresh" class="btn ghost" type="button">↻ Refresh</button></div><label class="note">Trip aktif</label><select id="gmuOpsTripSelect" class="gmu-ops-select"></select><div id="gmuOpsBrief" style="margin-top:12px"></div></div><div class="card section"><div class="head"><div><h3>Ask Ops Agent</h3><p>Perintah natural untuk membuka pekerjaan operasional</p></div><span id="gmuOpsAuthority" class="badge info">Assist / Auto Draft</span></div><div id="gmuOpsReply" class="gmu-ops-reply">Saya siap membantu menyiapkan dan mengontrol operasional EduTrans.</div><div class="gmu-ops-command"><input id="gmuOpsCommand" placeholder="Contoh: cek vendor trip besok"><button id="gmuOpsSend" class="btn primary" type="button">Kirim</button></div></div></div><div class="card section" style="margin-top:0"><div class="head"><div><h3>Perintah Cepat</h3><p>Pilih pekerjaan yang ingin dibantu</p></div></div><div class="gmu-ops-actions">${QUICK_ACTIONS.map(a=>`<button type="button" class="gmu-ops-action" data-ops-action="${h(a)}"><b>${h(a)}</b><small>${a==='Analisis RAB'?'Analisis →':'Buka →'}</small></button>`).join('')}</div><div class="gmu-ops-policy" style="margin-top:14px">Assign/finalisasi/konfirmasi penting memerlukan konfirmasi Manager. Batas Manager: biaya dalam RAB ≤ ${money(MANAGER_PLANNED_RAB_LIMIT)}, di luar RAB ≤ ${money(MANAGER_UNPLANNED_LIMIT)}, darurat ≤ ${money(MANAGER_EMERGENCY_LIMIT)}, diskon ≤ ${MANAGER_MAX_DISCOUNT_PCT}%. Margin <${CRITICAL_MARGIN_PCT}% serta refund, penggunaan cadangan, perubahan harga, rekening dan jurnal memerlukan approval Direktur.</div></div></div>`;content.appendChild(page);
  }

  function installNav(){if(!allowed()||q('#nav [data-page="opsAgent"]'))return;const nav=q('#nav');if(!nav)return;const button=document.createElement('button');button.dataset.page='opsAgent';button.innerHTML='✦ &nbsp; Ops Agent';const operation=q('#nav [data-page="operation"]');if(operation?.nextSibling)nav.insertBefore(button,operation.nextSibling);else nav.appendChild(button)}
  function installDock(){if(!isManager()||q('#gmuOpsDock'))return;const dock=document.createElement('button');dock.id='gmuOpsDock';dock.type='button';dock.className='gmu-ops-dock';dock.innerHTML='<div><b>✦ GMU EduTrans Ops Agent</b><small id="gmuOpsDockText">Siap membantu operasional Manager</small></div><span>Buka</span>';dock.addEventListener('click',()=>{try{navTo('opsAgent')}catch(_){}});document.body.appendChild(dock)}

  function patchApplyRole(){try{if(typeof applyRole!=='function'||applyRole.__gmuOpsPatched)return;const original=applyRole;const wrapped=function(...args){const result=original.apply(this,args);if(allowed())q('#nav [data-page="opsAgent"]')?.classList.remove('hidden');else q('#nav [data-page="opsAgent"]')?.classList.add('hidden');return result};wrapped.__gmuOpsPatched=true;applyRole=wrapped}catch(_){}}

  function bind(){
    q('#gmuOpsRefresh')?.addEventListener('click',()=>refresh());
    q('#gmuOpsTripSelect')?.addEventListener('change',async e=>{selectedBookingId=String(e.target.value||'');await refresh()});
    q('#opsAgent')?.addEventListener('click',e=>{const button=e.target.closest('[data-ops-action]');if(button)runAction(button.dataset.opsAction).catch(err=>reply(err?.message||String(err),'warn'))});
    const send=()=>{const input=q('#gmuOpsCommand'),value=input?.value||'';if(input)input.value='';routeCommand(value).catch(err=>reply(err?.message||String(err),'warn'))};
    q('#gmuOpsSend')?.addEventListener('click',send);q('#gmuOpsCommand')?.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();send()}});
  }

  function init(){installStyle();patchApplyRole();const timer=setInterval(()=>{if(typeof profile==='undefined'||!profile||typeof sb==='undefined'||!sb)return;clearInterval(timer);if(!allowed())return;installPage();installNav();installDock();bind();if(!selectedBookingId&&nextTrip())selectedBookingId=String(nextTrip().id);refresh().catch(e=>reply(e?.message||String(e),'warn'))},250);window.GmuManagerOpsAgent=Object.freeze({version:VERSION,authorityFor,refresh,quickActions:[...QUICK_ACTIONS],authorityMatrix:Object.freeze({plannedRab:MANAGER_PLANNED_RAB_LIMIT,unplanned:MANAGER_UNPLANNED_LIMIT,emergency:MANAGER_EMERGENCY_LIMIT,maxDiscountPct:MANAGER_MAX_DISCOUNT_PCT,criticalMarginPct:CRITICAL_MARGIN_PCT})})}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();


/* ===== company-operating-system-v100.js ===== */
(() => {
  'use strict';

  const VERSION = 'v10.0-company-operating-system-id';
  const POLICY = Object.freeze({
    targetNetProfitMonthly: 15_000_000,
    healthyMarginPct: 25,
    criticalMarginPct: 20,
    managerPlannedRabLimit: 1_000_000,
    managerUnplannedLimit: 250_000,
    managerEmergencyLimit: 500_000,
    managerMaxDiscountPct: 5,
    managerTransportPerAttendance: 30_000,
    officeUtilitiesMonthly: 1_000_000,
    priorityRegions: ['Cianjur', 'Sukabumi'],
    technologyCosts: [
      'Server & Hosting', 'Domain', 'Cloud & Database', 'AI & Otomatisasi',
      'Email & Notifikasi', 'Penyimpanan & Backup', 'Software & Langganan',
      'Keamanan Digital', 'API Pihak Ketiga'
    ],
    marketSegments: [
      'Sekolah & Madrasah', 'Pesantren', 'Perguruan Tinggi', 'Instansi Pemerintah',
      'Komunitas & Organisasi', 'Perusahaan / Corporate', 'Partner & Mitra'
    ]
  });

  const q = (sel, root = document) => root.querySelector(sel);
  const h = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = value => 'Rp ' + Number(value || 0).toLocaleString('id-ID');

  function role() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }

  function isDirector() { return ['Owner', 'Director', 'Direktur'].includes(role()); }
  function isManager() { return ['Manager', 'Manager EduTrans'].includes(role()); }

  function roleMission() {
    if (isDirector()) return 'Menentukan hasil, arah, batas risiko, dan keputusan strategis. Pekerjaan harian dikelola Manager.';
    if (isManager()) return 'Bertanggung jawab atas laba unit, penjualan, pemesanan, operasional, biaya, tim, pelanggan, dan rencana pemulihan.';
    if (role() === 'Sales') return 'Menghasilkan calon pelanggan, tindak lanjut, penawaran, negosiasi, dan pemesanan dengan margin sehat.';
    if (['Finance', 'Keuangan'].includes(role())) return 'Menjaga tagihan, pembayaran, piutang, kewajiban, biaya aktual, penggajian, kas, dan penutupan keuangan.';
    if (['Admin', 'Operation', 'Operasional'].includes(role())) return 'Menjaga dokumen, manifest, rundown, vendor, crew, dan kesiapan kegiatan.';
    if (role() === 'TL') return 'Menjalankan kegiatan sesuai penugasan, rundown, keselamatan, pelayanan, dan laporan.';
    return 'Menjalankan tugas sesuai jabatan, SOP, target, dan kewenangan.';
  }

  function parseMoney(text) {
    const m = String(text || '').match(/(?:rp\s*)?([0-9][0-9.]{3,})/i);
    return m ? Number(m[1].replace(/\./g, '')) : 0;
  }

  function parsePct(text, keys) {
    const s = String(text || '').toLowerCase();
    for (const key of keys) {
      const m = s.match(new RegExp(`${key}\\s*[:=]?\\s*([0-9]+(?:[.,][0-9]+)?)\\s*%?`, 'i'));
      if (m) return Number(m[1].replace(',', '.'));
    }
    return null;
  }

  function authorityFor(text) {
    const s = String(text || '').toLowerCase();
    const amount = parseMoney(s);
    const discount = parsePct(s, ['diskon', 'discount']);
    const margin = parsePct(s, ['margin']);
    const strategic = ['refund', 'pembatalan', 'cancellation', 'rekening', 'jurnal', 'cadangan kas', 'reserve', 'investasi', 'capex', 'pegawai tetap', 'fraud', 'legal'];

    if (strategic.some(k => s.includes(k))) return { level: 'DIRECTOR', reason: 'Keputusan strategis / sensitif' };
    if (margin !== null && margin < POLICY.criticalMarginPct) return { level: 'DIRECTOR', reason: 'Margin di bawah 20%' };
    if (discount !== null && discount > POLICY.managerMaxDiscountPct) return { level: 'DIRECTOR', reason: 'Diskon di atas 5%' };

    const emergency = s.includes('darurat') || s.includes('emergency');
    const unplanned = s.includes('luar rab') || s.includes('di luar rab') || s.includes('unplanned');
    if (emergency && amount > POLICY.managerEmergencyLimit) return { level: 'DIRECTOR', reason: 'Biaya darurat di atas Rp500.000' };
    if (unplanned && amount > POLICY.managerUnplannedLimit) return { level: 'DIRECTOR', reason: 'Biaya di luar RAB di atas Rp250.000' };
    if (amount > POLICY.managerPlannedRabLimit) return { level: 'DIRECTOR', reason: 'Biaya dalam RAB di atas Rp1.000.000' };

    return { level: isManager() ? 'MANAGER' : 'NORMAL', reason: 'Masuk batas kewenangan operasional' };
  }

  function installStyle() {
    if (q('#gmuCompanyOsStyle')) return;
    const style = document.createElement('style');
    style.id = 'gmuCompanyOsStyle';
    style.textContent = `
      .gmu-company-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
      .gmu-company-kpi{border:1px solid var(--line);border-radius:14px;padding:12px;background:#fff}
      .gmu-company-kpi b{display:block;font-size:15px;color:var(--gd)}
      .gmu-company-kpi small{font-size:8px;color:var(--muted)}
      .gmu-company-list{font-size:9px;line-height:1.65;color:var(--text)}
      .gmu-company-policy{background:#f4f8f6;border-radius:14px;padding:12px;font-size:9px;line-height:1.6}
      @media(max-width:760px){.gmu-company-grid{grid-template-columns:1fr}}
    `;
    document.head.appendChild(style);
  }

  function installPage() {
    if (q('#companyControl')) return;
    const content = q('.content');
    if (!content) return;
    const page = document.createElement('section');
    page.id = 'companyControl';
    page.className = 'page';
    page.innerHTML = `
      <div class="notice">Sistem Kendali GMU EduTrans ${VERSION} • Bahasa Indonesia • Direktur fokus pada hasil dan pengecualian strategis.</div>
      <div class="card section">
        <div class="head"><div><h3>Kendali Perusahaan</h3><p id="gmuCompanyMission"></p></div><span class="badge info">Cianjur • Sukabumi</span></div>
        <div class="gmu-company-grid">
          <div class="gmu-company-kpi"><small>Target laba bersih bulanan</small><b>${money(POLICY.targetNetProfitMonthly)}</b></div>
          <div class="gmu-company-kpi"><small>Target margin sehat</small><b>≥ ${POLICY.healthyMarginPct}%</b></div>
          <div class="gmu-company-kpi"><small>Transport Manager</small><b>${money(POLICY.managerTransportPerAttendance)}/hari hadir</b></div>
          <div class="gmu-company-kpi"><small>Utilitas kantor</small><b>${money(POLICY.officeUtilitiesMonthly)}/bulan</b></div>
        </div>
      </div>
      <div class="gmu-company-grid">
        <div class="card section" style="margin-top:0"><div class="head"><div><h3>Kewenangan Manager</h3><p>Batas tindakan tanpa persetujuan Direktur</p></div></div>
          <div class="gmu-company-list">• Biaya dalam RAB ≤ ${money(POLICY.managerPlannedRabLimit)}<br>• Biaya di luar RAB ≤ ${money(POLICY.managerUnplannedLimit)}<br>• Darurat saat trip ≤ ${money(POLICY.managerEmergencyLimit)}<br>• Diskon ≤ ${POLICY.managerMaxDiscountPct}%<br>• Margin 20–24,99%: tinjauan Manager<br>• Margin &lt;20%: persetujuan Direktur</div>
        </div>
        <div class="card section" style="margin-top:0"><div class="head"><div><h3>Pasar Prioritas</h3><p>Fokus tahap pertama</p></div></div>
          <div class="gmu-company-list">${POLICY.marketSegments.map(x => `• ${h(x)}`).join('<br>')}</div>
        </div>
      </div>
      <div class="card section"><div class="head"><div><h3>Biaya Teknologi & Infrastruktur Digital</h3><p>Wajib masuk overhead sebelum laba bersih dinyatakan tercapai</p></div></div>
        <div class="gmu-company-list">${POLICY.technologyCosts.map(x => `• ${h(x)}`).join('<br>')}</div>
      </div>
      <div class="gmu-company-policy"><b>Prinsip manajemen:</b> Direktur menentukan hasil → Manager mengelola bisnis → ERP mengatur proses → AI membantu analisis → Tim menjalankan tugas. Masalah normal berhenti di Manager; Direktur menerima pengecualian strategis/kritis.</div>
    `;
    content.appendChild(page);
    const mission = q('#gmuCompanyMission');
    if (mission) mission.textContent = roleMission();
  }

  function installNav() {
    if (q('#nav [data-page="companyControl"]')) return;
    const nav = q('#nav');
    if (!nav) return;
    const button = document.createElement('button');
    button.dataset.page = 'companyControl';
    button.innerHTML = '⌁ &nbsp; Kendali Perusahaan';
    nav.appendChild(button);
  }

  function correctOpsPolicyText() {
    const page = q('#opsAgent');
    if (!page) return;
    const policyText = [...page.querySelectorAll('.gmu-ops-policy')].find(el => /Transaksi di atas|approval Direktur|Batas Manager/i.test(el.textContent || ''));
    if (!policyText) return;
    const desired = 'Batas Manager: dalam RAB ≤ Rp1.000.000; di luar RAB ≤ Rp250.000; darurat trip ≤ Rp500.000; diskon ≤5%. Margin <20%, refund, perubahan harga strategis, rekening, jurnal dan keputusan sensitif wajib persetujuan Direktur.';
    if (policyText.textContent !== desired) policyText.textContent = desired;
  }

  function blockOutOfAuthorityOpsCommand(event) {
    const send = event.target?.closest?.('#gmuOpsSend');
    if (!send) return;
    const input = q('#gmuOpsCommand');
    const text = input?.value || '';
    const auth = authorityFor(text);
    if (auth.level !== 'DIRECTOR') return;

    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    const badge = q('#gmuOpsAuthority');
    const reply = q('#gmuOpsReply');
    if (badge) badge.textContent = 'Perlu Persetujuan Direktur';
    if (reply) {
      reply.dataset.tone = 'warn';
      reply.textContent = `${auth.reason}. Sistem tidak meneruskan tindakan ini sebagai kewenangan Manager.`;
    }
  }

  function blockOutOfAuthorityEnter(event) {
    if (event.key !== 'Enter' || event.target?.id !== 'gmuOpsCommand') return;
    const auth = authorityFor(event.target.value || '');
    if (auth.level !== 'DIRECTOR') return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    const badge = q('#gmuOpsAuthority');
    const reply = q('#gmuOpsReply');
    if (badge) badge.textContent = 'Perlu Persetujuan Direktur';
    if (reply) {
      reply.dataset.tone = 'warn';
      reply.textContent = `${auth.reason}. Sistem tidak meneruskan tindakan ini sebagai kewenangan Manager.`;
    }
  }

  function relabelCommonNavigation() {
    const labels = {
      dashboard: 'Ringkasan', booking: 'Pemesanan', customer: 'Pelanggan', finance: 'Keuangan',
      operation: 'Operasional', reports: 'Laporan', workflow: 'Persetujuan'
    };
    Object.entries(labels).forEach(([page, label]) => {
      const btn = q(`#nav [data-page="${page}"]`);
      if (!btn) return;
      const raw = (btn.textContent || '').trim();
      const icon = raw.split(/\s+/)[0] || '';
      const desired = `${icon}  ${label}`.trim();
      if (raw !== desired) btn.textContent = desired;
    });
  }

  function init() {
    installStyle();
    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      installPage();
      installNav();
      relabelCommonNavigation();
      correctOpsPolicyText();
    }, 250);

    document.addEventListener('click', blockOutOfAuthorityOpsCommand, true);
    document.addEventListener('keydown', blockOutOfAuthorityEnter, true);

    let queued = false;
    const observer = new MutationObserver(() => {
      if (queued) return;
      queued = true;
      requestAnimationFrame(() => {
        queued = false;
        correctOpsPolicyText();
        relabelCommonNavigation();
      });
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });

    window.GmuCompanyOperatingSystem = Object.freeze({
      version: VERSION,
      policy: POLICY,
      authorityFor,
      roleMission,
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();


/* ===== market-intelligence-v101.js ===== */
(() => {
  'use strict';

  const VERSION = 'v10.1-market-intelligence';
  const ALLOWED = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales','Admin','Operation']);
  const EDITOR = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales']);
  const REGIONS = ['Cianjur','Sukabumi'];
  const TYPES = ['Sekolah & Madrasah','Pesantren','Perguruan Tinggi','Instansi Pemerintah','Komunitas & Organisasi','Perusahaan / Corporate','Partner & Mitra'];
  const q = (s, root = document) => root.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  let rows = [];
  let region = 'Semua';

  function currentRole() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }

  function currentUserId() {
    try { return String(profile?.id || user?.id || ''); } catch (_) { return ''; }
  }

  function isAllowed() { return ALLOWED.has(currentRole()); }
  function canEdit() { return EDITOR.has(currentRole()); }

  function installStyle() {
    if (q('#gmuMarketStyle')) return;
    const style = document.createElement('style');
    style.id = 'gmuMarketStyle';
    style.textContent = `
      .gmu-market-toolbar{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
      .gmu-market-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:12px 0}
      .gmu-market-stat{background:#fff;border:1px solid var(--line);border-radius:14px;padding:12px}
      .gmu-market-stat small{display:block;color:var(--muted);font-size:8px}.gmu-market-stat b{font-size:17px;color:var(--gd)}
      .gmu-market-row{display:grid;grid-template-columns:2fr 1.2fr .8fr .8fr 1fr;gap:8px;align-items:center;padding:11px 0;border-bottom:1px solid var(--line);font-size:9px}
      .gmu-market-row:last-child{border-bottom:0}.gmu-market-score{font-weight:800;color:var(--g)}
      .gmu-market-empty{padding:18px;text-align:center;color:var(--muted);font-size:10px}
      .gmu-market-modal{position:fixed;inset:0;background:rgba(0,0,0,.36);z-index:9999;display:flex;align-items:center;justify-content:center;padding:18px}
      .gmu-market-dialog{background:#fff;border-radius:18px;width:min(560px,100%);max-height:90vh;overflow:auto;padding:18px;box-shadow:0 20px 60px rgba(0,0,0,.25)}
      .gmu-market-form{display:grid;grid-template-columns:1fr 1fr;gap:10px}.gmu-market-form label{font-size:8px;color:var(--muted)}
      .gmu-market-form input,.gmu-market-form select,.gmu-market-form textarea{width:100%;padding:9px;border:1px solid var(--line);border-radius:10px;font:inherit}
      .gmu-market-form .full{grid-column:1/-1}.gmu-market-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:14px}
      @media(max-width:760px){.gmu-market-grid{grid-template-columns:repeat(2,1fr)}.gmu-market-row{grid-template-columns:1fr}.gmu-market-form{grid-template-columns:1fr}.gmu-market-form .full{grid-column:auto}}
    `;
    document.head.appendChild(style);
  }

  function installPage() {
    if (!isAllowed() || q('#marketIntelligence')) return;
    const content = q('.content');
    const nav = q('#nav');
    if (!content || !nav) return;

    const page = document.createElement('section');
    page.id = 'marketIntelligence';
    page.className = 'page';
    page.innerHTML = `
      <div class="notice">Intelijen Pasar GMU EduTrans ${VERSION} • Fokus tahap pertama Cianjur & Sukabumi • data publik wajib diverifikasi.</div>
      <div class="card section">
        <div class="head"><div><h3>Intelijen Pasar</h3><p>Target calon klien → PIC Sales → tindak lanjut → penawaran → pemesanan</p></div></div>
        <div class="gmu-market-toolbar">
          <button class="btn secondary" id="gmuMarketAll">Semua</button>
          <button class="btn secondary" id="gmuMarketCianjur">Cianjur</button>
          <button class="btn secondary" id="gmuMarketSukabumi">Sukabumi</button>
          <button class="btn secondary" id="gmuMarketRefresh">Muat Ulang</button>
          ${canEdit() ? '<button class="btn" id="gmuMarketAdd">+ Tambah Target</button>' : ''}
        </div>
        <div id="gmuMarketStatus" style="margin-top:8px;font-size:9px;color:var(--muted)"></div>
        <div class="gmu-market-grid" id="gmuMarketStats"></div>
        <div id="gmuMarketList"></div>
      </div>
    `;
    content.appendChild(page);

    const button = document.createElement('button');
    button.dataset.page = 'marketIntelligence';
    button.innerHTML = '◎ &nbsp; Intelijen Pasar';
    button.addEventListener('click', () => showPage());
    nav.appendChild(button);

    q('#gmuMarketAll')?.addEventListener('click', () => { region = 'Semua'; render(); });
    q('#gmuMarketCianjur')?.addEventListener('click', () => { region = 'Cianjur'; render(); });
    q('#gmuMarketSukabumi')?.addEventListener('click', () => { region = 'Sukabumi'; render(); });
    q('#gmuMarketRefresh')?.addEventListener('click', load);
    q('#gmuMarketAdd')?.addEventListener('click', openAddDialog);
  }

  function showPage() {
    document.querySelectorAll('.page').forEach(el => el.classList.remove('active'));
    q('#marketIntelligence')?.classList.add('active');
    document.querySelectorAll('#nav [data-page]').forEach(el => el.classList.toggle('active', el.dataset.page === 'marketIntelligence'));
    const title = q('#title');
    if (title) title.textContent = 'Intelijen Pasar';
    load();
  }

  async function load() {
    const status = q('#gmuMarketStatus');
    if (!status) return;
    if (typeof sb === 'undefined' || !sb?.from) {
      status.textContent = 'Koneksi data belum tersedia.';
      return;
    }
    status.textContent = 'Memuat database target pasar…';
    try {
      const { data, error } = await sb.from('market_targets')
        .select('id,organization_name,organization_type,region,district,target_pic_role,potential_participants,program_fit,verification_status,potential_score,priority_grade,sales_status,assigned_sales,next_follow_up_at,estimated_revenue,estimated_profit,notes')
        .order('potential_score', { ascending: false })
        .limit(500);
      if (error) throw error;
      rows = Array.isArray(data) ? data : [];
      status.textContent = `${rows.length} target pasar termuat. Fokus aktif: Cianjur & Sukabumi.`;
      render();
    } catch (e) {
      rows = [];
      status.textContent = 'Database Intelijen Pasar belum diaktifkan pada backend ERP produksi. Modul UI sudah siap; migrasi tersedia di repository.';
      render();
    }
  }

  function filteredRows() {
    if (region === 'Semua') return rows.filter(x => REGIONS.includes(String(x.region || '')));
    return rows.filter(x => String(x.region || '') === region);
  }

  function render() {
    const list = filteredRows();
    const notContacted = list.filter(x => x.sales_status === 'BELUM DIHUBUNGI').length;
    const active = list.filter(x => ['TERTARIK','PELUANG POTENSIAL','PENAWARAN','NEGOSIASI','MENUNGGU DP'].includes(x.sales_status)).length;
    const won = list.filter(x => x.sales_status === 'BERHASIL').length;
    const stats = q('#gmuMarketStats');
    if (stats) stats.innerHTML = [
      ['Total Target', list.length], ['Belum Dihubungi', notContacted], ['Peluang Aktif', active], ['Berhasil', won]
    ].map(x => `<div class="gmu-market-stat"><small>${x[0]}</small><b>${x[1]}</b></div>`).join('');

    const root = q('#gmuMarketList');
    if (!root) return;
    if (!list.length) {
      root.innerHTML = '<div class="gmu-market-empty">Belum ada data target pasar pada wilayah ini.</div>';
      return;
    }
    root.innerHTML = list.map(x => `
      <div class="gmu-market-row">
        <div><b>${h(x.organization_name)}</b><br><small>${h(x.organization_type)} • ${h(x.region)}${x.district ? ' • ' + h(x.district) : ''}</small></div>
        <div>${h(x.target_pic_role || 'PIC belum ditentukan')}<br><small>${h((x.program_fit || []).join(', ') || 'Program belum dipetakan')}</small></div>
        <div class="gmu-market-score">${Number(x.potential_score || 0)}/100 • ${h(x.priority_grade || 'C')}</div>
        <div>${statusLabel(x.sales_status)}</div>
        <div>${money(x.estimated_revenue)}<br><small>${h(x.verification_status || 'PERLU VERIFIKASI')}</small></div>
      </div>
    `).join('');
  }

  function statusLabel(value) {
    return ({
      'BELUM DIHUBUNGI':'Belum Dihubungi','SUDAH DIHUBUNGI':'Sudah Dihubungi','TERTARIK':'Tertarik',
      'PELUANG POTENSIAL':'Peluang Potensial','PENAWARAN':'Penawaran','NEGOSIASI':'Negosiasi',
      'MENUNGGU DP':'Menunggu DP','BERHASIL':'Berhasil','TIDAK BERHASIL':'Tidak Berhasil',
      'PEMELIHARAAN HUBUNGAN':'Pemeliharaan Hubungan'
    })[value] || h(value || '-');
  }

  function openAddDialog() {
    if (!canEdit() || q('#gmuMarketModal')) return;
    const modal = document.createElement('div');
    modal.className = 'gmu-market-modal';
    modal.id = 'gmuMarketModal';
    modal.innerHTML = `
      <div class="gmu-market-dialog">
        <h3>Tambah Target Pasar</h3>
        <p style="font-size:9px;color:var(--muted)">Gunakan informasi organisasi yang relevan untuk kepentingan bisnis. Data hasil riset yang belum dipastikan tetap berstatus Perlu Verifikasi.</p>
        <div class="gmu-market-form">
          <label class="full">Nama organisasi<input id="gmuMtName" placeholder="Nama sekolah / instansi / perusahaan"></label>
          <label>Kategori<select id="gmuMtType">${TYPES.map(x => `<option>${h(x)}</option>`).join('')}</select></label>
          <label>Wilayah<select id="gmuMtRegion">${REGIONS.map(x => `<option>${x}</option>`).join('')}</select></label>
          <label>Kecamatan<input id="gmuMtDistrict" placeholder="Kecamatan"></label>
          <label>Target PIC/Jabatan<input id="gmuMtPic" placeholder="Kesiswaan / Humas / HR / GA / CSR"></label>
          <label>Skor Potensi 0–100<input id="gmuMtScore" type="number" min="0" max="100" value="50"></label>
          <label>Potensi Peserta<input id="gmuMtPax" type="number" min="0" value="0"></label>
          <label>Potensi Omzet<input id="gmuMtRevenue" type="number" min="0" value="0"></label>
          <label class="full">Program yang cocok<input id="gmuMtPrograms" placeholder="Pisahkan dengan koma"></label>
          <label class="full">Catatan<textarea id="gmuMtNotes" rows="3"></textarea></label>
        </div>
        <div id="gmuMtError" style="font-size:9px;color:#a33;margin-top:8px"></div>
        <div class="gmu-market-actions"><button class="btn secondary" id="gmuMtCancel">Batal</button><button class="btn" id="gmuMtSave">Simpan Target</button></div>
      </div>
    `;
    document.body.appendChild(modal);
    q('#gmuMtCancel')?.addEventListener('click', () => modal.remove());
    q('#gmuMtSave')?.addEventListener('click', saveNewTarget);
  }

  async function saveNewTarget() {
    const name = q('#gmuMtName')?.value?.trim();
    const errorEl = q('#gmuMtError');
    if (!name) { if (errorEl) errorEl.textContent = 'Nama organisasi wajib diisi.'; return; }
    if (typeof sb === 'undefined' || !sb?.from) { if (errorEl) errorEl.textContent = 'Koneksi data belum tersedia.'; return; }
    const score = Math.max(0, Math.min(100, Number(q('#gmuMtScore')?.value || 0)));
    const payload = {
      organization_name: name,
      organization_type: q('#gmuMtType')?.value,
      region: q('#gmuMtRegion')?.value,
      district: q('#gmuMtDistrict')?.value?.trim() || null,
      target_pic_role: q('#gmuMtPic')?.value?.trim() || null,
      potential_score: score,
      priority_grade: score >= 80 ? 'A' : score >= 60 ? 'B' : 'C',
      potential_participants: Number(q('#gmuMtPax')?.value || 0),
      estimated_revenue: Number(q('#gmuMtRevenue')?.value || 0),
      program_fit: String(q('#gmuMtPrograms')?.value || '').split(',').map(x => x.trim()).filter(Boolean),
      notes: q('#gmuMtNotes')?.value?.trim() || null,
      verification_status: 'PERLU VERIFIKASI',
      sales_status: 'BELUM DIHUBUNGI',
      created_by: currentUserId() || null
    };
    const { error } = await sb.from('market_targets').insert(payload);
    if (error) { if (errorEl) errorEl.textContent = error.message || 'Target gagal disimpan.'; return; }
    q('#gmuMarketModal')?.remove();
    await load();
  }

  function init() {
    installStyle();
    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      installPage();
    }, 250);
    window.GmuMarketIntelligence = Object.freeze({ version: VERSION, load });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();


/* ===== sales-target-engine-v103.js ===== */
(() => {
  'use strict';

  const VERSION = 'v10.3.1-sales-target-engine';
  const ALLOWED = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales']);
  const TARGET = Object.freeze({
    netProfitMonthly: 15_000_000,
    prospectsMonthly: 200,
    qualifiedLeadsMonthly: 20,
    quotationsMonthly: 12,
    minimumBookingsMonthly: 3,
    idealBookingsMonthly: '4–6',
    quotationToBookingPct: 25,
    prospectsDaily: 10,
    followUpsDaily: 10,
    prospectsWeekly: 50,
    qualifiedLeadsWeekly: 5,
    quotationsWeekly: 3,
    healthyMarginPct: 25,
  });

  const STATION_TARGET_DEFAULT = Object.freeze({
    programKey: 'EDU_STATION',
    programName: 'Edukasi Stasiun',
    periodMonth: '2026-09-01',
    periodLabel: 'September 2026',
    pricePerPax: 46_000,
    bepPax: 60,
    productivePax: 200,
    targetPax: 400,
    nextTargetPax: 600,
    salesRetainer: 600_000,
    salesFeePerPax: 2_500,
    targetBonus: 250_000,
    contributionTarget: 8_000_000,
    contributionMarginPct: 43.5,
  });

  let stationTarget = { ...STATION_TARGET_DEFAULT };

  const q = (s, root = document) => root.querySelector(s);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  const integer = v => Number(v || 0).toLocaleString('id-ID');

  function role() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }
  function allowed() { return ALLOWED.has(role()); }
  function isDirector() { return ['Owner','Director','Direktur'].includes(role()); }
  function isManager() { return ['Manager','Manager EduTrans'].includes(role()); }
  function isSales() { return role() === 'Sales'; }

  function missionText() {
    if (isDirector()) return 'Direktur menetapkan hasil. Manager bertanggung jawab mengubah target perusahaan menjadi target penjualan dan tindakan tim.';
    if (isManager()) return 'Manager bertanggung jawab menjaga funnel, konversi, booking, margin, dan rencana pemulihan bila forecast di bawah target.';
    if (isSales()) return 'Sales fokus pada aktivitas hari ini: calon pelanggan, tindak lanjut, penawaran, negosiasi, paid pax, dan booking sehat.';
    return 'Target penjualan mengikuti sasaran perusahaan dan kewenangan role.';
  }

  function stationComputed() {
    const targetRevenue = Number(stationTarget.targetPax || 0) * Number(stationTarget.pricePerPax || 0);
    const feeAtTarget = Number(stationTarget.targetPax || 0) * Number(stationTarget.salesFeePerPax || 0);
    const salesIncomeAtTarget = Number(stationTarget.salesRetainer || 0) + feeAtTarget + Number(stationTarget.targetBonus || 0);
    return { targetRevenue, feeAtTarget, salesIncomeAtTarget };
  }

  function installStyle() {
    if (q('#gmuSalesTargetStyle')) return;
    const style = document.createElement('style');
    style.id = 'gmuSalesTargetStyle';
    style.textContent = `
      .gmu-target-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}
      .gmu-target-card{border:1px solid var(--line);background:#fff;border-radius:14px;padding:12px}
      .gmu-target-card small{display:block;font-size:8px;color:var(--muted);margin-bottom:4px}
      .gmu-target-card b{display:block;font-size:17px;color:var(--gd)}
      .gmu-target-card span{display:block;font-size:8px;color:var(--muted);margin-top:4px;line-height:1.45}
      .gmu-target-flow{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8px}
      .gmu-target-flow div{border:1px solid var(--line);border-radius:12px;padding:10px;background:#f8fbf9;text-align:center}
      .gmu-target-flow b{display:block;color:var(--g);font-size:16px}.gmu-target-flow small{font-size:8px;color:var(--muted)}
      .gmu-target-note{font-size:9px;line-height:1.65;background:#f4f8f6;border-radius:14px;padding:12px}
      .gmu-target-actions{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}
      .gmu-target-actions .gmu-target-card b{font-size:14px}
      .gmu-station-target{border:1px solid #cfe3d7;background:#f7fbf8}
      .gmu-station-levels{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin-top:10px}
      .gmu-station-level{border:1px solid var(--line);border-radius:12px;background:#fff;padding:11px}
      .gmu-station-level small{display:block;font-size:8px;color:var(--muted);margin-bottom:4px}
      .gmu-station-level b{display:block;font-size:16px;color:var(--gd)}
      .gmu-station-level span{display:block;font-size:8px;color:var(--muted);margin-top:4px;line-height:1.45}
      .gmu-station-level.current{border-color:#93c7a7;background:#f2faf5}
      @media(max-width:920px){.gmu-target-grid{grid-template-columns:repeat(2,1fr)}.gmu-target-flow{grid-template-columns:1fr 1fr}.gmu-target-actions{grid-template-columns:1fr}.gmu-station-levels{grid-template-columns:repeat(2,1fr)}}
      @media(max-width:520px){.gmu-target-grid{grid-template-columns:1fr}.gmu-target-flow{grid-template-columns:1fr}.gmu-station-levels{grid-template-columns:1fr}}
    `;
    document.head.appendChild(style);
  }

  function stationSectionHtml() {
    const calc = stationComputed();
    return `
      <div class="card section gmu-station-target" id="gmuStationTargetSection">
        <div class="head">
          <div>
            <h3>Target Program • ${stationTarget.programName}</h3>
            <p>${stationTarget.periodLabel} • target dihitung dari paid pax, bukan quotation.</p>
          </div>
          <span class="badge ok">TARGET AKTIF</span>
        </div>
        <div class="gmu-target-grid">
          <div class="gmu-target-card"><small>Harga jual / pax</small><b>${money(stationTarget.pricePerPax)}</b><span>Harga resmi program; diskon wajib approval.</span></div>
          <div class="gmu-target-card"><small>Target bulan ini</small><b>${integer(stationTarget.targetPax)} paid pax</b><span>Target omzet ${money(calc.targetRevenue)}.</span></div>
          <div class="gmu-target-card"><small>Fee Sales / paid pax</small><b>${money(stationTarget.salesFeePerPax)}</b><span>= Rp50.000 per 20 pax = Rp100.000 per 40 pax.</span></div>
          <div class="gmu-target-card"><small>Penghasilan Sales di target</small><b>${money(calc.salesIncomeAtTarget)}</b><span>Retainer ${money(stationTarget.salesRetainer)} + fee ${money(calc.feeAtTarget)} + bonus ${money(stationTarget.targetBonus)}.</span></div>
          <div class="gmu-target-card"><small>Kontribusi program ke PT</small><b>${money(stationTarget.contributionTarget)}</b><span>Baseline target 400 pax; belum disebut laba bersih PT sebelum overhead korporat.</span></div>
          <div class="gmu-target-card"><small>Contribution margin baseline</small><b>± ${Number(stationTarget.contributionMarginPct || 0).toLocaleString('id-ID')}%</b><span>Harus tetap dijaga; bonus tidak boleh merusak margin.</span></div>
        </div>
        <div class="gmu-station-levels">
          <div class="gmu-station-level"><small>BEP TARGET</small><b>${integer(stationTarget.bepPax)} pax</b><span>Batas minimum untuk menutup retainer Sales.</span></div>
          <div class="gmu-station-level"><small>MINIMUM PRODUKTIF</small><b>${integer(stationTarget.productivePax)} pax</b><span>Sales mulai memberi kontribusi produktif.</span></div>
          <div class="gmu-station-level current"><small>TARGET BULAN INI</small><b>${integer(stationTarget.targetPax)} pax</b><span>Target resmi ${stationTarget.periodLabel}.</span></div>
          <div class="gmu-station-level"><small>TARGET NORMAL BERIKUTNYA</small><b>${integer(stationTarget.nextTargetPax)} pax</b><span>Naik setelah target 400 pax stabil dan margin aman.</span></div>
        </div>
        <div class="gmu-target-note" style="margin-top:10px">
          <b>Guardrail:</b> fee Sales hanya dari <b>paid pax</b>. Bonus target hanya cair jika pembayaran masuk, harga tidak didiskon tanpa approval, dan margin program tetap memenuhi kebijakan perusahaan.
        </div>
      </div>`;
  }

  function refreshStationSection() {
    const current = q('#gmuStationTargetSection');
    if (!current) return;
    const wrap = document.createElement('div');
    wrap.innerHTML = stationSectionHtml().trim();
    const next = wrap.firstElementChild;
    if (next) current.replaceWith(next);
  }

  async function loadStationTarget() {
    if (typeof sb === 'undefined' || !sb) return;
    try {
      const response = await sb
        .from('program_sales_targets')
        .select('program_key,program_name,period_month,price_per_pax,bep_pax,productive_pax,target_pax,next_target_pax,sales_retainer,sales_fee_per_pax,target_bonus,contribution_target,contribution_margin_pct')
        .eq('program_key', STATION_TARGET_DEFAULT.programKey)
        .eq('period_month', STATION_TARGET_DEFAULT.periodMonth)
        .eq('status', 'ACTIVE')
        .maybeSingle();
      if (response.error || !response.data) return;
      const row = response.data;
      stationTarget = {
        ...stationTarget,
        programKey: row.program_key || stationTarget.programKey,
        programName: row.program_name || stationTarget.programName,
        periodMonth: row.period_month || stationTarget.periodMonth,
        pricePerPax: Number(row.price_per_pax || stationTarget.pricePerPax),
        bepPax: Number(row.bep_pax || stationTarget.bepPax),
        productivePax: Number(row.productive_pax || stationTarget.productivePax),
        targetPax: Number(row.target_pax || stationTarget.targetPax),
        nextTargetPax: Number(row.next_target_pax || stationTarget.nextTargetPax),
        salesRetainer: Number(row.sales_retainer || stationTarget.salesRetainer),
        salesFeePerPax: Number(row.sales_fee_per_pax || stationTarget.salesFeePerPax),
        targetBonus: Number(row.target_bonus || stationTarget.targetBonus),
        contributionTarget: Number(row.contribution_target || stationTarget.contributionTarget),
        contributionMarginPct: Number(row.contribution_margin_pct || stationTarget.contributionMarginPct),
      };
      refreshStationSection();
    } catch (_) {
      // Fallback constants keep targeting visible when the migration has not been deployed yet.
    }
  }

  function installPage() {
    if (!allowed() || q('#salesTargetControl')) return;
    const content = q('.content');
    const nav = q('#nav');
    if (!content || !nav) return;

    const page = document.createElement('section');
    page.id = 'salesTargetControl';
    page.className = 'page';
    page.innerHTML = `
      <div class="notice">Target & Kinerja Penjualan ${VERSION} • Sasaran perusahaan diturunkan menjadi target tim, program, dan aktivitas harian Sales.</div>

      ${stationSectionHtml()}

      <div class="card section">
        <div class="head"><div><h3>Target Perusahaan → Target Penjualan</h3><p>${missionText()}</p></div><span class="badge info">Cianjur • Sukabumi</span></div>
        <div class="gmu-target-grid">
          <div class="gmu-target-card"><small>Target laba bersih perusahaan</small><b>${money(TARGET.netProfitMonthly)}</b><span>Sesudah seluruh HPP, SDM, komisi, overhead, utilitas, dan teknologi.</span></div>
          <div class="gmu-target-card"><small>Target prospek / bulan</small><b>${TARGET.prospectsMonthly}</b><span>Baseline awal sampai target dinamis dari unit economics aktif.</span></div>
          <div class="gmu-target-card"><small>Peluang potensial / bulan</small><b>${TARGET.qualifiedLeadsMonthly}</b><span>Target minimum lead yang sudah layak ditindaklanjuti serius.</span></div>
          <div class="gmu-target-card"><small>Penawaran harga / bulan</small><b>${TARGET.quotationsMonthly}</b><span>Penawaran hanya dari Master Program dan harus menjaga margin.</span></div>
          <div class="gmu-target-card"><small>Booking minimum / bulan</small><b>${TARGET.minimumBookingsMonthly}</b><span>Target awal; sasaran ideal ${TARGET.idealBookingsMonthly} booking/bulan.</span></div>
          <div class="gmu-target-card"><small>Konversi penawaran → booking</small><b>≥ ${TARGET.quotationToBookingPct}%</b><span>Jika turun, Manager wajib membuat rencana pemulihan.</span></div>
          <div class="gmu-target-card"><small>Margin kegiatan sehat</small><b>≥ ${TARGET.healthyMarginPct}%</b><span>Penjualan yang menambah omzet tetapi merusak margin bukan pertumbuhan sehat.</span></div>
          <div class="gmu-target-card"><small>Wilayah aktif</small><b>Cianjur + Sukabumi</b><span>Tidak ekspansi luas sebelum pasar inti, profit, cash, dan operasi stabil.</span></div>
        </div>
      </div>

      <div class="card section">
        <div class="head"><div><h3>Funnel Target Bulanan</h3><p>Baseline operasional yang wajib terlihat oleh Manager dan Sales.</p></div></div>
        <div class="gmu-target-flow">
          <div><small>Calon Pelanggan</small><b>${TARGET.prospectsMonthly}</b></div>
          <div><small>Peluang Potensial</small><b>${TARGET.qualifiedLeadsMonthly}</b></div>
          <div><small>Penawaran</small><b>${TARGET.quotationsMonthly}</b></div>
          <div><small>Booking Minimum</small><b>${TARGET.minimumBookingsMonthly}</b></div>
          <div><small>Booking Ideal</small><b>${TARGET.idealBookingsMonthly}</b></div>
        </div>
      </div>

      <div class="card section">
        <div class="head"><div><h3>${isSales() ? 'Target Saya' : 'Target Aktivitas Sales'}</h3><p>Dibuat sederhana agar Sales tahu persis apa yang harus dikerjakan hari ini.</p></div></div>
        <div class="gmu-target-actions">
          <div class="gmu-target-card"><small>Harian</small><b>${TARGET.prospectsDaily} prospek baru</b><span>${TARGET.followUpsDaily} tindak lanjut per hari.</span></div>
          <div class="gmu-target-card"><small>Mingguan</small><b>${TARGET.prospectsWeekly} prospek</b><span>${TARGET.qualifiedLeadsWeekly} peluang potensial + ${TARGET.quotationsWeekly} penawaran.</span></div>
          <div class="gmu-target-card"><small>Bulanan Program</small><b>${integer(stationTarget.targetPax)} paid pax</b><span>BEP ${integer(stationTarget.bepPax)} • minimum produktif ${integer(stationTarget.productivePax)} • next ${integer(stationTarget.nextTargetPax)} pax.</span></div>
        </div>
      </div>

      <div class="gmu-target-note">
        <b>Aturan sistem:</b> target program Edukasi Stasiun berjalan bersama baseline target perusahaan. Mesin target tetap menghitung <b>Target Laba Bersih → Required Contribution → Required Revenue → Required Booking/Pax → Product Mix → Target Tim → Target Sales → Aktivitas Harian</b>. Omzet tanpa cash-in dan margin sehat tidak dianggap pencapaian penuh.
      </div>
    `;
    content.appendChild(page);

    const button = document.createElement('button');
    button.dataset.page = 'salesTargetControl';
    button.innerHTML = '◎ &nbsp; Target & Kinerja';
    button.addEventListener('click', showPage);

    const marketButton = q('#nav [data-page="marketIntelligence"]');
    if (marketButton) nav.insertBefore(button, marketButton);
    else nav.appendChild(button);

    loadStationTarget();
  }

  function showPage() {
    document.querySelectorAll('.page').forEach(el => el.classList.remove('active'));
    q('#salesTargetControl')?.classList.add('active');
    document.querySelectorAll('#nav [data-page]').forEach(el => el.classList.toggle('active', el.dataset.page === 'salesTargetControl'));
    const title = q('#title');
    if (title) title.textContent = isSales() ? 'Target Saya' : 'Target & Kinerja Penjualan';
  }

  function init() {
    installStyle();
    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      installPage();
    }, 250);
    window.GmuSalesTargetEngine = Object.freeze({ version: VERSION, target: TARGET, stationTarget: STATION_TARGET_DEFAULT, showPage });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
