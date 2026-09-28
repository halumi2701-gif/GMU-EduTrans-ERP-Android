(() => {
  'use strict';

  const VERSION = 'v22.8-profit-allocation-recovery';
  const ALLOWED = new Set(['Owner','Director','Direktur','Finance']);
  const KEYS = [
    'MONTHLY_PAX_TARGET','MONTHLY_UTILITY_BUDGET','LOSS_RECOVERY_TARGET','LOSS_RECOVERY_PAID_TO_DATE',
    'PROFIT_SPLIT_RECOVERY_PCT','PROFIT_SPLIT_COMPANY_CASH_PCT','PROFIT_SPLIT_OWNER_PCT',
    'FIELD_CREW_MEAL_PER_PERSON','INTERNAL_MANAGER_FEE_PER_TRIP','INTERNAL_TL_MC_FEE_PER_TRIP',
    'INTERNAL_OPS_DOC_FEE_PER_TRIP','SALES_FIXED_MONTHLY','SALES_COMMISSION_PER_20_PAX',
    'ADMIN_PART_TIME_MONTHLY','FINANCE_PART_TIME_MONTHLY','OWNER_FIXED_SALARY'
  ];
  const state = { settings:{}, pnl:null, sales:null, loading:false, error:null };
  const q = (s,r=document) => r.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = v => 'Rp ' + Math.round(Number(v||0)).toLocaleString('id-ID');
  const integer = v => Math.round(Number(v||0)).toLocaleString('id-ID');

  function role(){ try{return String(profile?.role||'')}catch(_){return ''} }
  function allowed(){ return ALLOWED.has(role()); }
  function db(){ try{return typeof sb!=='undefined'&&sb?.from&&sb?.rpc?sb:null}catch(_){return null} }
  function periodMonth(){const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-01`}
  function today(){const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
  function setting(k,fallback=0){return Number(state.settings[k] ?? fallback)}

  function values(){
    const netProfit=Number(state.pnl?.net_profit||0);
    const distributable=Math.max(netProfit,0);
    const recoveryPct=setting('PROFIT_SPLIT_RECOVERY_PCT',50);
    const cashPct=setting('PROFIT_SPLIT_COMPANY_CASH_PCT',30);
    const ownerPct=setting('PROFIT_SPLIT_OWNER_PCT',20);
    const recoveryTarget=setting('LOSS_RECOVERY_TARGET',50000000);
    const recoveryPaid=setting('LOSS_RECOVERY_PAID_TO_DATE',0);
    const recoveryAllocation=distributable*recoveryPct/100;
    const companyCash=distributable*cashPct/100;
    const owner=distributable*ownerPct/100;
    const remaining=Math.max(recoveryTarget-recoveryPaid,0);
    const sales=Array.isArray(state.sales)?(state.sales[0]||{}):(state.sales||{});
    return {
      netProfit,distributable,recoveryPct,cashPct,ownerPct,recoveryTarget,recoveryPaid,recoveryAllocation,companyCash,owner,remaining,
      targetPax:Number(sales.target_paid_pax||setting('MONTHLY_PAX_TARGET',400)),
      paidPax:Number(sales.paid_pax||0),
      utility:setting('MONTHLY_UTILITY_BUDGET',1755000)
    };
  }

  function style(){
    if(q('#gmuProfitAllocation228Style'))return;
    const s=document.createElement('style');s.id='gmuProfitAllocation228Style';s.textContent=`
      .pa228-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;flex-wrap:wrap}
      .pa228-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-top:12px}
      .pa228-card{background:#fff;border:1px solid var(--line);border-radius:14px;padding:12px;min-width:0}
      .pa228-card small{display:block;color:var(--muted);font-size:8px}.pa228-card b{display:block;color:var(--gd);font-size:17px;margin:4px 0}
      .pa228-card span{font-size:8px;color:var(--muted);line-height:1.45}.pa228-section{margin-top:12px}
      .pa228-row{display:grid;grid-template-columns:1.6fr .8fr;gap:8px;padding:9px 0;border-bottom:1px solid var(--line);font-size:9px}
      .pa228-row:last-child{border-bottom:0}.pa228-row strong{color:var(--gd)}.pa228-note{margin-top:10px;padding:10px;border:1px solid var(--line);border-radius:12px;background:#f8fbf9;font-size:9px;line-height:1.5;color:var(--muted)}
      .pa228-btn{border:1px solid var(--line);background:#fff;border-radius:9px;padding:8px 10px;font-size:9px;cursor:pointer}
      @media(max-width:900px){.pa228-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:560px){.pa228-grid{grid-template-columns:1fr}.pa228-row{grid-template-columns:1fr}}
    `;document.head.appendChild(s);
  }

  function ensurePage(){
    let page=q('#profitAllocation228');
    if(page)return page;
    const host=q('.content')||q('main')||document.body;
    page=document.createElement('section');page.id='profitAllocation228';page.className='page';
    page.innerHTML='<div class="card section"><div data-pa228-body></div></div>';
    host.appendChild(page);
    const nav=q('#nav');
    if(nav&&!q('#nav [data-page="profitAllocation228"]')){
      const b=document.createElement('button');b.type='button';b.dataset.page='profitAllocation228';b.textContent='▣  Pembagian Laba';
      b.addEventListener('click',()=>show());nav.appendChild(b);
    }
    return page;
  }

  function show(){
    document.querySelectorAll('.page').forEach(x=>x.classList.remove('active'));
    ensurePage().classList.add('active');
    document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page==='profitAllocation228'));
    if(q('#title'))q('#title').textContent='Pembagian Laba';
    load();
  }

  function render(){
    if(!allowed())return;
    const page=ensurePage(),body=q('[data-pa228-body]',page);if(!body)return;
    const v=values();
    const noProfit=v.distributable<=0?'<div class="pa228-note"><b>Belum ada laba positif untuk dibagi.</b> Pembagian 50/30/20 hanya berlaku pada laba bersih positif setelah biaya dan overhead yang sudah dibukukan di GL.</div>':'';
    body.innerHTML=`
      <div class="pa228-head"><div><div style="font-size:8px;color:var(--muted)">GMU EduTrans • ${VERSION}</div><h2 style="margin:3px 0">Pembagian Laba & Recovery</h2><p style="margin:0;color:var(--muted);font-size:9px">Target ${integer(v.targetPax)} pax/bulan • utilitas plafon ${money(v.utility)} • pembagian otomatis dari laba bersih positif.</p></div><button class="pa228-btn" data-pa228-refresh>Muat Ulang</button></div>
      <div class="pa228-grid">
        <div class="pa228-card"><small>Target / aktual paid pax</small><b>${integer(v.targetPax)} / ${integer(v.paidPax)}</b><span>Gabungan seluruh program bulan berjalan.</span></div>
        <div class="pa228-card"><small>Laba bersih GL bulan ini</small><b>${money(v.netProfit)}</b><span>Basis pembagian setelah biaya/overhead yang sudah dibukukan.</span></div>
        <div class="pa228-card"><small>Bayar utang / recovery • ${v.recoveryPct}%</small><b>${money(v.recoveryAllocation)}</b><span>Alokasi bulan berjalan.</span></div>
        <div class="pa228-card"><small>Kas perusahaan • ${v.cashPct}%</small><b>${money(v.companyCash)}</b><span>Ditahan di perusahaan, bukan untuk ditarik.</span></div>
        <div class="pa228-card"><small>Hak Owner • ${v.ownerPct}%</small><b>${money(v.owner)}</b><span>Owner tidak memiliki gaji tetap.</span></div>
        <div class="pa228-card"><small>Target recovery awal</small><b>${money(v.recoveryTarget)}</b><span>Kerugian/utang yang sedang dipulihkan.</span></div>
        <div class="pa228-card"><small>Recovery sudah dibayar</small><b>${money(v.recoveryPaid)}</b><span>Akumulasi pembayaran yang telah dicatat.</span></div>
        <div class="pa228-card"><small>Sisa recovery tercatat</small><b>${money(v.remaining)}</b><span>Sebelum alokasi bulan ini benar-benar dibayarkan.</span></div>
      </div>
      ${noProfit}
      <div class="pa228-section pa228-card"><h3 style="margin:0 0 8px">Policy Biaya Internal</h3>
        <div class="pa228-row"><strong>Manager EduTrans</strong><span>${money(setting('INTERNAL_MANAGER_FEE_PER_TRIP',130000))} / kegiatan</span></div>
        <div class="pa228-row"><strong>TL / MC</strong><span>${money(setting('INTERNAL_TL_MC_FEE_PER_TRIP',110000))} / kegiatan</span></div>
        <div class="pa228-row"><strong>Ops + Dokumentasi</strong><span>${money(setting('INTERNAL_OPS_DOC_FEE_PER_TRIP',55000))} / kegiatan</span></div>
        <div class="pa228-row"><strong>Uang makan crew lapangan</strong><span>${money(setting('FIELD_CREW_MEAL_PER_PERSON',12000))} / orang hadir / kegiatan</span></div>
        <div class="pa228-row"><strong>Sales</strong><span>${money(setting('SALES_FIXED_MONTHLY',600000))} / bulan + ${money(setting('SALES_COMMISSION_PER_20_PAX',50000))} / 20 pax</span></div>
        <div class="pa228-row"><strong>Admin part-time</strong><span>${money(setting('ADMIN_PART_TIME_MONTHLY',450000))} / bulan</span></div>
        <div class="pa228-row"><strong>Finance part-time</strong><span>${money(setting('FINANCE_PART_TIME_MONTHLY',550000))} / bulan</span></div>
        <div class="pa228-row"><strong>Owner fixed salary</strong><span>${money(setting('OWNER_FIXED_SALARY',0))} — memakai pembagian laba ${v.ownerPct}%</span></div>
      </div>
      <div class="pa228-note">Rumus: laba bersih positif × ${v.recoveryPct}% recovery + ${v.cashPct}% kas perusahaan + ${v.ownerPct}% Owner. Jika laba bersih ≤ 0, seluruh alokasi otomatis Rp0.</div>
      ${state.error?`<div class="pa228-note" style="border-color:#efc5c5;color:#963434">Data belum lengkap: ${h(state.error)}</div>`:''}
    `;
    q('[data-pa228-refresh]',body)?.addEventListener('click',load);
  }

  async function load(){
    if(!allowed()||!db()||state.loading)return;
    state.loading=true;state.error=null;
    try{
      const [settingsRes,pnlRes,salesRes]=await Promise.all([
        db().from('company_control_settings').select('setting_key,numeric_value,text_value').in('setting_key',KEYS),
        db().rpc('internal_gl_profit_loss',{p_start:periodMonth(),p_end:today()}),
        db().rpc('gmu_sales_portfolio_summary',{p_period_month:periodMonth()})
      ]);
      if(settingsRes.error)throw settingsRes.error;if(pnlRes.error)throw pnlRes.error;if(salesRes.error)throw salesRes.error;
      state.settings={};(settingsRes.data||[]).forEach(x=>state.settings[x.setting_key]=Number(x.numeric_value||0));
      state.pnl=Array.isArray(pnlRes.data)?(pnlRes.data[0]||{}):(pnlRes.data||{});
      state.sales=Array.isArray(salesRes.data)?salesRes.data:(salesRes.data?[salesRes.data]:[]);
    }catch(e){state.error=e?.message||String(e);}finally{state.loading=false;render();}
  }

  function init(){
    const timer=setInterval(()=>{if(typeof profile==='undefined'||!profile)return;clearInterval(timer);if(!allowed())return;style();ensurePage();load();setInterval(load,60_000);},250);
    window.GmuProfitAllocationV228=Object.freeze({version:VERSION,show,refresh:load});
  }

  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();