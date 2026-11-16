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
