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
