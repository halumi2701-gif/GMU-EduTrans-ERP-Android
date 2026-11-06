(() => {
  'use strict';

  const VERSION = 'v20-enterprise-operating-system';
  const LEADERS = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans']);
  const HR_ROLES = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Admin']);
  const FINANCE_ROLES = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Finance']);
  const q=(s,r=document)=>r.querySelector(s);
  const h=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money=v=>'Rp '+Number(v||0).toLocaleString('id-ID');
  const dt=v=>v?new Date(v).toLocaleString('id-ID',{dateStyle:'medium',timeStyle:'short'}):'—';
  const d=v=>v?new Date(v).toLocaleDateString('id-ID',{dateStyle:'medium'}):'—';
  const state={tower:null,exceptions:[],policies:[],probation:[],leaveBalances:[],contracts:[],kpis:[],reviews:[],training:[],warnings:[],offboarding:[],comp:null,payrollPeriods:[],assetAccess:[],error:null,loading:false};

  function role(){try{return String(profile?.role||'')}catch(_){return ''}}
  function uid(){try{return String(profile?.id||user?.id||'')}catch(_){return ''}}
  function db(){try{return typeof sb!=='undefined'&&sb?.from?sb:null}catch(_){return null}}
  function leader(){return LEADERS.has(role())}
  function hr(){return HR_ROLES.has(role())}
  function finance(){return FINANCE_ROLES.has(role())}

  function installStyle(){
    if(q('#gmuV200Style')) return;
    const s=document.createElement('style'); s.id='gmuV200Style'; s.textContent=`
      .g200{margin-top:12px}.g200-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px}.g200-3{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.g200-2{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.g200-card{background:#fff;border:1px solid var(--line);border-radius:14px;padding:12px;min-width:0}.g200-card small{display:block;font-size:8px;color:var(--muted)}.g200-card b{display:block;font-size:16px;color:var(--gd);margin:3px 0}.g200-card h4{margin:0 0 8px;color:var(--gd)}.g200-card p,.g200-card li{font-size:9px;color:var(--muted);line-height:1.5}.g200-card ul,.g200-card ol{margin:6px 0;padding-left:18px}.g200-kicker{font-size:8px;color:var(--muted);text-transform:uppercase;letter-spacing:.08em}.g200-health{display:flex;align-items:center;gap:8px}.g200-dot{width:9px;height:9px;border-radius:50%;background:#2d8b57}.g200-dot.warn{background:#d3a522}.g200-dot.bad{background:#c94848}.g200-list{border:1px solid var(--line);border-radius:14px;overflow:hidden;background:#fff}.g200-row{display:grid;grid-template-columns:minmax(170px,1.5fr) minmax(100px,.8fr) minmax(110px,.8fr) minmax(120px,1fr) auto;gap:8px;padding:10px 12px;border-bottom:1px solid var(--line);align-items:center;font-size:9px}.g200-row:last-child{border-bottom:0}.g200-row strong{color:var(--gd)}.g200-row small{display:block;font-size:8px;color:var(--muted);margin-top:2px}.g200-pill{display:inline-flex;padding:4px 7px;border:1px solid var(--line);border-radius:999px;font-size:8px;background:#f7faf8}.g200-pill.high{background:#fff7e6;border-color:#ead7a6}.g200-pill.critical{background:#fff0f0;border-color:#efc5c5}.g200-actions{display:flex;flex-wrap:wrap;gap:6px}.g200-btn{border:1px solid var(--line);background:#fff;color:var(--gd);border-radius:9px;padding:7px 9px;font:inherit;font-size:8px;cursor:pointer}.g200-btn.primary{background:var(--g);border-color:var(--g);color:#fff}.g200-table{width:100%;border-collapse:collapse;font-size:9px}.g200-table th,.g200-table td{padding:8px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}.g200-table th{font-size:8px;color:var(--muted)}.g200-empty{padding:14px;border:1px dashed var(--line);border-radius:12px;color:var(--muted);font-size:9px;text-align:center}.g200-error{padding:10px;border:1px solid #efc5c5;background:#fff4f4;color:#963434;border-radius:11px;font-size:9px}
      @media(max-width:1000px){.g200-grid{grid-template-columns:repeat(2,1fr)}.g200-3,.g200-2{grid-template-columns:1fr 1fr}.g200-row{grid-template-columns:1.4fr .8fr .8fr}.g200-row .g200-actions{grid-column:1/-1}}
      @media(max-width:620px){.g200-grid,.g200-3,.g200-2,.g200-row{grid-template-columns:1fr}}
    `; document.head.appendChild(s);
  }

  function ensurePage(id,title,subtitle='GMU EduTrans Enterprise Operating System'){
    if(q(`#${CSS.escape(id)}`)) return q(`#${CSS.escape(id)}`);
    const host=q('.content')||q('main')||document.body; const el=document.createElement('section'); el.id=id; el.className='page';
    el.innerHTML=`<div class="page-head"><div><h2>${h(title)}</h2><p>${h(subtitle)}</p></div></div>`; host.appendChild(el); return el;
  }
  function ensureNav(id,label,allowed){if(!allowed)return;const nav=q('#nav');if(!nav||q(`#nav [data-page="${id}"]`))return;const b=document.createElement('button');b.type='button';b.dataset.page=id;b.textContent=label;b.addEventListener('click',()=>showPage(id,label));nav.appendChild(b)}
  function showPage(id,label){document.querySelectorAll('.page').forEach(x=>x.classList.remove('active'));q(`#${CSS.escape(id)}`)?.classList.add('active');document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page===id));if(q('#title'))q('#title').textContent=label;refresh()}
  function mount(pageId,key,html){const p=q(`#${CSS.escape(pageId)}`);if(!p)return;let box=q(`[data-g200="${key}"]`,p);if(!box){box=document.createElement('div');box.className='g200';box.dataset.g200=key;p.appendChild(box)}box.innerHTML=html}
  function btn(label,attrs='',cls=''){return `<button type="button" class="g200-btn ${cls}" ${attrs}>${h(label)}</button>`}
  async function select(table,cols='*',builder){let r=db().from(table).select(cols);if(builder)r=builder(r);const {data,error}=await r;if(error)throw error;return Array.isArray(data)?data:[]}
  async function rpc(fn,args={}){const {data,error}=await db().rpc(fn,args);if(error)throw error;return data}

  async function load(){
    if(!db()||!role()) return; state.loading=true; state.error=null;
    try{
      const self=uid(); const requests=[];
      if(leader()) requests.push(rpc('internal_enterprise_control_tower').then(x=>state.tower=x));
      if(leader()||finance()||role()==='Admin') requests.push(select('enterprise_exception_queue','id,exception_key,domain,exception_type,severity,title,description,entity_type,entity_id,assigned_role,assigned_to,due_at,status,resolution,resolved_at,created_at,updated_at',r=>r.order('created_at',{ascending:false}).limit(150)).then(x=>state.exceptions=x));
      if(leader()) requests.push(select('enterprise_automation_policies','policy_code,domain,trigger_event,action_type,assigned_role,sla_hours,requires_approval,active,severity,description,updated_at',r=>r.eq('active',true).order('domain').order('policy_code')).then(x=>state.policies=x));
      requests.push(select('staff_probation_checkpoints','id,staff_id,contract_id,checkpoint_day,due_date,status,score,decision,notes,reviewed_at',r=>hr()?r.order('due_date').limit(200):r.eq('staff_id',self).order('due_date')).then(x=>state.probation=x));
      requests.push(select('staff_leave_balances','id,staff_id,year,leave_type,opening_balance,accrued,used,adjustment,notes,updated_at',r=>hr()?r.eq('year',new Date().getFullYear()).limit(300):r.eq('staff_id',self).eq('year',new Date().getFullYear())).then(x=>state.leaveBalances=x));
      requests.push(select('staff_contracts','id,staff_id,employment_status,start_date,end_date,position_title,notes,created_at',r=>hr()?r.order('created_at',{ascending:false}).limit(300):r.eq('staff_id',self).order('created_at',{ascending:false})).then(x=>state.contracts=x));
      requests.push(select('staff_kpis','id,staff_id,period,metric_name,target,actual,unit,score,notes,created_at',r=>hr()?r.order('created_at',{ascending:false}).limit(300):r.eq('staff_id',self).order('created_at',{ascending:false}).limit(100)).then(x=>state.kpis=x));
      requests.push(select('staff_reviews','id,staff_id,review_period,discipline,quality,communication,responsibility,teamwork,sop_compliance,overall_score,grade,notes,reviewed_at',r=>hr()?r.order('reviewed_at',{ascending:false}).limit(200):r.eq('staff_id',self).order('reviewed_at',{ascending:false}).limit(50)).then(x=>state.reviews=x));
      requests.push(select('staff_training','id,staff_id,title,provider,training_date,certificate_no,expiry_date,notes,created_at',r=>hr()?r.order('training_date',{ascending:false}).limit(250):r.eq('staff_id',self).order('training_date',{ascending:false})).then(x=>state.training=x));
      requests.push(select('staff_warnings','id,staff_id,warning_level,incident_date,reason,action_plan,status,created_at',r=>hr()?r.order('created_at',{ascending:false}).limit(150):r.eq('staff_id',self).order('created_at',{ascending:false})).then(x=>state.warnings=x));
      requests.push(select('staff_offboarding','id,staff_id,last_working_date,handover_complete,access_revoked,notes,created_at',r=>hr()?r.order('created_at',{ascending:false}).limit(100):r.eq('staff_id',self).order('created_at',{ascending:false})).then(x=>state.offboarding=x));
      requests.push(select('staff_asset_access_handover','id,staff_id,item_type,item_name,reference,lifecycle_stage,status,assigned_at,completed_at,notes,updated_at',r=>hr()?r.order('updated_at',{ascending:false}).limit(250):r.eq('staff_id',self).order('updated_at',{ascending:false})).then(x=>state.assetAccess=x));
      if(finance()||leader()) requests.push(select('payroll_periods','id,period_start,period_end,status,calculated_at,approved_at,locked_at,paid_at,notes,updated_at',r=>r.order('period_start',{ascending:false}).limit(24)).then(x=>state.payrollPeriods=x));
      requests.push(select('staff_compensation_profiles','staff_id,compensation_type,base_monthly,transport_per_attendance_day,effective_from,effective_to,status,notes,approved_at,updated_at',r=>r.eq('staff_id',self).limit(1)).then(x=>state.comp=x[0]||null));
      await Promise.allSettled(requests);
    }catch(e){state.error=e?.message||String(e)}finally{state.loading=false;render()}
  }

  function healthLevel(t){if(!t)return['warn','Menunggu data'];const critical=Number(t.governance?.critical_exceptions||0)+Number(t.quality?.critical_risks||0);const overdue=Number(t.operations?.overdue_tasks||0)+Number(t.sales?.overdue_followups||0);if(critical>0)return['bad','Kritis'];if(overdue>0)return['warn','Perlu Perhatian'];return['','Sehat']}
  function renderTower(){
    if(!leader())return; const t=state.tower||{}; const [cls,label]=healthLevel(t);
    ensurePage('enterpriseControlTower','Enterprise Control Tower','Direktur & Manager • satu layar kesehatan perusahaan');
    mount('enterpriseControlTower','tower',`${state.error?`<div class="g200-error">${h(state.error)}</div>`:''}<div class="g200-grid">
      <div class="g200-card"><small>Kesehatan Perusahaan</small><div class="g200-health"><span class="g200-dot ${cls}"></span><b>${h(label)}</b></div><p>Berbasis exception, overdue, risk dan control data.</p></div>
      <div class="g200-card"><small>Open Lead</small><b>${Number(t.sales?.open_leads||0)}</b><p>${Number(t.sales?.unassigned_leads||0)} belum punya PIC • ${Number(t.sales?.overdue_followups||0)} follow-up terlambat</p></div>
      <div class="g200-card"><small>Weighted Pipeline</small><b>${money(t.sales?.weighted_pipeline)}</b><p>Nilai pipeline × probability.</p></div>
      <div class="g200-card"><small>Tugas Operasional</small><b>${Number(t.operations?.open_tasks||0)}</b><p>${Number(t.operations?.overdue_tasks||0)} overdue • ${Number(t.operations?.unresolved_incidents||0)} insiden belum selesai</p></div>
      <div class="g200-card"><small>SDM Aktif</small><b>${Number(t.people?.active_staff||0)}</b><p>${Number(t.people?.probation_due||0)} probation due • ${Number(t.people?.contracts_expiring_30d||0)} kontrak ≤30 hari</p></div>
      <div class="g200-card"><small>Mutu & Risiko</small><b>${Number(t.quality?.open_capa||0)} CAPA</b><p>${Number(t.quality?.open_tickets||0)} tiket terbuka • ${Number(t.quality?.critical_risks||0)} risiko kritis</p></div>
      <div class="g200-card"><small>Payroll & Closing</small><b>${Number(t.finance?.open_payroll_entries||0)}</b><p>${Number(t.finance?.open_payroll_periods||0)} periode payroll terbuka • ${Number(t.finance?.unclosed_finance_periods||0)} periode finance belum lock</p></div>
      <div class="g200-card"><small>Governance</small><b>${Number(t.governance?.open_exceptions||0)} exception</b><p>${Number(t.governance?.critical_exceptions||0)} critical • ${Number(t.governance?.pending_approvals||0)} approval pending</p></div>
    </div><div class="g200-3" style="margin-top:10px"><div class="g200-card"><h4>Revenue Engine</h4><p>Lead → Quote → DP → Booking → Repeat Order. Fokus pada unassigned, overdue, conversion dan pipeline gap.</p><div class="g200-actions">${btn('CRM & Target','data-g200-go="salesTargetControl"')}</div></div><div class="g200-card"><h4>Delivery Engine</h4><p>Booking → H-7/H-3/H-1 → Trip → Actual Cost → Closing. Exception menjadi tugas, bukan chat tercecer.</p><div class="g200-actions">${btn('Tugas Otomatis','data-g200-go="taskAutomationControl"')}</div></div><div class="g200-card"><h4>People Engine</h4><p>Recruitment → Contract → Probation → KPI → Payroll → Development → Offboarding.</p><div class="g200-actions">${btn('People OS','data-g200-go="enterprisePeopleOS"')}</div></div></div>`);
  }

  function renderExceptions(){
    if(!(leader()||finance()||role()==='Admin'))return; ensurePage('enterpriseExceptionCenter','Exception Center','Masalah penting yang membutuhkan tindakan manusia');
    const open=state.exceptions.filter(x=>!['RESOLVED','DISMISSED'].includes(String(x.status)));
    const rows=open.slice(0,100).map(x=>`<div class="g200-row"><div><strong>${h(x.title)}</strong><small>${h(x.domain)} • ${h(x.description||x.exception_type)}</small></div><div><span class="g200-pill ${String(x.severity).toLowerCase()}">${h(x.severity)}</span></div><div>${h(x.assigned_role||'—')}</div><div>${dt(x.due_at)}</div><div class="g200-actions">${leader()?btn('Proses',`data-g200-ex-status="IN_PROGRESS" data-id="${h(x.id)}"`):''}${leader()?btn('Selesaikan',`data-g200-ex-status="RESOLVED" data-id="${h(x.id)}"`,'primary'):''}</div></div>`).join('');
    mount('enterpriseExceptionCenter','exceptions',`<div class="g200-grid"><div class="g200-card"><small>Exception terbuka</small><b>${open.length}</b></div><div class="g200-card"><small>Critical</small><b>${open.filter(x=>x.severity==='CRITICAL').length}</b></div><div class="g200-card"><small>Overdue</small><b>${open.filter(x=>x.due_at&&new Date(x.due_at)<new Date()).length}</b></div><div class="g200-card"><small>Prinsip</small><b>Exception Only</b><p>Direktur fokus pada penyimpangan strategis, bukan rutinitas.</p></div></div><div class="g200-list" style="margin-top:10px">${rows||'<div class="g200-empty">Tidak ada exception aktif.</div>'}</div>`);
  }

  function renderPeople(){
    ensurePage('enterprisePeopleOS',hr()?'People OS — Seluruh SDM':'People OS Saya',hr()?'HRIS lifecycle & workforce control':'Kontrak, KPI, pengembangan, kompensasi dan akses saya');
    const selfOnly=!hr(); const cp=state.comp; const pendingProb=state.probation.filter(x=>['PENDING','DUE'].includes(x.status)); const activeWarnings=state.warnings.filter(x=>!['CLOSED','RESOLVED'].includes(String(x.status).toUpperCase()));
    const avgKpi=state.kpis.length?state.kpis.reduce((a,x)=>a+Number(x.score||0),0)/state.kpis.length:null; const latestReview=state.reviews[0];
    const cards=`<div class="g200-grid"><div class="g200-card"><small>${selfOnly?'Kontrak Saya':'Kontrak tercatat'}</small><b>${state.contracts.length}</b><p>${selfOnly&&state.contracts[0]?`${h(state.contracts[0].position_title||'')} • ${h(state.contracts[0].employment_status||'')}`:'Lifecycle kontrak & masa kerja.'}</p></div><div class="g200-card"><small>Probation / Checkpoint</small><b>${pendingProb.length}</b><p>30/60/90 hari yang belum selesai.</p></div><div class="g200-card"><small>KPI</small><b>${avgKpi===null?'—':avgKpi.toFixed(1)}</b><p>${state.kpis.length} metric tercatat.</p></div><div class="g200-card"><small>Review Terakhir</small><b>${latestReview?.overall_score??'—'}</b><p>${h(latestReview?.grade||'Belum ada review')}</p></div><div class="g200-card"><small>Training</small><b>${state.training.length}</b><p>Sertifikasi & pengembangan kompetensi.</p></div><div class="g200-card"><small>Warning Aktif</small><b>${activeWarnings.length}</b><p>Coaching/PIP/discipline harus terdokumentasi.</p></div><div class="g200-card"><small>Asset & Access</small><b>${state.assetAccess.filter(x=>!['RETURNED','REVOKED','CLOSED'].includes(x.status)).length}</b><p>Onboarding/offboarding handover.</p></div><div class="g200-card"><small>Kompensasi</small><b>${cp?h(cp.compensation_type):'—'}</b><p>${cp?`${money(cp.base_monthly)} + transport ${money(cp.transport_per_attendance_day)}/hari hadir`:'Belum ada profil kompensasi aktif.'}</p></div></div>`;
    const probationRows=state.probation.slice(0,30).map(x=>`<tr><td>${x.checkpoint_day} hari</td><td>${d(x.due_date)}</td><td>${h(x.status)}</td><td>${x.score??'—'}</td><td>${h(x.decision||'—')}</td></tr>`).join('');
    const kpiRows=state.kpis.slice(0,30).map(x=>`<tr><td>${h(x.period)}</td><td>${h(x.metric_name)}</td><td>${x.actual??'—'} / ${x.target??'—'} ${h(x.unit||'')}</td><td>${x.score??'—'}</td></tr>`).join('');
    mount('enterprisePeopleOS','people',`${cards}<div class="g200-2" style="margin-top:10px"><div class="g200-card"><h4>Probation 30/60/90</h4><table class="g200-table"><thead><tr><th>Checkpoint</th><th>Jatuh tempo</th><th>Status</th><th>Score</th><th>Keputusan</th></tr></thead><tbody>${probationRows||'<tr><td colspan="5">Belum ada checkpoint.</td></tr>'}</tbody></table></div><div class="g200-card"><h4>KPI & Performance</h4><table class="g200-table"><thead><tr><th>Periode</th><th>Metric</th><th>Aktual/Target</th><th>Score</th></tr></thead><tbody>${kpiRows||'<tr><td colspan="4">Belum ada KPI.</td></tr>'}</tbody></table></div></div><div class="g200-3" style="margin-top:10px"><div class="g200-card"><h4>Lifecycle</h4><ol><li>Recruitment & approval</li><li>Contract & onboarding</li><li>Probation 30/60/90</li><li>Attendance, leave & assignment</li><li>KPI & performance review</li><li>Payroll/fee & development</li><li>Warning/PIP bila perlu</li><li>Offboarding + revoke access</li></ol></div><div class="g200-card"><h4>My Employee Experience</h4><ul><li>Jobdesk & SOP</li><li>Tugas hari ini</li><li>KPI & target</li><li>Cuti & kehadiran</li><li>Payroll/fee</li><li>Training & review</li><li>Kontrak & akses</li></ul></div><div class="g200-card"><h4>Aturan</h4><p>AI boleh memberi rekomendasi staffing, coaching dan risiko. Keputusan kontrak, disiplin, kompensasi dan PHK tetap manusia serta mengikuti hukum yang berlaku.</p><div class="g200-actions">${btn('Jobdesk & SOP','data-g200-go="rolePlaybook"')}${btn('Tugas Saya','data-g200-go="myTasksHub"')}</div></div></div>`);
  }

  function renderAutomation(){
    if(!leader())return; ensurePage('enterpriseAutomationMap','Automation Map','Policy lintas divisi • trigger → action → owner → SLA');
    const rows=state.policies.map(x=>`<tr><td>${h(x.domain)}</td><td>${h(x.trigger_event)}</td><td>${h(x.action_type)}</td><td>${h(x.assigned_role||'—')}</td><td>${Number(x.sla_hours||0)} jam</td><td>${x.requires_approval?'Ya':'Tidak'}</td><td><span class="g200-pill ${String(x.severity).toLowerCase()}">${h(x.severity)}</span></td></tr>`).join('');
    mount('enterpriseAutomationMap','map',`<div class="g200-card"><h4>Enterprise Flow</h4><p><b>Lead → Quote → DP → Booking → Ops → Crew/Vendor → Trip → Actual Cost → Payroll → Closing → Feedback/CAPA → Repeat Order.</b></p><p>Perubahan bisnis menghasilkan event, task, approval, notification atau exception. Aksi sensitif tetap membutuhkan manusia.</p></div><div class="g200-card" style="margin-top:10px;overflow:auto"><table class="g200-table"><thead><tr><th>Domain</th><th>Trigger</th><th>Aksi</th><th>Owner</th><th>SLA</th><th>Approval</th><th>Severity</th></tr></thead><tbody>${rows||'<tr><td colspan="7">Policy belum tersedia.</td></tr>'}</tbody></table></div>`);
  }

  function render(){installStyle();renderTower();renderExceptions();renderPeople();renderAutomation()}

  async function updateException(id,status){
    if(!leader())return; const values={status,updated_at:new Date().toISOString()}; if(status==='RESOLVED'){values.resolved_by=uid();values.resolved_at=new Date().toISOString();values.resolution='Diselesaikan melalui Enterprise Exception Center';}
    const {error}=await db().from('enterprise_exception_queue').update(values).eq('id',id); if(error)throw error; await load();
  }

  function nav(){const r=role();ensureNav('enterpriseControlTower','Enterprise Control Tower',LEADERS.has(r));ensureNav('enterprisePeopleOS',HR_ROLES.has(r)?'People OS':'People OS Saya',!!r);ensureNav('enterpriseExceptionCenter','Exception Center',LEADERS.has(r)||FINANCE_ROLES.has(r)||r==='Admin');ensureNav('enterpriseAutomationMap','Automation Map',LEADERS.has(r))}
  function go(id){const b=q(`#nav [data-page="${CSS.escape(id)}"]`);if(b){b.click();return}const labels={enterprisePeopleOS:'People OS',salesTargetControl:'Target & Kinerja',taskAutomationControl:'Tugas Otomatis',rolePlaybook:'Jobdesk & SOP Saya',myTasksHub:'Tugas Saya'};showPage(id,labels[id]||id)}

  function init(){
    let tries=0; const timer=setInterval(()=>{tries++;if(role()&&q('#nav')){clearInterval(timer);nav();render();load()}if(tries>60)clearInterval(timer)},250);
    document.addEventListener('click',async e=>{const g=e.target.closest('[data-g200-go]');if(g){go(g.dataset.g200Go);return}const x=e.target.closest('[data-g200-ex-status]');if(x){x.disabled=true;try{await updateException(x.dataset.id,x.dataset.g200ExStatus)}catch(err){alert(err?.message||String(err))}finally{x.disabled=false}}});
    setInterval(()=>{if(role())load()},60000);
  }

  window.GmuEnterpriseOS=Object.freeze({version:VERSION,refresh:load,show:id=>go(id)});
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
