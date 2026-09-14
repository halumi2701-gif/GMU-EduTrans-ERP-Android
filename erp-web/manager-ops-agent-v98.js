(() => {
  'use strict';

  const VERSION = 'v9.8-manager-ops-agent';
  const ALLOWED_ROLES = new Set(['Owner', 'Director', 'Direktur', 'Manager', 'Manager EduTrans']);
  const MANAGER_ROLES = new Set(['Manager', 'Manager EduTrans']);
  const DIRECTOR_APPROVAL_LIMIT = 2_000_000;
  const QUICK_ACTIONS = ['Siapkan Trip','Buat Rundown','Cek Kesiapan','Susun Crew','Cek Vendor','Analisis RAB','Buat Operation Sheet','Buat Laporan'];
  const q = (sel, root = document) => root.querySelector(sel);
  const h = (value) => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = (value) => 'Rp ' + Number(value || 0).toLocaleString('id-ID');

  let selectedBookingId = '';
  let extra = { vendorPos: [], documents: [], costs: [] };
  let loading = false;

  function currentRole(){ try{return String(profile?.role||'')}catch(_){return ''} }
  function allowed(){ return ALLOWED_ROLES.has(currentRole()) }
  function isManager(){ return MANAGER_ROLES.has(currentRole()) }
  function bookings(){ try{return Array.isArray(data?.bookings)?data.bookings:[]}catch(_){return []} }
  function nextTrip(){ const rows=bookings().filter(b=>!['Completed','Closed'].includes(String(b?.status||''))).slice().sort((a,b)=>String(a?.trip_date||'').localeCompare(String(b?.trip_date||'')));return rows[0]||null }
  function selectedTrip(){ return bookings().find(b=>String(b.id)===String(selectedBookingId))||nextTrip() }
  function customerName(booking){ try{return customer(booking?.customer_id)?.name||'-'}catch(_){return '-'} }
  function tripRow(id){ try{return data.trips?.find(x=>String(x.booking_id)===String(id))}catch(_){return null} }
  function operationSheet(id){ try{return data.operationSheets?.find(x=>String(x.booking_id)===String(id))}catch(_){return null} }

  async function queryOptional(table, bookingId){
    try{
      const {data:rows,error}=await sb.from(table).select('*').eq('booking_id',bookingId);
      if(error)throw error;
      return rows||[];
    }catch(e){console.warn(`${VERSION}: ${table}`,e?.message||e);return []}
  }

  async function loadExtras(bookingId){
    if(!bookingId||typeof sb==='undefined'||!sb){extra={vendorPos:[],documents:[],costs:[]};return}
    const [vendorPos,documents,costs]=await Promise.all([queryOptional('vendor_pos',bookingId),queryOptional('documents',bookingId),queryOptional('trip_costs',bookingId)]);
    extra={vendorPos,documents,costs};
  }

  function readiness(booking){
    if(!booking)return{score:0,missing:[],checks:[]};
    const id=booking.id,os=operationSheet(id);
    let rundown=false,manifest=false;
    try{rundown=data.rundownItems?.some(x=>String(x.booking_id)===String(id))}catch(_){}
    try{manifest=data.manifests?.some(x=>String(x.booking_id)===String(id))}catch(_){}
    const checks=[['Rundown',!!rundown],['Manifest',!!manifest],['Operation Sheet',!!os],['Vendor/PO',extra.vendorPos.length>0],['Documents',extra.documents.length>=5]];
    const completed=checks.filter(x=>x[1]).length;
    return{score:Math.round(completed*100/checks.length),missing:checks.filter(x=>!x[1]).map(x=>x[0]),checks};
  }

  function rabSnapshot(){const rab=extra.costs.reduce((s,r)=>s+Number(r?.rab_amount||0),0),actual=extra.costs.reduce((s,r)=>s+Number(r?.actual_amount||0),0);return{rab,actual,variance:rab-actual}}
  function authorityFor(action,amount=0){const n=String(action||'').toLowerCase();if(Number(amount||0)>DIRECTOR_APPROVAL_LIMIT)return'DIRECTOR_APPROVAL';if(['refund','closing','hapus piutang','ubah harga','rekening','jurnal'].some(x=>n.includes(x)))return'DIRECTOR_APPROVAL';if(['assign','kirim','approve','ubah','final','konfirmasi vendor'].some(x=>n.includes(x)))return'MANAGER_CONFIRMATION';return'AUTO'}
  function statusText(a){return a==='DIRECTOR_APPROVAL'?'Perlu Approval Direktur':a==='MANAGER_CONFIRMATION'?'Perlu Konfirmasi Manager':'Assist / Auto Draft'}

  function renderTripSelect(){const select=q('#gmuOpsTripSelect');if(!select)return;const rows=bookings().filter(b=>String(b.status||'')!=='Closed');if(!selectedBookingId&&nextTrip())selectedBookingId=String(nextTrip().id);select.innerHTML=rows.map(b=>`<option value="${h(b.id)}" ${String(b.id)===String(selectedBookingId)?'selected':''}>${h(b.booking_no)} • ${h(b.program_name)} • ${h(b.trip_date)}</option>`).join('')||'<option value="">Belum ada trip</option>'}

  function renderPanel(){
    if(!allowed())return;
    renderTripSelect();
    const booking=selectedTrip(),ready=readiness(booking),rab=rabSnapshot(),summary=q('#gmuOpsBrief');
    if(summary){
      if(!booking)summary.innerHTML='<div class="gmu-ops-empty">Belum ada trip aktif atau mendatang.</div>';
      else{
        let pending=0;try{pending=data.approvals?.filter(x=>x.status==='Pending').length||0}catch(_){}
        summary.innerHTML=`<div class="gmu-ops-brief-head"><div><b>${h(booking.program_name)}</b><small>${h(customerName(booking))} • ${h(booking.trip_date)} • ${Number(booking.pax||0)} pax</small></div><span class="badge ${ready.score>=80?'ok':'warn'}">${ready.score}% READY</span></div><div class="gmu-ops-progress"><i style="width:${ready.score}%"></i></div><div class="gmu-ops-checks">${ready.checks.map(x=>`<span class="${x[1]?'done':'missing'}">${x[1]?'✓':'○'} ${h(x[0])}</span>`).join('')}</div>${ready.missing.length?`<p class="gmu-ops-attention">Perlu perhatian: ${h(ready.missing.join(', '))}</p>`:'<p class="gmu-ops-good">Dokumen inti trip sudah siap.</p>'}${pending?`<p class="gmu-ops-attention">${pending} approval sedang menunggu tindakan.</p>`:''}<div class="gmu-ops-rab"><span>RAB operasional</span><b>${rab.rab?money(rab.rab):'Belum diisi'}</b><span>Aktual ${money(rab.actual)}</span></div>`;
      }
    }
    const dock=q('#gmuOpsDockText');if(dock)dock.textContent=booking?`Next trip ${ready.score}% ready • ${booking.program_name}`:'Siap membantu operasional Manager';
  }

  function reply(message,tone=''){const el=q('#gmuOpsReply');if(!el)return;el.textContent=message;el.dataset.tone=tone}
  async function refresh(){if(loading||!allowed())return;loading=true;try{const b=selectedTrip();if(b){selectedBookingId=String(b.id);await loadExtras(b.id)}else extra={vendorPos:[],documents:[],costs:[]};renderPanel()}finally{loading=false}}

  function openTripFolderLocal(id){try{if(typeof openTripFolder==='function'){openTripFolder(id);return true}}catch(_){}try{if(typeof navTo==='function')navTo('tripfolder')}catch(_){}return false}
  function openOperationLocal(id){try{if(typeof openOperation==='function'){openOperation(id);return true}}catch(_){}try{if(typeof navTo==='function')navTo('operation')}catch(_){}return false}

  async function runAction(action){
    const booking=selectedTrip();if(!booking&&action!=='Cek Vendor'){reply('Belum ada trip yang dapat diproses.','warn');return}
    q('#gmuOpsAuthority').textContent=statusText(authorityFor(action));
    if(action==='Cek Kesiapan'){await refresh();const r=readiness(selectedTrip());reply(r.missing.length?`Kesiapan ${r.score}%. Lengkapi: ${r.missing.join(', ')}.`:`Kesiapan ${r.score}%. Trip inti siap.`,r.score>=80?'ok':'warn');return}
    if(action==='Analisis RAB'){await refresh();const r=rabSnapshot();if(r.rab<=0)reply('RAB operasional belum diisi untuk trip ini.','warn');else if(r.variance<0)reply(`RAB ${money(r.rab)} • Aktual ${money(r.actual)}. Aktual melewati RAB ${money(-r.variance)}.`,'warn');else reply(`RAB ${money(r.rab)} • Aktual ${money(r.actual)}. Sisa ${money(r.variance)}.`,'ok');return}
    if(action==='Cek Vendor'){try{navTo('vendors')}catch(_){}reply('Vendor Master dibuka. Konfirmasi vendor tetap memerlukan tindakan Manager.');return}
    if(action==='Buat Operation Sheet'||action==='Susun Crew'){openOperationLocal(booking.id);reply(action==='Susun Crew'?'Operation Sheet dibuka untuk penugasan TL/crew.':'Operation Sheet dibuka untuk disiapkan.');return}
    if(['Siapkan Trip','Buat Rundown','Buat Laporan'].includes(action)){openTripFolderLocal(booking.id);reply(`${action}: Trip Folder dibuka. Penyimpanan/finalisasi tetap dilakukan user berwenang.`);return}
  }

  async function routeCommand(command){
    const text=String(command||'').toLowerCase().trim();if(!text)return;
    const amountMatch=text.match(/(?:rp\s*)?([0-9][0-9.]{5,})/i),amount=amountMatch?Number(amountMatch[1].replace(/\./g,'')):0,authority=authorityFor(text,amount);
    q('#gmuOpsAuthority').textContent=statusText(authority);
    if(authority==='DIRECTOR_APPROVAL'&&amount>DIRECTOR_APPROVAL_LIMIT){reply(`Nilai ${money(amount)} melewati batas Manager ${money(DIRECTOR_APPROVAL_LIMIT)}. Tindakan harus dinaikkan ke Direktur.`,'warn');return}
    if(text.includes('rab')||text.includes('biaya')||text.includes('cost'))return runAction('Analisis RAB');
    if(text.includes('vendor')||text.includes(' po '))return runAction('Cek Vendor');
    if(text.includes('rundown'))return runAction('Buat Rundown');
    if(text.includes('manifest')||text.includes('dokumen')||text.includes('folder'))return runAction('Siapkan Trip');
    if(text.includes('crew')||text.includes('tl')||text.includes('tour leader'))return runAction('Susun Crew');
    if(text.includes('operation')||text.includes('opsheet'))return runAction('Buat Operation Sheet');
    if(text.includes('laporan')||text.includes('report'))return runAction('Buat Laporan');
    if(text.includes('siap')||text.includes('kesiapan')||text.includes('ready'))return runAction('Cek Kesiapan');
    reply('Saya bisa membantu: siapkan trip, rundown, kesiapan, crew/TL, vendor, RAB operasional, Operation Sheet, dan laporan.');
  }

  function installStyle(){
    if(q('#gmuOpsAgentStyle'))return;const style=document.createElement('style');style.id='gmuOpsAgentStyle';style.textContent=`.gmu-ops-shell{display:grid;grid-template-columns:1.15fr .85fr;gap:14px}.gmu-ops-brief-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.gmu-ops-brief-head b,.gmu-ops-brief-head small{display:block}.gmu-ops-brief-head small{font-size:9px;color:var(--muted);margin-top:3px}.gmu-ops-progress{height:9px;background:#e6eee9;border-radius:999px;overflow:hidden;margin:12px 0}.gmu-ops-progress i{display:block;height:100%;background:var(--g);border-radius:999px}.gmu-ops-checks{display:flex;gap:6px;flex-wrap:wrap}.gmu-ops-checks span{font-size:8px;font-weight:800;padding:5px 7px;border-radius:999px}.gmu-ops-checks .done{background:#e5f5ed;color:#0b6649}.gmu-ops-checks .missing{background:#fff4dc;color:#956300}.gmu-ops-attention{font-size:9px;color:#956300;font-weight:800}.gmu-ops-good{font-size:9px;color:var(--g);font-weight:800}.gmu-ops-rab{display:grid;grid-template-columns:1fr auto;gap:3px 10px;border-top:1px solid var(--line);margin-top:12px;padding-top:10px}.gmu-ops-rab span{font-size:8px;color:var(--muted)}.gmu-ops-rab span:last-child{grid-column:1/-1}.gmu-ops-actions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.gmu-ops-action{border:1px solid var(--line);background:#fff;border-radius:13px;padding:11px;text-align:left;cursor:pointer;color:var(--text)}.gmu-ops-action b{display:block;font-size:10px}.gmu-ops-action small{font-size:8px;color:var(--g)}.gmu-ops-command{display:grid;grid-template-columns:1fr auto;gap:8px}.gmu-ops-command input,.gmu-ops-select{border:1px solid var(--line);border-radius:10px;padding:10px;background:#fff;color:var(--text)}.gmu-ops-select{width:100%;margin-top:7px}.gmu-ops-reply{border-radius:12px;background:#f4f8f6;padding:11px;font-size:10px;line-height:1.5;margin:10px 0}.gmu-ops-reply[data-tone="warn"]{background:#fff7e8;color:#805c00}.gmu-ops-reply[data-tone="ok"]{background:#eaf7ef;color:#0b6649}.gmu-ops-policy{font-size:9px;line-height:1.55;color:var(--muted)}.gmu-ops-empty{font-size:10px;color:var(--muted)}.gmu-ops-dock{position:fixed;left:calc(270px + 24px);right:24px;bottom:18px;z-index:18;background:linear-gradient(135deg,var(--gd),var(--g));color:#fff;border:0;border-radius:16px;padding:11px 14px;box-shadow:0 14px 35px rgba(3,48,32,.22);display:flex;align-items:center;justify-content:space-between;gap:12px;cursor:pointer}.gmu-ops-dock b{display:block;font-size:11px}.gmu-ops-dock small{display:block;font-size:8px;color:#d6ebe1;margin-top:2px}.gmu-ops-dock span{font-size:9px;font-weight:900;background:#fff;color:var(--gd);padding:7px 10px;border-radius:999px}@media(max-width:900px){.gmu-ops-shell{grid-template-columns:1fr}}@media(max-width:760px){.gmu-ops-dock{left:13px;right:13px;bottom:12px}}`;
    document.head.appendChild(style);
  }

  function installPage(){
    if(q('#opsAgent'))return;const content=q('.content');if(!content)return;const page=document.createElement('section');page.id='opsAgent';page.className='page';page.innerHTML=`<div class="notice">GMU EduTrans Ops Agent ${VERSION} • Assistant operasional. Perubahan penting tetap memakai konfirmasi dan approval.</div><div class="gmu-ops-shell"><div><div class="card section" style="margin-top:0"><div class="head"><div><h3>Daily Ops Brief</h3><p>Kesiapan trip dan kebutuhan tindakan Manager</p></div><button id="gmuOpsRefresh" class="btn ghost" type="button">↻ Refresh</button></div><label class="note">Trip aktif</label><select id="gmuOpsTripSelect" class="gmu-ops-select"></select><div id="gmuOpsBrief" style="margin-top:12px"></div></div><div class="card section"><div class="head"><div><h3>Ask Ops Agent</h3><p>Perintah natural untuk membuka pekerjaan operasional</p></div><span id="gmuOpsAuthority" class="badge info">Assist / Auto Draft</span></div><div id="gmuOpsReply" class="gmu-ops-reply">Saya siap membantu menyiapkan dan mengontrol operasional EduTrans.</div><div class="gmu-ops-command"><input id="gmuOpsCommand" placeholder="Contoh: cek vendor trip besok"><button id="gmuOpsSend" class="btn primary" type="button">Kirim</button></div></div></div><div class="card section" style="margin-top:0"><div class="head"><div><h3>Perintah Cepat</h3><p>Pilih pekerjaan yang ingin dibantu</p></div></div><div class="gmu-ops-actions">${QUICK_ACTIONS.map(a=>`<button type="button" class="gmu-ops-action" data-ops-action="${h(a)}"><b>${h(a)}</b><small>${a==='Analisis RAB'?'Analisis →':'Buka →'}</small></button>`).join('')}</div><div class="gmu-ops-policy" style="margin-top:14px">Assign/finalisasi/konfirmasi penting memerlukan konfirmasi Manager. Transaksi di atas ${money(DIRECTOR_APPROVAL_LIMIT)} serta refund, closing, perubahan harga, rekening dan jurnal memerlukan approval Direktur.</div></div></div>`;content.appendChild(page);
  }

  function installNav(){if(!allowed()||q('#nav [data-page="opsAgent"]'))return;const nav=q('#nav');if(!nav)return;const button=document.createElement('button');button.dataset.page='opsAgent';button.innerHTML='✦ &nbsp; Ops Agent';const operation=q('#nav [data-page="operation"]');if(operation?.nextSibling)nav.insertBefore(button,operation.nextSibling);else nav.appendChild(button)}
  function installDock(){if(!isManager()||q('#gmuOpsDock'))return;const dock=document.createElement('button');dock.id='gmuOpsDock';dock.type='button';dock.className='gmu-ops-dock';dock.innerHTML='<div><b>✦ GMU EduTrans Ops Agent</b><small id="gmuOpsDockText">Siap membantu operasional Manager</small></div><span>Buka</span>';dock.addEventListener('click',()=>{try{navTo('opsAgent')}catch(_){}});document.body.appendChild(dock)}

  function patchApplyRole(){try{if(typeof applyRole!=='function'||applyRole.__gmuOpsPatched)return;const original=applyRole;const wrapped=function(...args){const result=original.apply(this,args);if(allowed())q('#nav [data-page="opsAgent"]')?.classList.remove('hidden');else q('#nav [data-page="opsAgent"]')?.classList.add('hidden');return result};wrapped.__gmuOpsPatched=true;applyRole=wrapped}catch(_){}}

  function bind(){
    q('#gmuOpsRefresh')?.addEventListener('click',()=>refresh());
    q('#gmuOpsTripSelect')?.addEventListener('change',async e=>{selectedBookingId=String(e.target.value||'');await refresh()});
    q('#opsAgent')?.addEventListener('click',e=>{const button=e.target.closest('[data-ops-action]');if(button)runAction(button.dataset.opsAction).catch(err=>reply(err?.message||String(err),'warn'))});
    const send=()=>{const input=q('#gmuOpsCommand'),value=input?.value||'';if(input)input.value='';routeCommand(value).catch(err=>reply(err?.message||String(err),'warn'))};
    q('#gmuOpsSend')?.addEventListener('click',send);q('#gmuOpsCommand')?.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();send()}});
  }

  function init(){installStyle();patchApplyRole();const timer=setInterval(()=>{if(typeof profile==='undefined'||!profile||typeof sb==='undefined'||!sb)return;clearInterval(timer);if(!allowed())return;installPage();installNav();installDock();bind();if(!selectedBookingId&&nextTrip())selectedBookingId=String(nextTrip().id);refresh().catch(e=>reply(e?.message||String(e),'warn'))},250);window.GmuManagerOpsAgent=Object.freeze({version:VERSION,authorityFor,refresh,quickActions:[...QUICK_ACTIONS],directorApprovalLimit:DIRECTOR_APPROVAL_LIMIT})}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
