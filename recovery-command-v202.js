(() => {
  'use strict';

  const VERSION = 'v20.2-recovery-accountability-command';
  const ROLES = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans']);
  const OPEN_EXCEPTION = new Set(['OPEN','IN_PROGRESS','WAITING']);
  const q = (s,r=document) => r.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const dt = v => v ? new Date(v).toLocaleString('id-ID',{dateStyle:'medium',timeStyle:'short'}) : '—';
  const todayKey = () => new Date().toISOString().slice(0,10);
  const state = {exceptions:[],tasks:[],loading:false,error:null,syncing:false};

  function role(){ try{return String(profile?.role||'')}catch(_){return ''} }
  function uid(){ try{return String(profile?.id||user?.id||'')}catch(_){return ''} }
  function db(){ try{return typeof sb!=='undefined'&&sb?.from?sb:null}catch(_){return null} }
  function allowed(){ return ROLES.has(role()); }

  function recoverySpec(e){
    const type=String(e?.exception_type||'').toUpperCase();
    const domain=String(e?.domain||'').toUpperCase();
    if(type==='SALES_TARGET_GAP') return {action:'Susun recovery prospek harian, bagi target ke Sales, dan pastikan aktivitas tercatat setiap hari.',target:'Aktivitas Sales kembali minimal sesuai target prorata.',check:'Bandingkan prospek aktual dengan target prorata pada Prioritas Bisnis.'};
    if(type==='FOLLOWUP_OVERDUE') return {action:'Tuntaskan seluruh follow-up overdue, tetapkan next action dan tanggal tindak lanjut baru untuk setiap lead.',target:'Follow-up overdue = 0.',check:'Cek CRM dan daftar follow-up yang melewati jadwal.'};
    if(type==='AR_OVERDUE') return {action:'Validasi saldo piutang, hubungi customer, catat komitmen bayar, dan eskalasi hambatan pembayaran.',target:'Piutang overdue turun dan setiap invoice memiliki tindak lanjut yang jelas.',check:'Cek AR Aging dan pembayaran terverifikasi.'};
    if(type==='TRIP_PROFIT_NOT_CLOSED') return {action:'Lengkapi biaya aktual, pendapatan, fee/payroll terkait kegiatan, lalu selesaikan Trip Closing.',target:'Seluruh kegiatan Closed memiliki profitability closing.',check:'Cek Trip Closing dan laporan laba kegiatan.'};
    if(type==='LOW_MARGIN') return {action:'Analisis penyebab margin rendah, pisahkan cost overrun vs pricing issue, lalu buat tindakan koreksi sebelum paket dijual kembali.',target:'Tidak ada kegiatan/paket berulang dengan margin di bawah standar tanpa approval.',check:'Margin sehat minimal 25%; margin <20% wajib eskalasi Direktur.'};
    if(type==='PACKAGE_NOT_READY') return {action:'Lengkapi approval paket, cost template aktif, harga minimum, dan validasi margin sebelum paket dijual.',target:'Semua paket aktif siap dijual secara komersial.',check:'Cek Master Paket: aktif + approved + cost template aktif.'};
    if(domain==='SALES') return {action:'Pulihkan gap Sales dan dokumentasikan next action.',target:'KPI Sales kembali sesuai target berjalan.',check:'Cek Sales Engine dan CRM.'};
    if(domain==='FINANCE') return {action:'Selesaikan exception Finance dan bukti closing/rekonsiliasinya.',target:'Tidak ada exception Finance prioritas yang terbuka.',check:'Cek Finance Closing, AR/AP dan periode.'};
    return {action:'Analisis akar masalah, tetapkan tindakan koreksi, PIC dan deadline, lalu dokumentasikan bukti hasil.',target:'Exception prioritas tidak lagi aktif.',check:'Status exception harus pulih dari data sumber, bukan ditutup manual.'};
  }

  function taskKey(e){ return `v202-recovery:${e.id}:${todayKey()}`; }
  function priority(e){ return String(e?.severity||'HIGH').toUpperCase()==='CRITICAL'?'CRITICAL':'HIGH'; }

  async function selectExceptions(){
    const {data,error}=await db().from('enterprise_exception_queue')
      .select('id,exception_key,domain,exception_type,severity,title,description,assigned_role,assigned_to,due_at,status,created_at,updated_at')
      .in('status',[...OPEN_EXCEPTION]).in('severity',['HIGH','CRITICAL'])
      .order('severity',{ascending:true}).order('due_at',{ascending:true}).limit(150);
    if(error) throw error; return Array.isArray(data)?data:[];
  }

  async function selectTasks(){
    const {data,error}=await db().from('automation_tasks')
      .select('id,task_key,assigned_to,assigned_role,task_type,title,description,due_at,priority,status,evidence_required,evidence,completed_at,created_at,updated_at')
      .eq('task_type','RECOVERY_ACTION').like('task_key','v202-recovery:%')
      .order('created_at',{ascending:false}).limit(250);
    if(error) throw error; return Array.isArray(data)?data:[];
  }

  async function ensureRecoveryTasks(exceptions,tasks){
    if(state.syncing||!exceptions.length) return;
    const keys=new Set(tasks.map(x=>x.task_key));
    const missing=exceptions.filter(e=>!keys.has(taskKey(e)));
    if(!missing.length) return;
    state.syncing=true;
    try{
      const rows=missing.map(e=>{
        const s=recoverySpec(e);
        return {
          task_key:taskKey(e), assigned_to:e.assigned_to||null, assigned_role:e.assigned_role||'Manager',
          task_type:'RECOVERY_ACTION', title:`Recovery: ${e.title}`,
          description:`MASALAH: ${e.description||e.title}\nTINDAKAN: ${s.action}\nTARGET HASIL: ${s.target}\nEVALUASI: ${s.check}\nSUMBER EXCEPTION: ${e.exception_key}`,
          due_at:e.due_at||new Date(Date.now()+4*3600000).toISOString(), priority:priority(e), status:'OPEN',
          evidence_required:true, approval_required:false,
          evidence:{source:'GMU v20.2 Recovery Command',exception_id:e.id,exception_key:e.exception_key,generated_on:todayKey()}
        };
      });
      const {error}=await db().from('automation_tasks').insert(rows);
      if(error && !String(error.code||'').includes('23505')) throw error;
    } finally { state.syncing=false; }
  }

  async function load(){
    if(!allowed()||!db()||state.loading) return;
    state.loading=true; state.error=null; render();
    try{
      let [exceptions,tasks]=await Promise.all([selectExceptions(),selectTasks()]);
      await ensureRecoveryTasks(exceptions,tasks);
      tasks=await selectTasks();
      state.exceptions=exceptions; state.tasks=tasks;
    }catch(e){state.error=e?.message||String(e)}finally{state.loading=false;render()}
  }

  function activeTaskFor(e){
    const prefix=`v202-recovery:${e.id}:`;
    return state.tasks.find(t=>String(t.task_key||'').startsWith(prefix) && t.task_key===taskKey(e)) || state.tasks.find(t=>String(t.task_key||'').startsWith(prefix));
  }
  function overdue(t){return t?.due_at && new Date(t.due_at)<new Date() && !['DONE','CANCELLED'].includes(t.status)}
  function badge(text,kind='neutral'){return `<span class="gr202-pill ${kind}">${h(text)}</span>`}

  function row(e){
    const t=activeTaskFor(e), s=recoverySpec(e); const critical=e.severity==='CRITICAL';
    const taskStatus=t?.status||'BELUM DIBUAT'; const due=t?.due_at||e.due_at;
    const kind=critical?'bad':overdue(t)?'bad':taskStatus==='DONE'?'ok':'warn';
    return `<div class="gr202-row">
      <div><div class="gr202-kicker">${h(e.domain)} • ${h(e.exception_type)}</div><strong>${h(e.title)}</strong><p>${h(e.description||'')}</p></div>
      <div>${badge(e.severity,critical?'bad':'warn')}<small>PIC: ${h(t?.assigned_role||e.assigned_role||'Manager')}</small><small>Deadline: ${h(dt(due))}</small></div>
      <div><b>${h(s.action)}</b><small>Target: ${h(s.target)}</small><small>Evaluasi: ${h(s.check)}</small></div>
      <div>${badge(taskStatus,kind)}<div class="gr202-actions">${t&&['OPEN','OVERDUE'].includes(t.status)?`<button data-start="${h(t.id)}">Mulai</button>`:''}${t&&['OPEN','IN_PROGRESS','OVERDUE','WAITING_APPROVAL'].includes(t.status)?`<button class="primary" data-done="${h(t.id)}">Selesai + Bukti</button>`:''}</div></div>
    </div>`;
  }

  function render(){
    const body=q('[data-recovery-body]'); if(!body)return;
    if(state.loading){body.innerHTML='<div class="gr202-note">Menyusun Recovery Command dari exception aktual…</div>';return}
    if(state.error){body.innerHTML=`<div class="gr202-error">${h(state.error)}</div>`;return}
    const open=state.exceptions.length, critical=state.exceptions.filter(x=>x.severity==='CRITICAL').length;
    const active=state.tasks.filter(x=>['OPEN','IN_PROGRESS','OVERDUE','WAITING_APPROVAL'].includes(x.status)).length;
    const late=state.tasks.filter(overdue).length;
    const director=state.exceptions.filter(x=>['Director','Direktur','Owner'].includes(x.assigned_role)).length;
    body.innerHTML=`<div class="gr202-metrics">
      <div><small>Exception Prioritas</small><b>${open}</b></div><div><small>Kritis</small><b>${critical}</b></div><div><small>Tugas Recovery Aktif</small><b>${active}</b></div><div><small>Terlambat</small><b>${late}</b></div>
    </div>
    <div class="gr202-director ${director?'warn':''}"><b>${director?`${director} tindakan membutuhkan Direktur`:'TIDAK ADA TINDAKAN DIREKTUR'}</b><span>${director?'Hanya exception strategis/kritis yang dinaikkan.':'Operasional tetap menjadi tanggung jawab Manager dan tim.'}</span></div>
    <div class="gr202-note"><b>Formula v20.2:</b> MASALAH → PENYEBAB/DATA → PIC → TINDAKAN → DEADLINE → TARGET HASIL → BUKTI → EVALUASI. Task selesai tidak otomatis menutup exception; exception hanya pulih jika angka/data sumber sudah sehat.</div>
    <div class="gr202-list">${open?state.exceptions.map(row).join(''):'<div class="gr202-empty">Tidak ada exception HIGH/CRITICAL. Sistem dalam kondisi terkendali.</div>'}</div>`;
    body.querySelectorAll('[data-start]').forEach(b=>b.addEventListener('click',()=>setStatus(b.dataset.start,'IN_PROGRESS')));
    body.querySelectorAll('[data-done]').forEach(b=>b.addEventListener('click',()=>complete(b.dataset.done)));
  }

  async function setStatus(id,status){
    const {error}=await db().from('automation_tasks').update({status,updated_at:new Date().toISOString()}).eq('id',id);
    if(error){alert(`Gagal memperbarui tugas: ${error.message||error}`);return} await load();
  }
  async function complete(id){
    const summary=prompt('Bukti/hasil tindakan yang sudah dilakukan:'); if(!summary||!summary.trim())return;
    const task=state.tasks.find(x=>x.id===id); const evidence={...(task?.evidence||{}),completion_summary:summary.trim(),completed_via:VERSION,completed_at:new Date().toISOString()};
    const {error}=await db().from('automation_tasks').update({status:'DONE',evidence,completed_by:uid()||null,completed_at:new Date().toISOString(),updated_at:new Date().toISOString()}).eq('id',id);
    if(error){alert(`Gagal menyelesaikan tugas: ${error.message||error}`);return} await load();
  }

  function style(){
    if(q('#gmuRecovery202Style'))return; const s=document.createElement('style');s.id='gmuRecovery202Style';s.textContent=`
      .gr202-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;flex-wrap:wrap}.gr202-head h2{margin:0;color:var(--gd)}.gr202-head p{margin:5px 0 0;font-size:9px;color:var(--muted);max-width:760px}.gr202-head button,.gr202-actions button{border:1px solid var(--line);background:#fff;color:var(--gd);border-radius:9px;padding:7px 9px;font:inherit;font-size:8px;cursor:pointer}.gr202-actions button.primary{background:var(--g);border-color:var(--g);color:#fff}.gr202-metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin:12px 0}.gr202-metrics>div{padding:10px;border:1px solid var(--line);border-radius:12px;background:#fff}.gr202-metrics small{display:block;font-size:7px;color:var(--muted)}.gr202-metrics b{font-size:17px;color:var(--gd)}.gr202-director{display:flex;justify-content:space-between;gap:10px;align-items:center;padding:10px 12px;background:#eef9f1;border:1px solid #cce8d5;border-radius:12px;font-size:9px;margin-bottom:9px}.gr202-director.warn{background:#fff4f1;border-color:#efc5c5}.gr202-director span{color:var(--muted)}.gr202-note{padding:10px 12px;border-radius:12px;background:#f4f8f6;font-size:9px;line-height:1.55;margin:9px 0}.gr202-list{display:grid;gap:8px}.gr202-row{display:grid;grid-template-columns:1.5fr .7fr 1.35fr .7fr;gap:10px;padding:12px;border:1px solid var(--line);border-radius:14px;background:#fff;align-items:start}.gr202-row strong{display:block;color:var(--gd);font-size:10px;margin:3px 0}.gr202-row b{font-size:9px;color:var(--gd)}.gr202-row p,.gr202-row small{display:block;font-size:8px;color:var(--muted);line-height:1.5;margin:4px 0}.gr202-kicker{font-size:7px;color:var(--muted);text-transform:uppercase;letter-spacing:.04em}.gr202-pill{display:inline-flex;padding:4px 7px;border-radius:999px;border:1px solid var(--line);font-size:7px;font-weight:900;margin-bottom:5px}.gr202-pill.ok{background:#eef9f1;color:#25643a}.gr202-pill.warn{background:#fff8e9;color:#895d14}.gr202-pill.bad{background:#fff1f1;color:#a33434}.gr202-pill.neutral{background:#f5f7f6;color:#66746d}.gr202-actions{display:flex;gap:5px;flex-wrap:wrap;margin-top:7px}.gr202-empty{text-align:center;padding:18px;border:1px dashed var(--line);border-radius:12px;color:var(--muted);font-size:9px}.gr202-error{padding:10px;border:1px solid #efc5c5;background:#fff4f4;color:#963434;border-radius:12px;font-size:9px}
      @media(max-width:980px){.gr202-row{grid-template-columns:1fr 1fr}.gr202-metrics{grid-template-columns:repeat(2,1fr)}}@media(max-width:620px){.gr202-row,.gr202-metrics{grid-template-columns:1fr}.gr202-director{align-items:flex-start;flex-direction:column}}
    `;document.head.appendChild(s);
  }

  function show(){document.querySelectorAll('.page').forEach(x=>x.classList.remove('active'));q('#recoveryCommand202')?.classList.add('active');document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page==='recoveryCommand202'));if(q('#title'))q('#title').textContent='Pusat Pemulihan';load()}
  function install(){
    if(!allowed()||q('#recoveryCommand202'))return; style(); const content=q('.content'),nav=q('#nav');if(!content||!nav)return;
    const page=document.createElement('section');page.id='recoveryCommand202';page.className='page';page.innerHTML=`<div class="card section"><div class="gr202-head"><div><div class="gr202-kicker">GMU EduTrans • ${VERSION}</div><h2>Recovery Command & Accountability</h2><p>Setiap gap HIGH/CRITICAL otomatis menjadi tugas recovery harian dengan PIC, deadline, target hasil dan bukti penyelesaian.</p></div><button data-refresh>Refresh</button></div><div data-recovery-body></div></div>`;content.appendChild(page);q('[data-refresh]',page)?.addEventListener('click',load);
    const b=document.createElement('button');b.type='button';b.dataset.page='recoveryCommand202';b.textContent='◆  Pusat Pemulihan';b.addEventListener('click',show);nav.prepend(b);load();
  }
  function init(){const timer=setInterval(()=>{if(!role())return;clearInterval(timer);install();if(allowed())setInterval(load,60000)},250);window.GmuRecoveryCommand=Object.freeze({version:VERSION,show,load})}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();