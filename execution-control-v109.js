(() => {
  'use strict';

  const VERSION = 'v10.9-execution-control-layer';
  const q = (s, r = document) => r.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');

  function role(){ try { return String(profile?.role || ''); } catch (_) { return ''; } }
  function appData(){ try { return data || {}; } catch (_) { return {}; } }
  function arr(name){ const d=appData(); return Array.isArray(d[name]) ? d[name] : []; }
  function today(){ const d=new Date(); d.setHours(0,0,0,0); return d; }
  function daysTo(v){ if(!v) return null; const d=new Date(`${String(v).slice(0,10)}T00:00:00`); if(Number.isNaN(d.getTime())) return null; return Math.round((d-today())/86400000); }
  function bookingRevenue(b){ return Number(b?.pax||0) * Number(b?.price_per_pax||0); }
  function paidFor(id){ return arr('payments').filter(x=>x.booking_id===id).reduce((a,x)=>a+(x.payment_type==='Refund'?-Number(x.amount||0):Number(x.amount||0)),0); }
  function costFor(id){ return arr('costs').filter(x=>x.booking_id===id).reduce((a,x)=>a+Number(x.actual_amount||0),0); }
  function safeAlerts(){ try { return typeof workflowAlerts==='function' ? (workflowAlerts()||[]) : []; } catch (_) { return []; } }

  const ROLE_FLOW = {
    'Owner': ['Kendalikan target laba, kas, risiko dan keputusan strategis','Tinjau hanya approval strategis/critical','Pastikan Manager memiliki recovery plan bila forecast meleset'],
    'Director': ['Kendalikan target laba, kas, risiko dan keputusan strategis','Tinjau hanya approval strategis/critical','Pastikan Manager memiliki recovery plan bila forecast meleset'],
    'Direktur': ['Kendalikan target laba, kas, risiko dan keputusan strategis','Tinjau hanya approval strategis/critical','Pastikan Manager memiliki recovery plan bila forecast meleset'],
    'Manager': ['Buka target & gap hari ini','Selesaikan overdue lintas Sales/Ops/Finance','Pastikan H-7/H-3/H-1 dan trip closing berjalan','Buat recovery plan untuk target yang meleset'],
    'Manager EduTrans': ['Buka target & gap hari ini','Selesaikan overdue lintas Sales/Ops/Finance','Pastikan H-7/H-3/H-1 dan trip closing berjalan','Buat recovery plan untuk target yang meleset'],
    'Sales': ['Kerjakan 10 prospek','Kerjakan 10 tindak lanjut','Naikkan qualified lead → quotation → booking','Catat lost reason bila gagal'],
    'Admin': ['Lengkapi data booking','Pastikan manifest dan Berkas Kegiatan','Tutup dokumen H-1 dan H+1'],
    'Operation': ['Cek kesiapan trip terdekat','Pastikan vendor/crew/rundown/manifest','Laporkan hambatan sebelum critical'],
    'Operasional': ['Cek kesiapan trip terdekat','Pastikan vendor/crew/rundown/manifest','Laporkan hambatan sebelum critical'],
    'Finance': ['Cek invoice dan pembayaran','Tindak lanjuti piutang overdue','Verifikasi biaya aktual dan vendor payment','Tutup keuangan trip maksimal H+3'],
    'Keuangan': ['Cek invoice dan pembayaran','Tindak lanjuti piutang overdue','Verifikasi biaya aktual dan vendor payment','Tutup keuangan trip maksimal H+3'],
    'TL': ['Baca rundown dan operation sheet','Briefing crew','Jalankan trip sesuai SOP keselamatan','Kirim laporan dan insiden H+1'],
    'Crew': ['Konfirmasi penugasan','Hadir sesuai call time','Jalankan checklist tugas','Kirim bukti/hasil pekerjaan'],
  };

  function installStyle(){
    if(q('#gmuV109Style')) return;
    const s=document.createElement('style'); s.id='gmuV109Style'; s.textContent=`
      .g109{margin-top:12px}.g109-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.g109-3{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.g109-2{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.g109-card{border:1px solid var(--line);border-radius:14px;background:#fff;padding:12px}.g109-card h4{margin:0 0 6px;color:var(--gd)}.g109-card small{display:block;font-size:8px;color:var(--muted)}.g109-card b{display:block;color:var(--gd);font-size:15px;margin:3px 0}.g109-card p,.g109-card li{font-size:9px;color:var(--muted);line-height:1.55}.g109-card ul,.g109-card ol{padding-left:18px;margin:7px 0}.g109-row{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;padding:9px 0;border-bottom:1px solid var(--line);font-size:9px}.g109-row:last-child{border-bottom:0}.g109-pill{font-size:8px;padding:4px 8px;border-radius:999px;border:1px solid var(--line);background:#f5faf7;white-space:nowrap}.g109-warn{background:#fff9ed;border-color:#ead9ac}.g109-critical{background:#fff3f3;border-color:#efcaca}.g109-actions{display:flex;gap:7px;flex-wrap:wrap;margin-top:10px}.g109-btn{border:1px solid var(--line);background:#fff;color:var(--gd);border-radius:10px;padding:8px 10px;font:inherit;font-size:9px;cursor:pointer}.g109-empty{padding:12px;border:1px dashed var(--line);border-radius:12px;color:var(--muted);font-size:9px}.g109-table{width:100%;border-collapse:collapse;font-size:9px}.g109-table th,.g109-table td{padding:8px;border-bottom:1px solid var(--line);text-align:left}.g109-table th{font-size:8px;color:var(--muted)}@media(max-width:960px){.g109-grid{grid-template-columns:repeat(2,1fr)}.g109-3{grid-template-columns:1fr 1fr}}@media(max-width:620px){.g109-grid,.g109-3,.g109-2{grid-template-columns:1fr}}
    `; document.head.appendChild(s);
  }

  function go(page){ try { if(typeof navTo==='function' && q(`#${page}`)){ navTo(page); return; } } catch(_){} document.querySelectorAll('.page').forEach(x=>x.classList.remove('active')); q(`#${page}`)?.classList.add('active'); document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page===page)); }
  function btn(page,label){ return `<button class="g109-btn" data-g109-go="${h(page)}">${h(label)}</button>`; }
  function mount(pageId,key,html){ const page=q(`#${pageId}`); if(!page) return; let box=q(`[data-g109="${key}"]`,page); if(!box){box=document.createElement('div');box.className='g109';box.dataset.g109=key;page.appendChild(box);} box.innerHTML=html; }

  function ensureNav(page,label,allowed=true){ if(!allowed) return; const nav=q('#nav'); if(!nav || q(`#nav [data-page="${page}"]`)) return; const b=document.createElement('button'); b.type='button'; b.dataset.page=page; b.textContent=label; b.addEventListener('click',()=>go(page)); nav.appendChild(b); }
  function ensurePage(page,label){ if(q(`#${page}`)) return q(`#${page}`); const host=q('.content')||q('main')||document.body; const el=document.createElement('section'); el.id=page; el.className='page'; el.innerHTML=`<div class="page-head"><div><h2>${h(label)}</h2><p>GMU EduTrans • Execution & Control Layer</p></div></div>`; host.appendChild(el); return el; }

  function tripLifecycleRows(){
    const rows=[];
    for(const b of arr('bookings')){
      const d=daysTo(b.trip_date); if(d===null) continue;
      let stage='',owner='',task='';
      if(d===7){stage='H-7';owner='Manager + Ops';task='Kunci vendor, crew, RAB, dokumen dan kebutuhan kegiatan';}
      else if(d===3){stage='H-3';owner='Manager + Ops';task='Rekonfirmasi vendor/crew/customer dan cek kesiapan';}
      else if(d===1){stage='H-1';owner='Ops + TL';task='Final manifest, rundown, briefing, emergency contact dan departure info';}
      else if(d===-1){stage='H+1';owner='TL + Ops';task='Laporan, insiden, dokumen, feedback dan repeat-order trigger';}
      else if(d===-3){stage='H+3';owner='Finance + Manager';task='Actual cost, vendor payment, laba/margin dan financial closing';}
      if(stage) rows.push({name:b.customer_name||b.customer||b.program_name||b.id||'Kegiatan',stage,owner,task});
    }
    return rows;
  }

  function buildTaskAutomation(){
    ensurePage('taskAutomationControl','Tugas Otomatis');
    const life=tripLifecycleRows(); const alerts=safeAlerts(); const pending=arr('approvals').filter(x=>String(x.status||'')==='Pending');
    mount('taskAutomationControl','main',`<div class="g109-3"><div class="g109-card"><small>Trigger Kegiatan Hari Ini</small><b>${life.length}</b><p>H-7/H-3/H-1/H+1/H+3 yang jatuh pada hari ini.</p></div><div class="g109-card"><small>Workflow Alert</small><b>${alerts.length}</b><p>Alert dari sistem existing.</p></div><div class="g109-card"><small>Menunggu Persetujuan</small><b>${pending.length}</b><p>Item approval yang belum selesai.</p></div></div><div class="g109-card" style="margin-top:10px"><h4>Siklus Otomatis Kegiatan</h4>${life.length?`<table class="g109-table"><thead><tr><th>Kegiatan</th><th>Tahap</th><th>PIC</th><th>Tugas wajib</th></tr></thead><tbody>${life.map(x=>`<tr><td>${h(x.name)}</td><td>${h(x.stage)}</td><td>${h(x.owner)}</td><td>${h(x.task)}</td></tr>`).join('')}</tbody></table>`:'<div class="g109-empty">Tidak ada trigger H-7/H-3/H-1/H+1/H+3 tepat hari ini.</div>'}<div class="g109-actions">${btn('myTasksHub','Tugas Saya')}${btn('workflow','Persetujuan')}${btn('operation','Operasional')}</div></div><div class="g109-card" style="margin-top:10px"><h4>Aturan Tugas</h4><ol><li>Setiap tugas harus punya PIC, deadline, prioritas, SOP, data terkait, bukti, dan status.</li><li>Status resmi: Belum Dimulai → Sedang Dikerjakan → Menunggu Pihak Lain → Selesai.</li><li>Tugas terlambat naik menjadi Perlu Perhatian; tugas kritis/escalation tidak boleh disembunyikan.</li><li>Direktur tidak menjadi PIC tugas operasional rutin.</li></ol></div>`);
  }

  function buildPayroll(){
    ensurePage('payrollControl','Penggajian & Fee');
    const assignments=arr('assignments'); const commissions=arr('commissions'); const attendance=arr('attendance');
    const earned=commissions.filter(x=>['Earned','Diperoleh'].includes(String(x.status||''))).reduce((a,x)=>a+Number(x.amount||0),0);
    mount('payrollControl','main',`<div class="g109-grid"><div class="g109-card"><small>Penugasan tercatat</small><b>${assignments.length}</b><p>Basis fee per kegiatan bila tabel assignment tersedia.</p></div><div class="g109-card"><small>Kehadiran tercatat</small><b>${attendance.length}</b><p>Transport/attendance allowance harus berbasis kehadiran terverifikasi.</p></div><div class="g109-card"><small>Komisi Earned</small><b>${money(earned)}</b><p>Komisi belum dibayar tetap terpisah dari komisi Paid.</p></div><div class="g109-card"><small>Status Backend</small><b>${assignments.length||commissions.length||attendance.length?'Data tersedia':'Belum terhubung penuh'}</b><p>Sistem tidak membuat nominal payroll palsu.</p></div></div><div class="g109-2" style="margin-top:10px"><div class="g109-card"><h4>Siklus Fee Kegiatan</h4><ol><li>Assignment dibuat Manager.</li><li>Crew menerima/menolak penugasan.</li><li>Trip selesai dan attendance diverifikasi.</li><li>Fee berubah menjadi Earned.</li><li>Finance verifikasi.</li><li>Pembayaran menjadi Paid dan tercatat sekali.</li></ol></div><div class="g109-card"><h4>Komponen Penghasilan</h4><ul><li>Gaji/retainer tetap bulanan.</li><li>Fee per trip/tugas.</li><li>Komisi Sales: Pending → Earned → Paid.</li><li>Bonus kinerja setelah syarat laba/margin/kas terpenuhi.</li><li>Reimbursement terpisah, wajib bukti dan approval.</li><li>Transport Manager Rp30.000/hari hadir sesuai kebijakan yang sudah ditetapkan.</li></ul></div></div><div class="g109-actions">${btn('peopleControl','SDM & Personalia')}${btn('finance','Keuangan')}${btn('rolePlaybook','Jobdesk & SOP Saya')}</div>`);
  }

  function buildRecruitment(){
    ensurePage('recruitmentControl','Rekrutmen & Onboarding');
    const employees=arr('employees'); const upcoming=arr('bookings').filter(b=>{const d=daysTo(b.trip_date);return d!==null&&d>=0&&d<=30;});
    mount('recruitmentControl','main',`<div class="g109-grid"><div class="g109-card"><small>SDM tercatat</small><b>${employees.length||'—'}</b><p>Jika data employee belum tersedia, ERP tidak mengarang headcount.</p></div><div class="g109-card"><small>Kegiatan 30 hari</small><b>${upcoming.length}</b><p>Input workload untuk perencanaan SDM.</p></div><div class="g109-card"><small>Prinsip</small><b>Capacity First</b><p>Tambah orang karena gap kapasitas, bukan karena panik.</p></div><div class="g109-card"><small>Volume belum stabil</small><b>Freelancer First</b><p>Permanent hire hanya bila ekonomi dan runway sehat.</p></div></div><div class="g109-card" style="margin-top:10px"><h4>Alur Rekrutmen Resmi</h4><ol><li>Workload/capacity gap terdeteksi.</li><li>ERP rekomendasikan: tanpa tambahan / freelancer / part-time / permanent.</li><li>Manager membuat staffing request: role, alasan, jumlah, target, budget, start date, impact.</li><li>Finance menghitung affordability, fixed cost dan dampak target.</li><li>Permanent hire → Direktur approval. Freelancer operasional dalam budget → Manager.</li><li>Candidate pipeline: Applied → Screening → Assessment → Interview → Offer → Hired/Rejected.</li><li>Hubungan kerja ditetapkan PKWTT/PKWT/freelancer/project/vendor sesuai aturan.</li><li>Onboarding 30/60/90 hari: akun ERP, jobdesk, SOP, KPI, code of conduct, training, akses dan kompensasi.</li><li>Evaluasi → coaching/PIP bila perlu; tindakan disiplin/PHK tetap keputusan manusia dan sesuai hukum.</li></ol></div><div class="g109-actions">${btn('workforcePlanningControl','Perencanaan SDM')}${btn('peopleControl','SDM & Personalia')}${btn('rolePlaybook','Jobdesk & SOP')}</div>`);
  }

  function buildQuality(){
    ensurePage('serviceRecoveryControl','Keluhan & CAPA');
    const complaints=arr('complaints'); const incidents=arr('incidents'); const open=complaints.filter(x=>!['Closed','Selesai'].includes(String(x.status||'')));
    mount('serviceRecoveryControl','main',`<div class="g109-grid"><div class="g109-card"><small>Keluhan tercatat</small><b>${complaints.length}</b></div><div class="g109-card"><small>Keluhan terbuka</small><b>${open.length}</b></div><div class="g109-card"><small>Insiden tercatat</small><b>${incidents.length}</b></div><div class="g109-card"><small>Critical Rule</small><b>Safety / Legal</b><p>Langsung eskalasi Manager + Direktur.</p></div></div><div class="g109-2" style="margin-top:10px"><div class="g109-card"><h4>Complaint Lifecycle</h4><ol><li>Terima & catat fakta.</li><li>Klasifikasi Ringan/Sedang/Berat/Kritis.</li><li>Tentukan PIC dan target response/resolve.</li><li>Lakukan service recovery sesuai authority.</li><li>Root cause analysis.</li><li>Corrective Action / Preventive Action.</li><li>Verifikasi efektivitas.</li><li>Close hanya setelah bukti lengkap.</li></ol></div><div class="g109-card"><h4>Evaluasi Pasca Trip</h4><ul><li>Kejelasan informasi.</li><li>Ketepatan waktu.</li><li>Pelayanan tim.</li><li>Kualitas program.</li><li>Safety & comfort.</li><li>Kesesuaian janji penawaran.</li><li>Kepuasan keseluruhan + repeat intent.</li></ul></div></div><div class="g109-actions">${btn('qualityControl','Mutu & Pelanggan')}${btn('triparchive','Trip Archive')}${btn('riskRegisterControl','Daftar Risiko')}</div>`);
  }

  function buildCashForecast(){
    ensurePage('cashForecastControl','Perkiraan Kas');
    const bs=arr('bookings');
    const horizons=[7,30,60,90].map(day=>{const rows=bs.filter(b=>{const d=daysTo(b.trip_date);return d!==null&&d>=0&&d<=day;});const rev=rows.reduce((a,b)=>a+bookingRevenue(b),0);const paid=rows.reduce((a,b)=>a+paidFor(b.id),0);const cost=rows.reduce((a,b)=>a+costFor(b.id),0);return {day,count:rows.length,receivable:Math.max(0,rev-paid),knownCost:cost};});
    mount('cashForecastControl','main',`<div class="g109-grid">${horizons.map(x=>`<div class="g109-card"><small>${x.day} Hari</small><b>${x.count} kegiatan</b><p>Piutang dikenal: ${money(x.receivable)}<br>Biaya aktual tercatat: ${money(x.knownCost)}</p></div>`).join('')}</div><div class="g109-card" style="margin-top:10px"><h4>Aturan Cash Forecast</h4><p>Forecast tidak memakai saldo bank palsu. Rumus final harus membaca: Total Cash/Bank − committed customer cash − AP jangka pendek − payroll due − pajak/kewajiban − trip funding. Runway = Available Cash / fixed cost bulanan.</p><div class="g109-actions">${btn('treasuryControl','Kas & Likuiditas')}${btn('finance','Keuangan')}${btn('growthControl','Pertumbuhan')}</div></div>`);
  }

  function buildWorkforce(){
    ensurePage('workforcePlanningControl','Perencanaan SDM');
    const next30=arr('bookings').filter(b=>{const d=daysTo(b.trip_date);return d!==null&&d>=0&&d<=30;}); const pax=next30.reduce((a,b)=>a+Number(b.pax||0),0); const assignments=arr('assignments');
    mount('workforcePlanningControl','main',`<div class="g109-grid"><div class="g109-card"><small>Kegiatan 30 hari</small><b>${next30.length}</b></div><div class="g109-card"><small>Pax 30 hari</small><b>${pax}</b></div><div class="g109-card"><small>Assignment tercatat</small><b>${assignments.length}</b></div><div class="g109-card"><small>Keputusan</small><b>Data-driven</b><p>Produktivitas dulu, rekrut jika gap kapasitas nyata.</p></div></div><div class="g109-card" style="margin-top:10px"><h4>Capacity → Workload → Staffing Gap → Cost Impact → Recommendation</h4><ul><li>Sales: active prospects, overdue, lead target, conversion.</li><li>Ops: trip/pax, dokumen, vendor, crew, H-7/H-3/H-1.</li><li>Finance: invoice, payment, AP/AR, closing workload.</li><li>Field: jumlah trip, durasi, lokasi, kompetensi dan call-time.</li><li>AI hanya merekomendasikan. Hiring permanent tetap approval manusia.</li></ul><div class="g109-actions">${btn('recruitmentControl','Rekrutmen')}${btn('peopleControl','SDM & Personalia')}${btn('salesTargetControl','Target & Kinerja')}</div></div>`);
  }

  function buildRisk(){
    ensurePage('riskRegisterControl','Daftar Risiko');
    const alerts=safeAlerts(); const critical=alerts.filter(x=>['critical','kritis','high','tinggi'].includes(String(x.level||'').toLowerCase()));
    const risks=[['Penjualan','Pipeline tidak cukup untuk target laba','Manager','Recovery plan + percepat lead/quotation'],['Kas','Piutang terlambat / trip funding tidak cukup','Finance + Manager','Collection + cash gate'],['Operasional','Readiness <80% / item critical hilang','Manager','Block ready-to-operate sampai resolved'],['Keselamatan','Insiden peserta/transport/medis','TL + Manager','Safety first + escalation'],['SDM','Capacity gap / overload / kompetensi','Manager','Rebalance → freelancer → hire gate'],['Vendor','Kegagalan layanan / kenaikan biaya','Ops + Manager','Backup vendor + review score'],['Data','Akses salah / data hilang / audit gap','Manager + Owner','Need-to-know + audit + backup']];
    mount('riskRegisterControl','main',`<div class="g109-grid"><div class="g109-card"><small>Alert aktif</small><b>${alerts.length}</b></div><div class="g109-card"><small>Alert high/critical</small><b>${critical.length}</b></div><div class="g109-card"><small>Risk Owner</small><b>Wajib</b><p>Setiap risiko punya pemilik, mitigasi, deadline dan review.</p></div><div class="g109-card"><small>Direktur</small><b>Exception Only</b><p>Masuk untuk risiko strategis/critical.</p></div></div><div class="g109-card" style="margin-top:10px"><table class="g109-table"><thead><tr><th>Kategori</th><th>Risiko</th><th>Pemilik</th><th>Mitigasi minimum</th></tr></thead><tbody>${risks.map(r=>`<tr><td>${h(r[0])}</td><td>${h(r[1])}</td><td>${h(r[2])}</td><td>${h(r[3])}</td></tr>`).join('')}</tbody></table><div class="g109-actions">${btn('governanceControl','Tata Kelola')}${btn('serviceRecoveryControl','Keluhan & CAPA')}${btn('aiCenter','Pusat AI')}</div></div>`);
  }

  function enhanceRoleExecution(){
    const page=q('#rolePlaybook'); if(!page) return; const flow=ROLE_FLOW[role()]||['Buka Tugas Saya','Ikuti SOP jabatan','Laporkan hambatan kepada atasan'];
    mount('rolePlaybook','today-execution',`<div class="g109-card"><h4>Urutan Kerja Saya Hari Ini</h4><ol>${flow.map(x=>`<li>${h(x)}</li>`).join('')}</ol><div class="g109-actions">${btn('myTasksHub','Tugas Saya')}${btn('taskAutomationControl','Tugas Otomatis')}${btn('salesTargetControl','Target & Kinerja')}</div></div>`);
  }

  function installNav(){
    const r=role(); const management=['Owner','Director','Direktur','Manager','Manager EduTrans']; const finance=management.includes(r)||['Finance','Keuangan'].includes(r); const hr=management.includes(r); const quality=management.includes(r)||['Admin','Operation','Operasional','TL'].includes(r);
    ensureNav('taskAutomationControl','Tugas Otomatis',!!r);
    ensureNav('payrollControl','Penggajian & Fee',finance||r==='Sales'||r==='Crew'||r==='TL');
    ensureNav('recruitmentControl','Rekrutmen & Onboarding',hr);
    ensureNav('serviceRecoveryControl','Keluhan & CAPA',quality);
    ensureNav('cashForecastControl','Perkiraan Kas',finance);
    ensureNav('workforcePlanningControl','Perencanaan SDM',hr);
    ensureNav('riskRegisterControl','Daftar Risiko',management.includes(r));
  }

  function render(){ if(!role()) return; installStyle(); installNav(); buildTaskAutomation(); buildPayroll(); buildRecruitment(); buildQuality(); buildCashForecast(); buildWorkforce(); buildRisk(); enhanceRoleExecution(); window.GmuExecutionControl=Object.freeze({version:VERSION,refresh:render}); }
  function init(){ const t=setInterval(()=>{render(); if(role()&&q('#nav')) clearInterval(t);},250); setTimeout(()=>clearInterval(t),15000); document.addEventListener('click',e=>{const b=e.target.closest('[data-g109-go]');if(b)go(b.dataset.g109Go);}); setInterval(()=>{if(role())render();},30000); }
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',init,{once:true}); else init();
})();
