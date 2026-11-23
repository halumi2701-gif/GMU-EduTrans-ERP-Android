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
