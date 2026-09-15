(() => {
  'use strict';

  const VERSION = 'v21.4-company-control-center';
  const MANAGEMENT = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans']);
  const FINANCE = new Set(['Finance']);
  const PAYMENT_CREATORS = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Finance','Admin','Operation']);
  const q = (s,r=document) => r.querySelector(s);
  const esc = v => String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = v => new Intl.NumberFormat('id-ID',{style:'currency',currency:'IDR',maximumFractionDigits:0}).format(Number(v||0));
  const fmtDate = v => v ? new Date(v).toLocaleDateString('id-ID',{day:'2-digit',month:'short',year:'numeric'}) : '—';
  const state = { summary:null, payments:[], accruals:[], recruitments:[], loading:false, error:null };

  function role(){ try { return String(profile?.role || ''); } catch (_) { return ''; } }
  function db(){ try { return typeof sb !== 'undefined' && sb?.from ? sb : null; } catch (_) { return null; } }
  function allowed(){ return MANAGEMENT.has(role()) || FINANCE.has(role()) || PAYMENT_CREATORS.has(role()); }
  function canManage(){ return MANAGEMENT.has(role()) || FINANCE.has(role()); }
  function canCreatePayment(){ return PAYMENT_CREATORS.has(role()); }

  function ensureStyle(){
    if(q('#gmuV214Style')) return;
    const s=document.createElement('style'); s.id='gmuV214Style'; s.textContent=`
      .v214-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;flex-wrap:wrap}.v214-head h2{margin:0;color:var(--gd)}.v214-head p{margin:5px 0 0;font-size:9px;color:var(--muted);max-width:820px}.v214-actions{display:flex;gap:6px;flex-wrap:wrap}.v214-btn{border:1px solid var(--line);background:#fff;color:var(--gd);border-radius:9px;padding:7px 10px;font:inherit;font-size:8px;cursor:pointer}.v214-btn.primary{background:var(--gd);color:#fff}.v214-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin-top:12px}.v214-card{border:1px solid var(--line);background:#fff;border-radius:13px;padding:11px}.v214-card small{display:block;color:var(--muted);font-size:8px}.v214-card strong{display:block;color:var(--gd);font-size:16px;margin:4px 0}.v214-card p{font-size:8px;color:var(--muted);margin:0;line-height:1.45}.v214-section{margin-top:14px}.v214-section h4{margin:0 0 7px;color:var(--gd)}.v214-table{width:100%;border-collapse:collapse;font-size:8px}.v214-table th,.v214-table td{text-align:left;padding:8px 6px;border-top:1px solid var(--line);vertical-align:top}.v214-table th{color:var(--muted);font-weight:600}.v214-pill{display:inline-block;padding:3px 7px;border-radius:999px;background:#f1f5f2;color:var(--gd);font-size:7px;font-weight:700}.v214-pill.bad{background:#fff0f0;color:#9c3434}.v214-pill.warn{background:#fff5e8;color:#945d12}.v214-note{padding:10px 12px;border-radius:12px;background:#f5f8f6;border:1px solid var(--line);font-size:8px;line-height:1.55;color:var(--gd)}.v214-error{padding:10px 12px;border-radius:12px;background:#fff4f4;border:1px solid #efc5c5;color:#963434;font-size:9px}.v214-scroll{overflow:auto;border:1px solid var(--line);border-radius:12px;background:#fff}@media(max-width:900px){.v214-grid{grid-template-columns:1fr 1fr}}@media(max-width:620px){.v214-grid{grid-template-columns:1fr}.v214-actions{width:100%}.v214-table{min-width:760px}}
    `; document.head.appendChild(s);
  }

  function ensurePage(){
    if(!allowed()) return null;
    let page=q('#companyControlV214');
    if(!page){
      page=document.createElement('section'); page.id='companyControlV214'; page.className='page';
      page.innerHTML=`<div class="v214-head"><div><div style="font-size:8px;color:var(--muted)">GMU EduTrans • Integrated Company Control</div><h2>Company Control Center v21.4</h2><p>Lapisan integrasi tambahan di atas Compensation Autopilot v21.3. Seluruh fitur ERP sebelumnya tetap aktif.</p></div><div class="v214-actions">${canCreatePayment()?'<button type="button" class="v214-btn primary" data-v214-new-payment>+ Payment Request</button>':''}<button type="button" class="v214-btn" data-v214-refresh>Refresh</button></div></div><div data-v214-body></div>`;
      (q('.content')||q('main')||document.body).appendChild(page);
      q('[data-v214-refresh]',page)?.addEventListener('click',load);
      q('[data-v214-new-payment]',page)?.addEventListener('click',newPaymentRequest);
    }
    const nav=q('#nav');
    if(nav&&!q('#nav [data-page="companyControlV214"]')){
      const b=document.createElement('button'); b.type='button'; b.dataset.page='companyControlV214'; b.textContent='Company Control'; b.addEventListener('click',show); nav.appendChild(b);
    }
    return page;
  }

  function show(){
    document.querySelectorAll('.page').forEach(x=>x.classList.remove('active'));
    q('#companyControlV214')?.classList.add('active');
    document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page==='companyControlV214'));
    if(q('#title')) q('#title').textContent='Company Control';
    load();
  }

  function card(label,value,desc){ return `<div class="v214-card"><small>${esc(label)}</small><strong>${esc(value)}</strong><p>${esc(desc)}</p></div>`; }
  function pill(value){ const v=String(value||'—'); const bad=['OVERDUE','ON_HOLD','REJECTED','FAILED'].includes(v); const warn=['SUBMITTED','UNPAID','NEED_REVIEW','SOURCING','INTERVIEW','OFFER','ONBOARDING'].includes(v); return `<span class="v214-pill ${bad?'bad':warn?'warn':''}">${esc(v)}</span>`; }

  function render(){
    const body=q('[data-v214-body]'); if(!body) return;
    if(state.loading&&!state.summary){ body.innerHTML='<div class="v214-note">Membaca kendali perusahaan…</div>'; return; }
    if(state.error){ body.innerHTML=`<div class="v214-error">${esc(state.error)}</div>`; return; }
    const s=state.summary||{};
    const revenueTarget=Number(s.revenue_target||50000000), revenue=Number(s.revenue_realized||0), cash=Number(s.cash_collected||0), pipeline=Number(s.pipeline_value||0);
    const progress=revenueTarget>0?Math.min(100,(revenue/revenueTarget)*100):0;
    body.innerHTML=`
      <div class="v214-grid">
        ${card('Target Omzet',money(revenueTarget),`Realisasi ${progress.toFixed(1)}% bulan berjalan.`)}
        ${card('Revenue Realized',money(revenue),'Omzet dan cash dipisahkan agar keputusan fee tidak salah.')}
        ${card('Cash Collected',money(cash),'Dasar aman untuk pembayaran, komisi, dan bonus.')}
        ${card('Pipeline',money(pipeline),`Target coverage ${Number(s.pipeline_coverage_target_x||3).toFixed(1)}× omzet.`)}
        ${card('Payment Approval',String(s.payments_waiting_approval||0),'Permintaan pembayaran menunggu keputusan.')}
        ${card('Overdue Payment',String(s.overdue_payments||0),'Pembayaran belum selesai melewati due date.')}
        ${card('Recruitment Aktif',String(s.open_recruitments||0),'Kebutuhan SDM yang masih dalam pipeline.')}
        ${card('Fee/Bonus Hold',String(s.compensation_on_hold||0),`Margin floor ${Number(s.healthy_margin_floor_pct||25).toFixed(0)}%.`)}
      </div>
      <div class="v214-section"><h4>Payment Requests</h4>${renderPayments()}</div>
      <div class="v214-section"><h4>Compensation Accrual</h4>${renderAccruals()}</div>
      <div class="v214-section"><h4>Recruitment & Workforce</h4>${renderRecruitments()}</div>
      <div class="v214-section"><div class="v214-note"><b>Control rule:</b> Payment Request hanya membuat permintaan pembayaran, bukan memindahkan uang. Transfer, refund, payroll payment, compensation change, strategic pricing, hiring/firing permanen, legal dan safety-critical action tetap membutuhkan approval manusia.</div></div>`;
  }

  function renderPayments(){
    if(!state.payments.length) return '<div class="v214-note">Belum ada payment request yang terlihat untuk role ini.</div>';
    return `<div class="v214-scroll"><table class="v214-table"><thead><tr><th>No.</th><th>Kategori</th><th>Penerima</th><th>Tujuan</th><th>Nominal</th><th>Jatuh Tempo</th><th>Approval</th><th>Payment</th></tr></thead><tbody>${state.payments.map(x=>`<tr><td>${esc(x.request_no)}</td><td>${esc(x.category)}</td><td>${esc(x.payee_name)}</td><td>${esc(x.purpose)}</td><td>${money(x.amount)}</td><td>${fmtDate(x.due_date)}</td><td>${pill(x.approval_state)}</td><td>${pill(x.payment_state)}</td></tr>`).join('')}</tbody></table></div>`;
  }

  function renderAccruals(){
    if(!canManage()) return '<div class="v214-note">Detail fee hanya terlihat sesuai hak akses.</div>';
    if(!state.accruals.length) return '<div class="v214-note">Belum ada accrual fee/bonus.</div>';
    return `<div class="v214-scroll"><table class="v214-table"><thead><tr><th>Policy</th><th>Booking</th><th>Source</th><th>Calculated</th><th>Collection</th><th>Margin</th><th>Status</th></tr></thead><tbody>${state.accruals.map(x=>`<tr><td>${esc(x.policy_code||'—')}</td><td>${esc(x.booking_id||'—')}</td><td>${money(x.source_amount)}</td><td>${money(x.calculated_amount)}</td><td>${(Number(x.collection_ratio||0)*100).toFixed(0)}%</td><td>${x.margin_pct==null?'—':Number(x.margin_pct).toFixed(1)+'%'}</td><td>${pill(x.state)}</td></tr>`).join('')}</tbody></table></div>`;
  }

  function renderRecruitments(){
    if(!MANAGEMENT.has(role())) return '<div class="v214-note">Recruitment dikelola Owner/Director/Manager.</div>';
    if(!state.recruitments.length) return '<div class="v214-note">Tidak ada recruitment case aktif.</div>';
    return `<div class="v214-scroll"><table class="v214-table"><thead><tr><th>Posisi</th><th>Tipe</th><th>Alasan</th><th>Need By</th><th>Status</th><th>Kandidat</th></tr></thead><tbody>${state.recruitments.map(x=>`<tr><td>${esc(x.position_title)}</td><td>${esc(x.employment_type)}</td><td>${esc(x.reason)}</td><td>${fmtDate(x.need_by)}</td><td>${pill(x.status)}</td><td>${esc(x.candidate_name||'—')}</td></tr>`).join('')}</tbody></table></div>`;
  }

  async function load(){
    if(!allowed()||!db()||state.loading) return;
    state.loading=true; state.error=null; render();
    try{
      const summaryQ=await db().from('v_company_command_center').select('*').maybeSingle();
      if(summaryQ.error) throw summaryQ.error; state.summary=summaryQ.data||{};
      const p=await db().from('payment_requests').select('request_no,category,payee_name,purpose,amount,due_date,approval_state,payment_state,requested_at').order('requested_at',{ascending:false}).limit(12);
      if(p.error) throw p.error; state.payments=p.data||[];
      if(canManage()){
        const a=await db().from('compensation_accruals').select('policy_code,booking_id,source_amount,calculated_amount,collection_ratio,margin_pct,state,created_at').order('created_at',{ascending:false}).limit(12);
        if(a.error) throw a.error; state.accruals=a.data||[];
      }
      if(MANAGEMENT.has(role())){
        const r=await db().from('recruitment_cases').select('position_title,employment_type,reason,need_by,status,candidate_name,created_at').in('status',['NEED_REVIEW','APPROVED','SOURCING','INTERVIEW','OFFER','ONBOARDING']).order('created_at',{ascending:false}).limit(12);
        if(r.error) throw r.error; state.recruitments=r.data||[];
      }
    }catch(e){ state.error=e?.message||String(e); }
    finally{ state.loading=false; render(); }
  }

  async function currentUserId(){
    try { const {data}=await db().auth.getUser(); return data?.user?.id || profile?.id || null; } catch (_) { return profile?.id || null; }
  }

  async function newPaymentRequest(){
    if(!canCreatePayment()||!db()) return;
    const categories=['VENDOR','CREW','PAYROLL','COMMISSION','BONUS','REFUND','REIMBURSEMENT','OFFICE','TAX','OTHER'];
    const category=String(prompt(`Kategori (${categories.join(', ')}):`,'VENDOR')||'').trim().toUpperCase();
    if(!categories.includes(category)){ alert('Kategori tidak valid.'); return; }
    const payee=String(prompt('Nama penerima pembayaran:','')||'').trim(); if(!payee) return;
    const purpose=String(prompt('Tujuan pembayaran:','')||'').trim(); if(!purpose) return;
    const amount=Number(String(prompt('Nominal (Rp):','0')||'').replace(/[^0-9.-]/g,'')); if(!Number.isFinite(amount)||amount<=0){ alert('Nominal harus lebih dari 0.'); return; }
    const booking=String(prompt('Booking ID (boleh kosong):','')||'').trim()||null;
    const due=String(prompt('Jatuh tempo YYYY-MM-DD (boleh kosong):','')||'').trim()||null;
    if(!confirm(`Buat Payment Request ${category} sebesar ${money(amount)} untuk ${payee}? Ini belum membayar uang; hanya membuat permintaan approval.`)) return;
    const uid=await currentUserId();
    const requestNo=`PAY-${new Date().toISOString().replace(/[-:.TZ]/g,'').slice(0,14)}-${Math.floor(Math.random()*900+100)}`;
    const {error}=await db().from('payment_requests').insert({request_no:requestNo,category,booking_id:booking,payee_name:payee,amount,purpose,due_date:due,requested_by:uid,approval_state:'SUBMITTED',payment_state:'UNPAID'});
    if(error){ alert(error.message||String(error)); return; }
    alert(`Payment Request ${requestNo} dibuat dan menunggu approval.`); await load();
  }

  function boot(){ if(!allowed()) return; ensureStyle(); ensurePage(); load(); window.__GMU_COMPANY_CONTROL_V214__={version:VERSION,show,load}; }
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',boot,{once:true}); else boot();
})();