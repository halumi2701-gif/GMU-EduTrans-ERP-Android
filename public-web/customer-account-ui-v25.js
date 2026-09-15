/* GMU EduTrans Public Web — Customer Account UI v25
 * Modal signup/login used by Customer Account Gate v25.
 * Host must configure GMU_CUSTOMER_ACCOUNT_GATE_V25 with a Supabase browser client first.
 */
(function(){
  'use strict';
  const q=(s,r=document)=>r.querySelector(s);
  const h=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  let pendingPackage=null;

  function style(){
    if(q('#gmuCustomerAccountUiStyle')) return;
    const s=document.createElement('style');s.id='gmuCustomerAccountUiStyle';s.textContent=`
      .gcu-backdrop{position:fixed;inset:0;z-index:20000;background:rgba(5,20,12,.55);display:flex;align-items:center;justify-content:center;padding:16px}.gcu-dialog{width:min(520px,100%);max-height:92vh;overflow:auto;background:#fff;border-radius:20px;padding:20px;box-shadow:0 24px 70px rgba(0,0,0,.22)}.gcu-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.gcu-head h3{margin:0;color:#123d27}.gcu-head p{margin:5px 0 0;color:#65736b;font-size:13px;line-height:1.5}.gcu-close{border:0;background:#f2f5f3;width:34px;height:34px;border-radius:50%;cursor:pointer}.gcu-tabs{display:grid;grid-template-columns:1fr 1fr;gap:6px;margin:16px 0}.gcu-tab{border:1px solid #dce5df;background:#fff;border-radius:10px;padding:10px;cursor:pointer;font-weight:700}.gcu-tab.active{background:#17633d;color:#fff;border-color:#17633d}.gcu-form{display:grid;grid-template-columns:1fr 1fr;gap:10px}.gcu-field{display:flex;flex-direction:column;gap:5px}.gcu-field.full{grid-column:1/-1}.gcu-field label{font-size:11px;color:#65736b}.gcu-field input{padding:11px;border:1px solid #dce5df;border-radius:10px;font:inherit}.gcu-consent{display:flex;align-items:flex-start;gap:8px;font-size:11px;color:#56655d;line-height:1.45}.gcu-btn{width:100%;border:0;border-radius:11px;background:#17633d;color:#fff;padding:12px;font-weight:800;cursor:pointer}.gcu-btn:disabled{opacity:.55;cursor:not-allowed}.gcu-msg{margin-top:10px;padding:10px;border-radius:10px;background:#f4f7f5;color:#456052;font-size:12px}.gcu-msg.bad{background:#fff0f0;color:#8e3030}@media(max-width:600px){.gcu-form{grid-template-columns:1fr}.gcu-field.full{grid-column:auto}}
    `;document.head.appendChild(s);
  }

  function modal(){
    let m=q('#gmuCustomerAccountModal');
    if(m) return m;
    m=document.createElement('div');m.id='gmuCustomerAccountModal';m.className='gcu-backdrop';m.hidden=true;
    m.innerHTML=`<div class="gcu-dialog" role="dialog" aria-modal="true" aria-labelledby="gcuTitle">
      <div class="gcu-head"><div><h3 id="gcuTitle">Akun Customer GMU EduTrans</h3><p>Daftar atau masuk terlebih dahulu untuk memesan paket. Akun membantu GMU menyimpan riwayat minat dan booking Anda.</p></div><button type="button" class="gcu-close" aria-label="Tutup">×</button></div>
      <div class="gcu-tabs"><button type="button" class="gcu-tab active" data-gcu-tab="signup">Daftar</button><button type="button" class="gcu-tab" data-gcu-tab="login">Masuk</button></div>
      <form id="gcuSignup" class="gcu-form">
        <div class="gcu-field"><label>Nama lengkap</label><input name="fullName" required autocomplete="name"></div>
        <div class="gcu-field"><label>WhatsApp</label><input name="whatsapp" required inputmode="tel" autocomplete="tel"></div>
        <div class="gcu-field"><label>Instansi / Sekolah</label><input name="institutionName" autocomplete="organization"></div>
        <div class="gcu-field"><label>Kota</label><input name="city" autocomplete="address-level2"></div>
        <div class="gcu-field full"><label>Email</label><input name="email" type="email" required autocomplete="email"></div>
        <div class="gcu-field full"><label>Password</label><input name="password" type="password" required minlength="8" autocomplete="new-password"></div>
        <label class="gcu-consent full"><input name="followupConsent" type="checkbox"> <span>Saya bersedia dihubungi GMU EduTrans terkait program/paket yang saya minati dan proses booking saya.</span></label>
        <div class="gcu-field full"><button class="gcu-btn" type="submit">Buat Akun & Lanjutkan</button></div>
      </form>
      <form id="gcuLogin" class="gcu-form" hidden>
        <div class="gcu-field full"><label>Email</label><input name="email" type="email" required autocomplete="email"></div>
        <div class="gcu-field full"><label>Password</label><input name="password" type="password" required autocomplete="current-password"></div>
        <div class="gcu-field full"><button class="gcu-btn" type="submit">Masuk & Lanjutkan</button></div>
      </form>
      <div id="gcuMessage" class="gcu-msg" hidden></div>
    </div>`;
    document.body.appendChild(m);wire(m);return m;
  }

  function message(text,bad=false){const el=q('#gcuMessage');if(!el)return;el.hidden=!text;el.textContent=text||'';el.classList.toggle('bad',!!bad);}
  function open(packageId){pendingPackage=packageId||pendingPackage;style();const m=modal();m.hidden=false;message('');q('#gcuSignup input')?.focus();}
  function close(){const m=q('#gmuCustomerAccountModal');if(m)m.hidden=true;}
  function setTab(tab){q('[data-gcu-tab="signup"]')?.classList.toggle('active',tab==='signup');q('[data-gcu-tab="login"]')?.classList.toggle('active',tab==='login');q('#gcuSignup').hidden=tab!=='signup';q('#gcuLogin').hidden=tab!=='login';message('');}

  async function continuePending(){
    const gate=window.GMU_CUSTOMER_ACCOUNT_GATE_V25;
    if(!gate) return;
    const id=pendingPackage||sessionStorage.getItem('gmu_pending_package_id');
    if(!id){close();return;}
    const ok=await gate.requireAccount(id);
    if(!ok) return;
    sessionStorage.removeItem('gmu_pending_package_id');
    close();
    if(typeof window.choosePackage==='function') await window.choosePackage(id);
  }

  function setBusy(form,busy){const btn=q('button[type="submit"]',form);if(btn)btn.disabled=busy;}
  function wire(m){
    q('.gcu-close',m).addEventListener('click',close);
    m.addEventListener('click',e=>{if(e.target===m)close();});
    m.querySelectorAll('[data-gcu-tab]').forEach(b=>b.addEventListener('click',()=>setTab(b.dataset.gcuTab)));

    q('#gcuSignup',m).addEventListener('submit',async e=>{
      e.preventDefault();const form=e.currentTarget;setBusy(form,true);message('Membuat akun...');
      try{
        const fd=new FormData(form);const gate=window.GMU_CUSTOMER_ACCOUNT_GATE_V25;if(!gate)throw new Error('Modul akun belum siap.');
        const data=await gate.signUp({email:fd.get('email'),password:fd.get('password'),fullName:fd.get('fullName'),whatsapp:fd.get('whatsapp'),institutionName:fd.get('institutionName'),city:fd.get('city'),followupConsent:fd.get('followupConsent')==='on'});
        if(data?.session){message('Akun berhasil dibuat. Melanjutkan booking...');await continuePending();}
        else{message('Akun berhasil dibuat. Silakan cek email untuk verifikasi, lalu masuk kembali untuk melanjutkan order.');setTab('login');}
      }catch(err){message(err?.message||String(err),true);}finally{setBusy(form,false);}
    });

    q('#gcuLogin',m).addEventListener('submit',async e=>{
      e.preventDefault();const form=e.currentTarget;setBusy(form,true);message('Masuk...');
      try{const fd=new FormData(form);const gate=window.GMU_CUSTOMER_ACCOUNT_GATE_V25;if(!gate)throw new Error('Modul akun belum siap.');await gate.signIn({email:fd.get('email'),password:fd.get('password')});message('Berhasil masuk. Melanjutkan booking...');await continuePending();}
      catch(err){message(err?.message||String(err),true);}finally{setBusy(form,false);}
    });
  }

  window.addEventListener('gmu:auth-required',e=>open(e.detail?.packageId));
  window.addEventListener('gmu:auth-error',e=>{open(pendingPackage);message(e.detail?.message||'Gagal memproses akun.',true);});
  window.GMU_CUSTOMER_ACCOUNT_UI_V25={version:'v25-customer-account-ui',open,close,setTab};
})();
