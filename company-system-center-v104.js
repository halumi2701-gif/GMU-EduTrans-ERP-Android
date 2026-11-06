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
