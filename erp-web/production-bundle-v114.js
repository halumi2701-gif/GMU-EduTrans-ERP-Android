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

/* ===== station-target-v216.js ===== */
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

/* ===== sales-target-v217.js ===== */
(() => {
  'use strict';

  const VERSION = 'v22.2-sales-portfolio-target-safe';
  const ALLOWED = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales']);
  const FALLBACK = Object.freeze({
    paidPaxMonthly: 400,
    bepPax: 60,
    productivePax: 100,
    stretchPax: 500,
    outstandingPax: 600,
    leadsMonthly: 100,
    followupsMonthly: 200,
    quotationsMonthly: 20,
    bookingsMonthly: 4,
    salesRetainer: 600_000,
    salesFeePerPaidPax: 5_000,
    targetBonus: 250_000,
  });

  const state = { summary: null, programs: [], loading: false, error: null };
  const q = (s, root = document) => root.querySelector(s);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  const integer = v => Number(v || 0).toLocaleString('id-ID');
  const pct = v => Number(v || 0).toLocaleString('id-ID', { maximumFractionDigits: 2 });

  function role(){ try { return String(profile?.role || ''); } catch (_) { return ''; } }
  function allowed(){ return ALLOWED.has(role()); }
  function isSales(){ return role() === 'Sales'; }
  function db(){ try { return typeof sb !== 'undefined' && sb?.rpc ? sb : null; } catch (_) { return null; } }

  function periodMonth(){
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-01`;
  }
  function periodLabel(){ return new Date(periodMonth()+'T00:00:00').toLocaleDateString('id-ID',{month:'long',year:'numeric'}); }

  function normalized(){
    const r=state.summary||{};
    const target=Number(r.target_paid_pax||FALLBACK.paidPaxMonthly);
    const paidPax=Number(r.paid_pax||0);
    const feePerPax=Number(r.sales_fee_per_paid_pax||FALLBACK.salesFeePerPaidPax);
    const retainer=Number(r.sales_retainer||FALLBACK.salesRetainer);
    const targetBonus=Number(r.target_bonus_earned??(paidPax>=target?FALLBACK.targetBonus:0));
    return {
      target, paidPax,
      paidBookings:Number(r.paid_bookings||0),
      cashIn:Number(r.cash_in||0), bookedValue:Number(r.booked_value||0),
      bep:Number(r.bep_paid_pax||FALLBACK.bepPax), productive:Number(r.productive_paid_pax||FALLBACK.productivePax),
      stretch:Number(r.stretch_paid_pax||FALLBACK.stretchPax), outstanding:Number(r.outstanding_paid_pax||FALLBACK.outstandingPax),
      achievement:r.achievement_pct==null?(target?paidPax/target*100:0):Number(r.achievement_pct),
      above:Math.max(paidPax-target,0),
      level:String(r.achievement_level||(paidPax>=target?'TARGET_TERCAPAI':'PROSES')),
      retainer, feePerPax,
      variableFee:Number(r.variable_sales_fee??paidPax*feePerPax),
      targetBonus,
      modeledIncome:Number(r.modeled_sales_income??(retainer+paidPax*feePerPax+targetBonus)),
      canViewFinance:r.can_view_finance===true && !isSales(),
    };
  }

  function statusLabel(level){
    return ({DI_BAWAH_BEP:'DI BAWAH BEP',BEP:'BEP',PRODUKTIF:'PRODUKTIF',TARGET_TERCAPAI:'TARGET TERCAPAI',STRETCH:'STRETCH',OUTSTANDING:'OUTSTANDING'})[level]||level.replaceAll('_',' ');
  }

  function programBreakdown(canViewFinance){
    if(!state.programs.length) return '<div class="gmu-target-note">Belum ada paid pax bulan ini. Booking baru masuk target setelah ada pembayaran positif terverifikasi.</div>';
    return `<div class="gmu-target-flow">${state.programs.slice(0,8).map(x=>`
      <div><small>${String(x.program_name||'Program')}</small><b>${integer(x.paid_pax)} pax</b><small>${integer(x.paid_bookings)} booking${canViewFinance?' • '+money(x.booked_value):''}</small></div>`).join('')}</div>`;
  }

  function render(){
    if(!allowed()) return;
    const anchor=q('#gmuStationTargetSection')||q('#salesTargetControl')||q('.content');
    if(!anchor) return;
    let section=q('#gmuSales200Target');
    if(!section){
      section=document.createElement('div');section.id='gmuSales200Target';section.className='card section';
      if(anchor.id==='gmuStationTargetSection') anchor.insertAdjacentElement('afterend',section);
      else if(anchor.id==='salesTargetControl') anchor.prepend(section); else anchor.appendChild(section);
    }
    const s=normalized();
    const financialCards=s.canViewFinance?`
      <div class="gmu-target-card"><small>Cash-in terverifikasi</small><b>${money(s.cashIn)}</b><span>Pembayaran terverifikasi pada bulan berjalan.</span></div>
      <div class="gmu-target-card"><small>Nilai booking paid-pax</small><b>${money(s.bookedValue)}</b><span>Nilai booking yang pertama kali menjadi paid pada bulan ini.</span></div>`:'';
    const scopeText=isSales()?'Target saya • paid pax dari booking yang menjadi tanggung jawab Sales ini.':'Target tim • gabungan paid pax seluruh program GMU EduTrans.';
    const errorNote=state.error?`<div class="gmu-target-note" style="margin-top:10px;color:var(--bad)">Data otomatis belum dapat dimuat: ${String(state.error)}</div>`:'';

    section.innerHTML=`
      <div class="head"><div><h3>Target Sales GMU EduTrans • Seluruh Program</h3><p>${periodLabel()} • ${scopeText} Target utama perusahaan 400 pax/bulan lintas seluruh program.</p></div><span class="badge ${s.paidPax>=s.target?'ok':'info'}">${statusLabel(s.level)}</span></div>
      <div class="gmu-target-grid">
        <div class="gmu-target-card"><small>Target paid pax / bulan</small><b>${integer(s.target)} pax</b><span>Lintas Edukasi Kereta, Stasiun, Pandanwangi, Membatik, Factory Visit dan Custom EduTrip.</span></div>
        <div class="gmu-target-card"><small>Aktual paid pax otomatis</small><b>${integer(s.paidPax)} pax</b><span>${integer(s.paidBookings)} booking memiliki pembayaran positif terverifikasi.</span></div>
        <div class="gmu-target-card"><small>Achievement</small><b>${pct(s.achievement)}%</b><span>${s.above>0?`Lebih target +${integer(s.above)} pax.`:`Kurang ${integer(Math.max(s.target-s.paidPax,0))} pax menuju target.`}</span></div>
        ${financialCards}
        <div class="gmu-target-card"><small>Fee Sales aktual</small><b>${money(s.variableFee)}</b><span>${money(s.feePerPax)} × ${integer(s.paidPax)} paid pax.</span></div>
        <div class="gmu-target-card"><small>Bonus target</small><b>${money(s.targetBonus)}</b><span>Aktif otomatis saat paid pax ≥ ${integer(s.target)}.</span></div>
        <div class="gmu-target-card"><small>Penghasilan Sales</small><b>${money(s.modeledIncome)}</b><span>Retainer ${money(s.retainer)} + fee paid pax + bonus bila target tercapai.</span></div>
      </div>
      <div class="gmu-station-levels" style="margin-top:10px">
        <div class="gmu-station-level"><small>BEP</small><b>${integer(s.bep)} pax</b></div>
        <div class="gmu-station-level"><small>PRODUKTIF</small><b>${integer(s.productive)} pax</b></div>
        <div class="gmu-station-level current"><small>TARGET UTAMA</small><b>${integer(s.target)} pax</b></div>
        <div class="gmu-station-level"><small>STRETCH</small><b>${integer(s.stretch)} pax</b></div>
        <div class="gmu-station-level"><small>OUTSTANDING</small><b>${integer(s.outstanding)} pax</b></div>
      </div>
      <div style="margin-top:12px"><div class="head"><div><h3 style="font-size:13px">Breakdown Paid Pax per Program</h3><p>Semua program dijumlahkan ke target yang sama.</p></div><span class="badge neutral">AUTO SUM</span></div>${programBreakdown(s.canViewFinance)}</div>
      <div class="gmu-target-note" style="margin-top:10px"><b>Mesin hitung:</b> booking tetap terpisah per program. Saat pembayaran positif diverifikasi, seluruh pax booking masuk sebagai <b>paid pax satu kali</b> pada bulan pembayaran pertama. DP + pelunasan tidak menggandakan pax. Contoh 38 + 37 + 20 + 60 + 99 + 27 + 20 = <b>301 pax</b> → achievement <b>150,5%</b>.</div>
      ${isSales()?'<div class="gmu-target-note" style="margin-top:8px">Privasi: role Sales tidak menerima omzet, cash-in, HPP, laba, atau margin perusahaan dari mesin ini.</div>':''}
      ${errorNote}`;
  }

  async function load(){
    if(!allowed()||!db()||state.loading)return;
    state.loading=true;state.error=null;
    try{
      const month=periodMonth();
      const [summaryResponse,programResponse]=await Promise.all([
        db().rpc('gmu_sales_portfolio_summary',{p_period_month:month}),
        db().rpc('gmu_sales_portfolio_program_breakdown',{p_period_month:month}),
      ]);
      if(summaryResponse.error)throw summaryResponse.error;if(programResponse.error)throw programResponse.error;
      const summaryData=Array.isArray(summaryResponse.data)?summaryResponse.data[0]:summaryResponse.data;
      state.summary=summaryData||null;
      state.programs=Array.isArray(programResponse.data)?programResponse.data:[];
    }catch(e){state.error=e?.message||String(e);}finally{state.loading=false;render();}
  }

  function init(){
    const timer=setInterval(()=>{if(typeof profile==='undefined'||!profile)return;clearInterval(timer);if(!allowed())return;render();load();const observer=new MutationObserver(()=>{if(!q('#gmuSales200Target'))render();});observer.observe(document.body,{childList:true,subtree:true});setInterval(load,60_000);},250);
    window.GmuSalesTargetV217=Object.freeze({version:VERSION,refresh:load,target:FALLBACK});
  }

  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();


/* ===== sales-funnel-forecast-v225.js ===== */
(() => {
  'use strict';

  const VERSION = 'v22.5-sales-funnel-forecast';
  const ROLES = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales']);
  const CFG = Object.freeze({
    targetPax: 400,
    bepPax: 60,
    productivePax: 200,
    stretchPax: 600,
    outstandingPax: 800,
    avgPaxPerSchool: 40,
    leadsMonthly: 200,
    contactedMonthly: 160,
    pipelineMultiple: 3,
    leadToContacted: 80,
    contactedToQualified: 40,
    qualifiedToQuotation: 60,
    quotationToClose: 30,
    retainer: 600000,
    feePerPaidPax: 2500,
  });
  const state = { summary:null, controls:[], requests:[], loading:false, error:null };
  const q = (s,r=document) => r.querySelector(s);
  const n = v => Number(v || 0);
  const int = v => n(v).toLocaleString('id-ID');
  const pct = v => n(v).toLocaleString('id-ID',{maximumFractionDigits:1});
  const money = v => 'Rp ' + n(v).toLocaleString('id-ID');
  const safe = v => String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

  function role(){ try{return String(profile?.role||'');}catch(_){return '';} }
  function userId(){ try{return String(profile?.id||'');}catch(_){return '';} }
  function allowed(){ return ROLES.has(role()); }
  function sales(){ return role()==='Sales'; }
  function db(){ try{return typeof sb!=='undefined'&&sb?.rpc?sb:null;}catch(_){return null;} }
  function monthStart(){ const d=new Date(); return new Date(d.getFullYear(),d.getMonth(),1); }
  function nextMonth(){ const d=monthStart(); return new Date(d.getFullYear(),d.getMonth()+1,1); }
  function monthKey(){ const d=monthStart(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-01`; }
  function monthLabel(){ return monthStart().toLocaleDateString('id-ID',{month:'long',year:'numeric'}); }
  function within(v,a,b){ if(!v)return false; const d=new Date(v); return !Number.isNaN(d.getTime())&&d>=a&&d<b; }

  const RANK={NEW:0,CONTACTED:1,NURTURE:1,QUALIFIED:2,QUOTATION:3,NEGOTIATION:4,WAITING_DP:5,WON:6,LOST:-1};
  const rank = s => RANK[String(s||'').toUpperCase()] ?? 0;

  function summary(){
    const r=state.summary||{};
    const target=Math.max(CFG.targetPax,n(r.target_paid_pax));
    const paid=n(r.paid_pax);
    const fee=n(r.sales_fee_per_paid_pax)||CFG.feePerPaidPax;
    const retainer=n(r.sales_retainer)||CFG.retainer;
    const approved=n(r.approved_bonus||r.target_bonus_approved);
    return {
      target, paid, paidBookings:n(r.paid_bookings), fee, retainer, approved,
      income:retainer+paid*fee+approved,
      achievement:target?paid/target*100:0,
      finance:r.can_view_finance===true&&!sales(),
      cashIn:n(r.cash_in), bookedValue:n(r.booked_value),
    };
  }

  function metrics(s){
    const start=monthStart(), next=nextMonth(), now=new Date();
    const reqMap=new Map(state.requests.map(x=>[String(x.id||''),x]));
    const monthReq=state.requests.filter(x=>within(x.created_at,start,next));
    const monthIds=new Set(monthReq.map(x=>String(x.id||'')));
    const monthCtl=state.controls.filter(x=>monthIds.has(String(x.booking_request_id||'')));
    const wonMonth=state.controls.filter(x=>String(x.stage||'').toUpperCase()==='WON'&&within(x.won_at,start,next));
    const active=state.controls.filter(x=>!['WON','LOST'].includes(String(x.stage||'').toUpperCase()));
    const contacted=monthCtl.filter(x=>rank(x.stage)>=1).length;
    const qualified=monthCtl.filter(x=>rank(x.stage)>=2).length;
    const quotes=monthCtl.filter(x=>rank(x.stage)>=3).length;
    const wonCohort=monthCtl.filter(x=>String(x.stage||'').toUpperCase()==='WON').length;
    let openPax=0, weighted=0, hotPax=0, due=0;
    active.forEach(x=>{
      const pax=n(reqMap.get(String(x.booking_request_id||''))?.pax);
      const prob=Math.max(0,Math.min(100,n(x.probability_pct)));
      openPax+=pax; weighted+=pax*prob/100;
      if(['NEGOTIATION','WAITING_DP'].includes(String(x.stage||'').toUpperCase()))hotPax+=pax;
      if(x.next_follow_up_at&&new Date(x.next_follow_up_at)<=now)due++;
    });
    const wonPax=wonMonth.reduce((a,x)=>a+n(reqMap.get(String(x.booking_request_id||''))?.pax),0);
    const avgWon=wonMonth.length?wonPax/wonMonth.length:CFG.avgPaxPerSchool;
    const totalDays=new Date(next.getFullYear(),next.getMonth(),0).getDate();
    const day=Math.max(1,Math.min(totalDays,now.getDate()));
    const left=Math.max(totalDays-day,0);
    const remain=Math.max(s.target-s.paid,0);
    const paceToday=Math.round(s.target*day/totalDays);
    const paceForecast=s.paid>=s.target?s.paid:Math.round(s.paid/day*totalDays);
    const weightedPotential=Math.round(s.paid+weighted);
    const maxPotential=Math.round(s.paid+openPax);
    const needDaily=remain?Math.ceil(remain/Math.max(left,1)):0;
    const coverage=remain?openPax/remain:CFG.pipelineMultiple;
    const closes=Math.ceil(s.target/CFG.avgPaxPerSchool);
    const quotesNeed=Math.ceil(closes/(CFG.quotationToClose/100));
    const qualifiedNeed=Math.ceil(quotesNeed/(CFG.qualifiedToQuotation/100));
    const contactedNeed=Math.ceil(qualifiedNeed/(CFG.contactedToQualified/100));
    const modelLeads=Math.ceil(contactedNeed/(CFG.leadToContacted/100));
    const leadsNeed=Math.max(CFG.leadsMonthly,modelLeads);
    const conversion=(a,b)=>b?a/b*100:0;
    let status='PIPELINE BELUM CUKUP', cls='bad', note=`Maksimum potensi ${int(maxPotential)} pax masih di bawah target ${int(s.target)}.`;
    if(s.paid>=s.target){status='TARGET TERCAPAI';cls='ok';note=`Paid pax sudah ${int(s.paid)} dari target ${int(s.target)}.`;}
    else if(weightedPotential>=s.target&&s.paid>=paceToday*.9){status='ON TRACK';cls='ok';note='Pace paid pax dan weighted pipeline masih mendukung target akhir bulan.';}
    else if(maxPotential>=s.target&&(weightedPotential>=s.target||coverage>=CFG.pipelineMultiple)){status='MASIH MUNGKIN';cls='info';note='Pipeline cukup secara volume, tetapi follow-up dan conversion harus dijaga sampai pembayaran masuk.';}
    else if(maxPotential>=s.target){status='BERISIKO';cls='info';note='Secara jumlah masih mungkin, tetapi coverage/probability pipeline belum cukup aman.';}
    return {
      leads:monthReq.length,contacted,qualified,quotes,won:wonMonth.length,wonCohort,
      openPax,weightedPotential,maxPotential,hotPax,due,avgWon,totalDays,day,left,remain,paceToday,paceForecast,needDaily,coverage,
      closes,quotesNeed,qualifiedNeed,contactedNeed,modelLeads,leadsNeed,
      c1:conversion(contacted,monthReq.length),c2:conversion(qualified,contacted),c3:conversion(quotes,qualified),c4:conversion(wonCohort,quotes),
      remainingSchools:remain?Math.ceil(remain/Math.max(avgWon,1)):0,
      baselinePipeline:s.target*CFG.pipelineMultiple,status,cls,note,
    };
  }

  function style(){
    if(q('#gmuSalesFunnelStyle'))return;
    const el=document.createElement('style');el.id='gmuSalesFunnelStyle';el.textContent=`
      #gmuSales200Target{display:none!important}
      .gmu-sf-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px;margin-top:10px}
      .gmu-sf-funnel{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8px;margin-top:10px}
      .gmu-sf-step{border:1px solid var(--line);background:#fff;border-radius:13px;padding:10px}.gmu-sf-step small{display:block;font-size:8px;color:var(--muted)}.gmu-sf-step b{display:block;font-size:17px;color:var(--gd);margin:4px 0}.gmu-sf-step span{display:block;font-size:8px;color:var(--muted);line-height:1.45}.gmu-sf-step strong{color:var(--g)}
      .gmu-sf-status{border:1px solid var(--line);background:#f7fbf8;border-radius:14px;padding:12px;margin-top:10px}
      @media(max-width:920px){.gmu-sf-grid{grid-template-columns:repeat(2,1fr)}.gmu-sf-funnel{grid-template-columns:repeat(2,1fr)}}
      @media(max-width:520px){.gmu-sf-grid,.gmu-sf-funnel{grid-template-columns:1fr}}
    `;document.head.appendChild(el);
  }

  function card(label,value,desc){return `<div class="gmu-target-card"><small>${label}</small><b>${value}</b><span>${desc}</span></div>`;}

  function render(){
    if(!allowed())return;
    const anchor=q('#gmuStationTargetSection')||q('#salesTargetControl')||q('.content');if(!anchor)return;
    let root=q('#gmuSalesFunnelForecast');
    if(!root){root=document.createElement('div');root.id='gmuSalesFunnelForecast';root.className='card section';if(anchor.id==='gmuStationTargetSection')anchor.insertAdjacentElement('afterend',root);else if(anchor.id==='salesTargetControl')anchor.prepend(root);else anchor.appendChild(root);}
    const s=summary(),m=metrics(s);
    const fin=s.finance?card('Cash-in terverifikasi',money(s.cashIn),'Pembayaran terverifikasi bulan berjalan.')+card('Nilai booking paid-pax',money(s.bookedValue),'Nilai booking yang sudah memiliki pembayaran positif.'):'';
    const bonus=s.approved>0?money(s.approved):(s.paid>=s.target?'ELIGIBLE':'BELUM');
    root.innerHTML=`
      <div class="head"><div><h3>Sales Funnel & Forecast • Target ${int(s.target)} Pax</h3><p>${monthLabel()} • ${sales()?'Target saya':'Target tim'} • semua program dijumlahkan.</p></div><span class="badge ${m.cls}">${m.status}</span></div>
      <div class="gmu-sf-grid">
        ${card('Target paid pax / bulan',int(s.target)+' pax','Target awal GMU saat ini minimal 400 pax.')}
        ${card('Aktual paid pax',int(s.paid)+' pax',pct(s.achievement)+'% • kurang '+int(m.remain)+' pax.')}
        ${card('Target pace hari ini',int(m.paceToday)+' pax','Hari ke-'+int(m.day)+' dari '+int(m.totalDays)+'.')}
        ${card('Kebutuhan sisa hari',int(m.needDaily)+' pax/hari',int(m.left)+' hari tersisa.')}
        ${fin}
        ${card('Fee Sales aktual',money(s.paid*s.fee),money(s.fee)+' × '+int(s.paid)+' paid pax.')}
        ${card('Bonus target',bonus,'Profit-funded: margin dan collection tetap harus lolos guardrail.')}
        ${card('Penghasilan ter-model',money(s.income),'Retainer '+money(s.retainer)+' + fee + bonus yang sudah disetujui.')}
      </div>
      <div class="gmu-sf-status"><div class="head"><div><h3 style="font-size:13px">Masih mungkin mencapai target?</h3><p>${m.note}</p></div><span class="badge ${m.cls}">${m.status}</span></div><div class="gmu-sf-grid">
        ${card('Pace forecast',int(m.paceForecast)+' pax','Jika pace paid pax sekarang bertahan.')}
        ${card('Weighted potential',int(m.weightedPotential)+' pax','Paid pax + pipeline × probability CRM.')}
        ${card('Maximum potential',int(m.maxPotential)+' pax','Paid pax + seluruh pax lead aktif.')}
        ${card('Pipeline coverage',pct(m.coverage)+'×','Guardrail awal minimal '+CFG.pipelineMultiple+'× kebutuhan sisa.')}
      </div></div>
      <div style="margin-top:14px"><div class="head"><div><h3 style="font-size:13px">Funnel Minimum untuk ${int(s.target)} Pax</h3><p>Baseline 40 pax/sekolah. Target: ${int(m.closes)} sekolah closing dan sekitar ${int(m.quotesNeed)} quotation.</p></div><span class="badge neutral">AUTO FORECAST</span></div><div class="gmu-sf-funnel">
        <div class="gmu-sf-step"><small>LEAD BARU</small><b>${int(m.leads)} / ${int(m.leadsNeed)}</b><span>Minimum model ${int(m.modelLeads)}; operasional ${int(m.leadsNeed)}.</span></div>
        <div class="gmu-sf-step"><small>CONTACTED</small><b>${int(m.contacted)} / ${int(CFG.contactedMonthly)}</b><span>Conversion <strong>${pct(m.c1)}%</strong> • min ${CFG.leadToContacted}%.</span></div>
        <div class="gmu-sf-step"><small>QUALIFIED</small><b>${int(m.qualified)} / ${int(m.qualifiedNeed)}</b><span>Conversion <strong>${pct(m.c2)}%</strong> • min ${CFG.contactedToQualified}%.</span></div>
        <div class="gmu-sf-step"><small>QUOTATION</small><b>${int(m.quotes)} / ${int(m.quotesNeed)}</b><span>Conversion <strong>${pct(m.c3)}%</strong> • min ${CFG.qualifiedToQuotation}%.</span></div>
        <div class="gmu-sf-step"><small>SEKOLAH CLOSING</small><b>${int(m.won)} / ${int(m.closes)}</b><span>Cohort quote→won <strong>${pct(m.c4)}%</strong> • min ${CFG.quotationToClose}%.</span></div>
      </div></div>
      <div class="gmu-sf-grid">
        ${card('Gap lead',int(Math.max(m.leadsNeed-m.leads,0)),'Kejar ± '+int(Math.ceil(Math.max(m.leadsNeed-m.leads,0)/Math.max(m.left,1)))+' lead baru/hari.')}
        ${card('Gap quotation',int(Math.max(m.quotesNeed-m.quotes,0)),'Target model '+int(m.quotesNeed)+' quotation/bulan.')}
        ${card('Gap sekolah closing',int(Math.max(m.closes-m.won,0)),'Estimasi sisa '+int(m.remainingSchools)+' sekolah dari avg '+pct(m.avgWon)+' pax.')}
        ${card('Follow-up jatuh tempo',int(m.due),'Hot pipeline NEGOTIATION/WAITING_DP: '+int(m.hotPax)+' pax.')}
      </div>
      <div class="gmu-target-note" style="margin-top:10px"><b>Rumus:</b> ${int(s.target)} pax ÷ 40 pax/sekolah = <b>${int(m.closes)} sekolah</b>. Quote→close minimum 30% membutuhkan ± <b>${int(m.quotesNeed)} quotation</b>; qualified→quote 60%; contacted→qualified 40%; lead→contacted 80%. Pipeline aktif disarankan minimal <b>${int(m.baselinePipeline)} pax</b> (3× target).</div>
      ${sales()?'<div class="gmu-target-note" style="margin-top:8px">Privasi: Sales hanya melihat target, CRM, paid pax, komisi, dan forecast miliknya; data laba, margin, kas perusahaan, dan payroll pihak lain tetap disembunyikan.</div>':''}
      ${state.error?`<div class="gmu-target-note" style="margin-top:8px;color:var(--bad)">${safe(state.error)}</div>`:''}`;
  }

  async function load(){
    if(!allowed()||!db()||state.loading)return;state.loading=true;state.error=null;
    try{
      const start=monthStart(), lookback=new Date(start.getFullYear()-1,start.getMonth(),1).toISOString(), uid=userId();
      if(sales()&&!uid)throw new Error('Profil Sales belum memiliki ID untuk scope CRM.');
      let cq=db().from('crm_lead_controls').select('booking_request_id,stage,owner_id,probability_pct,created_at,won_at,next_follow_up_at').gte('created_at',lookback);
      let rq=db().from('booking_requests').select('id,pax,assigned_sales,created_at,converted_booking_id,status').gte('created_at',lookback);
      if(sales()){cq=cq.eq('owner_id',uid);rq=rq.eq('assigned_sales',uid);}
      const [sr,cr,rr]=await Promise.all([db().rpc('gmu_sales_portfolio_summary',{p_period_month:monthKey()}),cq,rq]);
      if(sr.error)throw sr.error;if(cr.error)throw cr.error;if(rr.error)throw rr.error;
      state.summary=Array.isArray(sr.data)?(sr.data[0]||null):(sr.data||null);state.controls=Array.isArray(cr.data)?cr.data:[];state.requests=Array.isArray(rr.data)?rr.data:[];
    }catch(e){state.error=e?.message||String(e);}finally{state.loading=false;render();}
  }

  function init(){style();const timer=setInterval(()=>{if(typeof profile==='undefined'||!profile)return;clearInterval(timer);if(!allowed())return;render();load();const obs=new MutationObserver(()=>{if(!q('#gmuSalesFunnelForecast'))render();});obs.observe(document.body,{childList:true,subtree:true});setInterval(load,60000);},250);window.GmuSalesFunnelForecast=Object.freeze({version:VERSION,target:CFG,refresh:load});}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();


/* ===== station-operating-policy-v218.js ===== */
(() => {
  'use strict';

  const VERSION = 'v21.8-station-operating-policy';
  const ALLOWED = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales']);
  const POLICY = Object.freeze({
    programName: 'Edukasi Profesi & Lingkungan Stasiun',
    minimumMarginPct: 35,
    workforce: [
      ['Manager EduTrans', 'Rp1.000.000/bulan + Rp30.000/sesi'],
      ['Sales', 'Rp600.000/bulan + Rp2.500/paid pax + bonus Rp250.000 di 200 pax'],
      ['Admin / CS', 'Rp300.000/bulan'],
      ['Finance', 'Rp300.000/bulan'],
      ['Ops + Dokumentasi', 'Rp40.000/sesi'],
      ['TL / MC', 'Rp110.000/sesi'],
      ['Kepala Stasiun', 'Rp50.000/sesi'],
      ['6 Narasumber', 'Rp120.000/sesi total'],
    ],
    directCosts: [
      ['Mitra', 'Rp2.500/paid pax'],
      ['Makan crew', 'Rp40.000/sesi'],
      ['Snack', 'Rp2.500/pax'],
      ['Sertifikat', 'Rp500/pax'],
      ['Worksheet', 'Rp500/pax'],
    ],
    targetLevels: [
      ['BEP', '60 pax'],
      ['Produktif', '100 pax'],
      ['Target Utama', '200 pax'],
      ['Stretch', '300 pax'],
      ['Outstanding', '400 pax'],
    ],
  });

  const q = (s, root = document) => root.querySelector(s);
  function role() { try { return String(profile?.role || ''); } catch (_) { return ''; } }
  function allowed() { return ALLOWED.has(role()); }

  function rows(items) {
    return items.map(([name, rule]) => `<tr><td><b>${name}</b></td><td>${rule}</td></tr>`).join('');
  }

  function patchHealthyMargin() {
    document.querySelectorAll('.gmu-target-card').forEach(card => {
      const label = card.querySelector('small')?.textContent?.trim();
      if (label !== 'Margin kegiatan sehat') return;
      const value = card.querySelector('b');
      const note = card.querySelector('span');
      if (value) value.textContent = '≥ 35%';
      if (note) note.textContent = 'Quotation di bawah 35% wajib approval Manager/Owner.';
    });
  }

  function render() {
    if (!allowed() || q('#gmuStationOperatingPolicy')) return;
    const anchor = q('#gmuSales200Target') || q('#gmuStationTargetSection');
    if (!anchor) return;

    const section = document.createElement('div');
    section.id = 'gmuStationOperatingPolicy';
    section.className = 'card section';
    section.innerHTML = `
      <div class="head">
        <div>
          <h3>Struktur SDM & Biaya Program</h3>
          <p>${POLICY.programName} • policy internal untuk pricing, RAB, quotation, dan kontrol margin.</p>
        </div>
        <span class="badge info">MARGIN ≥ ${POLICY.minimumMarginPct}%</span>
      </div>
      <div class="gmu-target-grid">
        ${POLICY.targetLevels.map(([label,value]) => `<div class="gmu-target-card"><small>${label}</small><b>${value}</b><span>Level target program.</span></div>`).join('')}
      </div>
      <div class="wrap" style="margin-top:12px">
        <table>
          <thead><tr><th>SDM / Fungsi</th><th>Skema Fee</th></tr></thead>
          <tbody>${rows(POLICY.workforce)}</tbody>
        </table>
      </div>
      <div class="wrap" style="margin-top:12px">
        <table>
          <thead><tr><th>Biaya Langsung</th><th>Skema</th></tr></thead>
          <tbody>${rows(POLICY.directCosts)}</tbody>
        </table>
      </div>
      <div class="gmu-target-note" style="margin-top:10px">
        <b>Guardrail sistem:</b> harga aktif Rp55.000/pax minimum 20 pax dan Rp46.000/pax minimum 30 pax. Fee Sales hanya dari paid pax. Bonus Sales hanya cair saat target 200 paid pax tercapai, cash-in masuk, tidak ada diskon tanpa approval, dan projected/actual margin tetap ≥35%. Jika quotation di bawah 35%, sistem harus meminta approval Manager/Owner.
      </div>`;
    anchor.insertAdjacentElement('afterend', section);
  }

  function apply() {
    patchHealthyMargin();
    render();
  }

  function init() {
    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      if (!allowed()) return;
      apply();
      const observer = new MutationObserver(apply);
      observer.observe(document.body, { childList: true, subtree: true });
    }, 250);

    window.GmuStationOperatingPolicyV218 = Object.freeze({ version: VERSION, policy: POLICY });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();


/* ===== train-package-policy-v221.js ===== */
(() => {
  'use strict';

  const VERSION = 'v22.1-train-package-policy';
  const ALLOWED = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans']);
  const q = (s, root = document) => root.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  const state = { economics: [], addons: [], packages: [], loading: false, error: null };

  function role(){ try { return String(profile?.role || ''); } catch (_) { return ''; } }
  function allowed(){ return ALLOWED.has(role()); }
  function db(){ try { return typeof sb !== 'undefined' && sb?.from ? sb : null; } catch (_) { return null; } }

  function publicPrice(code){ return Number(state.packages.find(x => x.package_code === code)?.price_per_pax || 0); }

  function installStyle(){
    if(q('#gmuTrainPolicyStyle')) return;
    const s=document.createElement('style');
    s.id='gmuTrainPolicyStyle';
    s.textContent=`
      .gtp{margin-top:12px}.gtp-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:10px}.gtp-card{border:1px solid var(--line);border-radius:14px;padding:12px;background:#fff}.gtp-card.best{border-color:#94c8a9;background:#f4fbf7}.gtp-card small{display:block;font-size:8px;color:var(--muted)}.gtp-card h4{margin:5px 0;color:var(--gd);font-size:14px}.gtp-price{font-size:18px;font-weight:900;color:var(--g)}.gtp-row{display:flex;justify-content:space-between;gap:8px;font-size:9px;padding:4px 0;border-bottom:1px dashed var(--line)}.gtp-row:last-child{border-bottom:0}.gtp-note{font-size:9px;line-height:1.6;background:#f4f8f6;border-radius:12px;padding:11px;margin-top:10px}.gtp-addons{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:10px}@media(max-width:900px){.gtp-grid{grid-template-columns:1fr}.gtp-addons{grid-template-columns:1fr}}
    `;
    document.head.appendChild(s);
  }

  function packageCard(x){
    const price=publicPrice(x.package_code);
    return `<div class="gtp-card ${x.is_best_seller?'best':''}">
      <small>${h(x.package_code)} ${x.is_best_seller?'• BEST SELLER':''}</small>
      <h4>${h(x.package_name)}</h4>
      <div class="gtp-price">${money(price)} / pax</div>
      <div class="gtp-row"><span>Baseline</span><b>${Number(x.baseline_pax||20)} pax</b></div>
      <div class="gtp-row"><span>HPP fasilitas • LOCK</span><b>${money(x.facility_hpp_locked)}</b></div>
      <div class="gtp-row"><span>Fee Sales</span><b>${money(x.sales_fee_baseline)}</b></div>
      <div class="gtp-row"><span>Fee Mitra</span><b>${money(x.partner_fee_baseline)}</b></div>
      <div class="gtp-row"><span>Fee Manager</span><b>${money(x.manager_fee_baseline)}</b></div>
      <div class="gtp-row"><span>TL / Tutor</span><b>${money(x.tl_tutor_fee_baseline)}</b></div>
      <div class="gtp-row"><span>Ops + Dokumentasi</span><b>${money(x.ops_documentation_fee_baseline)}</b></div>
      <div class="gtp-row"><span>Total biaya baseline</span><b>${money(x.total_cost_baseline)}</b></div>
      <div class="gtp-row"><span>Kontribusi GMU</span><b>${money(x.trip_contribution_baseline)}</b></div>
      <div class="gtp-row"><span>Margin kontribusi</span><b>${Number(x.contribution_margin_pct||0).toLocaleString('id-ID',{maximumFractionDigits:1})}%</b></div>
    </div>`;
  }

  function addonCard(a){
    return `<div class="gtp-card"><small>ADD-ON • DI LUAR PROGRAM</small><h4>${h(a.addon_name)}</h4><div class="gtp-price">${money(a.sell_price_per_pax)} / pax</div><div class="gtp-row"><span>Biaya dasar tiket</span><b>${money(a.base_cost_per_pax)}</b></div><div class="gtp-row"><span>Handling</span><b>${money(a.handling_per_pax)}</b></div><div class="gtp-row"><span>Sudah termasuk paket?</span><b>TIDAK</b></div><div class="gtp-note">${h(a.notes||'Hanya untuk kebutuhan tiket tambahan di luar program.')}</div></div>`;
  }

  function render(){
    if(!allowed()) return;
    const anchor=q('#gmuSales200Target')||q('#gmuStationTargetSection')||q('#salesTargetControl')||q('.content');
    if(!anchor) return;
    let box=q('#gmuTrainPackagePolicy');
    if(!box){box=document.createElement('section');box.id='gmuTrainPackagePolicy';box.className='card section gtp';anchor.insertAdjacentElement('afterend',box);}
    if(state.loading){box.innerHTML='<div class="gtp-note">Memuat skema Paket Edukasi Kereta…</div>';return;}
    if(state.error){box.innerHTML=`<div class="head"><div><h3>Skema Final • Edukasi di Atas Kereta</h3></div></div><div class="gtp-note">Belum dapat memuat master ekonomi: ${h(state.error)}</div>`;return;}
    box.innerHTML=`
      <div class="head"><div><h3>Skema Final • Edukasi di Atas Kereta</h3><p>Internal Owner/Manager • HPP fasilitas dikunci • harga paket sudah termasuk tiket utama program.</p></div><span class="badge ok">HPP LOCK</span></div>
      <div class="gtp-grid">${state.economics.map(packageCard).join('')}</div>
      <div class="gtp-note"><b>Aturan:</b> minimum 20 pax. Sales dan Mitra adalah dua fee berbeda. Baseline masing-masing Rp50.000 per 20 pax dan Rp100.000 pada 40 pax. Manager/TL/Ops tidak otomatis dikali setiap tambahan 20 pax; peserta di atas baseline mengikuti tier crew yang ditetapkan operasional. Margin di atas adalah kontribusi trip sebelum overhead tetap perusahaan.</div>
      <div class="head" style="margin-top:14px"><div><h3 style="font-size:13px">Tiket Tambahan di Luar Program</h3><p>Tidak pernah otomatis ditambahkan ke harga Paket Hemat/Reguler/Lengkap.</p></div></div>
      <div class="gtp-addons">${state.addons.map(addonCard).join('')}</div>`;
  }

  async function load(){
    if(!allowed()||!db()||state.loading)return;
    state.loading=true;state.error=null;render();
    try{
      const [e,a,p]=await Promise.all([
        db().from('program_package_economics').select('*').eq('program_slug','edukasi-di-atas-kereta').order('total_cost_baseline',{ascending:true}),
        db().from('program_package_addons').select('*').eq('program_slug','edukasi-di-atas-kereta').eq('is_active',true).order('sell_price_per_pax',{ascending:true}),
        db().from('program_packages').select('package_code,price_per_pax,status,is_active').in('package_code',['TRAIN-HEMAT-39','TRAIN-REGULER-60','TRAIN-LENGKAP-75'])
      ]);
      if(e.error)throw e.error;if(a.error)throw a.error;if(p.error)throw p.error;
      state.economics=Array.isArray(e.data)?e.data:[];state.addons=Array.isArray(a.data)?a.data:[];state.packages=Array.isArray(p.data)?p.data:[];
    }catch(err){state.error=err?.message||String(err);}finally{state.loading=false;render();}
  }

  function init(){
    const timer=setInterval(()=>{if(typeof profile==='undefined'||!profile)return;clearInterval(timer);if(!allowed())return;installStyle();render();load();const observer=new MutationObserver(()=>{if(!q('#gmuTrainPackagePolicy'))render();});observer.observe(document.body,{childList:true,subtree:true});},250);
    window.GmuTrainPackagePolicyV221=Object.freeze({version:VERSION,refresh:load});
  }

  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();


/* ===== company-system-center-v104.js ===== */
(() => {
  'use strict';

  const VERSION = 'v10.4-company-system-center';
  const q = (s, root = document) => root.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);

  function role() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }
  function isDirector(){ return ['Owner','Director','Direktur'].includes(role()); }
  function isManager(){ return ['Manager','Manager EduTrans'].includes(role()); }
  function isSales(){ return role() === 'Sales'; }
  function isFinance(){ return ['Finance','Keuangan'].includes(role()); }
  function isOps(){ return ['Admin','Operation','Operasional'].includes(role()); }
  function isTL(){ return ['TL','Crew','Freelancer'].includes(role()); }

  function workspaceLabel() {
    if (isDirector()) return 'Kendali Direktur';
    if (isManager()) return 'Pusat Kendali Manager';
    if (isSales()) return 'Pusat Penjualan Saya';
    if (isFinance()) return 'Pekerjaan Keuangan Saya';
    if (isOps()) return 'Pekerjaan Operasional Saya';
    if (isTL()) return 'Penugasan Saya';
    return 'Ruang Kerja Saya';
  }

  function assistantLabel() {
    if (isDirector()) return 'Asisten AI Direktur';
    if (isManager()) return 'Asisten AI Manager';
    if (isSales()) return 'Asisten AI Penjualan';
    if (isFinance()) return 'Asisten AI Keuangan';
    if (isOps()) return 'Asisten AI Operasional';
    return 'Asisten AI GMU';
  }

  function modulesForRole() {
    const common = [
      ['Tugas Saya','Tugas harian, minggu ini, terlambat, menunggu pihak lain, selesai.'],
      ['Target & Kinerja','Target perusahaan → tim → individu → aktivitas harian.'],
      ['Pemesanan & Kegiatan','Booking → persiapan H-7/H-3/H-1 → kegiatan → closing.'],
      ['SOP & Persetujuan','Workflow, batas kewenangan, approval, audit log.'],
    ];
    if (isDirector()) return [
      ['Kendali Direktur','Laba, omzet, cash, sales, operasi, SDM, risiko, approval strategis.'],
      ['Target Perusahaan','Laba bersih Rp15 juta/bulan sebagai target awal perusahaan.'],
      ['Kas & Likuiditas','Kas tersedia, cadangan, runway, piutang, kewajiban dan cash forecast.'],
      ['Pertumbuhan','Growth readiness, investasi, tambah Sales, program baru, area baru.'],
      ['Mutu & Pelanggan','Kepuasan, komplain, repeat order, CAPA, kualitas program/vendor/crew.'],
      ['SDM & Personalia','Kinerja Manager, headcount, beban kerja, hiring impact, payroll overview.'],
      ['Tata Kelola','Risk register, audit log, kebijakan, dokumen, kontrol akses.'],
      ...common,
    ];
    if (isManager()) return [
      ['Pusat Kendali Manager','Target unit, funnel Sales, booking, readiness, RAB, margin, AR, tim.'],
      ['Rencana Pemulihan','Masalah → penyebab → PIC → tindakan → deadline → hasil → evaluasi.'],
      ['Kas Operasional','Trip funding, piutang kegiatan, kewajiban vendor, warning cash unit.'],
      ['Tim & SDM','Beban kerja, penugasan, kinerja, kebutuhan freelancer/pegawai.'],
      ['Mutu & Pelanggan','Feedback, komplain, service recovery, repeat opportunity.'],
      ['Vendor & Pengadaan','Vendor, PO, verifikasi, approval, pembayaran.'],
      ...common,
    ];
    if (isSales()) return [
      ['Pusat Penjualan Saya','Target, gap, prospek, follow-up, penawaran, booking, komisi.'],
      ['Intelijen Pasar','Target sekolah/instansi/perusahaan di Cianjur–Sukabumi.'],
      ['Pipeline Saya','Calon Pelanggan → Potensial → Penawaran → Negosiasi → Menunggu DP → Berhasil.'],
      ['Komisi Saya','Pending → Earned → Paid; menjaga margin dan syarat pembayaran.'],
      ...common,
    ];
    if (isFinance()) return [
      ['Pekerjaan Keuangan Saya','Tagihan, pembayaran, AR/AP, biaya aktual, closing.'],
      ['Kas & Likuiditas','Rekonsiliasi, cash availability, forecast, reserve visibility sesuai role.'],
      ['Penggajian','Gaji, fee trip, komisi, reimbursement, potongan, anti-double-payment.'],
      ['Biaya & Anggaran','Budget vs actual, recurring cost, teknologi, overhead, forecast EOM.'],
      ...common,
    ];
    if (isOps()) return [
      ['Pekerjaan Operasional Saya','Dokumen, manifest, rundown, vendor, crew, readiness.'],
      ['Checklist H-7/H-3/H-1','Persiapan, rekonfirmasi, kesiapan final dan critical missing items.'],
      ['Berkas Kegiatan','Booking, quotation, invoice, manifest, rundown, operation sheet, laporan.'],
      ['Insiden & Kesiapan','Readiness score, incident report, escalation keselamatan.'],
      ...common,
    ];
    return [
      ['Penugasan Saya','Call time, rundown, peserta, safety, bukti kerja dan laporan.'],
      ['Checklist Kegiatan','Tugas lapangan sesuai role dan kegiatan yang ditugaskan.'],
      ['Fee & Penghasilan','Status assignment → earned → finance approval → paid.'],
      ...common,
    ];
  }

  const featureGroups = [
    ['Penjualan & Pertumbuhan', ['Target Perusahaan','Target & Kinerja Sales','CRM & Pipeline','Intelijen Pasar Cianjur–Sukabumi','Campaign & Sumber Lead','Repeat Order & Referral','Product Mix & Forecast']],
    ['Operasional', ['Pemesanan','Berkas Kegiatan','RAB','Rundown','Lembar Operasional','Manifest','Crew & TL','Vendor','H-7/H-3/H-1','Readiness','Insiden','Closing H+1/H+3']],
    ['Keuangan & Kas', ['Invoice & Pembayaran','Piutang & Kewajiban','Budget vs Actual','Laba Kontribusi','Laba Bersih Perusahaan','Kas Tersedia','Cadangan Kas','Runway','Cash Forecast 7/30/60/90','Penggajian & Komisi','Biaya Teknologi']],
    ['SDM & Kinerja', ['Struktur & Kewenangan','Jobdesk','KPI','Kehadiran','Cuti/Izin','Payroll','Penilaian Kinerja','Coaching & PIP','Recruitment','Workforce Planning','Onboarding','Offboarding']],
    ['Mutu & Pelanggan', ['Survey Kepuasan','Komplain','Service Recovery','CAPA','Customer 360','Repeat Intent','Vendor/Crew/TL Score','Voice of Customer']],
    ['Tata Kelola & AI', ['Pusat Persetujuan','Audit Log','Risk Register','Dokumen & Versioning','Need-to-know Access','Asisten AI per Role','AI Cost Control','AI Growth Adviser','Meeting Assistant','Executive Summary']],
  ];

  function installStyle(){
    if(q('#gmuSystemCenterStyle')) return;
    const s=document.createElement('style');
    s.id='gmuSystemCenterStyle';
    s.textContent=`
      .gmu-system-hero{background:linear-gradient(135deg,#f5faf7,#eef7f2);border:1px solid var(--line);border-radius:18px;padding:16px;margin-bottom:12px}
      .gmu-system-hero h3{margin:0 0 4px}.gmu-system-hero p{margin:0;color:var(--muted);font-size:9px;line-height:1.6}
      .gmu-system-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
      .gmu-system-card{border:1px solid var(--line);border-radius:15px;padding:13px;background:#fff}
      .gmu-system-card h4{margin:0 0 4px;font-size:13px;color:var(--gd)}.gmu-system-card p{margin:0;color:var(--muted);font-size:9px;line-height:1.55}
      .gmu-system-groups{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
      .gmu-system-group{border:1px solid var(--line);border-radius:15px;padding:13px;background:#fff}.gmu-system-group h4{margin:0 0 8px;color:var(--gd);font-size:12px}
      .gmu-system-item{font-size:9px;line-height:1.6;padding:3px 0}.gmu-system-dot{display:inline-block;width:7px;height:7px;border-radius:50%;background:var(--g);margin-right:6px}
      .gmu-role-strip{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin-top:12px}.gmu-role-strip div{padding:10px;border-radius:12px;background:#f7faf8;border:1px solid var(--line);font-size:9px}.gmu-role-strip b{display:block;color:var(--gd);font-size:12px}
      @media(max-width:900px){.gmu-system-groups{grid-template-columns:1fr 1fr}.gmu-role-strip{grid-template-columns:1fr 1fr}}
      @media(max-width:640px){.gmu-system-grid,.gmu-system-groups,.gmu-role-strip{grid-template-columns:1fr}}
    `;
    document.head.appendChild(s);
  }

  function addNavButton(page,label,icon='◫'){
    const nav=q('#nav'); if(!nav || q(`#nav [data-page="${page}"]`)) return;
    const b=document.createElement('button'); b.dataset.page=page; b.innerHTML=`${icon} &nbsp; ${h(label)}`; b.addEventListener('click',()=>show(page,label)); nav.appendChild(b);
  }

  function show(page,label){
    document.querySelectorAll('.page').forEach(el=>el.classList.remove('active'));
    q('#'+page)?.classList.add('active');
    document.querySelectorAll('#nav [data-page]').forEach(el=>el.classList.toggle('active',el.dataset.page===page));
    const t=q('#title'); if(t) t.textContent=label;
  }

  function installSystemCenter(){
    if(q('#companySystemCenter')) return;
    const content=q('.content'); if(!content) return;
    const page=document.createElement('section');
    page.id='companySystemCenter'; page.className='page';
    page.innerHTML=`
      <div class="gmu-system-hero"><h3>Sistem Operasi Perusahaan GMU EduTrans</h3><p>Direktur menentukan hasil → Manager mengelola bisnis → ERP mengatur proses → AI membantu analisis → Tim menjalankan tugas. Seluruh modul di bawah adalah blueprint resmi perusahaan dan harus terlihat sesuai hak akses.</p></div>
      <div class="gmu-system-grid">
        ${modulesForRole().map(([a,b])=>`<div class="gmu-system-card"><h4>${h(a)}</h4><p>${h(b)}</p></div>`).join('')}
      </div>
      <div class="card section"><div class="head"><div><h3>Arsitektur Sistem Perusahaan</h3><p>Modul yang disusun dalam turnaround GMU EduTrans.</p></div><span class="badge info">${h(role()||'Role')}</span></div>
        <div class="gmu-system-groups">${featureGroups.map(([g,items])=>`<div class="gmu-system-group"><h4>${h(g)}</h4>${items.map(x=>`<div class="gmu-system-item"><span class="gmu-system-dot"></span>${h(x)}</div>`).join('')}</div>`).join('')}</div>
      </div>
      <div class="card section"><div class="head"><div><h3>Ritme Manajemen</h3><p>Sistem mengatur pekerjaan agar Direktur tidak kembali menjadi operator harian.</p></div></div>
        <div class="gmu-role-strip"><div><small>Harian</small><b>Tugas & Alert</b>Role melihat pekerjaan dan pengecualian.</div><div><small>Mingguan</small><b>Manager Review</b>Sales, Ops, Finance dan recovery.</div><div><small>Bulanan</small><b>Business Review</b>Laba, cash, KPI, produk dan SDM.</div><div><small>Kuartalan</small><b>Strategic Review</b>Pertumbuhan, investasi dan ekspansi.</div></div>
      </div>
    `;
    content.appendChild(page);
  }

  function installWorkspace(){
    if(q('#roleWorkspace')) return;
    const content=q('.content'); if(!content) return;
    const page=document.createElement('section'); page.id='roleWorkspace'; page.className='page';
    const work=modulesForRole();
    page.innerHTML=`
      <div class="notice">${h(workspaceLabel())} • role ${h(role())} • setiap role melihat target, tugas, SOP, KPI, approval dan informasi yang relevan.</div>
      <div class="card section"><div class="head"><div><h3>${h(workspaceLabel())}</h3><p>${h(assistantLabel())} membantu analisis dan penyusunan tindakan, tetapi tidak mengambil keputusan finansial/strategis tanpa otorisasi.</p></div></div>
        <div class="gmu-system-grid">${work.map(([a,b])=>`<div class="gmu-system-card"><h4>${h(a)}</h4><p>${h(b)}</p></div>`).join('')}</div>
      </div>
      <div class="card section"><div class="head"><div><h3>7 Hal yang Wajib Terlihat</h3><p>Standar workspace semua jabatan.</p></div></div>
        <div class="gmu-role-strip"><div><b>Tugas Saya</b>Hari ini & minggu ini.</div><div><b>Target Saya</b>KPI dan gap.</div><div><b>Jadwal Saya</b>Kegiatan & deadline.</div><div><b>Persetujuan Saya</b>Yang perlu tindakan.</div><div><b>SOP Saya</b>Prosedur kerja.</div><div><b>KPI Saya</b>Kinerja periode berjalan.</div><div><b>Penghasilan Saya</b>Sesuai hak akses.</div></div>
      </div>`;
    content.appendChild(page);
  }

  function installTasks(){
    if(q('#myTasksHub')) return;
    const content=q('.content'); if(!content) return;
    const page=document.createElement('section'); page.id='myTasksHub'; page.className='page';
    page.innerHTML=`<div class="notice">Tugas Saya • universal work queue GMU EduTrans.</div><div class="card section"><div class="head"><div><h3>Tugas Saya</h3><p>Hari Ini / Minggu Ini / Menunggu Persetujuan / Terlambat / Selesai</p></div></div><div class="gmu-role-strip"><div><b>Hari Ini</b>Tugas dengan deadline hari ini.</div><div><b>Minggu Ini</b>Tugas aktif 7 hari.</div><div><b>Menunggu</b>Pihak lain/persetujuan.</div><div><b>Terlambat</b>Wajib dibereskan atau dieskalasi.</div></div><div style="margin-top:12px;font-size:9px;color:var(--muted)">Field standar: nama tugas • PIC • deadline • prioritas • SOP • data terkait • status • catatan • bukti. Integrasi data backend akan membaca tabel tugas ketika tersedia.</div></div>`;
    content.appendChild(page);
  }

  function install(){
    installStyle(); installSystemCenter(); installWorkspace(); installTasks();
    addNavButton('companySystemCenter','Sistem Perusahaan','▦');
    addNavButton('roleWorkspace',workspaceLabel(),'⌂');
    addNavButton('myTasksHub','Tugas Saya','✓');
  }

  function init(){
    const timer=setInterval(()=>{ if(typeof profile==='undefined'||!profile) return; clearInterval(timer); install(); },250);
    window.GmuCompanySystemCenter=Object.freeze({version:VERSION,workspaceLabel,assistantLabel});
  }

  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',init,{once:true}); else init();
})();


/* ===== management-domains-v105.js ===== */
(() => {
  'use strict';

  const VERSION='v10.5-management-domains';
  const q=(s,r=document)=>r.querySelector(s);
  const h=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  function role(){try{return String(profile?.role||'')}catch(_){return ''}}
  const director=()=>['Owner','Director','Direktur'].includes(role());
  const manager=()=>['Manager','Manager EduTrans'].includes(role());
  const finance=()=>['Finance','Keuangan'].includes(role());
  const sales=()=>role()==='Sales';
  const ops=()=>['Admin','Operation','Operasional'].includes(role());

  function installStyle(){
    if(q('#gmuDomainsStyle')) return;
    const s=document.createElement('style');s.id='gmuDomainsStyle';s.textContent=`
      .gmu-domain-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.gmu-domain-card{border:1px solid var(--line);border-radius:14px;padding:12px;background:#fff}.gmu-domain-card small{display:block;font-size:8px;color:var(--muted)}.gmu-domain-card b{display:block;color:var(--gd);font-size:14px;margin:3px 0}.gmu-domain-card p{margin:0;font-size:9px;color:var(--muted);line-height:1.55}.gmu-domain-list{font-size:9px;line-height:1.7}.gmu-domain-rule{background:#f4f8f6;border-radius:13px;padding:11px;font-size:9px;line-height:1.65;margin-top:10px}.gmu-domain-actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:10px}@media(max-width:820px){.gmu-domain-grid{grid-template-columns:1fr 1fr}}@media(max-width:560px){.gmu-domain-grid{grid-template-columns:1fr}}
    `;document.head.appendChild(s);
  }
  function show(id,label){document.querySelectorAll('.page').forEach(x=>x.classList.remove('active'));q('#'+id)?.classList.add('active');document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page===id));const t=q('#title');if(t)t.textContent=label}
  function nav(id,label,icon,allow=true){if(!allow)return;const n=q('#nav');if(!n||q(`#nav [data-page="${id}"]`))return;const b=document.createElement('button');b.dataset.page=id;b.innerHTML=`${icon} &nbsp; ${h(label)}`;b.addEventListener('click',()=>show(id,label));n.appendChild(b)}
  function page(id,html){if(q('#'+id))return;const c=q('.content');if(!c)return;const s=document.createElement('section');s.id=id;s.className='page';s.innerHTML=html;c.appendChild(s)}
  const cards=a=>`<div class="gmu-domain-grid">${a.map(([k,v,d])=>`<div class="gmu-domain-card"><small>${h(k)}</small><b>${h(v)}</b><p>${h(d)}</p></div>`).join('')}</div>`;

  function installTreasury(){
    const allow=director()||manager()||finance(); if(!allow)return;
    page('treasuryControl',`<div class="notice">Kas & Likuiditas ${VERSION} • laba dan cash dipantau terpisah.</div><div class="card section"><div class="head"><div><h3>Kas & Likuiditas</h3><p>Kas tersedia, dana terikat, kewajiban, cadangan, trip funding, runway dan forecast.</p></div></div>${cards([
      ['Kas Tersedia','Rumus kontrol','Total Kas/Bank − dana pelanggan terikat − AP jangka pendek − payroll − pajak − trip funding.'],
      ['Runway','Target sehat 2–3 bulan','≥3 bulan Sehat • 2–2,9 Perlu Perhatian • 1–1,9 Peringatan • <1 Kritis.'],
      ['Trip Funding','Terpenuhi / Sebagian / Risiko','Setiap kegiatan harus punya status kecukupan dana sebelum berangkat.'],
      ['Forecast','7 / 30 / 60 / 90 hari','Proyeksi masuk-keluar kas, AR/AP, payroll, vendor, pajak dan trip.'],
      ['Cadangan','Darurat + Pertumbuhan','Penggunaan cadangan memerlukan persetujuan Direktur.'],
      ['Piutang','Due / Overdue / Kritis','Tidak boleh ada piutang overdue tanpa tindak lanjut.']
    ])}<div class="gmu-domain-rule"><b>Cash waterfall:</b> pemasukan → kewajiban trip → vendor/crew → payroll/kantor → pajak → cadangan darurat → cadangan pertumbuhan → free cash.</div></div>`);
    nav('treasuryControl','Kas & Likuiditas','◈',allow);
  }

  function installPeople(){
    const allow=director()||manager()||finance(); if(!allow)return;
    page('peopleControl',`<div class="notice">SDM & Personalia ${VERSION} • capacity → workload → staffing gap → cost impact → approval.</div><div class="card section"><div class="head"><div><h3>SDM & Personalia</h3><p>Struktur, KPI, attendance, payroll, recruitment, performance dan lifecycle.</p></div></div>${cards([
      ['Struktur','Direktur → Manager → Tim','Kewenangan berhenti di level terendah yang mampu menyelesaikan masalah.'],
      ['Kinerja','Target + kualitas + disiplin','Evaluasi bulanan; coaching dan PIP bila masalah berulang.'],
      ['Kehadiran','Office + Field Duty','Penugasan lapangan memakai call-time/shift, bukan jam kantor biasa.'],
      ['Penghasilan','Fixed + trip + commission','Pisahkan salary, fee internal, freelancer, komisi, reimbursement dan partner fee.'],
      ['Recruitment','Data-driven','No addition / Freelancer / Part-time / Permanent berdasarkan workload dan affordability.'],
      ['Lifecycle','Onboarding → Offboarding','Akses, SOP, KPI, training, asset handover dan revoke access.']
    ])}<div class="gmu-domain-rule"><b>Aturan tetap:</b> Manager transport Rp30.000 per hari hadir. Angka retainer/gaji yang pernah disimulasikan bukan master final sampai ditetapkan.</div></div>`);
    nav('peopleControl','SDM & Personalia','♙',allow);
  }

  function installQuality(){
    const allow=director()||manager()||sales()||ops(); if(!allow)return;
    page('qualityControl',`<div class="notice">Mutu & Pelanggan ${VERSION} • setiap kegiatan harus menghasilkan profit, pembelajaran dan peluang penjualan berikutnya.</div><div class="card section"><div class="head"><div><h3>Mutu & Pelanggan</h3><p>Survey, komplain, service recovery, CAPA, Customer 360 dan repeat order.</p></div></div>${cards([
      ['Kepuasan','Skor 1–5','4,5–5 Sangat Puas • 4–4,49 Puas • 3–3,99 Perlu Perbaikan • <3 Kritis.'],
      ['Rekomendasi','Niat merekomendasikan','Pertanyaan 0–10 + repeat intent.'],
      ['Komplain','Ringan → Kritis','Safety/legal/reputasi kritis dieskalasi ke Direktur.'],
      ['Service Recovery','Terukur & approved','Respons, solusi, biaya, PIC dan deadline.'],
      ['CAPA','Root cause + corrective action','Komplain tidak ditutup tanpa penyebab dan tindakan perbaikan.'],
      ['Retention','Repeat / referral / reactivation','Feedback positif harus menghasilkan next sales opportunity.']
    ])}</div>`);
    nav('qualityControl','Mutu & Pelanggan','♡',allow);
  }

  function installGrowth(){
    const allow=director()||manager();if(!allow)return;
    page('growthControl',`<div class="notice">Pertumbuhan ${VERSION} • profit-first growth, bukan omzet semata.</div><div class="card section"><div class="head"><div><h3>Pertumbuhan & Ekspansi</h3><p>ERP menentukan kesiapan tumbuh berdasarkan profit, cash, pipeline, capacity, customer dan risk.</p></div></div>${cards([
      ['Growth Readiness','Belum Siap / Stabilkan / Siap Bertumbuh / Siap Ekspansi','Status ditentukan dari profit, cash, pipeline, conversion, capacity dan risiko.'],
      ['Tambah Sales','Workload-driven','Tambah orang bila pipeline, overdue, target dan kapasitas membenarkan.'],
      ['Program Baru','Idea → Pilot → Evaluate → Active','Ukur inquiry, conversion, feedback, actual cost, margin dan difficulty.'],
      ['Area Baru','Remote → Partner → Coordinator → Branch','Cabang adalah tahap terakhir, bukan langkah pertama.'],
      ['Investasi','Cost → profit → cash → payback','Request wajib punya tujuan, biaya, impact, risk dan success metric.'],
      ['Stop Rule','Hentikan yang merusak perusahaan','Loss program, dead market, ineffective campaign, bad vendor, unhealthy partnership.']
    ])}<div class="gmu-domain-rule"><b>Prioritas:</b> Sehatkan Sales → Profit Konsisten → Cadangan Kas → Operasi Stabil → SDM Capacity → Scale Best Products → Area → Branch.</div></div>`);
    nav('growthControl','Pertumbuhan','↗',allow);
  }

  function installGovernance(){
    const allow=director()||manager()||finance()||ops();if(!allow)return;
    page('governanceControl',`<div class="notice">Tata Kelola ${VERSION} • approval, risk, audit dan dokumen harus meninggalkan jejak sistem.</div><div class="card section"><div class="head"><div><h3>Tata Kelola</h3><p>Approval matrix, risk register, audit log, document versioning dan need-to-know access.</p></div></div>${cards([
      ['Persetujuan','Satu inbox','Discount, RAB, refund, cancellation, vendor, unplanned cost, readiness override.'],
      ['Audit Log','Immutable untuk user biasa','User/role/time/before/after/reason/approval.'],
      ['Risk Register','Business + Ops + Finance + Safety','Risiko punya owner, mitigation dan status.'],
      ['Dokumen','Versioned','SOP, policy, form, template dan kontrak punya versi.'],
      ['Akses','Need-to-know','Keuangan strategis hanya Owner/Direktur; Manager hanya unit operational finance.'],
      ['Rapat','Data → Problem → Decision → PIC → Deadline','Weekly Manager control; monthly Director + Manager.']
    ])}</div>`);
    nav('governanceControl','Tata Kelola','§',allow);
  }

  function installAI(){
    const allow=director()||manager()||sales()||finance()||ops();if(!allow)return;
    page('aiCenter',`<div class="notice">Pusat AI ${VERSION} • AI menganalisis, merekomendasikan, menyusun draft dan membuat tugas; keputusan sensitif tetap manusia.</div><div class="card section"><div class="head"><div><h3>Pusat AI</h3><p>Asisten per-role untuk mengurangi pekerjaan manual dan menjaga kontrol.</p></div></div>${cards([
      ['Direktur','Asisten AI Direktur','Executive summary, anomaly, target gap, strategic approval context.'],
      ['Manager','Asisten AI Manager','Recovery plan, readiness, team workload, sales/ops/finance control.'],
      ['Sales','Asisten AI Penjualan','Lead priority, follow-up, draft penawaran, lost reason dan target gap.'],
      ['Operasional','Asisten AI Operasional','Rundown, crew, vendor, checklist H-7/H-3/H-1, laporan.'],
      ['Keuangan','Asisten AI Keuangan/Treasury','AR/AP, cost anomaly, closing, forecast, liquidity warning.'],
      ['Mutu/SDM/Growth','AI Coach & Adviser','Quality themes, workforce recommendation, growth readiness dan meeting assistant.']
    ])}<div class="gmu-domain-rule"><b>AI dilarang otomatis:</b> membelanjakan uang, transfer dana, refund besar, ubah harga master, hiring/firing, ubah payroll, pakai cadangan, approve margin kritis, atau keputusan irreversible safety/legal.</div><div class="gmu-domain-actions"><button class="btn secondary" id="gmuOpenOpsAgent">Buka Asisten Operasional</button><button class="btn secondary" id="gmuOpenTargets">Buka Target & Kinerja</button><button class="btn secondary" id="gmuOpenMarket">Buka Intelijen Pasar</button></div></div>`);
    nav('aiCenter','Pusat AI','✦',allow);
    q('#gmuOpenOpsAgent')?.addEventListener('click',()=>show('opsAgent','Asisten Operasional'));
    q('#gmuOpenTargets')?.addEventListener('click',()=>show('salesTargetControl','Target & Kinerja'));
    q('#gmuOpenMarket')?.addEventListener('click',()=>show('marketIntelligence','Intelijen Pasar'));
  }

  function init(){installStyle();const t=setInterval(()=>{if(typeof profile==='undefined'||!profile)return;clearInterval(t);installTreasury();installPeople();installQuality();installGrowth();installGovernance();installAI();},250);window.GmuManagementDomains=Object.freeze({version:VERSION})}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
