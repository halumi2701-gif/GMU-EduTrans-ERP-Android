(() => {
  'use strict';

  const VERSION = 'v21.2-target-cascade-workforce-autopilot';
  const MANAGEMENT = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans']);
  const DIRECTOR = new Set(['Owner','Director','Direktur']);
  const q=(s,r=document)=>r.querySelector(s);
  const h=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money=v=>new Intl.NumberFormat('id-ID',{style:'currency',currency:'IDR',maximumFractionDigits:0}).format(Number(v||0));
  const pct=(a,b)=>b>0?Math.max(0,Math.min(100,(Number(a||0)/Number(b))*100)):0;
  const state={data:null,loading:false,error:null};

  function role(){try{return String(profile?.role||'')}catch(_){return ''}}
  function db(){try{return typeof sb!=='undefined'&&sb?.rpc?sb:null}catch(_){return null}}
  function allowed(){return MANAGEMENT.has(role())}
  function canSet(){return DIRECTOR.has(role())}

  function ensureStyle(){
    if(q('#gmuV212Style')) return;
    const s=document.createElement('style');s.id='gmuV212Style';s.textContent=`
      .v212-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;flex-wrap:wrap}.v212-head h2{margin:0;color:var(--gd)}.v212-head p{margin:5px 0 0;font-size:9px;color:var(--muted);max-width:840px}.v212-actions{display:flex;gap:6px;flex-wrap:wrap}.v212-btn{border:1px solid var(--line);background:#fff;color:var(--gd);border-radius:9px;padding:7px 10px;font:inherit;font-size:8px;cursor:pointer}.v212-btn.primary{background:var(--gd);color:#fff}.v212-banner{margin:12px 0;padding:13px;border-radius:14px;background:#eef9f1;border:1px solid #cce8d5}.v212-banner.warn{background:#fff5ed;border-color:#efd2b8}.v212-banner b{display:block;color:var(--gd);font-size:13px}.v212-banner span{font-size:8px;color:var(--muted)}.v212-progress{height:9px;border-radius:999px;background:#e8eeea;overflow:hidden;margin-top:9px}.v212-progress i{display:block;height:100%;background:var(--gd);border-radius:999px}.v212-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}.v212-card{border:1px solid var(--line);background:#fff;border-radius:13px;padding:11px}.v212-card small{display:block;color:var(--muted);font-size:8px}.v212-card strong{display:block;color:var(--gd);font-size:17px;margin:4px 0}.v212-card p{font-size:8px;color:var(--muted);margin:0;line-height:1.45}.v212-section{margin-top:12px}.v212-section h4{margin:0 0 7px;color:var(--gd)}.v212-row{display:grid;grid-template-columns:115px 1fr 110px;gap:8px;align-items:center;border-top:1px solid var(--line);padding:8px 2px;font-size:8px}.v212-row:first-child{border-top:0}.v212-role{font-weight:700;color:var(--gd)}.v212-muted{color:var(--muted)}.v212-error{padding:10px;border-radius:12px;background:#fff4f4;border:1px solid #efc5c5;color:#963434;font-size:9px}.v212-note{padding:10px 12px;border-radius:12px;background:#f5f8f6;border:1px solid var(--line);font-size:8px;line-height:1.55;color:var(--gd)}@media(max-width:900px){.v212-grid{grid-template-columns:1fr 1fr}}@media(max-width:620px){.v212-grid{grid-template-columns:1fr}.v212-row{grid-template-columns:1fr}.v212-actions{width:100%}}
    `;document.head.appendChild(s);
  }

  function ensurePage(){
    if(!allowed()) return null;
    let page=q('#targetCascadeV212');
    if(!page){
      page=document.createElement('section');page.id='targetCascadeV212';page.className='page';
      page.innerHTML=`<div class="v212-head"><div><div class="v212-muted">GMU EduTrans • Director Command → Workforce Execution</div><h2>Target Cascade & Workforce Autopilot v21.2</h2><p>Target laba Direktur otomatis diterjemahkan menjadi target omzet, gap forecast, target Manager/Sales, SLA Admin/Ops/Finance, guardrail SDM dan tugas recovery.</p></div><div class="v212-actions">${canSet()?'<button type="button" class="v212-btn primary" data-v212-set>Set Target Laba</button>':''}<button type="button" class="v212-btn" data-v212-refresh>Refresh</button></div></div><div data-v212-body></div>`;
      (q('.content')||q('main')||document.body).appendChild(page);
      q('[data-v212-refresh]',page)?.addEventListener('click',load);
      q('[data-v212-set]',page)?.addEventListener('click',setTarget);
    }
    const nav=q('#nav');
    if(nav&&!q('#nav [data-page="targetCascadeV212"]')){
      const b=document.createElement('button');b.type='button';b.dataset.page='targetCascadeV212';b.textContent='Target Cascade';b.addEventListener('click',show);nav.appendChild(b);
    }
    return page;
  }

  function show(){
    document.querySelectorAll('.page').forEach(x=>x.classList.remove('active'));
    q('#targetCascadeV212')?.classList.add('active');
    document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page==='targetCascadeV212'));
    if(q('#title')) q('#title').textContent='Target Cascade';
    load();
  }

  function card(label,value,desc){return `<div class="v212-card"><small>${h(label)}</small><strong>${h(value)}</strong><p>${h(desc)}</p></div>`}

  function render(){
    const body=q('[data-v212-body]');if(!body)return;
    if(state.loading&&!state.data){body.innerHTML='<div class="v212-note">Menghitung target, forecast, payroll guardrail dan pembagian kerja…</div>';return}
    if(state.error){body.innerHTML=`<div class="v212-error">${h(state.error)}</div>`;return}
    const d=state.data||{},t=d.target||{},s=d.snapshot||{},detail=s.detail||{},cg=detail.compensation_guardrail||{};
    const progress=pct(Math.max(0,Number(t.target_amount||0)-Number(s.recovery_gap||0)),Number(t.target_amount||0));
    const assignments=Array.isArray(d.assignments)?d.assignments:[];
    body.innerHTML=`
      <div class="v212-banner ${Number(s.forecast_gap||0)>0?'warn':''}"><b>Recovery ${money(t.target_amount)} • Progress ${progress.toFixed(1)}%</b><span>Sisa recovery ${money(s.recovery_gap)}. Direktur menetapkan hasil; Manager dan sistem menggerakkan pekerjaan harian.</span><div class="v212-progress"><i style="width:${progress}%"></i></div></div>
      <div class="v212-grid">
        ${card('Target Laba Bulanan',money(s.target_profit_month),'Floor operasional selama recovery mode.')}
        ${card('Target Omzet Bulanan',money(s.target_revenue_month),'Sudah mempertimbangkan margin target, payroll tetap aktif dan overhead terencana.')}
        ${card('Forecast Gap',money(s.forecast_gap),'Jika >0, recovery task otomatis tetap aktif.')}
        ${card('Pipeline Tertimbang',money(s.weighted_pipeline),'Nilai pipeline dikalikan probabilitas closing.')}
        ${card('Omzet Sisa Bulan Ini',money(s.required_revenue_month),'Kebutuhan berdasarkan gap laba saat ini.')}
        ${card('Omzet Recovery Total',money(s.required_revenue_recovery),'Estimasi pada margin target saat ini.')}
        ${card('Sales Aktif',Number(s.active_sales_count||0),'Jika 0, fungsi Sales otomatis fallback ke Manager.')}
        ${card('Confidence Data',s.data_confidence||'LOW',s.avg_profit_per_trip?`Rata-rata laba/trip ${money(s.avg_profit_per_trip)}.`:'Belum cukup histori closing; ERP tidak mengarang jumlah booking.')}
      </div>
      <div class="v212-section"><h4>Workforce & Compensation Guardrail</h4><div class="v212-grid">
        ${card('Fixed Payroll Aktif',money(s.fixed_payroll),'Gaji/retainer aktif yang sudah tercatat.')}
        ${card('Overhead Terencana',money(s.planned_overhead),'Budget fixed/operating bulan berjalan.')}
        ${card('Headroom Fixed Payroll',money(s.additional_fixed_payroll_headroom),'Batas tambahan berbasis weighted pipeline saat ini; bukan izin otomatis menaikkan gaji.')}
        ${card('Rekomendasi',cg.recommendation==='KEEP_FIXED_PAYROLL_LEAN_USE_VARIABLE_FEE'?'LEAN + VARIABLE':'REVIEW OWNER','Perubahan gaji/retainer tetap melalui approval Owner.')}
      </div></div>
      <div class="v212-section"><h4>Target Turun ke SDM</h4>${assignments.length?assignments.map(a=>`<div class="v212-row"><div class="v212-role">${h(a.assigned_role)}</div><div><b>${h(a.objective)}</b><div class="v212-muted">Target asal: ${h(a.payload?.desired_role||a.assigned_role)}${a.payload?.fallback_used?' • fallback sementara':''}</div></div><div>${a.target_value==null?'—':(String(a.target_unit||'').toLowerCase().includes('rp')?money(a.target_value):h(a.target_value+' '+(a.target_unit||'')))}</div></div>`).join(''):'<div class="v212-note">Belum ada assignment aktif.</div>'}</div>
      <div class="v212-section"><div class="v212-note"><b>Aturan:</b> target tidak dianggap tercapai karena task dicentang. Actual laba berasal dari data keuangan/closing. Transfer, payroll payment, refund, perubahan harga strategis, margin &lt;20%, reserve, hiring/firing, legal dan safety tetap membutuhkan approval manusia.</div></div>`;
  }

  async function load(){
    if(!allowed()||!db()||state.loading)return;
    state.loading=true;state.error=null;render();
    try{const {data,error}=await db().rpc('internal_target_cascade_status');if(error)throw error;state.data=data||{};}catch(e){state.error=e?.message||String(e)}finally{state.loading=false;render()}
  }

  async function setTarget(){
    if(!canSet()||!db())return;
    const current=Number(state.data?.target?.target_amount||50000000);
    const raw=prompt('Target laba/recovery (Rp):',String(Math.round(current)));
    if(raw==null)return;
    const amount=Number(String(raw).replace(/[^0-9.-]/g,''));
    if(!Number.isFinite(amount)||amount<=0){alert('Target harus lebih dari 0.');return}
    const mode=confirm('OK = Recovery Kumulatif. Cancel = Target Laba Bulanan.')?'RECOVERY_CUMULATIVE':'MONTHLY_NET_PROFIT';
    const {error}=await db().rpc('internal_set_executive_profit_target',{p_target_amount:amount,p_target_mode:mode,p_monthly_profit_floor:15000000,p_target_margin_pct:25,p_recovery_start_date:new Date().toISOString().slice(0,10),p_notes:'Target ditetapkan dari ERP Target Cascade v21.2'});
    if(error){alert(error.message||String(error));return}await load();
  }

  function boot(){if(!allowed())return;ensureStyle();ensurePage();load();setInterval(()=>{if(allowed())load()},60000);window.__GMU_TARGET_CASCADE_V212__={version:VERSION,show,load};}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot,{once:true});else boot();
})();