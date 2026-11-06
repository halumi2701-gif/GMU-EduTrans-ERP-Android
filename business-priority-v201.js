(() => {
  'use strict';

  const VERSION = 'v20.1-business-priority-command';
  const ROLES = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans']);
  const q = (s,r=document) => r.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = v => v == null ? '—' : 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  const num = v => Number(v || 0).toLocaleString('id-ID',{maximumFractionDigits:1});
  let state = null;
  let loading = false;
  let error = null;

  function role(){ try{return String(profile?.role||'')}catch(_){return ''} }
  function db(){ try{return typeof sb!=='undefined'&&sb?.rpc?sb:null}catch(_){return null} }
  function allowed(){ return ROLES.has(role()); }

  function style(){
    if(q('#gmuPriority201Style')) return;
    const s=document.createElement('style'); s.id='gmuPriority201Style'; s.textContent=`
      .gp201-hero{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;flex-wrap:wrap}.gp201-hero h2{margin:0;color:var(--gd)}.gp201-hero p{margin:5px 0 0;font-size:9px;color:var(--muted);max-width:760px;line-height:1.55}
      .gp201-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:12px}.gp201-card{border:1px solid var(--line);border-radius:15px;background:#fff;padding:13px;min-width:0}.gp201-card h4{margin:5px 0 8px;color:var(--gd)}.gp201-card p{font-size:9px;line-height:1.55;color:var(--muted);margin:5px 0}.gp201-kicker{font-size:8px;color:var(--muted);text-transform:uppercase;letter-spacing:.04em}.gp201-status{display:inline-flex;border-radius:999px;padding:4px 7px;font-size:8px;font-weight:900;border:1px solid var(--line)}.gp201-ok{background:#eef9f1;color:#25643a}.gp201-warn{background:#fff8e9;color:#895d14}.gp201-bad{background:#fff1f1;color:#a33434}.gp201-neutral{background:#f5f7f6;color:#66746d}
      .gp201-metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin-top:8px}.gp201-metric{background:#f8fbf9;border-radius:10px;padding:8px}.gp201-metric small{display:block;font-size:7px;color:var(--muted)}.gp201-metric b{display:block;font-size:13px;color:var(--gd);margin-top:2px}.gp201-progress{height:7px;background:#edf1ef;border-radius:999px;overflow:hidden;margin-top:5px}.gp201-progress span{display:block;height:100%;background:var(--g);border-radius:999px}.gp201-actions{display:flex;gap:6px;flex-wrap:wrap;margin-top:10px}.gp201-btn{border:1px solid var(--line);background:#fff;color:var(--gd);border-radius:9px;padding:7px 9px;font:inherit;font-size:8px;cursor:pointer}.gp201-btn.primary{background:var(--g);border-color:var(--g);color:#fff}.gp201-order{display:flex;gap:6px;flex-wrap:wrap}.gp201-order span{font-size:8px;padding:5px 7px;border-radius:999px;background:#f4f8f6;border:1px solid var(--line)}.gp201-note{margin-top:12px;border-radius:13px;padding:11px;background:#f4f8f6;font-size:9px;line-height:1.6}.gp201-error{border:1px solid #efc5c5;background:#fff4f4;color:#963434;padding:10px;border-radius:12px;font-size:9px}
      @media(max-width:980px){.gp201-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:620px){.gp201-grid{grid-template-columns:1fr}.gp201-metrics{grid-template-columns:1fr}}
    `; document.head.appendChild(s);
  }

  function badge(text,kind='neutral'){return `<span class="gp201-status gp201-${kind}">${h(text)}</span>`}
  function progress(actual,target){const p=target?Math.max(0,Math.min(100,Number(actual||0)/Number(target)*100)):0;return `<div class="gp201-progress"><span style="width:${p}%"></span></div>`}
  function metric(label,value,sub=''){return `<div class="gp201-metric"><small>${h(label)}</small><b>${h(value)}</b>${sub?`<small>${h(sub)}</small>`:''}</div>`}

  function statusSales(s){const a=s?.actual||{},p=s?.prorated||{}; if((a.prospects||0)<Math.max(1,(p.prospects||0)*.5)||s?.unassigned>0)return ['KRITIS','bad']; if((a.prospects||0)<(p.prospects||0)||s?.overdue_followups>0)return ['PERLU AKSI','warn']; return ['TERKENDALI','ok']}
  function statusProfit(p){if(!p?.data_ready)return ['DATA BELUM SIAP','neutral']; if((p.low_margin_trips||0)>0||(p.avg_margin_pct||0)<20)return ['KRITIS','bad']; if((p.avg_margin_pct||0)<25)return ['REVIEW','warn']; return ['SEHAT','ok']}
  function statusFinance(f){if(!f?.period_ready)return ['PERIODE BELUM SIAP','bad']; if((f.overdue_invoice_count||0)>0)return ['PIUTANG OVERDUE','warn']; return ['TERKENDALI','ok']}
  function statusPackage(p){return p?.ready?['SIAP JUAL','ok']:['BELUM SIAP','warn']}
  function statusAI(a){if((a?.critical_exceptions||0)>0)return ['ESKALASI','bad']; if((a?.open_exceptions||0)>0)return ['TINDAKAN','warn']; return ['NORMAL','ok']}

  function cardSales(s){const st=statusSales(s),a=s?.actual||{},p=s?.prorated||{};return `<div class="gp201-card"><div class="gp201-kicker">1 • Sales Engine</div><h4>Aktivitas → Pipeline → Booking</h4>${badge(st[0],st[1])}<div class="gp201-metrics">${metric('Prospek bulan ini',`${a.prospects||0} / ${p.prospects||0}`,'aktual / target prorata')}${metric('Peluang potensial',`${a.qualified||0} / ${p.qualified||0}`)}${metric('Penawaran',`${a.quotations||0} / ${p.quotations||0}`)}${metric('Booking sehat',`${a.bookings||0} / ${p.bookings||0}`)}${metric('Lead tanpa PIC',s?.unassigned||0)}${metric('Follow-up overdue',s?.overdue_followups||0)}</div>${progress(a.prospects,p.prospects)}<p>Weighted pipeline: <b>${money(s?.weighted_pipeline)}</b>. Sistem memprioritaskan pembagian PIC, follow-up, dan recovery sebelum ekspansi.</p><div class="gp201-actions"><button class="gp201-btn primary" data-jump="Target & Kinerja">Buka Sales</button></div></div>`}
  function cardProfit(p){const st=statusProfit(p);return `<div class="gp201-card"><div class="gp201-kicker">2 • Profitability</div><h4>Omzet bukan tujuan tanpa laba</h4>${badge(st[0],st[1])}<div class="gp201-metrics">${metric('Target laba bersih',money(p?.target_net_profit))}${metric('Laba bulan ini',money(p?.month_net_profit))}${metric('Gap laba',money(p?.profit_gap))}${metric('Margin rata-rata',p?.avg_margin_pct==null?'—':`${num(p.avg_margin_pct)}%`)}${metric('Required booking',p?.required_bookings==null?'—':p.required_bookings)}${metric('Required revenue',p?.required_revenue==null?'—':money(p.required_revenue))}</div><p>${p?.data_ready?'Perhitungan required booking/revenue memakai histori closing nyata.':'Belum ada histori closing profit yang cukup. ERP tidak mengarang required booking/revenue; Finance wajib membangun baseline dari Trip Closing.'}</p><div class="gp201-actions"><button class="gp201-btn" data-jump="Keuangan">Buka Keuangan</button></div></div>`}
  function cardFinance(f){const st=statusFinance(f);return `<div class="gp201-card"><div class="gp201-kicker">3 • Finance Closing</div><h4>Kas, piutang & periode</h4>${badge(st[0],st[1])}<div class="gp201-metrics">${metric('Periode bulan ini',f?.current_period_status||'MISSING')}${metric('Invoice overdue',f?.overdue_invoice_count||0)}${metric('Piutang overdue',money(f?.overdue_ar_amount))}${metric('Period ready',f?.period_ready?'YA':'TIDAK')}</div><p>Periode bulanan dibuka otomatis. Closing tetap membutuhkan data biaya aktual, pembayaran, rekonsiliasi, dan approval yang benar.</p><div class="gp201-actions"><button class="gp201-btn" data-jump="Accounting">Buka Accounting</button><button class="gp201-btn" data-jump="Kas & Likuiditas">Kas</button></div></div>`}
  function cardPackage(p){const st=statusPackage(p);return `<div class="gp201-card"><div class="gp201-kicker">4 • Master Paket</div><h4>Satu sumber harga & biaya</h4>${badge(st[0],st[1])}<div class="gp201-metrics">${metric('Paket aktif',p?.active_packages||0)}${metric('Belum approved',p?.unapproved_packages||0)}${metric('Belum punya cost template',p?.uncosted_packages||0)}${metric('Siap dijual',p?.ready?'YA':'BELUM')}</div><p>Paket hanya dianggap siap bila aktif, approved, dan mempunyai cost template internal aktif.</p><div class="gp201-actions"><button class="gp201-btn primary" data-jump="Master Paket">Master Paket</button></div></div>`}
  function cardAI(a){const st=statusAI(a);return `<div class="gp201-card"><div class="gp201-kicker">5 • Manager AI</div><h4>Exception → Recovery Plan</h4>${badge(st[0],st[1])}<div class="gp201-metrics">${metric('Draft tindakan AI',a?.open_action_drafts||0)}${metric('Exception terbuka',a?.open_exceptions||0)}${metric('Exception kritis',a?.critical_exceptions||0)}${metric('Approval manusia','WAJIB','untuk aksi sensitif')}</div><p>AI menyusun recovery plan dari masalah nyata. Pengeluaran, refund, payroll, perubahan harga, margin kritis, dan keputusan irreversible tetap melalui manusia.</p><div class="gp201-actions"><button class="gp201-btn" data-jump="Pusat AI">Pusat AI</button><button class="gp201-btn" data-jump="Exception Center">Exception</button></div></div>`}
  function cardExecutive(e){const kind=e?.status==='CRITICAL'?'bad':e?.status==='ACTION_REQUIRED'?'warn':'ok';return `<div class="gp201-card"><div class="gp201-kicker">6 • Executive Control Tower</div><h4>Direktur fokus exception & hasil</h4>${badge(e?.status||'—',kind)}<div class="gp201-metrics">${metric('Target laba bersih',money(e?.net_profit_target))}${metric('Critical exception',e?.critical_exceptions||0)}</div><p>Urutan pemulihan dikunci agar perusahaan tidak sibuk di banyak hal sekaligus.</p><div class="gp201-order">${(e?.priority_order||[]).map((x,i)=>`<span>${i+1}. ${h(x)}</span>`).join('')}</div><div class="gp201-actions"><button class="gp201-btn primary" data-jump="Enterprise Control Tower">Control Tower</button></div></div>`}

  function render(){
    const host=q('#businessPriority201'); if(!host)return;
    const body=q('[data-priority-body]',host); if(!body)return;
    if(loading){body.innerHTML='<div class="gp201-note">Memuat prioritas bisnis aktual…</div>';return}
    if(error){body.innerHTML=`<div class="gp201-error">${h(error)}</div>`;return}
    if(!state){body.innerHTML='<div class="gp201-note">Data prioritas belum tersedia.</div>';return}
    body.innerHTML=`<div class="gp201-grid">${cardSales(state.sales)}${cardProfit(state.profitability)}${cardFinance(state.finance_closing)}${cardPackage(state.master_package)}${cardAI(state.manager_ai)}${cardExecutive(state.executive)}</div><div class="gp201-note"><b>Prinsip v20.1:</b> Sales Engine → Profitability → Finance Closing → Master Paket → Manager AI → Executive Control Tower. Required revenue/booking hanya dihitung jika histori profitability nyata tersedia.</div>`;
    body.querySelectorAll('[data-jump]').forEach(b=>b.addEventListener('click',()=>jump(b.dataset.jump)));
  }

  async function load(){
    if(!allowed()||!db()||loading)return; loading=true;error=null;render();
    try{const {data,error:e}=await db().rpc('internal_gmu_priority_command',{p_as_of:new Date().toISOString().slice(0,10)});if(e)throw e;state=data||null}catch(e){error=e?.message||String(e)}finally{loading=false;render()}
  }

  function jump(text){
    const btn=[...document.querySelectorAll('#nav [data-page]')].find(x=>String(x.textContent||'').toLowerCase().includes(String(text||'').toLowerCase()));
    if(btn){btn.click();return true} return false;
  }

  function show(){
    document.querySelectorAll('.page').forEach(x=>x.classList.remove('active'));q('#businessPriority201')?.classList.add('active');document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page==='businessPriority201'));const t=q('#title');if(t)t.textContent='Prioritas Bisnis';load();
  }

  function install(){
    if(!allowed()||q('#businessPriority201'))return;style();const content=q('.content');const nav=q('#nav');if(!content||!nav)return;
    const page=document.createElement('section');page.id='businessPriority201';page.className='page';page.innerHTML=`<div class="card section"><div class="gp201-hero"><div><div class="gp201-kicker">GMU EduTrans • ${VERSION}</div><h2>Prioritas Bisnis</h2><p>Enam kontrol yang paling menentukan pemulihan GMU. Halaman ini sengaja ringkas: angka aktual, gap, status, dan jalur tindakan.</p></div><button type="button" class="gp201-btn" data-refresh>Refresh</button></div><div data-priority-body></div></div>`;content.appendChild(page);
    q('[data-refresh]',page)?.addEventListener('click',load);
    const b=document.createElement('button');b.type='button';b.dataset.page='businessPriority201';b.textContent='◆  Prioritas Bisnis';b.addEventListener('click',show);nav.prepend(b);
    load();
  }

  function init(){
    const timer=setInterval(()=>{if(!role())return;clearInterval(timer);install();if(allowed())setInterval(load,60000)},250);
    window.GmuBusinessPriority=Object.freeze({version:VERSION,show,load,jump});
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();