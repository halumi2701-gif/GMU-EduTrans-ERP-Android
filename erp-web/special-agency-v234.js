(() => {
  'use strict';
  const VERSION='v23.4-station-special-agency';
  const ROLES=new Set(['Owner','Director','Direktur','Manager','Manager EduTrans']);
  const q=(s,r=document)=>r.querySelector(s);
  const h=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));
  const money=v=>'Rp'+Number(v||0).toLocaleString('id-ID');
  const state={partners:[],customers:[],quote:null,busy:false,initialized:false,requestKey:null};

  function role(){try{return String(profile?.role||'')}catch(_){return ''}}
  function allowed(){return ROLES.has(role())}
  function db(){try{return typeof sb!=='undefined' && sb?.rpc ? sb : null}catch(_){return null}}
  function status(message,bad=false){
    const el=q('#gsaStatus'); if(!el)return;
    el.textContent=message||'';el.style.color=bad?'#a52d2d':'var(--g)';
  }
  function today(){
    const d=new Date(),local=new Date(d.getTime()-d.getTimezoneOffset()*60000);
    return local.toISOString().slice(0,10);
  }
  function installStyle(){
    if(q('#gsaStyle'))return;
    const el=document.createElement('style');el.id='gsaStyle';
    el.textContent=`
    .gsa-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}
    .gsa-card{padding:18px;background:#fff;border:1px solid var(--line);border-radius:16px}
    .gsa-field{display:flex;flex-direction:column;gap:6px;margin:10px 0}
    .gsa-field label{font-size:10px;font-weight:700}
    .gsa-field input,.gsa-field select{width:100%;box-sizing:border-box;padding:11px;border:1px solid var(--line);border-radius:9px;background:#fff;color:var(--text)}
    .gsa-summary{background:#eaf5ec;border-radius:12px;padding:14px;line-height:1.9}
    .gsa-summary strong{font-size:22px;color:#165b31}
    .gsa-warn{border:1px solid #edc682;background:#fff9e7;border-radius:10px;padding:10px;font-size:10px}
    .gsa-note{color:var(--muted);font-size:10px;line-height:1.6}
    @media(max-width:820px){.gsa-grid{grid-template-columns:1fr}}
    `;
    document.head.appendChild(el);
  }
  function installPage(){
    if(q('#specialAgency'))return;
    const root=q('.content');if(!root)return;
    const section=document.createElement('section');
    section.id='specialAgency';section.className='page';
    section.innerHTML=`
    <div class="notice">Master Kemitraan / Agen Khusus • Hanya Manager, Owner atau Direktur yang berwenang membuat booking.</div>
    <div class="gsa-grid">
      <div class="gsa-card">
        <h3>Agen Khusus — Edukasi Stasiun</h3>
        <p class="gsa-note">Pilih mitra, sekolah/customer terdaftar, tanggal kegiatan dan peserta. Harga tidak dapat diedit.</p>
        <form id="gsaForm">
          <div class="gsa-field"><label>Mitra khusus</label><select id="gsaPartner" required><option value="">Memuat mitra…</option></select></div>
          <div class="gsa-field"><label>Customer / Sekolah di ERP</label><select id="gsaCustomer" required><option value="">Memuat customer…</option></select></div>
          <div class="gsa-field"><label>Tanggal kegiatan</label><input id="gsaDate" type="date" required min="${today()}" value="${today()}"></div>
          <div class="gsa-field"><label>Jumlah peserta (minimal 20)</label><input id="gsaPax" type="number" min="20" step="1" required value="20"></div>
          <p id="gsaStatus" class="gsa-note" role="status" aria-live="polite"></p>
          <button type="button" id="gsaPreview" class="btn ghost">Hitung dari ERP</button>
          <button type="submit" id="gsaCreate" class="btn primary">Buat Booking Agen Khusus</button>
        </form>
      </div>
      <div class="gsa-card">
        <h3>Rincian Harga Terkunci</h3>
        <div class="gsa-summary" id="gsaQuote">Pilih mitra dan peserta, lalu klik Hitung dari ERP.</div>
        <p class="gsa-note">Harga paket Rp55.000/pax telah termasuk komisi mitra Rp5.000/pax. Komisi Sales GMU Rp0. Tidak menetapkan harga jual kembali agen.</p>
        <div id="gsaWarning" class="gsa-warn" style="display:none">Rombongan 40 pax ke atas memerlukan review skema dan HPP. Jangan gabungkan otomatis dengan tier B2B organisasi.</div>
        <div class="gsa-warn">Booking dibuat sebagai Lead, bukan transaksi lunas. Finance wajib meninjau HPP aktual sebelum penerbitan quotation dan pelaksanaan.</div>
      </div>
    </div>`;
    root.appendChild(section);
  }
  function installNav(){
    if(q('#nav [data-page="specialAgency"]'))return;
    const nav=q('#nav');if(!nav)return;
    const b=document.createElement('button');b.dataset.page='specialAgency';b.innerHTML='🤝 &nbsp; Agen Khusus';
    const anchor=q('#nav [data-page="packageMaster"]');
    if(anchor?.nextSibling)nav.insertBefore(b,anchor.nextSibling);else nav.appendChild(b);
  }
  function syncVisibility(){
    const el=q('#nav [data-page="specialAgency"]');
    if(el)el.classList.toggle('hidden',!allowed());
    const page=q('#specialAgency');if(page&&!allowed())page.classList.remove('active');
  }
  async function loadData(){
    if(!allowed()||!db())return;
    const [p,c]=await Promise.all([
      db().from('special_agency_partners').select('partner_code,display_name,partner_label,is_active').eq('is_active',true).order('display_name'),
      db().from('customers').select('id,name').order('name').limit(500)
    ]);
    if(p.error)throw p.error;if(c.error)throw c.error;
    state.partners=p.data||[];state.customers=c.data||[];
    const partner=q('#gsaPartner'),customer=q('#gsaCustomer');
    if(partner)partner.innerHTML='<option value="">— Pilih agen khusus —</option>'+state.partners.map(a=>`<option value="${h(a.partner_code)}">${h(a.display_name)} — ${h(a.partner_label)}</option>`).join('');
    if(customer)customer.innerHTML='<option value="">— Pilih sekolah/customer —</option>'+state.customers.map(x=>`<option value="${h(x.id)}">${h(x.name)}</option>`).join('');
    status(state.partners.length?'Pilih data untuk membuat booking.':'Belum ada mitra aktif. Pastikan migrasi ERP telah diterapkan.',!state.partners.length);
  }
  function validInput(){
    const partner=q('#gsaPartner')?.value||'';
    const customer=q('#gsaCustomer')?.value||'';
    const date=q('#gsaDate')?.value||'';
    const pax=Number(q('#gsaPax')?.value||0);
    if(!partner||!customer||!date||!Number.isInteger(pax)||pax<20)throw new Error('Lengkapi mitra, customer, tanggal dan peserta minimal 20.');
    if(date<today())throw new Error('Tanggal kegiatan tidak boleh lewat.');
    return {partner,customer,date,pax};
  }
  async function preview(){
    if(state.busy)return;
    let form;try{form=validInput()}catch(e){status(e.message,true);return}
    state.busy=true;state.quote=null;
    try{
      const {data,error}=await db().rpc('gmu_special_agency_quote',{p_partner_code:form.partner,p_pax:form.pax});
      if(error)throw error;
      state.quote={...data,form};
      const quote=q('#gsaQuote');
      if(quote)quote.innerHTML=`
        <div>Mitra: <b>${h(data.partner_name)}</b></div>
        <div>Harga/pax: <b>${money(data.price_per_pax)}</b></div>
        <div>Total paket: <strong>${money(data.gross_total)}</strong></div>
        <div>Komisi mitra termasuk: <b>${money(data.partner_commission_total)}</b></div>
        <div>Komisi Sales GMU: <b>Rp0</b></div>
        <div>GMU setelah komisi mitra: <b>${money(data.gmu_after_partner_commission)}</b></div>`;
      const warn=q('#gsaWarning');if(warn)warn.style.display=data.cost_review_required?'block':'none';
      status('Harga dihitung langsung dari server ERP. Tidak ada komisi ganda.');
    }catch(e){status(e?.message||String(e),true)}
    finally{state.busy=false}
  }
  async function create(event){
    event.preventDefault();if(state.busy)return;
    let form;try{form=validInput()}catch(e){status(e.message,true);return}
    if(!state.quote || JSON.stringify(state.quote.form)!==JSON.stringify(form)){
      status('Klik Hitung dari ERP terlebih dahulu agar penawaran sesuai data terbaru.',true);return;
    }
    if(!window.confirm('Buat booking Lead agen khusus? Harga Rp55.000/pax sudah termasuk komisi mitra Rp5.000/pax.'))return;
    state.busy=true;const button=q('#gsaCreate');if(button)button.disabled=true;
    try{
      const key=state.requestKey||(window.crypto?.randomUUID?.()||null);
      state.requestKey=key;
      const {data,error}=await db().rpc('gmu_create_special_agency_booking',{
        p_customer_id:form.customer,p_partner_code:form.partner,
        p_trip_date:form.date,p_pax:form.pax,p_request_key:key
      });
      if(error)throw error;
      state.quote=null;state.requestKey=null;
      status(`Booking ${data.booking_no} berhasil dibuat sebagai Lead. Lanjutkan validasi RAB dan Finance.`);
      if(q('#gsaQuote'))q('#gsaQuote').textContent=`Booking: ${data.booking_no}. Pilih Hitung dari ERP untuk transaksi berikutnya.`;
      try{ if(typeof window.GmuMainRefresh==='function')window.GmuMainRefresh(); }catch(_){}
    }catch(e){status('Gagal membuat booking: '+(e?.message||String(e)),true)}
    finally{state.busy=false;if(button)button.disabled=false}
  }
  function resetQuote(){
    state.quote=null;state.requestKey=null;
    if(q('#gsaQuote'))q('#gsaQuote').textContent='Data berubah. Klik Hitung dari ERP kembali.';
  }
  function bind(){
    q('#gsaPreview')?.addEventListener('click',preview);
    q('#gsaForm')?.addEventListener('submit',create);
    ['gsaPartner','gsaCustomer','gsaDate','gsaPax'].forEach(id=>q('#'+id)?.addEventListener('change',resetQuote));
  }
  function init(){
    const wait=setInterval(()=>{
      if(typeof profile==='undefined'||!profile||!db()||!q('.content')||!q('#nav'))return;
      clearInterval(wait);
      if(!allowed())return;
      installStyle();installPage();installNav();bind();syncVisibility();
      const nav=q('#nav');
      if(nav)new MutationObserver(syncVisibility).observe(nav,{attributes:true,subtree:true,attributeFilter:['class']});
      loadData().catch(e=>status('Modul belum siap: '+(e?.message||String(e)),true));
      state.initialized=true;
    },250);
    setTimeout(()=>clearInterval(wait),25000);
    window.GmuSpecialAgency=Object.freeze({version:VERSION,refresh:loadData});
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();