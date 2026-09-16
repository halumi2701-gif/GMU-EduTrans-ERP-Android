(() => {
  'use strict';

  const VERSION = 'v22.5-sales-funnel-forecast';
  const ROLES = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales']);
  const CFG = Object.freeze({
    targetPax: 400,
    bepPax: 60,
    productivePax: 200,
    stretchPax: 600,
    outstandingPax: 800,
    avgPaxPerSchool: 40,
    leadsMonthly: 200,
    contactedMonthly: 160,
    pipelineMultiple: 3,
    leadToContacted: 80,
    contactedToQualified: 40,
    qualifiedToQuotation: 60,
    quotationToClose: 30,
    retainer: 600000,
    feePerPaidPax: 2500,
  });
  const state = { summary:null, controls:[], requests:[], loading:false, error:null };
  const q = (s,r=document) => r.querySelector(s);
  const n = v => Number(v || 0);
  const int = v => n(v).toLocaleString('id-ID');
  const pct = v => n(v).toLocaleString('id-ID',{maximumFractionDigits:1});
  const money = v => 'Rp ' + n(v).toLocaleString('id-ID');
  const safe = v => String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

  function role(){ try{return String(profile?.role||'');}catch(_){return '';} }
  function userId(){ try{return String(profile?.id||'');}catch(_){return '';} }
  function allowed(){ return ROLES.has(role()); }
  function sales(){ return role()==='Sales'; }
  function db(){ try{return typeof sb!=='undefined'&&sb?.rpc?sb:null;}catch(_){return null;} }
  function monthStart(){ const d=new Date(); return new Date(d.getFullYear(),d.getMonth(),1); }
  function nextMonth(){ const d=monthStart(); return new Date(d.getFullYear(),d.getMonth()+1,1); }
  function monthKey(){ const d=monthStart(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-01`; }
  function monthLabel(){ return monthStart().toLocaleDateString('id-ID',{month:'long',year:'numeric'}); }
  function within(v,a,b){ if(!v)return false; const d=new Date(v); return !Number.isNaN(d.getTime())&&d>=a&&d<b; }

  const RANK={NEW:0,CONTACTED:1,NURTURE:1,QUALIFIED:2,QUOTATION:3,NEGOTIATION:4,WAITING_DP:5,WON:6,LOST:-1};
  const rank = s => RANK[String(s||'').toUpperCase()] ?? 0;

  function summary(){
    const r=state.summary||{};
    const target=Math.max(CFG.targetPax,n(r.target_paid_pax));
    const paid=n(r.paid_pax);
    const fee=n(r.sales_fee_per_paid_pax)||CFG.feePerPaidPax;
    const retainer=n(r.sales_retainer)||CFG.retainer;
    const approved=n(r.approved_bonus||r.target_bonus_approved);
    return {
      target, paid, paidBookings:n(r.paid_bookings), fee, retainer, approved,
      income:retainer+paid*fee+approved,
      achievement:target?paid/target*100:0,
      finance:r.can_view_finance===true&&!sales(),
      cashIn:n(r.cash_in), bookedValue:n(r.booked_value),
    };
  }

  function metrics(s){
    const start=monthStart(), next=nextMonth(), now=new Date();
    const reqMap=new Map(state.requests.map(x=>[String(x.id||''),x]));
    const monthReq=state.requests.filter(x=>within(x.created_at,start,next));
    const monthIds=new Set(monthReq.map(x=>String(x.id||'')));
    const monthCtl=state.controls.filter(x=>monthIds.has(String(x.booking_request_id||'')));
    const wonMonth=state.controls.filter(x=>String(x.stage||'').toUpperCase()==='WON'&&within(x.won_at,start,next));
    const active=state.controls.filter(x=>!['WON','LOST'].includes(String(x.stage||'').toUpperCase()));
    const contacted=monthCtl.filter(x=>rank(x.stage)>=1).length;
    const qualified=monthCtl.filter(x=>rank(x.stage)>=2).length;
    const quotes=monthCtl.filter(x=>rank(x.stage)>=3).length;
    const wonCohort=monthCtl.filter(x=>String(x.stage||'').toUpperCase()==='WON').length;
    let openPax=0, weighted=0, hotPax=0, due=0;
    active.forEach(x=>{
      const pax=n(reqMap.get(String(x.booking_request_id||''))?.pax);
      const prob=Math.max(0,Math.min(100,n(x.probability_pct)));
      openPax+=pax; weighted+=pax*prob/100;
      if(['NEGOTIATION','WAITING_DP'].includes(String(x.stage||'').toUpperCase()))hotPax+=pax;
      if(x.next_follow_up_at&&new Date(x.next_follow_up_at)<=now)due++;
    });
    const wonPax=wonMonth.reduce((a,x)=>a+n(reqMap.get(String(x.booking_request_id||''))?.pax),0);
    const avgWon=wonMonth.length?wonPax/wonMonth.length:CFG.avgPaxPerSchool;
    const totalDays=new Date(next.getFullYear(),next.getMonth(),0).getDate();
    const day=Math.max(1,Math.min(totalDays,now.getDate()));
    const left=Math.max(totalDays-day,0);
    const remain=Math.max(s.target-s.paid,0);
    const paceToday=Math.round(s.target*day/totalDays);
    const paceForecast=s.paid>=s.target?s.paid:Math.round(s.paid/day*totalDays);
    const weightedPotential=Math.round(s.paid+weighted);
    const maxPotential=Math.round(s.paid+openPax);
    const needDaily=remain?Math.ceil(remain/Math.max(left,1)):0;
    const coverage=remain?openPax/remain:CFG.pipelineMultiple;
    const closes=Math.ceil(s.target/CFG.avgPaxPerSchool);
    const quotesNeed=Math.ceil(closes/(CFG.quotationToClose/100));
    const qualifiedNeed=Math.ceil(quotesNeed/(CFG.qualifiedToQuotation/100));
    const contactedNeed=Math.ceil(qualifiedNeed/(CFG.contactedToQualified/100));
    const modelLeads=Math.ceil(contactedNeed/(CFG.leadToContacted/100));
    const leadsNeed=Math.max(CFG.leadsMonthly,modelLeads);
    const conversion=(a,b)=>b?a/b*100:0;
    let status='PIPELINE BELUM CUKUP', cls='bad', note=`Maksimum potensi ${int(maxPotential)} pax masih di bawah target ${int(s.target)}.`;
    if(s.paid>=s.target){status='TARGET TERCAPAI';cls='ok';note=`Paid pax sudah ${int(s.paid)} dari target ${int(s.target)}.`;}
    else if(weightedPotential>=s.target&&s.paid>=paceToday*.9){status='ON TRACK';cls='ok';note='Pace paid pax dan weighted pipeline masih mendukung target akhir bulan.';}
    else if(maxPotential>=s.target&&(weightedPotential>=s.target||coverage>=CFG.pipelineMultiple)){status='MASIH MUNGKIN';cls='info';note='Pipeline cukup secara volume, tetapi follow-up dan conversion harus dijaga sampai pembayaran masuk.';}
    else if(maxPotential>=s.target){status='BERISIKO';cls='info';note='Secara jumlah masih mungkin, tetapi coverage/probability pipeline belum cukup aman.';}
    return {
      leads:monthReq.length,contacted,qualified,quotes,won:wonMonth.length,wonCohort,
      openPax,weightedPotential,maxPotential,hotPax,due,avgWon,totalDays,day,left,remain,paceToday,paceForecast,needDaily,coverage,
      closes,quotesNeed,qualifiedNeed,contactedNeed,modelLeads,leadsNeed,
      c1:conversion(contacted,monthReq.length),c2:conversion(qualified,contacted),c3:conversion(quotes,qualified),c4:conversion(wonCohort,quotes),
      remainingSchools:remain?Math.ceil(remain/Math.max(avgWon,1)):0,
      baselinePipeline:s.target*CFG.pipelineMultiple,status,cls,note,
    };
  }

  function style(){
    if(q('#gmuSalesFunnelStyle'))return;
    const el=document.createElement('style');el.id='gmuSalesFunnelStyle';el.textContent=`
      #gmuSales200Target{display:none!important}
      .gmu-sf-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px;margin-top:10px}
      .gmu-sf-funnel{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8px;margin-top:10px}
      .gmu-sf-step{border:1px solid var(--line);background:#fff;border-radius:13px;padding:10px}.gmu-sf-step small{display:block;font-size:8px;color:var(--muted)}.gmu-sf-step b{display:block;font-size:17px;color:var(--gd);margin:4px 0}.gmu-sf-step span{display:block;font-size:8px;color:var(--muted);line-height:1.45}.gmu-sf-step strong{color:var(--g)}
      .gmu-sf-status{border:1px solid var(--line);background:#f7fbf8;border-radius:14px;padding:12px;margin-top:10px}
      @media(max-width:920px){.gmu-sf-grid{grid-template-columns:repeat(2,1fr)}.gmu-sf-funnel{grid-template-columns:repeat(2,1fr)}}
      @media(max-width:520px){.gmu-sf-grid,.gmu-sf-funnel{grid-template-columns:1fr}}
    `;document.head.appendChild(el);
  }

  function card(label,value,desc){return `<div class="gmu-target-card"><small>${label}</small><b>${value}</b><span>${desc}</span></div>`;}

  function render(){
    if(!allowed())return;
    const anchor=q('#gmuStationTargetSection')||q('#salesTargetControl')||q('.content');if(!anchor)return;
    let root=q('#gmuSalesFunnelForecast');
    if(!root){root=document.createElement('div');root.id='gmuSalesFunnelForecast';root.className='card section';if(anchor.id==='gmuStationTargetSection')anchor.insertAdjacentElement('afterend',root);else if(anchor.id==='salesTargetControl')anchor.prepend(root);else anchor.appendChild(root);}
    const s=summary(),m=metrics(s);
    const fin=s.finance?card('Cash-in terverifikasi',money(s.cashIn),'Pembayaran terverifikasi bulan berjalan.')+card('Nilai booking paid-pax',money(s.bookedValue),'Nilai booking yang sudah memiliki pembayaran positif.'):'';
    const bonus=s.approved>0?money(s.approved):(s.paid>=s.target?'ELIGIBLE':'BELUM');
    root.innerHTML=`
      <div class="head"><div><h3>Sales Funnel & Forecast • Target ${int(s.target)} Pax</h3><p>${monthLabel()} • ${sales()?'Target saya':'Target tim'} • semua program dijumlahkan.</p></div><span class="badge ${m.cls}">${m.status}</span></div>
      <div class="gmu-sf-grid">
        ${card('Target paid pax / bulan',int(s.target)+' pax','Target awal GMU saat ini minimal 400 pax.')}
        ${card('Aktual paid pax',int(s.paid)+' pax',pct(s.achievement)+'% • kurang '+int(m.remain)+' pax.')}
        ${card('Target pace hari ini',int(m.paceToday)+' pax','Hari ke-'+int(m.day)+' dari '+int(m.totalDays)+'.')}
        ${card('Kebutuhan sisa hari',int(m.needDaily)+' pax/hari',int(m.left)+' hari tersisa.')}
        ${fin}
        ${card('Fee Sales aktual',money(s.paid*s.fee),money(s.fee)+' × '+int(s.paid)+' paid pax.')}
        ${card('Bonus target',bonus,'Profit-funded: margin dan collection tetap harus lolos guardrail.')}
        ${card('Penghasilan ter-model',money(s.income),'Retainer '+money(s.retainer)+' + fee + bonus yang sudah disetujui.')}
      </div>
      <div class="gmu-sf-status"><div class="head"><div><h3 style="font-size:13px">Masih mungkin mencapai target?</h3><p>${m.note}</p></div><span class="badge ${m.cls}">${m.status}</span></div><div class="gmu-sf-grid">
        ${card('Pace forecast',int(m.paceForecast)+' pax','Jika pace paid pax sekarang bertahan.')}
        ${card('Weighted potential',int(m.weightedPotential)+' pax','Paid pax + pipeline × probability CRM.')}
        ${card('Maximum potential',int(m.maxPotential)+' pax','Paid pax + seluruh pax lead aktif.')}
        ${card('Pipeline coverage',pct(m.coverage)+'×','Guardrail awal minimal '+CFG.pipelineMultiple+'× kebutuhan sisa.')}
      </div></div>
      <div style="margin-top:14px"><div class="head"><div><h3 style="font-size:13px">Funnel Minimum untuk ${int(s.target)} Pax</h3><p>Baseline 40 pax/sekolah. Target: ${int(m.closes)} sekolah closing dan sekitar ${int(m.quotesNeed)} quotation.</p></div><span class="badge neutral">AUTO FORECAST</span></div><div class="gmu-sf-funnel">
        <div class="gmu-sf-step"><small>LEAD BARU</small><b>${int(m.leads)} / ${int(m.leadsNeed)}</b><span>Minimum model ${int(m.modelLeads)}; operasional ${int(m.leadsNeed)}.</span></div>
        <div class="gmu-sf-step"><small>CONTACTED</small><b>${int(m.contacted)} / ${int(CFG.contactedMonthly)}</b><span>Conversion <strong>${pct(m.c1)}%</strong> • min ${CFG.leadToContacted}%.</span></div>
        <div class="gmu-sf-step"><small>QUALIFIED</small><b>${int(m.qualified)} / ${int(m.qualifiedNeed)}</b><span>Conversion <strong>${pct(m.c2)}%</strong> • min ${CFG.contactedToQualified}%.</span></div>
        <div class="gmu-sf-step"><small>QUOTATION</small><b>${int(m.quotes)} / ${int(m.quotesNeed)}</b><span>Conversion <strong>${pct(m.c3)}%</strong> • min ${CFG.qualifiedToQuotation}%.</span></div>
        <div class="gmu-sf-step"><small>SEKOLAH CLOSING</small><b>${int(m.won)} / ${int(m.closes)}</b><span>Cohort quote→won <strong>${pct(m.c4)}%</strong> • min ${CFG.quotationToClose}%.</span></div>
      </div></div>
      <div class="gmu-sf-grid">
        ${card('Gap lead',int(Math.max(m.leadsNeed-m.leads,0)),'Kejar ± '+int(Math.ceil(Math.max(m.leadsNeed-m.leads,0)/Math.max(m.left,1)))+' lead baru/hari.')}
        ${card('Gap quotation',int(Math.max(m.quotesNeed-m.quotes,0)),'Target model '+int(m.quotesNeed)+' quotation/bulan.')}
        ${card('Gap sekolah closing',int(Math.max(m.closes-m.won,0)),'Estimasi sisa '+int(m.remainingSchools)+' sekolah dari avg '+pct(m.avgWon)+' pax.')}
        ${card('Follow-up jatuh tempo',int(m.due),'Hot pipeline NEGOTIATION/WAITING_DP: '+int(m.hotPax)+' pax.')}
      </div>
      <div class="gmu-target-note" style="margin-top:10px"><b>Rumus:</b> ${int(s.target)} pax ÷ 40 pax/sekolah = <b>${int(m.closes)} sekolah</b>. Quote→close minimum 30% membutuhkan ± <b>${int(m.quotesNeed)} quotation</b>; qualified→quote 60%; contacted→qualified 40%; lead→contacted 80%. Pipeline aktif disarankan minimal <b>${int(m.baselinePipeline)} pax</b> (3× target).</div>
      ${sales()?'<div class="gmu-target-note" style="margin-top:8px">Privasi: Sales hanya melihat target, CRM, paid pax, komisi, dan forecast miliknya; data laba, margin, kas perusahaan, dan payroll pihak lain tetap disembunyikan.</div>':''}
      ${state.error?`<div class="gmu-target-note" style="margin-top:8px;color:var(--bad)">${safe(state.error)}</div>`:''}`;
  }

  async function load(){
    if(!allowed()||!db()||state.loading)return;state.loading=true;state.error=null;
    try{
      const start=monthStart(), lookback=new Date(start.getFullYear()-1,start.getMonth(),1).toISOString(), uid=userId();
      if(sales()&&!uid)throw new Error('Profil Sales belum memiliki ID untuk scope CRM.');
      let cq=db().from('crm_lead_controls').select('booking_request_id,stage,owner_id,probability_pct,created_at,won_at,next_follow_up_at').gte('created_at',lookback);
      let rq=db().from('booking_requests').select('id,pax,assigned_sales,created_at,converted_booking_id,status').gte('created_at',lookback);
      if(sales()){cq=cq.eq('owner_id',uid);rq=rq.eq('assigned_sales',uid);}
      const [sr,cr,rr]=await Promise.all([db().rpc('gmu_sales_portfolio_summary',{p_period_month:monthKey()}),cq,rq]);
      if(sr.error)throw sr.error;if(cr.error)throw cr.error;if(rr.error)throw rr.error;
      state.summary=Array.isArray(sr.data)?(sr.data[0]||null):(sr.data||null);state.controls=Array.isArray(cr.data)?cr.data:[];state.requests=Array.isArray(rr.data)?rr.data:[];
    }catch(e){state.error=e?.message||String(e);}finally{state.loading=false;render();}
  }

  function init(){style();const timer=setInterval(()=>{if(typeof profile==='undefined'||!profile)return;clearInterval(timer);if(!allowed())return;render();load();const obs=new MutationObserver(()=>{if(!q('#gmuSalesFunnelForecast'))render();});obs.observe(document.body,{childList:true,subtree:true});setInterval(load,60000);},250);window.GmuSalesFunnelForecast=Object.freeze({version:VERSION,target:CFG,refresh:load});}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
