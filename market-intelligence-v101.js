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
