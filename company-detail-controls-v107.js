(() => {
  'use strict';

  const VERSION = 'v10.7-company-detail-controls';
  const TARGET = Object.freeze({
    netProfit: 15_000_000,
    prospects: 200,
    qualified: 20,
    quotations: 12,
    minBookings: 3,
    healthyMargin: 25,
  });

  const q = (s, r = document) => r.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  const pct = (v, d = 1) => `${Number(v || 0).toFixed(d)}%`;

  function role(){ try { return String(profile?.role || ''); } catch (_) { return ''; } }
  function isDirector(){ return ['Owner','Director','Direktur'].includes(role()); }
  function isManager(){ return ['Manager','Manager EduTrans'].includes(role()); }
  function isSales(){ return role() === 'Sales'; }
  function isFinance(){ return ['Finance','Keuangan'].includes(role()); }
  function isOps(){ return ['Admin','Operation','Operasional'].includes(role()); }

  function appData(){
    try { return data || {}; } catch (_) { return {}; }
  }
  function arr(name){ const d=appData(); return Array.isArray(d[name]) ? d[name] : []; }
  function monthKey(v){ if(!v) return ''; const d=new Date(String(v).length===10?`${v}T00:00:00`:v); if(Number.isNaN(d.getTime())) return ''; return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`; }
  function thisMonthKey(){ const d=new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`; }
  function bookingRevenue(b){ return Number(b?.pax||0) * Number(b?.price_per_pax||0); }
  function bookingPaid(id){ return arr('payments').filter(x=>x.booking_id===id).reduce((a,x)=>a+(x.payment_type==='Refund'?-Number(x.amount||0):Number(x.amount||0)),0); }
  function bookingCost(id){ return arr('costs').filter(x=>x.booking_id===id).reduce((a,x)=>a+Number(x.actual_amount||0),0); }
  function currentMonthBookings(){ const key=thisMonthKey(); return arr('bookings').filter(b=>monthKey(b.trip_date||b.created_at)===key); }
  function activeBookings(){ return arr('bookings').filter(b=>!['Closed','Completed'].includes(String(b.status||''))); }
  function margin(rev,cost){ return rev>0 ? ((rev-cost)/rev)*100 : 0; }
  function safeWorkflowAlerts(){ try { return typeof workflowAlerts==='function' ? (workflowAlerts()||[]) : []; } catch (_) { return []; } }

  function companyMetrics(){
    const bs=currentMonthBookings();
    const all=arr('bookings');
    const rev=bs.reduce((a,b)=>a+bookingRevenue(b),0);
    const paid=bs.reduce((a,b)=>a+bookingPaid(b.id),0);
    const cost=bs.reduce((a,b)=>a+bookingCost(b.id),0);
    const profit=rev-cost;
    const m=margin(rev,cost);
    const quote=all.filter(b=>['Quotation','Negosiasi','Negotiation'].includes(String(b.status||'')) && monthKey(b.trip_date||b.created_at)===thisMonthKey()).length;
    const lead=all.filter(b=>['Lead','Qualified','Peluang','Prospect'].includes(String(b.status||'')) && monthKey(b.trip_date||b.created_at)===thisMonthKey()).length;
    const bookings=bs.filter(b=>!['Lead','Quotation'].includes(String(b.status||''))).length;
    const upcoming=activeBookings().filter(b=>new Date(`${b.trip_date}T00:00:00`)>=new Date(new Date().setHours(0,0,0,0))).length;
    const pending=arr('approvals').filter(a=>a.status==='Pending').length;
    const receivable=Math.max(0,rev-paid);
    return {bs,rev,paid,cost,profit,m,quote,lead,bookings,upcoming,pending,receivable};
  }

  function installStyle(){
    if(q('#gmuV107Style')) return;
    const s=document.createElement('style'); s.id='gmuV107Style'; s.textContent=`
      .gmu107{margin-top:12px}.gmu107-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.gmu107-3{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.gmu107-2{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.gmu107-card{border:1px solid var(--line);border-radius:14px;background:#fff;padding:12px}.gmu107-card small{font-size:8px;color:var(--muted);display:block}.gmu107-card b{display:block;font-size:15px;color:var(--gd);margin:3px 0}.gmu107-card p{font-size:9px;color:var(--muted);line-height:1.55;margin:0}.gmu107-progress{height:8px;background:#edf3ef;border-radius:999px;overflow:hidden;margin-top:8px}.gmu107-progress i{display:block;height:100%;background:var(--g);border-radius:999px}.gmu107-row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:9px 0;border-bottom:1px solid var(--line);font-size:9px}.gmu107-row:last-child{border-bottom:0}.gmu107-row span{color:var(--muted)}.gmu107-pill{font-size:8px;padding:4px 8px;border-radius:999px;background:#f2f7f4;border:1px solid var(--line);white-space:nowrap}.gmu107-actionbar{display:flex;gap:7px;flex-wrap:wrap;margin-top:10px}.gmu107-btn{border:1px solid var(--line);background:#fff;border-radius:10px;padding:8px 10px;font:inherit;font-size:9px;cursor:pointer;color:var(--gd)}.gmu107-btn:hover{background:#f5faf7}.gmu107-title{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:10px}.gmu107-title h4{margin:0;color:var(--gd)}.gmu107-title p{margin:2px 0 0;font-size:9px;color:var(--muted)}.gmu107-empty{padding:12px;border:1px dashed var(--line);border-radius:12px;color:var(--muted);font-size:9px}.gmu107-alert{padding:10px 12px;border-radius:12px;background:#f7faf8;border:1px solid var(--line);font-size:9px;line-height:1.6}.gmu107-table{width:100%;border-collapse:collapse;font-size:9px}.gmu107-table th,.gmu107-table td{padding:8px;border-bottom:1px solid var(--line);text-align:left}.gmu107-table th{font-size:8px;color:var(--muted)}.gmu107-note{font-size:8px;color:var(--muted);margin-top:8px;line-height:1.5}@media(max-width:960px){.gmu107-grid{grid-template-columns:repeat(2,1fr)}.gmu107-3{grid-template-columns:1fr 1fr}}@media(max-width:620px){.gmu107-grid,.gmu107-3,.gmu107-2{grid-template-columns:1fr}}
    `; document.head.appendChild(s);
  }

  function go(page){
    try { if(typeof navTo==='function' && q(`#${page}`)){ navTo(page); return; } } catch(_){}
    document.querySelectorAll('.page').forEach(x=>x.classList.remove('active'));
    q(`#${page}`)?.classList.add('active');
    document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page===page));
    const btn=q(`#nav [data-page="${page}"]`); const t=q('#title'); if(t&&btn)t.textContent=(btn.textContent||page).trim();
  }

  function button(page,label){ return `<button class="gmu107-btn" data-gmu107-go="${h(page)}">${h(label)}</button>`; }
  function progress(actual,target){ const p=Math.max(0,Math.min(100,target?actual/target*100:0)); return `<div class="gmu107-progress"><i style="width:${p.toFixed(1)}%"></i></div>`; }
  function statusLabel(value,good,warn){ return value>=good?'Sehat':value>=warn?'Perlu Perhatian':'Kritis'; }

  function mount(pageId,key,html){
    const page=q(`#${pageId}`); if(!page) return;
    let box=q(`[data-gmu107="${key}"]`,page);
    if(!box){ box=document.createElement('div'); box.className='gmu107'; box.dataset.gmu107=key; page.appendChild(box); }
    box.innerHTML=html;
  }

  function enhanceCompanyControl(){
    if(!q('#companyControl')) return;
    const m=companyMetrics();
    mount('companyControl','executive-live',`
      <div class="card section"><div class="gmu107-title"><div><h4>Ringkasan Aktual Bulan Ini</h4><p>Angka dihitung dari booking, pembayaran, dan biaya aktual yang sudah ada di ERP.</p></div><span class="gmu107-pill">${h(new Date().toLocaleDateString('id-ID',{month:'long',year:'numeric'}))}</span></div>
      <div class="gmu107-grid">
        <div class="gmu107-card"><small>Omzet</small><b>${money(m.rev)}</b><p>${m.bs.length} kegiatan pada periode ini.</p></div>
        <div class="gmu107-card"><small>Laba estimasi kegiatan</small><b>${money(m.profit)}</b><p>Omzet − biaya aktual yang tercatat.</p></div>
        <div class="gmu107-card"><small>Margin estimasi</small><b>${pct(m.m)}</b><p>Status: ${statusLabel(m.m,25,20)}.</p></div>
        <div class="gmu107-card"><small>Piutang periode</small><b>${money(m.receivable)}</b><p>Omzet yang belum tercatat sebagai pembayaran.</p></div>
      </div>
      <div class="gmu107-actionbar">${button('salesTargetControl','Target & Kinerja')}${button('treasuryControl','Kas & Likuiditas')}${button('workflow','Persetujuan')}${button('companySystemCenter','Sistem Perusahaan')}</div>
      <div class="gmu107-note">Laba bersih perusahaan final tetap memerlukan seluruh overhead tetap, payroll, teknologi, pajak dan biaya lain tercatat lengkap. Sistem tidak menganggap laba kegiatan sebagai laba bersih perusahaan.</div></div>`);
  }

  function enhanceSalesTarget(){
    if(!q('#salesTargetControl')) return;
    const m=companyMetrics();
    const items=[
      ['Lead/Peluang',m.lead,TARGET.qualified,'Peluang yang tercatat dari status Lead/Qualified bulan ini.'],
      ['Penawaran',m.quote,TARGET.quotations,'Booking berstatus Quotation/Negotiation bulan ini.'],
      ['Booking',m.bookings,TARGET.minBookings,'Booking di luar status Lead/Quotation bulan ini.'],
      ['Laba estimasi',m.profit,TARGET.netProfit,'Pembanding sementara terhadap sasaran laba bersih; overhead belum seluruhnya teralokasi.'],
    ];
    mount('salesTargetControl','actual-progress',`
      <div class="card section"><div class="gmu107-title"><div><h4>Realisasi vs Target</h4><p>Bagian ini berubah mengikuti data ERP, bukan angka statis.</p></div><span class="gmu107-pill">LIVE ERP</span></div>
      <div class="gmu107-2">${items.map(([name,a,t,d])=>`<div class="gmu107-card"><small>${h(name)}</small><b>${typeof a==='number'&&name==='Laba estimasi'?money(a):h(a)} / ${name==='Laba estimasi'?money(t):h(t)}</b>${progress(a,t)}<p>${h(d)}</p></div>`).join('')}</div>
      <div class="gmu107-3" style="margin-top:10px"><div class="gmu107-card"><small>Prospek harian</small><b>10</b><p>Baseline aktivitas Sales. Aktivitas CRM terpisah perlu dicatat agar realisasi prospek dapat dihitung akurat.</p></div><div class="gmu107-card"><small>Tindak lanjut harian</small><b>10</b><p>Gunakan aktivitas CRM/lead agar sistem dapat menghitung overdue dan SLA.</p></div><div class="gmu107-card"><small>Konversi sasaran</small><b>≥25%</b><p>Jika di bawah target, Manager membuat Rencana Pemulihan.</p></div></div>
      <div class="gmu107-actionbar">${button('bookings','Pemesanan')}${button('customers','Pelanggan')}${button('marketIntelligence','Intelijen Pasar')}</div></div>`);
  }

  function enhanceTasks(){
    if(!q('#myTasksHub')) return;
    const alerts=safeWorkflowAlerts(); const approvals=arr('approvals').filter(a=>a.status==='Pending');
    const rows=[...alerts.slice(0,12).map(a=>({title:a.title||'Tugas',detail:a.text||'',status:a.level||'Aktif'})),...approvals.slice(0,8).map(a=>({title:`Persetujuan: ${a.approval_type||'-'}`,detail:a.booking_id||a.reference_id||'',status:'Menunggu Persetujuan'}))];
    mount('myTasksHub','live-work-queue',`
      <div class="card section"><div class="gmu107-title"><div><h4>Antrian Kerja Aktual</h4><p>Disusun dari workflow alert dan persetujuan pending yang sudah ada di ERP.</p></div><span class="gmu107-pill">${rows.length} item</span></div>
      ${rows.length?rows.map(x=>`<div class="gmu107-row"><div><b>${h(x.title)}</b><br><span>${h(x.detail)}</span></div><span class="gmu107-pill">${h(x.status)}</span></div>`).join(''):'<div class="gmu107-empty">Belum ada tugas otomatis atau persetujuan pending yang terdeteksi.</div>'}
      <div class="gmu107-actionbar">${button('workflow','Buka Persetujuan & Workflow')}${button('operation','Operasional')}${button('tripcontrol','Kendali Kegiatan')}</div></div>`);
  }

  function enhanceTreasury(){
    if(!q('#treasuryControl')) return;
    const m=companyMetrics(); const allPaid=arr('payments').reduce((a,x)=>a+(x.payment_type==='Refund'?-Number(x.amount||0):Number(x.amount||0)),0); const allCost=arr('costs').reduce((a,x)=>a+Number(x.actual_amount||0),0);
    mount('treasuryControl','treasury-live',`
      <div class="card section"><div class="gmu107-title"><div><h4>Posisi Kas Operasional dari Data ERP</h4><p>Kas bank aktual tidak akan dipalsukan; yang ditampilkan hanya data transaksi yang tersedia.</p></div><span class="gmu107-pill">Kontrol Kas</span></div>
      <div class="gmu107-grid"><div class="gmu107-card"><small>Total pembayaran tercatat</small><b>${money(allPaid)}</b><p>Akumulasi pembayaran customer setelah refund.</p></div><div class="gmu107-card"><small>Total biaya aktual tercatat</small><b>${money(allCost)}</b><p>Biaya aktual kegiatan yang tersedia.</p></div><div class="gmu107-card"><small>Piutang bulan ini</small><b>${money(m.receivable)}</b><p>Perlu ditindaklanjuti bila overdue.</p></div><div class="gmu107-card"><small>Kas tersedia aktual</small><b>Belum dihitung</b><p>Memerlukan saldo bank/kas, committed customer cash, AP, payroll dan pajak.</p></div></div>
      <div class="gmu107-2" style="margin-top:10px"><div class="gmu107-card"><b>Kontrol wajib</b><div class="gmu107-row"><span>Trip Funding</span><strong>Terpenuhi / Sebagian / Risiko</strong></div><div class="gmu107-row"><span>Runway</span><strong>≥3 bln sehat</strong></div><div class="gmu107-row"><span>Forecast</span><strong>7 / 30 / 60 / 90 hari</strong></div><div class="gmu107-row"><span>Cadangan</span><strong>Darurat + Pertumbuhan</strong></div></div><div class="gmu107-card"><b>Cash Waterfall</b><p>Pemasukan → kewajiban trip → vendor/crew → payroll & kantor → pajak → cadangan darurat → cadangan pertumbuhan → free cash.</p><div class="gmu107-actionbar">${button('finance','Keuangan')}${button('bookings','Pemesanan')}</div></div></div></div>`);
  }

  function enhancePeople(){
    if(!q('#peopleControl')) return;
    const people=arr('profiles'); const active=people.filter(p=>p.is_active!==false); const byRole={}; active.forEach(p=>{byRole[p.role||'Tanpa Role']=(byRole[p.role||'Tanpa Role']||0)+1});
    mount('peopleControl','people-live',`
      <div class="card section"><div class="gmu107-title"><div><h4>Struktur SDM Aktual</h4><p>Ringkasan dari profil pengguna aktif di ERP.</p></div><span class="gmu107-pill">${active.length} aktif</span></div>
      <div class="gmu107-grid"><div class="gmu107-card"><small>Total profil</small><b>${people.length}</b><p>Semua profil ERP.</p></div><div class="gmu107-card"><small>Aktif</small><b>${active.length}</b><p>Akun yang tidak dinonaktifkan.</p></div><div class="gmu107-card"><small>Role aktif</small><b>${Object.keys(byRole).length}</b><p>Distribusi jabatan yang sedang digunakan.</p></div><div class="gmu107-card"><small>Transport Manager</small><b>${money(30000)}/hadir</b><p>Kebijakan yang sudah ditetapkan.</p></div></div>
      <div class="gmu107-2" style="margin-top:10px"><div class="gmu107-card"><b>Komposisi Role</b>${Object.entries(byRole).map(([r,n])=>`<div class="gmu107-row"><span>${h(r)}</span><strong>${n}</strong></div>`).join('')||'<div class="gmu107-empty">Belum ada profil aktif.</div>'}</div><div class="gmu107-card"><b>Lifecycle Wajib</b><div class="gmu107-row"><span>Rekrutmen</span><strong>Need → Impact → Approval</strong></div><div class="gmu107-row"><span>Onboarding</span><strong>30 / 60 / 90 hari</strong></div><div class="gmu107-row"><span>Performance</span><strong>Bulanan</strong></div><div class="gmu107-row"><span>Coaching/PIP</span><strong>Bila berulang</strong></div><div class="gmu107-row"><span>Offboarding</span><strong>Handover + Revoke</strong></div></div></div>
      <div class="gmu107-actionbar">${button('users','Akun & Role')}${button('roleWorkspace','Workspace Role')}</div></div>`);
  }

  function enhanceQuality(){
    if(!q('#qualityControl')) return;
    const ev=arr('evaluations'); const scored=ev.filter(x=>Number(x.score)>0); const avg=scored.length?scored.reduce((a,x)=>a+Number(x.score),0)/scored.length:0;
    mount('qualityControl','quality-live',`
      <div class="card section"><div class="gmu107-title"><div><h4>Mutu Aktual dari Evaluasi Trip</h4><p>Skor memakai data evaluasi yang sudah tersimpan; komplain formal membutuhkan data ticket terpisah.</p></div><span class="gmu107-pill">${scored.length} evaluasi</span></div>
      <div class="gmu107-grid"><div class="gmu107-card"><small>Rata-rata skor</small><b>${scored.length?avg.toFixed(2)+'/5':'Belum ada'}</b><p>${scored.length?(avg>=4.5?'Sangat Puas':avg>=4?'Puas':avg>=3?'Perlu Perbaikan':'Kritis'):'Belum ada evaluasi.'}</p></div><div class="gmu107-card"><small>Evaluasi tercatat</small><b>${ev.length}</b><p>Internal/customer/vendor sesuai data yang tersedia.</p></div><div class="gmu107-card"><small>CAPA</small><b>Wajib</b><p>Root cause + corrective action untuk komplain/masalah berulang.</p></div><div class="gmu107-card"><small>Repeat Opportunity</small><b>H+1</b><p>Feedback positif harus diubah menjadi tindak lanjut repeat/referral.</p></div></div>
      <div class="gmu107-actionbar">${button('tripfolder','Berkas Kegiatan')}${button('customers','Pelanggan')}${button('salesTargetControl','Target Penjualan')}</div></div>`);
  }

  function enhanceGrowth(){
    if(!q('#growthControl')) return;
    const m=companyMetrics(); const healthy=m.profit>0&&m.m>=25&&m.bookings>=TARGET.minBookings; const status=healthy?'Siap Bertumbuh':m.profit>0?'Stabilkan':'Belum Siap';
    mount('growthControl','growth-live',`
      <div class="card section"><div class="gmu107-title"><div><h4>Kesiapan Bertumbuh</h4><p>Penilaian sementara dari data transaksi bulan ini. Cash runway dan capacity lengkap tetap wajib sebelum ekspansi.</p></div><span class="gmu107-pill">${h(status)}</span></div>
      <div class="gmu107-grid"><div class="gmu107-card"><small>Booking bulan ini</small><b>${m.bookings}</b><p>Target minimum ${TARGET.minBookings}.</p>${progress(m.bookings,TARGET.minBookings)}</div><div class="gmu107-card"><small>Laba kegiatan</small><b>${money(m.profit)}</b><p>Harus positif dan konsisten.</p></div><div class="gmu107-card"><small>Margin</small><b>${pct(m.m)}</b><p>Sasaran sehat ≥25%.</p>${progress(m.m,25)}</div><div class="gmu107-card"><small>Trip mendatang</small><b>${m.upcoming}</b><p>Dipakai untuk mengukur kapasitas operasional.</p></div></div>
      <div class="gmu107-alert" style="margin-top:10px"><b>Gate ekspansi:</b> profit konsisten → cash & cadangan sehat → pipeline cukup → operasi stabil → capacity tersedia → baru tambah Sales/program/area/cabang.</div>
      <div class="gmu107-actionbar">${button('salesTargetControl','Sales & Target')}${button('treasuryControl','Kas & Likuiditas')}${button('peopleControl','SDM & Capacity')}</div></div>`);
  }

  function enhanceGovernance(){
    if(!q('#governanceControl')) return;
    const approvals=arr('approvals'); const pending=approvals.filter(a=>a.status==='Pending'); const audit=arr('audit');
    mount('governanceControl','governance-live',`
      <div class="card section"><div class="gmu107-title"><div><h4>Kontrol Tata Kelola Aktual</h4><p>Approval dan audit log dibaca langsung dari ERP.</p></div><span class="gmu107-pill">${pending.length} pending</span></div>
      <div class="gmu107-grid"><div class="gmu107-card"><small>Persetujuan pending</small><b>${pending.length}</b><p>Harus punya PIC dan keputusan.</p></div><div class="gmu107-card"><small>Audit log terbaca</small><b>${audit.length}</b><p>Riwayat aktivitas terbaru yang tersedia.</p></div><div class="gmu107-card"><small>Margin kritis</small><b>&lt;20%</b><p>Wajib Direktur.</p></div><div class="gmu107-card"><small>Diskon Manager</small><b>≤5%</b><p>Di atas itu wajib Direktur.</p></div></div>
      <div class="gmu107-2" style="margin-top:10px"><div class="gmu107-card"><b>Matriks Kewenangan</b><div class="gmu107-row"><span>Dalam RAB</span><strong>Manager ≤Rp1 jt</strong></div><div class="gmu107-row"><span>Di luar RAB</span><strong>Manager ≤Rp250 rb</strong></div><div class="gmu107-row"><span>Darurat trip</span><strong>Manager ≤Rp500 rb</strong></div><div class="gmu107-row"><span>Refund/cancel/reserve</span><strong>Direktur</strong></div></div><div class="gmu107-card"><b>Management by Exception</b><p>Masalah normal selesai di level terendah yang memiliki kewenangan. Direktur menerima hanya pengecualian strategis, kritis, fraud, legal, safety, reserve, investasi dan kegagalan recovery.</p></div></div>
      <div class="gmu107-actionbar">${button('workflow','Pusat Persetujuan')}${button('companyControl','Kendali Perusahaan')}</div></div>`);
  }

  function enhanceAI(){
    if(!q('#aiCenter')) return;
    mount('aiCenter','ai-actions',`
      <div class="card section"><div class="gmu107-title"><div><h4>Aksi Asisten AI per Role</h4><p>AI mengarahkan pekerjaan; keputusan sensitif tetap manusia.</p></div><span class="gmu107-pill">${h(role())}</span></div>
      <div class="gmu107-3"><div class="gmu107-card"><b>Direktur</b><p>Ringkas target, forecast, cash, risiko, approval strategis dan apakah tindakan Direktur diperlukan.</p></div><div class="gmu107-card"><b>Manager</b><p>Buat recovery plan, cek funnel, readiness, biaya, AR, workload dan tindakan tim.</p></div><div class="gmu107-card"><b>Sales</b><p>Prioritaskan lead, jadwalkan follow-up, siapkan draft penawaran dan analisis gap target.</p></div><div class="gmu107-card"><b>Operasional</b><p>Susun rundown, crew, vendor, checklist H-7/H-3/H-1, readiness dan laporan.</p></div><div class="gmu107-card"><b>Keuangan</b><p>Analisis AR/AP, anomali biaya, closing, cash forecast dan liquidity warning.</p></div><div class="gmu107-card"><b>Mutu/SDM/Growth</b><p>Analisis feedback, kebutuhan SDM, efisiensi biaya dan kesiapan bertumbuh.</p></div></div>
      <div class="gmu107-actionbar">${button('companyControl','Analisis Direktur')}${button('salesTargetControl','Analisis Sales')}${button('operation','Operasional')}${button('treasuryControl','Treasury')}</div>
      <div class="gmu107-note">AI tidak boleh otomatis mengeluarkan uang, transfer, refund besar, ubah harga master, hire/fire, ubah payroll, pakai reserve, approve margin kritis, atau mengambil keputusan safety/legal irreversible.</div></div>`);
  }

  function enhanceSystemCenter(){
    if(!q('#companySystemCenter')) return;
    const modules=[
      ['Kendali Direktur','companyControl','Target, laba, margin, cash, risiko, approval strategis'],
      ['Target & Kinerja','salesTargetControl','Funnel Sales, aktivitas harian, booking, konversi'],
      ['Intelijen Pasar','marketIntelligence','Cianjur–Sukabumi, target akun, PIC Sales, prioritas'],
      ['Tugas Saya','myTasksHub','Hari ini, minggu ini, overdue, menunggu approval'],
      ['Kas & Likuiditas','treasuryControl','Piutang, funding, runway, forecast, reserve'],
      ['SDM & Personalia','peopleControl','Role, KPI, attendance, payroll, recruitment, lifecycle'],
      ['Mutu & Pelanggan','qualityControl','Survey, complaint, CAPA, repeat order'],
      ['Pertumbuhan','growthControl','Growth readiness, program baru, Sales, area, investasi'],
      ['Tata Kelola','governanceControl','Approval, audit, risk, dokumen, akses'],
      ['Pusat AI','aiCenter','Asisten AI per role dan guardrails'],
      ['Operasional','operation','Trip, TL, progress, operation sheet'],
      ['Berkas Kegiatan','tripfolder','Manifest, rundown, dokumen, laporan, evaluasi'],
      ['Keuangan','finance','Invoice, pembayaran, piutang, collection'],
      ['Pemesanan','bookings','Booking dan status perjalanan customer'],
      ['Vendor','vendors','Penyedia, kontak, kategori dan pengadaan'],
      ['Persetujuan','workflow','Approval queue, deadline dan audit log'],
    ];
    mount('companySystemCenter','module-directory',`
      <div class="card section"><div class="gmu107-title"><div><h4>Direktori Modul Perusahaan</h4><p>Sekarang kartu di bawah bisa langsung dibuka; tidak lagi hanya daftar konsep.</p></div><span class="gmu107-pill">${modules.length} modul</span></div>
      <div class="gmu107-3">${modules.map(([a,p,d])=>`<div class="gmu107-card"><b>${h(a)}</b><p>${h(d)}</p><div class="gmu107-actionbar">${button(p,'Buka Modul')}</div></div>`).join('')}</div></div>`);
  }

  function enhanceWorkspace(){
    if(!q('#roleWorkspace')) return;
    const m=companyMetrics(); const alerts=safeWorkflowAlerts();
    let cards=[];
    if(isDirector()) cards=[['Target laba',money(TARGET.netProfit),'Sasaran laba bersih perusahaan'],['Laba kegiatan',money(m.profit),'Belum termasuk seluruh overhead'],['Pending approval',m.pending,'Pengecualian yang perlu keputusan'],['Status margin',pct(m.m),statusLabel(m.m,25,20)]];
    else if(isManager()) cards=[['Booking bulan ini',m.bookings,'Target minimum 3'],['Trip mendatang',m.upcoming,'Harus readiness 100%'],['Piutang',money(m.receivable),'Tidak boleh overdue tanpa tindakan'],['Alert',alerts.length,'Masuk recovery bila berulang']];
    else if(isSales()) cards=[['Lead bulan ini',m.lead,'Target qualified 20'],['Penawaran',m.quote,'Target 12'],['Booking',m.bookings,'Minimum 3'],['Target harian','10 + 10','Prospek + tindak lanjut']];
    else if(isFinance()) cards=[['Piutang bulan ini',money(m.receivable),'Follow-up collection'],['Pembayaran',money(m.paid),'Tercatat bulan ini'],['Biaya aktual',money(m.cost),'Tercatat bulan ini'],['Pending approval',m.pending,'Periksa sesuai kewenangan']];
    else cards=[['Trip mendatang',m.upcoming,'Fokus kesiapan'],['Alert',alerts.length,'Perlu ditindaklanjuti'],['Kegiatan aktif',activeBookings().length,'Belum closed/completed'],['Role',role(),'Hak akses mengikuti jabatan']];
    mount('roleWorkspace','role-live',`<div class="card section"><div class="gmu107-title"><div><h4>Kontrol Kerja Saya — Aktual</h4><p>Ringkasan langsung sesuai role dan data ERP.</p></div><span class="gmu107-pill">${h(role())}</span></div><div class="gmu107-grid">${cards.map(([a,b,c])=>`<div class="gmu107-card"><small>${h(a)}</small><b>${h(b)}</b><p>${h(c)}</p></div>`).join('')}</div><div class="gmu107-actionbar">${button('myTasksHub','Tugas Saya')}${button('salesTargetControl','Target & Kinerja')}${button('workflow','Persetujuan')}${button('companySystemCenter','Semua Modul')}</div></div>`);
  }

  function refresh(){
    enhanceCompanyControl(); enhanceSalesTarget(); enhanceTasks(); enhanceTreasury(); enhancePeople(); enhanceQuality(); enhanceGrowth(); enhanceGovernance(); enhanceAI(); enhanceSystemCenter(); enhanceWorkspace();
    try { window.GmuRoleNavigationGuard?.sync?.(); } catch(_){}
  }

  function wrapRenderAll(){
    try {
      if(typeof renderAll!=='function' || renderAll.__gmuV107Wrapped) return;
      const original=renderAll;
      const wrapped=function(...args){ const r=original.apply(this,args); queueMicrotask(refresh); return r; };
      wrapped.__gmuV107Wrapped=true; wrapped.__gmuV107Original=original; window.renderAll=wrapped;
    } catch(_){}
  }

  function init(){
    installStyle();
    document.addEventListener('click',e=>{ const b=e.target.closest('[data-gmu107-go]'); if(!b)return; e.preventDefault(); go(b.dataset.gmu107Go); });
    const timer=setInterval(()=>{ if(!role()) return; wrapRenderAll(); refresh(); },500);
    setTimeout(()=>clearInterval(timer),20000);
    window.GmuCompanyDetailControls=Object.freeze({version:VERSION,refresh});
  }

  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',init,{once:true}); else init();
})();
