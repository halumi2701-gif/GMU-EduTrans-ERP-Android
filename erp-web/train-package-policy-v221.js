(() => {
  'use strict';

  const VERSION = 'v22.1-train-package-policy';
  const ALLOWED = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans']);
  const q = (s, root = document) => root.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  const state = { economics: [], addons: [], packages: [], loading: false, error: null };

  function role(){ try { return String(profile?.role || ''); } catch (_) { return ''; } }
  function allowed(){ return ALLOWED.has(role()); }
  function db(){ try { return typeof sb !== 'undefined' && sb?.from ? sb : null; } catch (_) { return null; } }

  function publicPrice(code){ return Number(state.packages.find(x => x.package_code === code)?.price_per_pax || 0); }

  function installStyle(){
    if(q('#gmuTrainPolicyStyle')) return;
    const s=document.createElement('style');
    s.id='gmuTrainPolicyStyle';
    s.textContent=`
      .gtp{margin-top:12px}.gtp-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:10px}.gtp-card{border:1px solid var(--line);border-radius:14px;padding:12px;background:#fff}.gtp-card.best{border-color:#94c8a9;background:#f4fbf7}.gtp-card small{display:block;font-size:8px;color:var(--muted)}.gtp-card h4{margin:5px 0;color:var(--gd);font-size:14px}.gtp-price{font-size:18px;font-weight:900;color:var(--g)}.gtp-row{display:flex;justify-content:space-between;gap:8px;font-size:9px;padding:4px 0;border-bottom:1px dashed var(--line)}.gtp-row:last-child{border-bottom:0}.gtp-note{font-size:9px;line-height:1.6;background:#f4f8f6;border-radius:12px;padding:11px;margin-top:10px}.gtp-addons{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:10px}@media(max-width:900px){.gtp-grid{grid-template-columns:1fr}.gtp-addons{grid-template-columns:1fr}}
    `;
    document.head.appendChild(s);
  }

  function packageCard(x){
    const price=publicPrice(x.package_code);
    return `<div class="gtp-card ${x.is_best_seller?'best':''}">
      <small>${h(x.package_code)} ${x.is_best_seller?'• BEST SELLER':''}</small>
      <h4>${h(x.package_name)}</h4>
      <div class="gtp-price">${money(price)} / pax</div>
      <div class="gtp-row"><span>Baseline</span><b>${Number(x.baseline_pax||20)} pax</b></div>
      <div class="gtp-row"><span>HPP fasilitas • LOCK</span><b>${money(x.facility_hpp_locked)}</b></div>
      <div class="gtp-row"><span>Fee Sales</span><b>${money(x.sales_fee_baseline)}</b></div>
      <div class="gtp-row"><span>Fee Mitra</span><b>${money(x.partner_fee_baseline)}</b></div>
      <div class="gtp-row"><span>Fee Manager</span><b>${money(x.manager_fee_baseline)}</b></div>
      <div class="gtp-row"><span>TL / Tutor</span><b>${money(x.tl_tutor_fee_baseline)}</b></div>
      <div class="gtp-row"><span>Ops + Dokumentasi</span><b>${money(x.ops_documentation_fee_baseline)}</b></div>
      <div class="gtp-row"><span>Total biaya baseline</span><b>${money(x.total_cost_baseline)}</b></div>
      <div class="gtp-row"><span>Kontribusi GMU</span><b>${money(x.trip_contribution_baseline)}</b></div>
      <div class="gtp-row"><span>Margin kontribusi</span><b>${Number(x.contribution_margin_pct||0).toLocaleString('id-ID',{maximumFractionDigits:1})}%</b></div>
    </div>`;
  }

  function addonCard(a){
    return `<div class="gtp-card"><small>ADD-ON • DI LUAR PROGRAM</small><h4>${h(a.addon_name)}</h4><div class="gtp-price">${money(a.sell_price_per_pax)} / pax</div><div class="gtp-row"><span>Biaya dasar tiket</span><b>${money(a.base_cost_per_pax)}</b></div><div class="gtp-row"><span>Handling</span><b>${money(a.handling_per_pax)}</b></div><div class="gtp-row"><span>Sudah termasuk paket?</span><b>TIDAK</b></div><div class="gtp-note">${h(a.notes||'Hanya untuk kebutuhan tiket tambahan di luar program.')}</div></div>`;
  }

  function render(){
    if(!allowed()) return;
    const anchor=q('#gmuSales200Target')||q('#gmuStationTargetSection')||q('#salesTargetControl')||q('.content');
    if(!anchor) return;
    let box=q('#gmuTrainPackagePolicy');
    if(!box){box=document.createElement('section');box.id='gmuTrainPackagePolicy';box.className='card section gtp';anchor.insertAdjacentElement('afterend',box);}
    if(state.loading){box.innerHTML='<div class="gtp-note">Memuat skema Paket Edukasi Kereta…</div>';return;}
    if(state.error){box.innerHTML=`<div class="head"><div><h3>Skema Final • Edukasi di Atas Kereta</h3></div></div><div class="gtp-note">Belum dapat memuat master ekonomi: ${h(state.error)}</div>`;return;}
    box.innerHTML=`
      <div class="head"><div><h3>Skema Final • Edukasi di Atas Kereta</h3><p>Internal Owner/Manager • HPP fasilitas dikunci • harga paket sudah termasuk tiket utama program.</p></div><span class="badge ok">HPP LOCK</span></div>
      <div class="gtp-grid">${state.economics.map(packageCard).join('')}</div>
      <div class="gtp-note"><b>Aturan:</b> minimum 20 pax. Sales dan Mitra adalah dua fee berbeda. Baseline masing-masing Rp50.000 per 20 pax dan Rp100.000 pada 40 pax. Manager/TL/Ops tidak otomatis dikali setiap tambahan 20 pax; peserta di atas baseline mengikuti tier crew yang ditetapkan operasional. Margin di atas adalah kontribusi trip sebelum overhead tetap perusahaan.</div>
      <div class="head" style="margin-top:14px"><div><h3 style="font-size:13px">Tiket Tambahan di Luar Program</h3><p>Tidak pernah otomatis ditambahkan ke harga Paket Hemat/Reguler/Lengkap.</p></div></div>
      <div class="gtp-addons">${state.addons.map(addonCard).join('')}</div>`;
  }

  async function load(){
    if(!allowed()||!db()||state.loading)return;
    state.loading=true;state.error=null;render();
    try{
      const [e,a,p]=await Promise.all([
        db().from('program_package_economics').select('*').eq('program_slug','edukasi-di-atas-kereta').order('total_cost_baseline',{ascending:true}),
        db().from('program_package_addons').select('*').eq('program_slug','edukasi-di-atas-kereta').eq('is_active',true).order('sell_price_per_pax',{ascending:true}),
        db().from('program_packages').select('package_code,price_per_pax,status,is_active').in('package_code',['TRAIN-HEMAT-39','TRAIN-REGULER-60','TRAIN-LENGKAP-75'])
      ]);
      if(e.error)throw e.error;if(a.error)throw a.error;if(p.error)throw p.error;
      state.economics=Array.isArray(e.data)?e.data:[];state.addons=Array.isArray(a.data)?a.data:[];state.packages=Array.isArray(p.data)?p.data:[];
    }catch(err){state.error=err?.message||String(err);}finally{state.loading=false;render();}
  }

  function init(){
    const timer=setInterval(()=>{if(typeof profile==='undefined'||!profile)return;clearInterval(timer);if(!allowed())return;installStyle();render();load();const observer=new MutationObserver(()=>{if(!q('#gmuTrainPackagePolicy'))render();});observer.observe(document.body,{childList:true,subtree:true});},250);
    window.GmuTrainPackagePolicyV221=Object.freeze({version:VERSION,refresh:load});
  }

  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
