(() => {
  'use strict';

  const VERSION = 'v22.0-customer-account-crm';
  const ROLES = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Admin','Sales']);
  const MANAGERS = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Admin']);
  const q = (s, r = document) => r.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const dt = v => v ? new Date(v).toLocaleString('id-ID',{dateStyle:'medium',timeStyle:'short'}) : '—';
  const state = { accounts: [], sales: [], loading: false, error: null };

  function role(){ try { return String(profile?.role || ''); } catch (_) { return ''; } }
  function uid(){ try { return String(profile?.id || user?.id || ''); } catch (_) { return ''; } }
  function db(){ try { return typeof sb !== 'undefined' && sb?.from ? sb : null; } catch (_) { return null; } }
  function allowed(){ return ROLES.has(role()); }
  function canManage(){ return MANAGERS.has(role()); }

  function installStyle(){
    if(q('#gmuCustomerAccountStyle')) return;
    const s=document.createElement('style'); s.id='gmuCustomerAccountStyle'; s.textContent=`
      .gca{margin-top:12px}.gca-head{display:flex;justify-content:space-between;align-items:flex-start;gap:10px;flex-wrap:wrap}.gca-head h3{margin:0;color:var(--gd)}
      .gca-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px;margin-top:10px}.gca-card{background:#fff;border:1px solid var(--line);border-radius:13px;padding:11px}.gca-card small{display:block;color:var(--muted);font-size:8px}.gca-card b{display:block;color:var(--gd);font-size:17px;margin-top:3px}
      .gca-list{margin-top:10px;background:#fff;border:1px solid var(--line);border-radius:13px;overflow:hidden}.gca-row{display:grid;grid-template-columns:minmax(180px,1.4fr) minmax(120px,.8fr) minmax(125px,.9fr) minmax(130px,1fr) minmax(150px,1fr);gap:8px;padding:10px;border-bottom:1px solid var(--line);align-items:center;font-size:9px}.gca-row:last-child{border-bottom:0}.gca-row strong{color:var(--gd)}.gca-row small{display:block;color:var(--muted);font-size:8px;margin-top:2px}.gca-pill{display:inline-flex;padding:4px 7px;border:1px solid var(--line);border-radius:999px;background:#f7faf8;font-size:8px}.gca-warn{background:#fff8e9}.gca-ok{background:#eef9f1}.gca-bad{background:#fff1f1}.gca-actions{display:flex;gap:5px;flex-wrap:wrap}.gca-btn,.gca-select{border:1px solid var(--line);background:#fff;color:var(--gd);border-radius:8px;padding:6px 7px;font:inherit;font-size:8px}.gca-btn{cursor:pointer}.gca-btn:disabled{opacity:.45;cursor:not-allowed}.gca-empty{padding:14px;text-align:center;color:var(--muted);font-size:9px}
      @media(max-width:980px){.gca-grid{grid-template-columns:repeat(2,1fr)}.gca-row{grid-template-columns:1fr 1fr}.gca-row>div:last-child{grid-column:1/-1}}
      @media(max-width:620px){.gca-grid,.gca-row{grid-template-columns:1fr}.gca-row>div:last-child{grid-column:auto}}
    `; document.head.appendChild(s);
  }

  async function load(){
    if(!allowed() || !db() || state.loading) return;
    state.loading=true; state.error=null;
    try{
      let query=db().from('customer_accounts').select('user_id,email,full_name,whatsapp,institution_name,city,source,account_status,lead_stage,followup_consent,assigned_sales,last_interest_program,last_interest_package,first_registered_at,last_seen_at,last_interest_at,last_contact_at,next_follow_up_at,booking_request_count,last_booking_at,updated_at').order('first_registered_at',{ascending:false}).limit(300);
      const [{data:accounts,error:aerr},{data:sales,error:serr}]=await Promise.all([
        query,
        canManage()?db().from('profiles').select('id,full_name,role,is_active').eq('role','Sales').eq('is_active',true).order('full_name'):Promise.resolve({data:[],error:null})
      ]);
      if(aerr) throw aerr; if(serr) throw serr;
      state.accounts=Array.isArray(accounts)?accounts:[];
      state.sales=Array.isArray(sales)?sales:[];
    }catch(e){ state.error=e?.message||String(e); }
    finally{ state.loading=false; render(); }
  }

  function salesName(id){ return state.sales.find(x=>String(x.id)===String(id))?.full_name || (id?'Sales':'Belum ditugaskan'); }
  function consentClass(a){ return a.followup_consent?'gca-ok':'gca-warn'; }
  function stageClass(stage){ return ['BOOKED'].includes(stage)?'gca-ok':['LOST'].includes(stage)?'gca-bad':'gca-warn'; }

  function assignOptions(a){
    if(!canManage()) return `<span class="gca-pill">${h(salesName(a.assigned_sales))}</span>`;
    const opts=[`<option value="">Belum ditugaskan</option>`,...state.sales.map(s=>`<option value="${h(s.id)}" ${String(s.id)===String(a.assigned_sales)?'selected':''}>${h(s.full_name||'Sales')}</option>`)];
    return `<select class="gca-select" data-gca-assign="${h(a.user_id)}">${opts.join('')}</select>`;
  }

  function row(a){
    const name=a.full_name||a.institution_name||a.email||a.whatsapp||'Customer';
    const contact=[a.whatsapp,a.email].filter(Boolean).join(' • ');
    const interest=[a.last_interest_program,a.last_interest_package].filter(Boolean).join(' / ')||'Belum memilih paket';
    const canContact=!!a.followup_consent;
    return `<div class="gca-row">
      <div><strong>${h(name)}</strong><small>${h(a.institution_name||'')} ${a.city?'• '+h(a.city):''}</small><small>${h(contact||'Kontak belum lengkap')}</small></div>
      <div><span class="gca-pill ${stageClass(a.lead_stage)}">${h(a.lead_stage)}</span><small>Daftar ${dt(a.first_registered_at)}</small><small>${Number(a.booking_request_count||0)} booking request</small></div>
      <div><strong>${h(interest)}</strong><small>Minat terakhir ${dt(a.last_interest_at)}</small></div>
      <div>${assignOptions(a)}<small class="gca-pill ${consentClass(a)}" style="margin-top:5px">${canContact?'Boleh follow-up':'Belum beri izin follow-up'}</small><small>Next: ${dt(a.next_follow_up_at)}</small></div>
      <div class="gca-actions">
        <button class="gca-btn" data-gca-contact="${h(a.user_id)}" ${canContact?'':'disabled'}>Sudah dihubungi</button>
        <button class="gca-btn" data-gca-nurture="${h(a.user_id)}" ${canContact?'':'disabled'}>Nurture 7 hari</button>
      </div>
    </div>`;
  }

  function render(){
    if(!allowed()) return;
    const anchor=q('#salesTargetControl')||q('#gmuSales200Target')||q('[data-g112="crm"]')||q('.content');
    if(!anchor) return;
    let box=q('#gmuCustomerAccountCrm');
    if(!box){box=document.createElement('section');box.id='gmuCustomerAccountCrm';box.className='card section gca';anchor.insertAdjacentElement('afterend',box);}
    if(state.error){box.innerHTML=`<div class="gca-head"><div><h3>Customer Account & Prospek Web</h3><p>Signup customer masuk ERP sebelum booking.</p></div></div><div class="gca-empty">Belum dapat memuat customer_accounts: ${h(state.error)}</div>`;return;}
    const total=state.accounts.length;
    const noBooking=state.accounts.filter(a=>Number(a.booking_request_count||0)===0).length;
    const followup=state.accounts.filter(a=>a.followup_consent && !['BOOKED','LOST'].includes(a.lead_stage)).length;
    const sevenDays=Date.now()-7*864e5;
    const new7=state.accounts.filter(a=>new Date(a.first_registered_at).getTime()>=sevenDays).length;
    box.innerHTML=`
      <div class="gca-head"><div><h3>Customer Account & Prospek Web</h3><p>Orang yang mendaftar akun terlihat di ERP walau belum order. Booking tetap dibuat hanya saat customer benar-benar mengirim pesanan.</p></div><span class="badge ok">ACCOUNT → LEAD</span></div>
      <div class="gca-grid">
        <div class="gca-card"><small>Total akun terdaftar</small><b>${total.toLocaleString('id-ID')}</b></div>
        <div class="gca-card"><small>Belum buat booking</small><b>${noBooking.toLocaleString('id-ID')}</b></div>
        <div class="gca-card"><small>Boleh difollow-up</small><b>${followup.toLocaleString('id-ID')}</b></div>
        <div class="gca-card"><small>Signup 7 hari terakhir</small><b>${new7.toLocaleString('id-ID')}</b></div>
      </div>
      <div class="gca-list">${total?state.accounts.map(row).join(''):'<div class="gca-empty">Belum ada akun customer yang terdaftar.</div>'}</div>`;
    wire();
  }

  async function updateAccount(id, values){
    const {error}=await db().from('customer_accounts').update(values).eq('user_id',id);
    if(error) throw error;
  }

  function wire(){
    q('#gmuCustomerAccountCrm')?.querySelectorAll('[data-gca-assign]').forEach(el=>el.addEventListener('change',async()=>{
      try{await updateAccount(el.dataset.gcaAssign,{assigned_sales:el.value||null});await load();}catch(e){alert(e?.message||e);}
    }));
    q('#gmuCustomerAccountCrm')?.querySelectorAll('[data-gca-contact]').forEach(el=>el.addEventListener('click',async()=>{
      try{const tomorrow=new Date(Date.now()+864e5).toISOString();await updateAccount(el.dataset.gcaContact,{lead_stage:'CONTACTED',last_contact_at:new Date().toISOString(),next_follow_up_at:tomorrow});await load();}catch(e){alert(e?.message||e);}
    }));
    q('#gmuCustomerAccountCrm')?.querySelectorAll('[data-gca-nurture]').forEach(el=>el.addEventListener('click',async()=>{
      try{const next=new Date(Date.now()+7*864e5).toISOString();await updateAccount(el.dataset.gcaNurture,{lead_stage:'NURTURE',account_status:'NURTURE',next_follow_up_at:next});await load();}catch(e){alert(e?.message||e);}
    }));
  }

  function init(){
    if(!allowed()) return;
    installStyle(); render(); load();
    const observer=new MutationObserver(()=>{ if(!q('#gmuCustomerAccountCrm')) render(); });
    observer.observe(document.body,{childList:true,subtree:true});
    window.GmuCustomerAccountCrmV220=Object.freeze({version:VERSION,refresh:load});
  }

  const timer=setInterval(()=>{
    if(typeof profile==='undefined'||!profile) return;
    clearInterval(timer); init();
  },250);
})();
