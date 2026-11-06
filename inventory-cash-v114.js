(() => {
  'use strict';
  const VERSION = 'v11.4-inventory-cash-visibility';
  const ALLOWED = new Set(['Owner','Manager','Admin','Finance','Operation','Director','Direktur','Manager EduTrans']);
  const q = (s,r=document)=>r.querySelector(s);
  const esc = v => String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));
  const money = v => 'Rp ' + Number(v||0).toLocaleString('id-ID');
  const date = v => { if(!v) return '—'; const d=new Date(v); return Number.isNaN(d.getTime())?'—':d.toLocaleDateString('id-ID',{day:'2-digit',month:'short',year:'numeric'}); };
  const role = () => { try{return String(profile?.role||'')}catch(_){return ''} };
  const db = () => { try{return typeof sb!=='undefined'&&sb?.from?sb:null}catch(_){return null} };
  const state={items:[],purchases:[],movements:[],ledger:[],errors:[],loading:false};
  function style(){
    if(q('#gmuV114Style'))return;
    const s=document.createElement('style');s.id='gmuV114Style';s.textContent=`
      .g114{margin-top:12px}.g114-head{display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:10px}.g114-head h3{margin:0;color:var(--gd)}.g114-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px}.g114-card{background:#fff;border:1px solid var(--line);border-radius:13px;padding:12px;min-width:0}.g114-card small{font-size:9px;color:var(--muted)}.g114-card b{display:block;color:var(--gd);font-size:16px;margin:4px 0}.g114-card p{font-size:9px;color:var(--muted);margin:4px 0}.g114-table-wrap{overflow:auto;background:#fff;border:1px solid var(--line);border-radius:12px;margin-top:10px}.g114-table{width:100%;border-collapse:collapse;font-size:10px;min-width:650px}.g114-table th,.g114-table td{padding:9px 10px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}.g114-table th{font-size:9px;color:var(--muted);white-space:nowrap}.g114-table tr:last-child td{border-bottom:0}.g114-tag{display:inline-block;padding:4px 7px;border:1px solid var(--line);border-radius:999px;font-size:8px;background:#f5faf7}.g114-error{padding:10px;border:1px solid #efc5c5;background:#fff4f4;color:#963434;border-radius:10px;font-size:9px;margin-top:8px}.g114-empty{padding:14px;text-align:center;border:1px dashed var(--line);border-radius:10px;color:var(--muted);font-size:10px;margin-top:8px}.g114-btn{border:1px solid var(--line);background:#fff;color:var(--gd);border-radius:9px;padding:8px 10px;font:inherit;font-size:9px;cursor:pointer}.g114-btn.primary{background:var(--g);color:#fff;border-color:var(--g)}
      @media(max-width:800px){.g114-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:500px){.g114-grid{grid-template-columns:1fr}}
    `;document.head.appendChild(s);
  }
  function ensurePage(){
    let page=q('#inventoryCashControl');if(page)return page;
    const host=q('.content')||q('main')||document.body;
    page=document.createElement('section');page.id='inventoryCashControl';page.className='page';
    page.innerHTML='<div class="page-head"><div><h2>Kas Keluar & Master Barang</h2><p>Stok mainan, pembelian, dan arus kas keluar</p></div></div>';
    host.appendChild(page);return page;
  }
  function ensureNav(){
    if(!ALLOWED.has(role()))return;
    const nav=q('#nav');if(!nav||q('#nav [data-page="inventoryCashControl"]'))return;
    const b=document.createElement('button');b.type='button';b.dataset.page='inventoryCashControl';b.textContent='Kas & Inventaris';
    b.addEventListener('click',()=>showPage());nav.appendChild(b);
  }
  function showPage(){
    document.querySelectorAll('.page').forEach(x=>x.classList.remove('active'));
    ensurePage().classList.add('active');
    document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page==='inventoryCashControl'));
    const title=q('#title');if(title)title.textContent='Kas & Inventaris';
    refresh();
  }
  async function select(table,cols,orderCol){
    let query=db().from(table).select(cols);
    if(orderCol)query=query.order(orderCol,{ascending:false}).limit(300);
    const {data,error}=await query;if(error)throw error;return Array.isArray(data)?data:[];
  }
  async function refresh(){
    if(state.loading||!db()||!ALLOWED.has(role()))return;
    state.loading=true;state.errors=[];
    const jobs=[
      ['items',()=>select('inventory_items','id,sku,item_name,category,unit,current_stock,average_cost,is_active,notes','updated_at')],
      ['purchases',()=>select('inventory_purchases','purchase_date,vendor_name,receipt_reference,payment_method,payment_status,total_amount,notes','purchase_date')],
      ['movements',()=>select('inventory_movements','item_id,movement_type,quantity,unit_cost,total_cost,movement_date,reference_no,source_type,notes','movement_date')],
      ['ledger',()=>select('treasury_ledger','id,transaction_date,direction,amount,description,status,source_type,source_id,created_at','transaction_date')]
    ];
    await Promise.all(jobs.map(async([key,fn])=>{try{state[key]=await fn()}catch(e){state[key]=[];state.errors.push(key+': '+(e?.message||String(e)))}}));
    state.loading=false;render();
  }
  function table(title,headers,rows){
    return '<div class="g114-table-wrap"><table class="g114-table"><thead><tr>'+headers.map(x=>'<th>'+esc(x)+'</th>').join('')+'</tr></thead><tbody>'+
      (rows.length?rows.join(''):'<tr><td colspan="'+headers.length+'">Belum ada data yang dapat ditampilkan.</td></tr>')+
      '</tbody></table></div>';
  }
  function render(){
    if(!ALLOWED.has(role()))return;
    style();const page=ensurePage();ensureNav();
    const items=state.items.filter(x=>x.is_active!==false);
    const totalStock=items.reduce((a,x)=>a+Number(x.current_stock||0),0);
    const stockValue=items.reduce((a,x)=>a+Number(x.current_stock||0)*Number(x.average_cost||0),0);
    const cashOut=state.ledger.filter(x=>String(x.direction).toUpperCase()==='OUTFLOW'&&String(x.status).toUpperCase()==='POSTED');
    const inventoryCash=cashOut.filter(x=>String(x.source_type).toUpperCase()==='MANUAL'&&/ALDI-TOYS-20261103-197000|pembelian stok mainan aldi toys/i.test(String(x.description||'')+' '+String(x.source_id||'')));
    const movementRows=state.movements.slice(0,100).map(m=>{
      const item=state.items.find(x=>String(x.id)===String(m.item_id))||{};
      return '<tr><td>'+esc(date(m.movement_date))+'</td><td><strong>'+esc(item.item_name||'Barang')+'</strong><br><small>'+esc(item.sku||'')+'</small></td><td>'+esc(m.movement_type)+'</td><td>'+Number(m.quantity||0).toLocaleString('id-ID')+' '+esc(item.unit||'unit')+'</td><td>'+money(m.unit_cost)+'</td><td>'+money(m.total_cost)+'</td><td>'+esc(m.reference_no||'—')+'</td></tr>';
    });
    const itemRows=items.map(x=>'<tr><td><strong>'+esc(x.item_name)+'</strong><br><small>'+esc(x.sku)+'</small></td><td>'+esc(x.category||'—')+'</td><td>'+Number(x.current_stock||0).toLocaleString('id-ID')+' '+esc(x.unit||'unit')+'</td><td>'+money(x.average_cost)+'</td><td>'+money(Number(x.current_stock||0)*Number(x.average_cost||0))+'</td><td>'+esc(x.notes||'—')+'</td></tr>');
    const purchaseRows=state.purchases.slice(0,60).map(x=>'<tr><td>'+esc(date(x.purchase_date))+'</td><td>'+esc(x.vendor_name)+'</td><td>'+esc(x.receipt_reference)+'</td><td>'+esc(x.payment_method)+'</td><td><span class="g114-tag">'+esc(x.payment_status)+'</span></td><td>'+money(x.total_amount)+'</td></tr>');
    const cashRows=cashOut.slice(0,100).map(x=>'<tr><td>'+esc(date(x.transaction_date))+'</td><td>'+esc(x.description||'—')+'</td><td>'+esc(x.source_type||'—')+'</td><td>'+esc(x.status||'—')+'</td><td>'+money(x.amount)+'</td></tr>');
    page.innerHTML='<div class="page-head"><div><h2>Kas Keluar & Master Barang</h2><p>ERP production • '+VERSION+' • Data realtime dari Supabase</p></div></div>'+
      '<div class="g114"><div class="g114-head"><div><h3>Ringkasan Persediaan & Kas</h3><small>Stok dan transaksi POSTED</small></div><button class="g114-btn primary" data-g114-refresh>Muat Ulang</button></div>'+
      '<div class="g114-grid"><div class="g114-card"><small>Jenis barang aktif</small><b>'+items.length+'</b></div><div class="g114-card"><small>Total unit stok</small><b>'+totalStock.toLocaleString('id-ID')+'</b></div><div class="g114-card"><small>Nilai estimasi stok</small><b>'+money(stockValue)+'</b></div><div class="g114-card"><small>Total Kas Keluar POSTED (termuat)</small><b>'+money(cashOut.reduce((a,x)=>a+Number(x.amount||0),0))+'</b><p>'+cashOut.length+' transaksi</p></div></div>'+
      '<div class="g114-card" style="margin-top:10px"><h3>Verifikasi pembelian mainan 3 Nov 2026</h3><p>Struk Aldi Toys • 33 unit • Rp197.000</p><div class="g114-grid"><div><small>Status kas keluar</small><b>'+ (inventoryCash.length?'Tercatat':'Belum ditemukan di ledger')+'</b></div><div><small>Transaksi terkait</small><b>'+inventoryCash.length+'</b></div><div><small>Nilai terdeteksi</small><b>'+money(inventoryCash.reduce((a,x)=>a+Number(x.amount||0),0))+'</b></div><div><small>Referensi</small><b style="font-size:10px">ALDI-TOYS-20261103-197000</b></div></div></div>'+
      '<h3 style="margin-top:14px">Master Barang / Stok</h3>'+table('Barang',['Nama Barang / SKU','Kategori','Stok','Harga Rata-rata','Nilai Stok','Catatan'],itemRows)+
      '<h3 style="margin-top:14px">Riwayat Pembelian Barang</h3>'+table('Pembelian',['Tanggal','Vendor','Referensi','Pembayaran','Status','Total'],purchaseRows)+
      '<h3 style="margin-top:14px">Mutasi Stok</h3>'+table('Mutasi',['Tanggal','Barang','Jenis','Qty','Biaya/unit','Total biaya','Referensi'],movementRows)+
      '<h3 style="margin-top:14px">Kas Keluar ERP (POSTED)</h3>'+table('Kas keluar',['Tanggal','Keterangan','Sumber','Status','Jumlah'],cashRows)+
      state.errors.map(e=>'<div class="g114-error">'+esc(e)+'</div>').join('')+'</div>';
  }
  function init(){
    if(window.GmuInventoryCashV114)return;
    ensureNav();if(ALLOWED.has(role())){ensurePage();render();refresh();}
    document.addEventListener('click',e=>{const b=e.target.closest?.('[data-g114-refresh]');if(b)refresh()});
    const timer=setInterval(()=>{if(db()&&ALLOWED.has(role())){clearInterval(timer);ensurePage();ensureNav();refresh()}},500);
    setTimeout(()=>clearInterval(timer),15000);
    setInterval(()=>{if(db()&&ALLOWED.has(role())&&!document.hidden)refresh()},60000);
    window.GmuInventoryCashV114=Object.freeze({version:VERSION,refresh,state});
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();