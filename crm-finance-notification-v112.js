(() => {
  'use strict';

  const VERSION = 'v11.2-crm-finance-notification-activation';
  const LEADERS = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans']);
  const CRM_MANAGERS = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Admin']);
  const q = (s, r = document) => r.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  const pct = v => Number.isFinite(Number(v)) ? `${Number(v).toLocaleString('id-ID',{maximumFractionDigits:2})}%` : '—';
  const dateOnly = d => {
    const x = d ? new Date(d) : new Date();
    if (Number.isNaN(x.getTime())) return '';
    const y=x.getFullYear(), m=String(x.getMonth()+1).padStart(2,'0'), day=String(x.getDate()).padStart(2,'0');
    return `${y}-${m}-${day}`;
  };
  const dt = v => v ? new Date(v).toLocaleString('id-ID',{dateStyle:'medium',timeStyle:'short'}) : '—';
  const state = {
    leads:[], requests:[], sales:[], scorecard:[], activities:[],
    ar:[], ap:[], trial:[], pnl:null, balance:null, cash:null, periods:[],
    customerOutbox:[], customerDelivery:[], internalDelivery:[],
    crmError:null, financeError:null, notificationError:null, loading:false
  };

  function role(){ try { return String(profile?.role || ''); } catch (_) { return ''; } }
  function uid(){ try { return String(profile?.id || user?.id || ''); } catch (_) { return ''; } }
  function db(){ try { return typeof sb !== 'undefined' && sb?.from ? sb : null; } catch (_) { return null; } }
  function canCRM(){ return CRM_MANAGERS.has(role()) || role()==='Sales'; }
  function canManageCRM(){ return CRM_MANAGERS.has(role()); }
  function canFinance(){ return LEADERS.has(role()); }
  function canNotifications(){ return LEADERS.has(role()); }

  function installStyle(){
    if(q('#gmuV112Style')) return;
    const s=document.createElement('style'); s.id='gmuV112Style'; s.textContent=`
      .g112{margin-top:12px}.g112-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10px;flex-wrap:wrap;margin-bottom:10px}.g112-head h4{margin:0;color:var(--gd)}.g112-kicker{font-size:8px;color:var(--muted);margin-bottom:4px}
      .g112-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px}.g112-grid3{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.g112-2{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.g112-card{background:#fff;border:1px solid var(--line);border-radius:13px;padding:11px;min-width:0}.g112-card small{display:block;font-size:8px;color:var(--muted)}.g112-card b{display:block;font-size:15px;color:var(--gd);margin:3px 0}.g112-card p{font-size:9px;color:var(--muted);line-height:1.5;margin:4px 0}
      .g112-list{background:#fff;border:1px solid var(--line);border-radius:13px;overflow:hidden}.g112-row{display:grid;grid-template-columns:minmax(180px,1.7fr) minmax(100px,.8fr) minmax(110px,.9fr) minmax(120px,1fr) auto;gap:8px;align-items:center;padding:9px 11px;border-bottom:1px solid var(--line);font-size:9px}.g112-row:last-child{border-bottom:0}.g112-row strong{color:var(--gd)}.g112-row small{display:block;color:var(--muted);font-size:8px;margin-top:2px}.g112-pill{display:inline-flex;border:1px solid var(--line);border-radius:999px;padding:4px 7px;font-size:8px;background:#f7faf8}.g112-warn{background:#fff8e9;border-color:#ead9a9}.g112-bad{background:#fff1f1;border-color:#efc5c5}.g112-ok{background:#eef9f1;border-color:#cce7d4}
      .g112-actions{display:flex;gap:6px;flex-wrap:wrap}.g112-btn{border:1px solid var(--line);background:#fff;color:var(--gd);border-radius:9px;padding:7px 9px;font:inherit;font-size:8px;cursor:pointer}.g112-btn.primary{background:var(--g);color:#fff;border-color:var(--g)}.g112-btn:disabled{opacity:.5;cursor:not-allowed}.g112-error{padding:10px;border:1px solid #efc5c5;background:#fff4f4;color:#963434;border-radius:11px;font-size:9px}.g112-empty{padding:14px;border:1px dashed var(--line);border-radius:12px;color:var(--muted);font-size:9px;text-align:center}
      .g112-table{width:100%;border-collapse:collapse;font-size:9px}.g112-table th,.g112-table td{padding:8px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}.g112-table th{font-size:8px;color:var(--muted)}
      .g112-modal{position:fixed;inset:0;background:rgba(7,25,15,.42);z-index:10050;display:flex;align-items:center;justify-content:center;padding:16px}.g112-dialog{background:#fff;width:min(620px,100%);max-height:90vh;overflow:auto;border-radius:17px;padding:16px}.g112-dialog h3{margin:0;color:var(--gd)}.g112-form{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-top:12px}.g112-field{display:flex;flex-direction:column;gap:4px}.g112-field.full{grid-column:1/-1}.g112-field label{font-size:8px;color:var(--muted)}.g112-field input,.g112-field select,.g112-field textarea{box-sizing:border-box;width:100%;padding:9px;border:1px solid var(--line);border-radius:10px;font:inherit;font-size:9px}.g112-field textarea{min-height:80px;resize:vertical}
      @media(max-width:980px){.g112-grid{grid-template-columns:repeat(2,1fr)}.g112-grid3,.g112-2{grid-template-columns:1fr 1fr}.g112-row{grid-template-columns:1.5fr .8fr .8fr 1fr}.g112-row>.g112-actions{grid-column:1/-1}}
      @media(max-width:620px){.g112-grid,.g112-grid3,.g112-2{grid-template-columns:1fr}.g112-row,.g112-form{grid-template-columns:1fr}.g112-field.full{grid-column:auto}}
    `; document.head.appendChild(s);
  }

  function ensurePage(id,title,subtitle='GMU EduTrans • Production Control'){
    if(q(`#${CSS.escape(id)}`)) return q(`#${CSS.escape(id)}`);
    const host=q('.content')||q('main')||document.body;
    const el=document.createElement('section'); el.id=id; el.className='page';
    el.innerHTML=`<div class="page-head"><div><h2>${h(title)}</h2><p>${h(subtitle)}</p></div></div>`;
    host.appendChild(el); return el;
  }
  function ensureNav(id,label,allowed){
    if(!allowed) return; const nav=q('#nav'); if(!nav||q(`#nav [data-page="${id}"]`)) return;
    const b=document.createElement('button'); b.type='button'; b.dataset.page=id; b.textContent=label;
    b.addEventListener('click',()=>showPage(id,label)); nav.appendChild(b);
  }
  function showPage(id,label){
    document.querySelectorAll('.page').forEach(x=>x.classList.remove('active')); q(`#${CSS.escape(id)}`)?.classList.add('active');
    document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page===id));
    const title=q('#title'); if(title) title.textContent=label;
    refresh();
  }
  function mount(pageId,key,html){
    const page=q(`#${CSS.escape(pageId)}`); if(!page) return;
    let box=q(`[data-g112="${key}"]`,page); if(!box){box=document.createElement('div');box.className='g112';box.dataset.g112=key;page.appendChild(box);} box.innerHTML=html;
  }
  function btn(label,attrs='',cls=''){ return `<button type="button" class="g112-btn ${cls}" ${attrs}>${h(label)}</button>`; }
  function statusClass(s){ s=String(s||'').toUpperCase(); return ['FAILED','ERROR','OVERDUE','LOST','CRITICAL'].includes(s)?'g112-bad':['PENDING','QUEUED','RETRY','WAITING_DP','NEGOTIATION','HIGH'].includes(s)?'g112-warn':'g112-ok'; }

  async function select(table,columns='*',builder){ let r=db().from(table).select(columns); if(builder) r=builder(r); const {data,error}=await r; if(error) throw error; return Array.isArray(data)?data:[]; }
  async function rpc(fn,args={}){ const {data,error}=await db().rpc(fn,args); if(error) throw error; return data; }
  async function updateWhere(table,match,values){ let r=db().from(table).update(values); Object.entries(match).forEach(([k,v])=>{r=r.eq(k,v)}); const {data,error}=await r.select(); if(error) throw error; return data?.[0]; }
  async function insert(table,values){ const {data,error}=await db().from(table).insert(values).select(); if(error) throw error; return data?.[0]; }

  function leadRequest(id){ return state.requests.find(x=>String(x.id)===String(id))||{}; }
  function salesName(id){ if(!id) return 'Belum Ditugaskan'; return state.sales.find(x=>String(x.id)===String(id))?.full_name||'Sales'; }

  async function loadCRM(){
    state.crmError=null; if(!canCRM()) return;
    try{
      const start=new Date(); start.setDate(1); start.setHours(0,0,0,0);
      const [leads,requests,sales,activities,scorecard]=await Promise.all([
        select('crm_lead_controls','booking_request_id,stage,owner_id,lead_source,qualification_score,probability_pct,estimated_value,last_contact_at,next_follow_up_at,lost_reason,lost_at,won_at,recovery_status,recovery_plan,recovery_due_at,updated_at',r=>r.order('updated_at',{ascending:false}).limit(300)),
        select('booking_requests','id,booking_code,institution_name,pic_name,whatsapp,email,city,source,status,assigned_sales,budget_per_pax,pax,converted_booking_id,created_at,updated_at',r=>r.order('created_at',{ascending:false}).limit(300)),
        canManageCRM()?select('profiles','id,full_name,role,is_active',r=>r.eq('is_active',true).eq('role','Sales').order('full_name')):Promise.resolve([]),
        select('crm_activities','id,booking_request_id,booking_id,customer_id,sales_id,channel,activity_type,outcome,lead_source,next_follow_up_at,lost_reason,notes,created_at',r=>r.order('created_at',{ascending:false}).limit(200)),
        rpc('internal_crm_sales_scorecard',{p_start:dateOnly(start),p_end:dateOnly(new Date())})
      ]);
      state.leads=leads; state.requests=requests; state.sales=sales; state.activities=activities; state.scorecard=Array.isArray(scorecard)?scorecard:[];
    }catch(e){ state.crmError=e?.message||String(e); }
  }

  async function loadFinance(){
    state.financeError=null; if(!canFinance()) return;
    try{
      const now=new Date(), start=new Date(now.getFullYear(),now.getMonth(),1), asOf=dateOnly(now), pStart=dateOnly(start), actor=uid();
      const res=await Promise.allSettled([
        rpc('internal_management_ar_aging',{p_as_of:asOf,p_actor:actor}),
        rpc('internal_management_ap_aging',{p_as_of:asOf,p_actor:actor}),
        rpc('internal_gl_trial_balance',{p_as_of:asOf}),
        rpc('internal_gl_profit_loss',{p_start:pStart,p_end:asOf}),
        rpc('internal_gl_balance_sheet',{p_as_of:asOf}),
        rpc('internal_gl_cash_flow',{p_start:pStart,p_end:asOf}),
        select('finance_periods','id,period_no,period_type,period_start,period_end,status,closed_at,reopened_at,reopen_reason,updated_at',r=>r.order('period_start',{ascending:false}).limit(24))
      ]);
      const keys=['ar','ap','trial','pnl','balance','cash','periods'];
      const errors=[];
      res.forEach((x,i)=>{ if(x.status==='fulfilled') state[keys[i]]=x.value; else {state[keys[i]]=i<3||i===6?[]:null;errors.push(x.reason?.message||String(x.reason));} });
      if(errors.length) state.financeError=[...new Set(errors)].join(' • ');
    }catch(e){ state.financeError=e?.message||String(e); }
  }

  async function loadNotifications(){
    state.notificationError=null; if(!canNotifications()) return;
    try{
      const [outbox,customer,internal]=await Promise.all([
        select('customer_notification_outbox','id,notification_id,booking_request_id,status,next_attempt_at,completed_at,last_error,created_at,updated_at',r=>r.order('created_at',{ascending:false}).limit(100)),
        select('customer_notification_deliveries','id,outbox_id,notification_id,booking_request_id,channel,recipient,provider,provider_message_id,status,attempts,max_attempts,available_at,last_attempt_at,sent_at,response_code,last_error,created_at,updated_at',r=>r.order('created_at',{ascending:false}).limit(150)),
        select('internal_notification_deliveries','id,notification_id,recipient_id,channel,recipient,status,attempts,max_attempts,available_at,sent_at,provider_message_id,response_code,last_error,created_at,updated_at',r=>r.order('created_at',{ascending:false}).limit(150))
      ]);
      state.customerOutbox=outbox; state.customerDelivery=customer; state.internalDelivery=internal;
    }catch(e){ state.notificationError=e?.message||String(e); }
  }

  function renderCRM(){
    if(!q('#salesTargetControl')||!canCRM()) return;
    const active=state.leads.filter(x=>!['WON','LOST'].includes(String(x.stage)));
    const unassigned=active.filter(x=>!x.owner_id);
    const overdue=active.filter(x=>x.next_follow_up_at && new Date(x.next_follow_up_at)<new Date());
    const weighted=active.reduce((a,x)=>a+Number(x.estimated_value||0)*Number(x.probability_pct||0)/100,0);
    const won=state.leads.filter(x=>x.stage==='WON').length, lost=state.leads.filter(x=>x.stage==='LOST').length;
    const scoreRows=state.scorecard.map(x=>`<tr><td>${h(x.sales_name)}</td><td>${Number(x.prospects||0)}</td><td>${Number(x.activities||0)}</td><td>${Number(x.qualified_leads||0)}</td><td>${Number(x.quotations||0)}</td><td>${Number(x.won_leads||0)}</td><td>${pct(x.conversion_pct)}</td><td>${money(x.pipeline_value)}</td></tr>`).join('');
    const leadRows=state.leads.slice(0,60).map(x=>{const r=leadRequest(x.booking_request_id);const isOver=x.next_follow_up_at&&new Date(x.next_follow_up_at)<new Date()&&!['WON','LOST'].includes(x.stage);return `<div class="g112-row"><div><strong>${h(r.institution_name||r.pic_name||r.booking_code||x.booking_request_id)}</strong><small>${h(r.city||'')} • ${h(x.lead_source||r.source||'Sumber belum dicatat')} • ${money(x.estimated_value)}</small></div><div><span class="g112-pill ${statusClass(x.stage)}">${h(x.stage)}</span></div><div>${h(salesName(x.owner_id))}<small>${h(x.recovery_status||'NONE')}</small></div><div><span class="g112-pill ${isOver?'g112-bad':''}">${h(x.next_follow_up_at?dt(x.next_follow_up_at):'Belum dijadwalkan')}</span></div><div class="g112-actions">${btn('Aktivitas',`data-g112-activity="${h(x.booking_request_id)}"`,'primary')}${canManageCRM()?btn('Atur Lead',`data-g112-lead="${h(x.booking_request_id)}"`):''}</div></div>`}).join('');
    mount('salesTargetControl','crm-v112',`<div class="g112-head"><div><div class="g112-kicker">CRM production • ${VERSION}</div><h4>Pipeline, Assignment & Conversion</h4></div><div class="g112-actions">${btn('Muat Ulang','data-g112-refresh')}</div></div><div class="g112-grid"><div class="g112-card"><small>Lead aktif</small><b>${active.length}</b><p>Belum Won/Lost.</p></div><div class="g112-card"><small>Belum Ditugaskan</small><b>${unassigned.length}</b><p>PIC Sales belum ditetapkan.</p></div><div class="g112-card"><small>Follow-up terlambat</small><b>${overdue.length}</b><p>Masuk recovery/task otomatis.</p></div><div class="g112-card"><small>Weighted Pipeline</small><b>${money(weighted)}</b><p>Nilai × probabilitas stage.</p></div></div><div class="g112-grid3" style="margin-top:9px"><div class="g112-card"><small>Won</small><b>${won}</b></div><div class="g112-card"><small>Lost</small><b>${lost}</b></div><div class="g112-card"><small>Aktivitas CRM termuat</small><b>${state.activities.length}</b></div></div><div class="g112-card" style="margin-top:9px"><h4>Scorecard Sales Bulan Ini</h4>${scoreRows?`<div style="overflow:auto"><table class="g112-table"><thead><tr><th>Sales</th><th>Prospek</th><th>Aktivitas</th><th>Qualified</th><th>Quotation</th><th>Won</th><th>Conversion</th><th>Pipeline</th></tr></thead><tbody>${scoreRows}</tbody></table></div>`:'<div class="g112-empty">Belum ada scorecard Sales yang dapat ditampilkan.</div>'}</div><div style="margin-top:9px">${leadRows?`<div class="g112-list">${leadRows}</div>`:'<div class="g112-empty">Belum ada lead yang dapat dilihat role ini.</div>'}</div>${state.crmError?`<div class="g112-error" style="margin-top:9px">CRM: ${h(state.crmError)}</div>`:''}`);
  }

  function agingTable(rows,label){ rows=Array.isArray(rows)?rows:[]; return `<div class="g112-card"><h4>${h(label)}</h4>${rows.length?`<table class="g112-table"><thead><tr><th>Bucket</th><th>Item</th><th>Outstanding</th></tr></thead><tbody>${rows.map(x=>`<tr><td>${h(x.bucket)}</td><td>${Number(x.item_count||0)}</td><td>${money(x.outstanding)}</td></tr>`).join('')}</tbody></table>`:'<div class="g112-empty">Tidak ada data / tidak memiliki akses.</div>'}</div>`; }
  function renderFinance(){
    if(!canFinance()) return; ensurePage('accountingControl','Accounting & Financial Statements'); ensureNav('accountingControl','Accounting & Laporan',true);
    const p=state.pnl||{}, b=state.balance||{}, c=state.cash||{};
    const period=state.periods?.[0];
    const trial=(Array.isArray(state.trial)?state.trial:[]).slice(0,80).map(x=>`<tr><td>${h(x.account_code)}</td><td>${h(x.account_name)}</td><td>${h(x.account_type)}</td><td>${money(x.debit_total)}</td><td>${money(x.credit_total)}</td><td>${money(x.ending_debit)}</td><td>${money(x.ending_credit)}</td></tr>`).join('');
    mount('accountingControl','finance-v112',`<div class="g112-head"><div><div class="g112-kicker">General Ledger production • ${VERSION}</div><h4>Laporan Keuangan & Closing Control</h4></div><div class="g112-actions">${btn('Muat Ulang','data-g112-refresh')}</div></div><div class="g112-grid"><div class="g112-card"><small>Omzet / Revenue bulan ini</small><b>${p.revenue===undefined?'—':money(p.revenue)}</b><p>COGS ${p.cogs===undefined?'—':money(p.cogs)}</p></div><div class="g112-card"><small>Laba Bersih bulan ini</small><b>${p.net_profit===undefined?'—':money(p.net_profit)}</b><p>Margin ${p.margin_pct===undefined?'—':pct(p.margin_pct)}</p></div><div class="g112-card"><small>Kas Penutupan GL</small><b>${c.gl_cash_balance===undefined?'—':money(c.gl_cash_balance)}</b><p>Net cash flow ${c.net_cash_flow===undefined?'—':money(c.net_cash_flow)}</p></div><div class="g112-card"><small>Periode terbaru</small><b>${h(period?.status||'—')}</b><p>${h(period?.period_no||'Belum ada periode')}</p></div></div><div class="g112-2" style="margin-top:9px">${agingTable(state.ar,'AR Aging / Piutang')}${agingTable(state.ap,'AP Aging / Hutang Vendor')}</div><div class="g112-grid3" style="margin-top:9px"><div class="g112-card"><small>Aset</small><b>${b.assets===undefined?'—':money(b.assets)}</b><p>Balanced: ${b.balanced===undefined?'—':(b.balanced?'Ya':'Tidak')}</p></div><div class="g112-card"><small>Liabilitas</small><b>${b.liabilities===undefined?'—':money(b.liabilities)}</b></div><div class="g112-card"><small>Ekuitas</small><b>${b.total_equity===undefined?'—':money(b.total_equity)}</b><p>Gap ${b.balance_gap===undefined?'—':money(b.balance_gap)}</p></div></div><div class="g112-card" style="margin-top:9px"><h4>Arus Kas Bulan Ini</h4><div class="g112-grid"><div><small>Opening</small><b>${c.opening_cash===undefined?'—':money(c.opening_cash)}</b></div><div><small>Operating</small><b>${c.operating_cash_flow===undefined?'—':money(c.operating_cash_flow)}</b></div><div><small>Investing</small><b>${c.investing_cash_flow===undefined?'—':money(c.investing_cash_flow)}</b></div><div><small>Financing</small><b>${c.financing_cash_flow===undefined?'—':money(c.financing_cash_flow)}</b></div></div></div><div class="g112-card" style="margin-top:9px"><h4>Trial Balance</h4>${trial?`<div style="overflow:auto"><table class="g112-table"><thead><tr><th>Kode</th><th>Akun</th><th>Tipe</th><th>Debit</th><th>Kredit</th><th>Saldo Debit</th><th>Saldo Kredit</th></tr></thead><tbody>${trial}</tbody></table></div>`:'<div class="g112-empty">Belum ada jurnal POSTED / akses tidak tersedia.</div>'}</div>${state.financeError?`<div class="g112-error" style="margin-top:9px">Sebagian laporan tidak termuat: ${h(state.financeError)}</div>`:''}`);
    if(q('#cashForecastControl')) mount('cashForecastControl','accounting-link',`<div class="g112-card"><h4>Accounting Production</h4><p>AR/AP Aging, Trial Balance, Laba Rugi, Neraca, Arus Kas dan Period Lock sudah memakai engine backend production.</p><div class="g112-actions">${btn('Buka Accounting','data-g112-go="accountingControl"','primary')}</div></div>`);
  }

  function renderNotifications(){
    if(!canNotifications()) return; ensurePage('notificationDeliveryControl','Pusat Notifikasi','Email, WhatsApp, retry & delivery log'); ensureNav('notificationDeliveryControl','Pusat Notifikasi',true);
    const all=[...state.customerDelivery.map(x=>({...x,scope:'Pelanggan'})),...state.internalDelivery.map(x=>({...x,scope:'Internal'}))].sort((a,b)=>new Date(b.created_at)-new Date(a.created_at));
    const failed=all.filter(x=>['FAILED','ERROR','DEAD'].includes(String(x.status||'').toUpperCase()));
    const pending=all.filter(x=>['PENDING','QUEUED','RETRY','PROCESSING'].includes(String(x.status||'').toUpperCase()));
    const sent=all.filter(x=>['SENT','DELIVERED','SUCCESS'].includes(String(x.status||'').toUpperCase()));
    const rows=all.slice(0,80).map(x=>`<div class="g112-row"><div><strong>${h(x.scope)} • ${h(x.channel||'CHANNEL')}</strong><small>${h(x.recipient||'Penerima tersamarkan')} ${x.provider?`• ${h(x.provider)}`:''}</small></div><div><span class="g112-pill ${statusClass(x.status)}">${h(x.status||'—')}</span></div><div>${Number(x.attempts||0)}/${Number(x.max_attempts||0)}<small>${x.response_code?`HTTP ${h(x.response_code)}`:''}</small></div><div>${h(x.sent_at?dt(x.sent_at):dt(x.available_at||x.created_at))}<small>${h(x.provider_message_id||'')}</small></div><div>${x.last_error?`<span class="g112-pill g112-bad" title="${h(x.last_error)}">Error</span>`:''}</div></div>`).join('');
    mount('notificationDeliveryControl','main',`<div class="g112-head"><div><div class="g112-kicker">Delivery workers production • ${VERSION}</div><h4>Notification Delivery Center</h4></div><div class="g112-actions">${btn('Muat Ulang','data-g112-refresh')}</div></div><div class="g112-grid"><div class="g112-card"><small>Outbox customer</small><b>${state.customerOutbox.length}</b><p>Antrian yang termuat.</p></div><div class="g112-card"><small>Pending / Retry</small><b>${pending.length}</b></div><div class="g112-card"><small>Terkirim</small><b>${sent.length}</b></div><div class="g112-card"><small>Gagal</small><b>${failed.length}</b><p>Perlu cek provider/recipient bila berulang.</p></div></div><div style="margin-top:9px">${rows?`<div class="g112-list">${rows}</div>`:'<div class="g112-empty">Belum ada delivery log yang dapat dilihat.</div>'}</div>${state.notificationError?`<div class="g112-error" style="margin-top:9px">Notifikasi: ${h(state.notificationError)}</div>`:''}`);
  }

  function closeModal(){ q('.g112-modal')?.remove(); }
  function openActivity(requestId){
    const req=leadRequest(requestId), lead=state.leads.find(x=>String(x.booking_request_id)===String(requestId))||{};
    const m=document.createElement('div');m.className='g112-modal';m.innerHTML=`<div class="g112-dialog"><h3>Catat Aktivitas CRM</h3><p class="g112-kicker">${h(req.institution_name||req.pic_name||req.booking_code||requestId)}</p><form class="g112-form" data-g112-form="activity"><input type="hidden" name="request_id" value="${h(requestId)}"><div class="g112-field"><label>Channel</label><select name="channel"><option>WHATSAPP</option><option>TELEPON</option><option>EMAIL</option><option>KUNJUNGAN</option><option>RAPAT</option><option>LAINNYA</option></select></div><div class="g112-field"><label>Jenis Aktivitas</label><input name="activity_type" required placeholder="Follow-up / Presentasi / Negosiasi"></div><div class="g112-field"><label>Hasil</label><input name="outcome" placeholder="Respons / hasil kontak"></div><div class="g112-field"><label>Next Follow-up</label><input name="next_follow_up_at" type="datetime-local"></div><div class="g112-field full"><label>Lost Reason (isi hanya jika gagal)</label><input name="lost_reason"></div><div class="g112-field full"><label>Catatan</label><textarea name="notes"></textarea></div><div class="g112-actions full">${btn('Batal','data-g112-close')}${btn('Simpan','type="submit"','primary')}</div></form></div>`;document.body.appendChild(m);
  }
  function openLead(requestId){
    const req=leadRequest(requestId), lead=state.leads.find(x=>String(x.booking_request_id)===String(requestId))||{};
    const opts=['NEW','CONTACTED','QUALIFIED','QUOTATION','NEGOTIATION','WAITING_DP','WON','LOST','NURTURE'].map(x=>`<option ${x===lead.stage?'selected':''}>${x}</option>`).join('');
    const salesOpts=`<option value="">Belum Ditugaskan</option>`+state.sales.map(x=>`<option value="${h(x.id)}" ${String(x.id)===String(lead.owner_id)?'selected':''}>${h(x.full_name)}</option>`).join('');
    const m=document.createElement('div');m.className='g112-modal';m.innerHTML=`<div class="g112-dialog"><h3>Atur Lead</h3><p class="g112-kicker">${h(req.institution_name||req.pic_name||req.booking_code||requestId)}</p><form class="g112-form" data-g112-form="lead"><input type="hidden" name="request_id" value="${h(requestId)}"><div class="g112-field"><label>Stage</label><select name="stage">${opts}</select></div><div class="g112-field"><label>PIC Sales</label><select name="owner_id">${salesOpts}</select></div><div class="g112-field"><label>Probabilitas %</label><input type="number" min="0" max="100" step="1" name="probability_pct" value="${h(lead.probability_pct??10)}"></div><div class="g112-field"><label>Nilai Potensi</label><input type="number" min="0" name="estimated_value" value="${h(lead.estimated_value??0)}"></div><div class="g112-field"><label>Next Follow-up</label><input type="datetime-local" name="next_follow_up_at"></div><div class="g112-field"><label>Recovery</label><select name="recovery_status"><option ${lead.recovery_status==='NONE'?'selected':''}>NONE</option><option ${lead.recovery_status==='NEEDED'?'selected':''}>NEEDED</option><option ${lead.recovery_status==='ACTIVE'?'selected':''}>ACTIVE</option><option ${lead.recovery_status==='CLOSED'?'selected':''}>CLOSED</option></select></div><div class="g112-field full"><label>Lost Reason</label><input name="lost_reason" value="${h(lead.lost_reason||'')}"></div><div class="g112-field full"><label>Recovery Plan</label><textarea name="recovery_plan">${h(lead.recovery_plan||'')}</textarea></div><div class="g112-actions full">${btn('Batal','data-g112-close')}${btn('Simpan','type="submit"','primary')}</div></form></div>`;document.body.appendChild(m);
  }

  async function submitActivity(form){
    const fd=new FormData(form), requestId=fd.get('request_id'), lead=state.leads.find(x=>String(x.booking_request_id)===String(requestId))||{}, req=leadRequest(requestId);
    const next=fd.get('next_follow_up_at');
    await insert('crm_activities',{booking_request_id:requestId,booking_id:req.converted_booking_id||null,customer_id:null,sales_id:lead.owner_id||uid()||null,channel:fd.get('channel'),activity_type:String(fd.get('activity_type')||'').trim(),outcome:String(fd.get('outcome')||'').trim()||null,lead_source:lead.lead_source||req.source||null,next_follow_up_at:next?new Date(String(next)).toISOString():null,lost_reason:String(fd.get('lost_reason')||'').trim()||null,notes:String(fd.get('notes')||'').trim()||null,created_by:uid()||null});
  }
  async function submitLead(form){
    const fd=new FormData(form), requestId=fd.get('request_id'), owner=String(fd.get('owner_id')||'')||null, next=fd.get('next_follow_up_at');
    const values={stage:fd.get('stage'),owner_id:owner,probability_pct:Number(fd.get('probability_pct')||0),estimated_value:Number(fd.get('estimated_value')||0),recovery_status:fd.get('recovery_status'),lost_reason:String(fd.get('lost_reason')||'').trim()||null,recovery_plan:String(fd.get('recovery_plan')||'').trim()||null,updated_by:uid()||null};
    if(next) values.next_follow_up_at=new Date(String(next)).toISOString();
    if(values.stage==='LOST'&&!values.lost_reason) throw new Error('Lost Reason wajib diisi untuk stage LOST.');
    if(values.stage==='LOST') values.lost_at=new Date().toISOString();
    if(values.stage==='WON') values.won_at=new Date().toISOString();
    await updateWhere('crm_lead_controls',{booking_request_id:requestId},values);
    await updateWhere('booking_requests',{id:requestId},{assigned_sales:owner});
  }

  async function refresh(){
    if(state.loading||!db()||!role()) return; state.loading=true;
    try{await Promise.all([loadCRM(),loadFinance(),loadNotifications()]);}finally{state.loading=false;renderAll();}
  }
  function renderAll(){ installStyle(); if(canFinance()) ensurePage('accountingControl','Accounting & Financial Statements'); if(canNotifications()) ensurePage('notificationDeliveryControl','Pusat Notifikasi'); ensureNav('accountingControl','Accounting & Laporan',canFinance()); ensureNav('notificationDeliveryControl','Pusat Notifikasi',canNotifications()); renderCRM(); renderFinance(); renderNotifications(); }

  function bind(){
    document.addEventListener('click',e=>{
      const t=e.target.closest?.('[data-g112-refresh],[data-g112-activity],[data-g112-lead],[data-g112-close],[data-g112-go]'); if(!t)return;
      if(t.hasAttribute('data-g112-refresh')) refresh();
      else if(t.dataset.g112Activity) openActivity(t.dataset.g112Activity);
      else if(t.dataset.g112Lead) openLead(t.dataset.g112Lead);
      else if(t.hasAttribute('data-g112-close')) closeModal();
      else if(t.dataset.g112Go) showPage(t.dataset.g112Go,t.dataset.g112Go==='accountingControl'?'Accounting & Laporan':t.dataset.g112Go);
    });
    document.addEventListener('submit',async e=>{
      const form=e.target.closest?.('[data-g112-form]'); if(!form)return; e.preventDefault();
      const submit=form.querySelector('[type="submit"]'); if(submit)submit.disabled=true;
      try{ if(form.dataset.g112Form==='activity') await submitActivity(form); else if(form.dataset.g112Form==='lead') await submitLead(form); closeModal(); await refresh(); }
      catch(err){ alert(err?.message||String(err)); if(submit)submit.disabled=false; }
    });
  }
  function init(){
    bind(); const timer=setInterval(()=>{if(role()&&db()){clearInterval(timer);renderAll();refresh();}},300); setTimeout(()=>clearInterval(timer),15000);
    setInterval(()=>{if(role()&&db()&&!document.hidden)refresh();},60000);
    window.GmuV112Control=Object.freeze({version:VERSION,state,refresh});
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
