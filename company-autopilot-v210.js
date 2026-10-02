(() => {
  'use strict';

  const VERSION = 'v21-company-autopilot';
  const ROLES = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans']);
  const q = (s,r=document) => r.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const state = {data:null,loading:false,error:null};

  function role(){ try{return String(profile?.role||'')}catch(_){return ''} }
  function db(){ try{return typeof sb!=='undefined'&&sb?.rpc?sb:null}catch(_){return null} }
  function allowed(){ return ROLES.has(role()); }

  function ensureStyle(){
    if(q('#gmuAutopilotV21Style')) return;
    const s=document.createElement('style'); s.id='gmuAutopilotV21Style'; s.textContent=`
      .ga21-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;flex-wrap:wrap}.ga21-head h2{margin:0;color:var(--gd)}.ga21-head p{margin:5px 0 0;font-size:9px;color:var(--muted);max-width:820px}.ga21-head button{border:1px solid var(--line);background:#fff;color:var(--gd);border-radius:9px;padding:7px 10px;font:inherit;font-size:8px;cursor:pointer}.ga21-banner{margin:12px 0;padding:12px 14px;border-radius:14px;background:#eef9f1;border:1px solid #cce8d5;display:flex;justify-content:space-between;gap:12px;align-items:center}.ga21-banner.warn{background:#fff2ef;border-color:#efc7bf}.ga21-banner b{font-size:12px;color:var(--gd)}.ga21-banner span{font-size:8px;color:var(--muted)}.ga21-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.ga21-card{border:1px solid var(--line);background:#fff;border-radius:14px;padding:12px}.ga21-card small{display:block;font-size:8px;color:var(--muted)}.ga21-card strong{display:block;font-size:20px;color:var(--gd);margin:4px 0}.ga21-card p{font-size:8px;color:var(--muted);line-height:1.5;margin:0}.ga21-flow{margin-top:10px;padding:12px;border-radius:14px;background:#f5f8f6;border:1px solid var(--line);font-size:9px;line-height:1.6;color:var(--gd)}.ga21-section{margin-top:11px}.ga21-section h4{margin:0 0 7px;color:var(--gd)}.ga21-error{padding:10px;border:1px solid #efc5c5;background:#fff4f4;color:#963434;border-radius:12px;font-size:9px}.ga21-loading{padding:14px;border:1px dashed var(--line);border-radius:12px;color:var(--muted);font-size:9px;text-align:center}@media(max-width:900px){.ga21-grid{grid-template-columns:1fr 1fr}}@media(max-width:620px){.ga21-grid{grid-template-columns:1fr}.ga21-banner{align-items:flex-start;flex-direction:column}}
    `; document.head.appendChild(s);
  }

  function ensurePage(){
    if(!allowed()) return null;
    let page=q('#companyAutopilotV21');
    if(!page){
      page=document.createElement('section'); page.id='companyAutopilotV21'; page.className='page';
      page.innerHTML=`<div class="ga21-head"><div><div class="ga21-kicker">GMU EduTrans • Enterprise Automation</div><h2>Company Autopilot v21</h2><p>Lead → Follow-up → Quotation → Payment → Booking → Operasional → Crew/Vendor → Trip → Finance → Payroll → Feedback → Repeat Order. Aksi sensitif tetap melalui approval manusia.</p></div><button type="button" data-ga21-refresh>Refresh</button></div><div data-ga21-body></div>`;
      (q('.content')||q('main')||document.body).appendChild(page);
      q('[data-ga21-refresh]',page)?.addEventListener('click',load);
    }
    const nav=q('#nav');
    if(nav&&!q('#nav [data-page="companyAutopilotV21"]')){
      const b=document.createElement('button'); b.type='button'; b.dataset.page='companyAutopilotV21'; b.textContent='Company Autopilot';
      b.addEventListener('click',show); nav.appendChild(b);
    }
    return page;
  }

  function show(){
    document.querySelectorAll('.page').forEach(x=>x.classList.remove('active'));
    q('#companyAutopilotV21')?.classList.add('active');
    document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page==='companyAutopilotV21'));
    if(q('#title')) q('#title').textContent='Company Autopilot';
    load();
  }

  function card(label,value,desc){return `<div class="ga21-card"><small>${h(label)}</small><strong>${h(value)}</strong><p>${h(desc)}</p></div>`}

  function render(){
    const body=q('[data-ga21-body]'); if(!body) return;
    if(state.loading&&!state.data){body.innerHTML='<div class="ga21-loading">Membaca status otomatisasi perusahaan…</div>';return}
    if(state.error){body.innerHTML=`<div class="ga21-error">${h(state.error)}</div>`;return}
    const d=state.data||{}, a=d.automation||{}, m=d.manager_score||{}, x=d.director||{};
    body.innerHTML=`
      <div class="ga21-banner ${x.action_required?'warn':''}"><div><b>${x.action_required?'TINDAKAN DIREKTUR DIPERLUKAN':'TIDAK ADA TINDAKAN DIREKTUR'}</b><span>${x.action_required?'Ada exception kritis strategis yang membutuhkan keputusan Owner/Direktur.':'Operasional tetap ditangani Manager, tim, ERP dan automation engine.'}</span></div><strong>${Number(x.critical_exceptions||0)} critical</strong></div>
      <div class="ga21-section"><h4>Automation Coverage Aktual</h4><div class="ga21-grid">
        ${card('Sequence Follow-up Aktif',Number(a.active_followup_sequences||0),'D+1, D+3, D+7, D+14; berhenti otomatis saat WON/LOST/booking.')}
        ${card('Follow-up Jatuh Tempo',Number(a.followup_due||0),'Antrian yang siap dikirim melalui notification delivery.')}
        ${card('Resource Request Terbuka',Number(a.resource_requests_open||0),'Crew, vendor, dokumen, customer info dan kebutuhan finance.')}
        ${card('Retention Aktif',Number(a.retention_active||0),'Terima kasih → feedback → testimonial → repeat order → nurture.')}
        ${card('Notifikasi Menunggu/Retry',Number(a.notifications_queued||0),'Delivery Center menangani kanal, retry dan kegagalan provider.')}
        ${card('Payment Pending',Number(a.payment_orders_pending||0),'Payment gateway order yang belum terminal.')}
      </div></div>
      <div class="ga21-section"><h4>Manager Accountability</h4><div class="ga21-grid">
        ${card('Recovery Terbuka',Number(m.open_recovery||0),'Recovery task HIGH/CRITICAL yang belum selesai.')}
        ${card('Tugas Terlambat',Number(m.overdue_tasks||0),'Tugas otomatis melewati deadline.')}
        ${card('Exception Terbuka',Number(m.open_exceptions||0),'Exception hanya ditutup bila data sumber kembali sehat.')}
      </div></div>
      <div class="ga21-flow"><b>Operating model v21:</b> Sistem mendeteksi → AI/Rule Engine menentukan next action → tugas/notifikasi dibuat → PIC mengeksekusi → deadline dipantau → data sumber diverifikasi → exception ditutup otomatis bila benar-benar pulih → Direktur hanya menerima keputusan strategis. Transfer uang, refund, payroll payment, perubahan harga strategis, margin &lt;20%, reserve, hiring/firing, legal dan safety tetap memakai approval manusia.</div>`;
  }

  async function load(){
    if(!allowed()||!db()||state.loading) return;
    state.loading=true; state.error=null; render();
    try{
      const {data,error}=await db().rpc('internal_company_autopilot_v21');
      if(error) throw error; state.data=data||{};
    }catch(e){state.error=e?.message||String(e)}finally{state.loading=false;render()}
  }

  function boot(){
    if(!allowed()) return;
    ensureStyle(); ensurePage(); load();
    setInterval(()=>{if(allowed()) load()},60000);
    window.__GMU_COMPANY_AUTOPILOT_V21__={version:VERSION,show,load};
  }

  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',boot,{once:true}); else boot();
})();