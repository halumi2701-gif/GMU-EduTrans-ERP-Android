(() => {
  'use strict';

  const VERSION = 'v10.2-production';
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
    technologyCosts: ['Server & Hosting','Domain','Cloud & Database','AI & Otomatisasi','Email & Notifikasi','Penyimpanan & Backup','Software & Langganan','Keamanan Digital','API Pihak Ketiga'],
  });

  const OWNER = new Set(['Owner','Director','Direktur']);
  const MANAGER = new Set(['Manager','Manager EduTrans']);
  const MARKET_ROLES = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales','Admin','Operation']);
  const q = (s, r = document) => r.querySelector(s);
  const qa = (s, r = document) => [...r.querySelectorAll(s)];
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const rp = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  const role = () => { try { return String(profile?.role || ''); } catch (_) { return ''; } };
  const isOwner = () => OWNER.has(role());
  const isManager = () => MANAGER.has(role());

  const LABELS = {
    'Dashboard':'Ringkasan',
    'Booking':'Pemesanan',
    'Customer':'Pelanggan',
    'Finance':'Keuangan',
    'Trip Operation':'Operasional Kegiatan',
    'Trip Control':'Kendali Kegiatan',
    'Trip Folder':'Berkas Kegiatan',
    'Workflow Control':'Kendali Proses',
    'User & Role':'Pengguna & Hak Akses',
    'Approval Queue':'Daftar Persetujuan',
    'Workflow Alerts':'Peringatan Proses',
    'Pending Approval':'Menunggu Persetujuan',
    'Trip Mendatang':'Kegiatan Mendatang',
    'Operation Sheet':'Lembar Operasional',
    'Trip Report':'Laporan Kegiatan',
    'Document Upload Center':'Pusat Dokumen',
    'Trip Archive':'Arsip Kegiatan',
  };

  function installStyle() {
    if (q('#gmuV102Style')) return;
    const s = document.createElement('style');
    s.id = 'gmuV102Style';
    s.textContent = `
      .gmu-v102-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:12px 0}
      .gmu-v102-card{background:#fff;border:1px solid var(--line);border-radius:15px;padding:14px}
      .gmu-v102-card small{display:block;color:var(--muted);font-size:9px}.gmu-v102-card b{display:block;font-size:19px;margin-top:5px;color:var(--gd)}
      .gmu-v102-list{display:grid;gap:8px}.gmu-v102-item{border:1px solid var(--line);border-radius:12px;padding:11px;background:#fff;font-size:10px}
      .gmu-v102-item b{display:block;margin-bottom:4px}.gmu-v102-toolbar{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
      .gmu-v102-row{display:grid;grid-template-columns:2fr 1.1fr .8fr .9fr 1fr;gap:8px;padding:10px 0;border-bottom:1px solid var(--line);align-items:center;font-size:9px}
      .gmu-v102-row:last-child{border-bottom:0}.gmu-v102-score{font-weight:900;color:var(--g)}
      .gmu-v102-status{font-size:9px;color:var(--muted);margin:8px 0}.gmu-v102-empty{padding:18px;text-align:center;color:var(--muted);font-size:10px}
      @media(max-width:760px){.gmu-v102-grid{grid-template-columns:1fr 1fr}.gmu-v102-row{grid-template-columns:1fr}}
      @media(max-width:480px){.gmu-v102-grid{grid-template-columns:1fr}}
    `;
    document.head.appendChild(s);
  }

  function relabelUi() {
    qa('#nav button').forEach(btn => {
      const t = btn.textContent.replace(/^\s*[^\wÀ-ÿ]+\s*/,'').trim();
      if (LABELS[t]) {
        const icon = btn.textContent.match(/^\s*([^\wÀ-ÿ]+)\s*/)?.[1] || '';
        btn.textContent = `${icon} ${LABELS[t]}`.trim();
      }
    });
    qa('h1,h2,h3,th,label,button,small,p').forEach(el => {
      const t = el.textContent.trim();
      if (LABELS[t]) el.textContent = LABELS[t];
    });
    if (isManager()) {
      const me = q('#meRole');
      if (me) me.textContent = 'Manager EduTrans';
    }
  }

  function showPage(id, title) {
    qa('.page').forEach(el => el.classList.remove('active'));
    q('#' + id)?.classList.add('active');
    qa('#nav button[data-page]').forEach(el => el.classList.toggle('active', el.dataset.page === id));
    if (q('#title')) q('#title').textContent = title;
    q('#side')?.classList.remove('open');
  }

  function addNav(page, label, icon = '◆') {
    const nav = q('#nav');
    if (!nav || q(`#nav [data-page="${page}"]`)) return null;
    const b = document.createElement('button');
    b.dataset.page = page;
    b.innerHTML = `${icon} &nbsp; ${label}`;
    nav.appendChild(b);
    return b;
  }

  function companyPage() {
    if ((!isOwner() && !isManager()) || q('#companyControl')) return;
    const content = q('.content');
    if (!content) return;
    const p = document.createElement('section');
    p.id = 'companyControl';
    p.className = 'page';
    p.innerHTML = `
      <div class="notice">Sistem Operasi Perusahaan ${VERSION} • Direktur menentukan hasil, Manager mengelola bisnis, ERP mengendalikan proses.</div>
      <div class="gmu-v102-grid">
        <div class="gmu-v102-card"><small>Target Laba Bersih Bulanan</small><b>${rp(POLICY.targetNetProfitMonthly)}</b></div>
        <div class="gmu-v102-card"><small>Margin Sehat</small><b>≥ ${POLICY.healthyMarginPct}%</b></div>
        <div class="gmu-v102-card"><small>Wilayah Utama</small><b>Cianjur & Sukabumi</b></div>
        <div class="gmu-v102-card"><small>Transport Manager</small><b>${rp(POLICY.managerTransportPerAttendance)}/hari</b></div>
      </div>
      <div class="grid2">
        <div class="card section" style="margin-top:0"><div class="head"><div><h3>Kewenangan Manager</h3><p>Batas final yang berlaku di GMU EduTrans</p></div></div>
          <div class="gmu-v102-list">
            <div class="gmu-v102-item"><b>Biaya sudah ada di RAB</b>Sampai ${rp(POLICY.managerPlannedRabLimit)} per transaksi dapat disetujui Manager jika sesuai RAB dan margin tidak kritis.</div>
            <div class="gmu-v102-item"><b>Biaya di luar RAB</b>Sampai ${rp(POLICY.managerUnplannedLimit)} dengan alasan dan bukti. Di atasnya naik ke Direktur.</div>
            <div class="gmu-v102-item"><b>Darurat saat kegiatan berjalan</b>Sampai ${rp(POLICY.managerEmergencyLimit)} untuk keselamatan/kelangsungan kegiatan, kemudian wajib dilaporkan.</div>
            <div class="gmu-v102-item"><b>Diskon & Margin</b>Diskon ≤ ${POLICY.managerMaxDiscountPct}% Manager. Margin 20–24,99% perlu perhatian. Margin < ${POLICY.criticalMarginPct}% wajib Direktur.</div>
          </div>
        </div>
        <div class="card section" style="margin-top:0"><div class="head"><div><h3>Biaya Perusahaan</h3><p>Wajib masuk sebelum laba bersih dinyatakan tercapai</p></div></div>
          <div class="gmu-v102-list">
            <div class="gmu-v102-item"><b>Utilitas Kantor</b>${rp(POLICY.officeUtilitiesMonthly)} / bulan</div>
            ${POLICY.technologyCosts.map(x => `<div class="gmu-v102-item">${esc(x)}</div>`).join('')}
          </div>
        </div>
      </div>
      <div class="card section"><div class="head"><div><h3>Prinsip Kendali Direktur</h3><p>Management by exception</p></div></div>
        <div class="gmu-v102-item"><b>Direktur fokus pada hasil.</b>Operasional normal ditangani Manager. Direktur menerima eskalasi hanya untuk target laba terancam, kas kritis, margin kritis, pengeluaran di atas kewenangan, refund besar, penggunaan cadangan kas, rekrutmen strategis, insiden serius, fraud, atau risiko hukum.</div>
      </div>`;
    content.appendChild(p);
    const b = addNav('companyControl','Kendali Perusahaan','★');
    b?.addEventListener('click', e => { e.stopPropagation(); showPage('companyControl','Kendali Perusahaan'); });
  }

  let marketRows = [];
  let marketRegion = 'Semua';
  function marketPage() {
    if (!MARKET_ROLES.has(role()) || q('#marketIntelligence')) return;
    const content = q('.content');
    if (!content) return;
    const p = document.createElement('section');
    p.id = 'marketIntelligence';
    p.className = 'page';
    p.innerHTML = `
      <div class="notice">Intelijen Pasar • Fokus tahap pertama Cianjur & Sukabumi. Data publik yang belum dipastikan wajib berstatus Perlu Verifikasi.</div>
      <div class="card section"><div class="head"><div><h3>Target Calon Klien</h3><p>Sekolah, pesantren, kampus, instansi, komunitas, perusahaan/corporate, partner & mitra</p></div></div>
        <div class="gmu-v102-toolbar"><button class="btn ghost" id="gmuMAll">Semua</button><button class="btn ghost" id="gmuMCjr">Cianjur</button><button class="btn ghost" id="gmuMSmi">Sukabumi</button><button class="btn ghost" id="gmuMReload">Muat Ulang</button></div>
        <div id="gmuMStatus" class="gmu-v102-status"></div><div id="gmuMStats" class="gmu-v102-grid"></div><div id="gmuMList"></div>
      </div>`;
    content.appendChild(p);
    const b = addNav('marketIntelligence','Intelijen Pasar','◎');
    b?.addEventListener('click', e => { e.stopPropagation(); showPage('marketIntelligence','Intelijen Pasar'); loadMarket(); });
    q('#gmuMAll')?.addEventListener('click',()=>{marketRegion='Semua';renderMarket();});
    q('#gmuMCjr')?.addEventListener('click',()=>{marketRegion='Cianjur';renderMarket();});
    q('#gmuMSmi')?.addEventListener('click',()=>{marketRegion='Sukabumi';renderMarket();});
    q('#gmuMReload')?.addEventListener('click',loadMarket);
  }

  async function loadMarket() {
    const status = q('#gmuMStatus');
    if (!status) return;
    if (typeof sb === 'undefined' || !sb?.from) { status.textContent = 'Koneksi data belum tersedia.'; return; }
    status.textContent = 'Memuat database target pasar…';
    try {
      const {data: d, error} = await sb.from('market_targets')
        .select('id,organization_name,organization_type,region,district,target_pic_role,potential_participants,program_fit,verification_status,potential_score,priority_grade,sales_status,assigned_sales,next_follow_up_at,estimated_revenue,estimated_profit,notes')
        .order('potential_score',{ascending:false}).limit(500);
      if (error) throw error;
      marketRows = Array.isArray(d) ? d : [];
      status.textContent = `${marketRows.length} target pasar termuat.`;
    } catch (_) {
      marketRows = [];
      status.textContent = 'UI Intelijen Pasar sudah aktif. Tabel market_targets belum tersedia/diizinkan pada backend produksi.';
    }
    renderMarket();
  }

  function renderMarket() {
    const rows = marketRows.filter(x => POLICY.priorityRegions.includes(String(x.region || '')) && (marketRegion === 'Semua' || String(x.region) === marketRegion));
    const active = rows.filter(x => ['TERTARIK','PELUANG POTENSIAL','PENAWARAN','NEGOSIASI','MENUNGGU DP'].includes(String(x.sales_status || ''))).length;
    const stats = q('#gmuMStats');
    if (stats) stats.innerHTML = [
      ['Total Target',rows.length],['Belum Dihubungi',rows.filter(x=>x.sales_status==='BELUM DIHUBUNGI').length],['Peluang Aktif',active],['Berhasil',rows.filter(x=>x.sales_status==='BERHASIL').length]
    ].map(x=>`<div class="gmu-v102-card"><small>${x[0]}</small><b>${x[1]}</b></div>`).join('');
    const root = q('#gmuMList');
    if (!root) return;
    if (!rows.length) { root.innerHTML='<div class="gmu-v102-empty">Belum ada target pasar pada wilayah ini.</div>'; return; }
    root.innerHTML = rows.map(x => `<div class="gmu-v102-row">
      <div><b>${esc(x.organization_name)}</b><br><small>${esc(x.organization_type || '-')} • ${esc(x.region || '-')}${x.district?' • '+esc(x.district):''}</small></div>
      <div>${esc(x.target_pic_role || 'PIC belum ditentukan')}<br><small>${esc(Array.isArray(x.program_fit)?x.program_fit.join(', '):(x.program_fit||'Program belum dipetakan'))}</small></div>
      <div class="gmu-v102-score">${Number(x.potential_score||0)}/100 • ${esc(x.priority_grade||'C')}</div>
      <div>${esc(x.sales_status||'BELUM DIHUBUNGI')}</div>
      <div>${rp(x.estimated_revenue)}<br><small>${esc(x.verification_status||'PERLU VERIFIKASI')}</small></div>
    </div>`).join('');
  }

  function enforceRoleUi() {
    if (isManager()) {
      const me = q('#meRole');
      if (me) me.textContent = 'Manager EduTrans';
    }
    relabelUi();
  }

  function installNotice() {
    const content = q('.content');
    if (!content || q('#gmuV102Notice')) return;
    const n = document.createElement('div');
    n.id = 'gmuV102Notice';
    n.className = 'notice';
    n.textContent = 'Sistem Operasi Perusahaan GMU EduTrans aktif • Bahasa utama Indonesia • Fokus pasar Cianjur & Sukabumi.';
    content.prepend(n);
  }

  function initAfterLogin() {
    installStyle();
    companyPage();
    marketPage();
    installNotice();
    enforceRoleUi();
  }

  function init() {
    window.GmuCompanyPolicy = POLICY;
    installStyle();
    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile || q('#app')?.classList.contains('hidden')) return;
      clearInterval(timer);
      initAfterLogin();
    }, 250);
    const observer = new MutationObserver(() => {
      if (typeof profile === 'undefined' || !profile) return;
      enforceRoleUi();
      companyPage();
      marketPage();
    });
    observer.observe(document.documentElement,{childList:true,subtree:true});
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init,{once:true}); else init();
})();
