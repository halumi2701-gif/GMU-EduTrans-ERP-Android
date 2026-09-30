import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";
import { PDFDocument, StandardFonts, rgb, PageSizes } from "npm:pdf-lib@1.17.1";
import QRCode from "npm:qrcode@1.5.4";

const URL = Deno.env.get("SUPABASE_URL") || "";
function secretKey() {
  const raw = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (raw) {
    try { const p = JSON.parse(raw); if (p && p.default) return p.default; } catch (_) {}
  }
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
}
const SECRET = secretKey();
const sb = createClient(URL, SECRET, { auth: { persistSession: false, autoRefreshToken: false } });
const LOGO_B64 = "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";
const GREEN=rgb(0.035,0.29,0.20), GOLD=rgb(0.89,0.67,0.12), INK=rgb(0.08,0.13,0.10), MUTED=rgb(0.40,0.46,0.42), LINE=rgb(0.86,0.90,0.87), PALE=rgb(0.95,0.98,0.96), WHITE=rgb(1,1,1);
const ALLOWED = new Set(["Owner","Manager"]);
const SALES_ALLOWED = new Set(["Sales","Owner","Manager","Manager EduTrans","Director","Direktur","SERVICE"]);
const COMPANY_WA = "+62 877-8390-6545";
const MANAGER_NAME = "Ayu Siti Sopia";
const MANAGER_SIGNATURE_PATH = "system/manager/ayu-siti-sopia-signature.png";


function respond(status, body) {
  return new Response(JSON.stringify(body), { status: status, headers: { "Content-Type":"application/json; charset=utf-8", "Cache-Control":"no-store", "X-Content-Type-Options":"nosniff" } });
}
function clean(v) {
  return String(v == null ? "" : v).replace(/[–—]/g,"-").replace(/[‘’]/g,"'").replace(/[“”]/g,'"').replace(/×/g,"x").replace(/•/g,"-").normalize("NFKD").replace(/[\u0300-\u036f]/g,"").replace(/[^\x20-\x7E]/g,"?");
}
function isUuid(v) { return typeof v === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v); }
function rupiah(v) { return "Rp " + new Intl.NumberFormat("id-ID",{maximumFractionDigits:0}).format(Number(v)||0); }
function indoDate(v) {
  const s=String(v||"").slice(0,10); if(!/^\d{4}-\d{2}-\d{2}$/.test(s)) return "-";
  const a=s.split("-").map(Number), mons=["","Januari","Februari","Maret","April","Mei","Juni","Juli","Agustus","September","Oktober","November","Desember"];
  return a[2] + " " + mons[a[1]] + " " + a[0];
}
function pathPart(v) { return clean(v).replace(/[^A-Za-z0-9._-]+/g,"-").replace(/-+/g,"-").replace(/^-|-$/g,"").slice(0,120) || "document"; }
function b64bytes(b64) { const raw=atob(b64), out=new Uint8Array(raw.length); for(let i=0;i<raw.length;i++) out[i]=raw.charCodeAt(i); return out; }
function wrap(font,size,text,max) {
  const words=clean(text).split(/\s+/).filter(Boolean); if(!words.length) return [""];
  const lines=[]; let line="";
  for(const w of words){ const t=line ? line+" "+w : w; if(font.widthOfTextAtSize(t,size)<=max) line=t; else { if(line) lines.push(line); line=w; } }
  if(line) lines.push(line); return lines;
}
async function authorize(req) {
  const h=req.headers.get("Authorization")||"", token=h.startsWith("Bearer ")?h.slice(7):"";
  if(!token) return { error: respond(401,{error:"Authorization required"}) };
  const u=await sb.auth.getUser(token);
  if(u.error || !u.data.user) return { error: respond(401,{error:"Session tidak valid"}) };
  const p=await sb.from("profiles").select("role,is_active,full_name,phone").eq("id",u.data.user.id).maybeSingle();
  if(!p.data || !p.data.is_active || !SALES_ALLOWED.has(String(p.data.role))) return { error: respond(403,{error:"Akun tidak memiliki akses dokumen quotation"}) };
  return { user:u.data.user, role:String(p.data.role), profile:p.data };
}
async function authorizeInternalToken(token) {
  if(!token || typeof token!=="string" || token.length<32 || token.length>256) return null;
  const r=await sb.rpc("gmu_verify_internal_pdf_token",{p_token:token});
  if(r.error || r.data!==true) return null;
  return { user:{id:"service"}, role:"SERVICE", profile:{role:"SERVICE",is_active:true,full_name:"System",phone:""} };
}
async function getRequest(id) {
  const r=await sb.from("booking_requests").select("*").eq("id",id).single(); if(r.error) throw r.error; return r.data;
}
async function getProgram(br) {
  if(br && br.program_id){ const r=await sb.from("programs").select("name").eq("id",br.program_id).maybeSingle(); if(r.data && r.data.name) return r.data.name; }
  return (br && br.custom_program) || "Custom Educational Trip";
}
function waDigits(v){ let d=String(v||"").replace(/\D/g,""); if(d.startsWith("0")) d="62"+d.slice(1); if(!d.startsWith("62")) d="62"+d; return d; }
function dataUrlBytes(url){ const i=url.indexOf(","); return b64bytes(url.slice(i+1)); }

async function officialQuoteContext(id,au){
  const q=await sb.from("quotations").select("*").eq("id",id).single(); if(q.error) throw q.error;
  const br=await getRequest(q.data.booking_request_id);
  if(au.role==="Sales" && String(br.assigned_sales||"")!==String(au.user.id)) throw new Error("QUOTATION_NOT_OWNED");
  const it=await sb.from("quotation_items").select("description,qty,unit,unit_price,amount").eq("quotation_id",id).order("sort_order"); if(it.error) throw it.error;
  const program=await getProgram(br);
  let pkg=null;
  if(br.package_id){
    const p=await sb.from("program_packages").select("name,facilities,sales_public_summary,duration_text,target_participants,booking_flow,public_terms,shared_rules,shared_statuses").eq("id",br.package_id).maybeSingle();
    pkg=p.data||null;
  }
  let sales={full_name:"GMU EduTrans",phone:COMPANY_WA};
  if(br.assigned_sales){
    const s=await sb.from("profiles").select("full_name,phone").eq("id",br.assigned_sales).maybeSingle();
    if(s.data) sales=s.data;
  }
  if(!String(sales.phone||"").trim()) sales.phone=COMPANY_WA;
  return {q:q.data,br,items:it.data||[],program,pkg,sales};
}

async function makeOfficialQuotationPdf(ctx){
  const {q,br,program,pkg,sales}=ctx;
  const pdf=await PDFDocument.create(), reg=await pdf.embedFont(StandardFonts.Helvetica), bold=await pdf.embedFont(StandardFonts.HelveticaBold);
  const sigFile=await sb.storage.from("gmu-trip-documents").download(MANAGER_SIGNATURE_PATH);
  if(sigFile.error||!sigFile.data) throw new Error("MANAGER_SIGNATURE_NOT_AVAILABLE");
  const logo=await pdf.embedPng(b64bytes(LOGO_B64)), sig=await pdf.embedPng(new Uint8Array(await sigFile.data.arrayBuffer()));
  const wa="https://wa.me/"+waDigits(sales.phone)+"?text="+encodeURIComponent("Halo GMU EduTrans, saya ingin menindaklanjuti quotation "+q.quotation_no);
  const qrData=await QRCode.toDataURL(wa,{type:"image/png",margin:1,width:320,errorCorrectionLevel:"M"});
  const qr=await pdf.embedPng(dataUrlBytes(qrData));
  const PW=PageSizes.A4[0],PH=PageSizes.A4[1];
  function base(title,subtitle,n){
    const p=pdf.addPage(PageSizes.A4);
    p.drawRectangle({x:0,y:PH-12,width:PW,height:12,color:GREEN});
    p.drawImage(logo,{x:40,y:PH-100,width:134,height:74});
    p.drawText(clean(title),{x:PW-260,y:PH-56,size:20,font:bold,color:GREEN});
    p.drawText(clean(subtitle),{x:PW-260,y:PH-77,size:9,font:reg,color:MUTED});
    p.drawLine({start:{x:40,y:52},end:{x:PW-40,y:52},thickness:.7,color:LINE});
    p.drawText("GMU EduTrans - PT Garsyani Multi Usaha",{x:40,y:34,size:7.5,font:reg,color:MUTED});
    const t="Page "+n+" / 3"; p.drawText(t,{x:PW-40-reg.widthOfTextAtSize(t,7.5),y:34,size:7.5,font:reg,color:MUTED});
    return p;
  }
  function lines(p,text,x,y,size,max,font=reg,color=INK,lead=size+3,maxLines=99){
    const ls=wrap(font,size,text,max).slice(0,maxLines); ls.forEach((s,i)=>p.drawText(s,{x,y:y-i*lead,size,font,color})); return y-ls.length*lead;
  }
  function section(p,t,y){ p.drawText(clean(t),{x:40,y,size:12,font:bold,color:GREEN}); p.drawLine({start:{x:40,y:y-7},end:{x:PW-40,y:y-7},thickness:.8,color:LINE}); return y-27; }
  function lv(p,l,v,x,y,w){ p.drawText(clean(l).toUpperCase(),{x,y,size:7,font:bold,color:MUTED}); lines(p,v,x,y-14,9.5,w,reg,INK,12,3); }

  let y=PH-132; const p1=base("QUOTATION RESMI","Program Edukasi - Penawaran GMU EduTrans",1);
  p1.drawRectangle({x:40,y:y-78,width:PW-80,height:76,color:PALE,borderColor:LINE,borderWidth:.8});
  lv(p1,"No. Quotation",q.quotation_no,54,y-18,220); lv(p1,"Tanggal Terbit",indoDate(q.created_at),310,y-18,210);
  lv(p1,"Berlaku Sampai",indoDate(q.valid_until),54,y-52,220); lv(p1,"Status",q.status,310,y-52,210); y-=103;
  y=section(p1,"Data Customer",y); lv(p1,"Sekolah / Instansi",br.institution_name,40,y,245); lv(p1,"PIC",br.pic_name,310,y,245); y-=52;
  lv(p1,"WhatsApp Customer",br.whatsapp,40,y,245); lv(p1,"Email",br.email||"-",310,y,245); y-=63;
  y=section(p1,"Program & Penawaran",y); lv(p1,"Program",program,40,y,245); lv(p1,"Tanggal Kegiatan",indoDate(br.trip_date),310,y,245); y-=52;
  lv(p1,"Jumlah Peserta",String(br.pax||0)+" peserta",40,y,245); lv(p1,"Sesi","Private / Shared sesuai order",310,y,245); y-=62;
  p1.drawRectangle({x:40,y:y-92,width:PW-80,height:94,color:GREEN});
  p1.drawText("TOTAL PENAWARAN",{x:56,y:y-27,size:9,font:bold,color:WHITE}); p1.drawText(rupiah(q.total),{x:56,y:y-61,size:26,font:bold,color:GOLD});
  const pp=br.pax?Number(q.total)/Number(br.pax):0; p1.drawText(rupiah(pp)+" / peserta",{x:PW-220,y:y-56,size:12,font:bold,color:WHITE}); y-=117;
  if(pkg&&pkg.sales_public_summary){ y=section(p1,"Ringkasan Program",y); lines(p1,pkg.sales_public_summary,40,y,9.5,PW-80,reg,INK,13,8); }

  y=PH-132; const p2=base("FASILITAS & KETENTUAN","Rincian yang diterima peserta",2);
  y=section(p2,"Fasilitas Program",y); const facilities=Array.isArray(pkg&&pkg.facilities)?pkg.facilities:[];
  if(facilities.length){ for(let i=0;i<facilities.length;i++){ if(y<100) break; p2.drawText(String(i+1)+".",{x:44,y,size:9,font:bold,color:GREEN}); y=lines(p2,facilities[i],62,y,9.2,PW-105,reg,INK,12,3)-3; } }
  else y=lines(p2,"Fasilitas mengikuti paket aktif dan rincian quotation.",44,y,9.5,PW-88);
  y-=8; y=section(p2,"Durasi & Pelaksanaan",y); lv(p2,"Durasi",(pkg&&pkg.duration_text)||"+/- 2 jam",40,y,245); lv(p2,"Meeting Point",br.meeting_point||"Dikoordinasikan dengan Sales",310,y,245); y-=58;
  y=section(p2,"Ketentuan",y); const terms=Array.isArray(pkg&&pkg.public_terms)&&pkg.public_terms.length?pkg.public_terms:String(q.terms||"").split(/\n+/).filter(Boolean);
  for(let i=0;i<Math.min(terms.length,10);i++){ if(y<95) break; p2.drawText("-",{x:44,y,size:9,font:bold,color:GREEN}); y=lines(p2,terms[i],58,y,9.1,PW-100,reg,INK,12,4)-3; }
  if(q.notes_customer&&y>145){ y-=8; y=section(p2,"Catatan",y); lines(p2,q.notes_customer,40,y,9.2,PW-80,reg,INK,12,6); }

  y=PH-132; const p3=base("KONFIRMASI & BOOKING","Kontak Sales dan pengesahan Manager",3);
  y=section(p3,"Alur Booking",y); const flow=Array.isArray(pkg&&pkg.booking_flow)&&pkg.booking_flow.length?pkg.booking_flow:["Konfirmasi paket dan jumlah peserta","Tentukan tanggal kegiatan","Quotation dikonfirmasi customer","Pembayaran/DP sesuai ketentuan","Tim GMU EduTrans menyiapkan operasional"];
  for(let i=0;i<Math.min(flow.length,8);i++){ p3.drawCircle({x:53,y:y+3,size:10,color:GREEN}); p3.drawText(String(i+1),{x:50.2,y:y-0.5,size:7.5,font:bold,color:WHITE}); y=lines(p3,flow[i],72,y,9.4,PW-115,reg,INK,12,3)-7; }
  y-=4; y=section(p3,"Informasi & Booking",y); p3.drawRectangle({x:40,y:y-122,width:PW-80,height:124,color:PALE,borderColor:LINE,borderWidth:.8});
  lv(p3,"Sales",sales.full_name||"Sales GMU EduTrans",56,y-22,280); lv(p3,"WhatsApp Sales",sales.phone||COMPANY_WA,56,y-58,280);
  p3.drawText("Kontak perusahaan: "+COMPANY_WA,{x:56,y:y-94,size:8,font:reg,color:MUTED}); p3.drawImage(qr,{x:PW-158,y:y-108,width:94,height:94}); y-=150;
  y=section(p3,"Pengesahan GMU EduTrans",y); p3.drawText("Disetujui dan diterbitkan oleh:",{x:40,y,size:9,font:reg,color:MUTED});
  p3.drawImage(sig,{x:40,y:y-92,width:120,height:91}); p3.drawText(MANAGER_NAME,{x:40,y:y-112,size:11,font:bold,color:INK}); p3.drawText("Manager GMU EduTrans",{x:40,y:y-128,size:9,font:reg,color:GREEN});
  p3.drawText("Dokumen ini sah secara elektronik tanpa cap perusahaan.",{x:310,y:y-112,size:8.2,font:reg,color:MUTED});
  p3.drawText("Nomor quotation dan data order berasal dari sistem ERP GMU EduTrans.",{x:310,y:y-128,size:8.2,font:reg,color:MUTED});
  pdf.setTitle("Quotation "+clean(q.quotation_no)+" - GMU EduTrans"); pdf.setAuthor("GMU EduTrans - PT Garsyani Multi Usaha"); pdf.setCreator("GMU EduTrans Auto Quotation");
  return await pdf.save();
}

async function salesList(userId){
  const b=await sb.from("bookings").select("id,booking_no").eq("sales_id",userId).order("created_at",{ascending:false}).limit(300); if(b.error) throw b.error;
  const ids=(b.data||[]).map(x=>String(x.id)); if(!ids.length) return [];
  const map=new Map((b.data||[]).map(x=>[String(x.id),String(x.booking_no||"-")]));
  const d=await sb.from("documents").select("id,booking_id,document_type,document_no,status,customer_title,storage_bucket,storage_path,file_name,generated_at").in("booking_id",ids).eq("document_type","Quotation").order("generated_at",{ascending:false}).limit(200); if(d.error) throw d.error;
  const out=[];
  for(const x of d.data||[]){
    let url=""; if(x.storage_bucket==="gmu-trip-documents"&&x.storage_path){ const s=await sb.storage.from("gmu-trip-documents").createSignedUrl(x.storage_path,3600); url=s.data?.signedUrl||""; }
    out.push({id:x.id,booking_id:x.booking_id,booking_no:map.get(String(x.booking_id))||"-",title:x.customer_title||"Quotation GMU EduTrans",document_type:x.document_type,status:x.status,file_url:url,file_name:x.file_name||"quotation.pdf",generated_at:x.generated_at});
  }
  return out;
}

async function ensureSalesPdfs(au){
  const br=await sb.from("booking_requests").select("id").eq("assigned_sales",au.user.id).limit(150); if(br.error) throw br.error;
  const requestIds=(br.data||[]).map(x=>x.id); if(!requestIds.length) return;
  const qs=await sb.from("quotations").select("id,quotation_no,status").in("booking_request_id",requestIds).in("status",["DRAFT","SENT"]).order("created_at",{ascending:false}).limit(30); if(qs.error) throw qs.error;
  if(!(qs.data||[]).length) return;
  const nos=(qs.data||[]).map(x=>x.quotation_no);
  const ds=await sb.from("documents").select("document_no").in("document_no",nos); if(ds.error) throw ds.error;
  const have=new Set((ds.data||[]).map(x=>String(x.document_no)));
  for(const q of qs.data||[]){
    if(have.has(String(q.quotation_no))) continue;
    try{
      const ctx=await officialQuoteContext(q.id,au);
      const bytes=await makeOfficialQuotationPdf(ctx);
      const revisionNo=Number(ctx.q.revision_no||0);
      await persist(bytes,{booking_code:ctx.br.booking_code,kind:"Quotation",title:revisionNo>0?"Quotation Revision R"+revisionNo+" - GMU EduTrans":"Quotation GMU EduTrans",document_no:ctx.q.quotation_no,booking_request_id:ctx.br.id,booking_id:ctx.q.booking_id},String(ctx.q.status).toUpperCase()==="SENT");
    }catch(e){ console.error("ensure Sales PDF",q.id,e); }
  }
}

async function makePdf(kind,docNo,customer,meta,items,notes,terms) {
  const pdf=await PDFDocument.create(), reg=await pdf.embedFont(StandardFonts.Helvetica), bold=await pdf.embedFont(StandardFonts.HelveticaBold), logo=await pdf.embedPng(b64bytes(LOGO_B64));
  const PW=PageSizes.A4[0], PH=PageSizes.A4[1]; let page=null, y=0;
  function newPage(){
    page=pdf.addPage(PageSizes.A4);
    page.drawRectangle({x:0,y:PH-12,width:PW,height:12,color:GREEN});
    page.drawImage(logo,{x:42,y:PH-103,width:136,height:75});
    page.drawText(clean(kind).toUpperCase(),{x:PW-248,y:PH-58,size:22,font:bold,color:GREEN});
    page.drawText(clean(docNo),{x:PW-248,y:PH-80,size:10,font:bold,color:INK});
    page.drawText("Educational Trip & Educational Tourism Operator",{x:42,y:PH-118,size:8.5,font:reg,color:MUTED});
    y=PH-145;
  }
  function ensure(n){ if(y-n<64) newPage(); }
  function label(x,yy,t){ page.drawText(clean(t).toUpperCase(),{x:x,y:yy,size:7.5,font:bold,color:MUTED}); }
  function val(x,yy,t,size,max){ const ls=wrap(reg,size,t,max); ls.slice(0,3).forEach(function(s,i){page.drawText(s,{x:x,y:yy-i*(size+3),size:size,font:reg,color:INK});}); }
  function section(t){ ensure(34); page.drawText(clean(t),{x:42,y:y,size:12,font:bold,color:GREEN}); page.drawLine({start:{x:42,y:y-7},end:{x:PW-42,y:y-7},thickness:.7,color:LINE}); y-=28; }
  function para(t){ const ls=wrap(reg,9.5,t,PW-84); ls.forEach(function(s){ensure(16);page.drawText(s,{x:42,y:y,size:9.5,font:reg,color:INK});y-=13;});y-=5; }

  newPage();
  section("Customer");
  label(42,y,"Institution / Customer"); val(42,y-13,customer.name,10,245);
  label(320,y,"PIC"); val(320,y-13,customer.pic,10,230); y-=48;
  label(42,y,"Contact"); val(42,y-13,[customer.whatsapp,customer.email].filter(Boolean).join(" | "),9,245);
  label(320,y,"Address"); val(320,y-13,[customer.address,customer.city].filter(Boolean).join(", "),9,230); y-=55;

  section("Trip Information");
  label(42,y,"Program"); val(42,y-13,meta.program,10,245);
  label(320,y,"Trip Date"); val(320,y-13,indoDate(meta.trip_date),10,230); y-=45;
  label(42,y,"Participants"); val(42,y-13,String(meta.pax||0)+" pax"+(meta.companion_pax?" + "+String(meta.companion_pax)+" companion":""),10,245);
  label(320,y,"Meeting Point"); val(320,y-13,meta.meeting_point||"-",10,230); y-=55;

  if(kind!=="Payment Receipt"){
    section("Commercial Details");
    page.drawRectangle({x:42,y:y-20,width:PW-84,height:22,color:GREEN});
    [["Description",49],["Qty",309],["Unit",359],["Unit Price",408],["Amount",484]].forEach(function(e){page.drawText(e[0],{x:e[1],y:y-13,size:7.5,font:bold,color:WHITE});});
    y-=29;
    for(const it of items||[]){
      const desc=wrap(reg,8.5,it.description,250), rowH=Math.max(25,desc.length*11+8); ensure(rowH+16);
      desc.forEach(function(s,i){page.drawText(s,{x:49,y:y-i*11,size:8.5,font:reg,color:INK});});
      page.drawText(clean(it.qty),{x:310,y:y,size:8.5,font:reg,color:INK});
      page.drawText(clean(it.unit),{x:360,y:y,size:8.5,font:reg,color:INK});
      const up=rupiah(it.unit_price), am=rupiah(it.amount);
      page.drawText(up,{x:472-reg.widthOfTextAtSize(up,8.3),y:y,size:8.3,font:reg,color:INK});
      page.drawText(am,{x:553-bold.widthOfTextAtSize(am,8.3),y:y,size:8.3,font:bold,color:INK});
      page.drawLine({start:{x:42,y:y-rowH+9},end:{x:PW-42,y:y-rowH+9},thickness:.5,color:LINE});
      y-=rowH;
    }
    y-=10;
    [["Subtotal",meta.subtotal],["Discount",meta.discount],["Tax",meta.tax],["TOTAL",meta.total]].forEach(function(e){
      ensure(22); const big=e[0]==="TOTAL", f=big?bold:reg, sz=big?11:9, s=rupiah(e[1]);
      page.drawText(e[0],{x:390,y:y,size:big?10:8.5,font:f,color:big?GREEN:MUTED});
      page.drawText(s,{x:553-f.widthOfTextAtSize(s,sz),y:y,size:sz,font:f,color:big?GREEN:INK}); y-=big?21:16;
    });
  } else {
    section("Payment Details");
    [["Payment Type",meta.payment_type],["Payment Date",indoDate(meta.payment_date)],["Method",meta.method||"-"],["Amount",rupiah(meta.amount)]].forEach(function(e,i){
      const bx=i%2===0?42:300, by=y-Math.floor(i/2)*64;
      page.drawRectangle({x:bx,y:by-45,width:253,height:52,color:PALE,borderColor:LINE,borderWidth:.7});
      label(bx+12,by-14,e[0]); page.drawText(clean(e[1]),{x:bx+12,y:by-33,size:e[0]==="Amount"?13:10,font:e[0]==="Amount"?bold:reg,color:e[0]==="Amount"?GREEN:INK});
    });
    y-=145;
    page.drawRectangle({x:42,y:y-70,width:PW-84,height:78,color:GREEN});
    page.drawText("PAYMENT RECEIVED",{x:58,y:y-24,size:10,font:bold,color:WHITE});
    page.drawText(rupiah(meta.amount),{x:58,y:y-52,size:24,font:bold,color:GOLD}); y-=100;
  }

  const statusItems = kind==="Quotation" ? [["Status",meta.status],["Valid Until",indoDate(meta.valid_until)]] : kind==="Invoice" ? [["Status",meta.status],["Issue Date",indoDate(meta.issue_date)],["Due Date",indoDate(meta.due_date)]] : [];
  if(statusItems.length){
    ensure(50); page.drawRectangle({x:42,y:y-35,width:PW-84,height:43,color:PALE,borderColor:LINE,borderWidth:.7});
    let sx=54; statusItems.forEach(function(e){label(sx,y-8,e[0]);page.drawText(clean(e[1]||"-"),{x:sx,y:y-24,size:9,font:bold,color:INK});sx+=kind==="Invoice"?155:245;}); y-=58;
  }
  if(notes){section("Customer Notes");para(notes);}
  if(terms){section("Terms & Conditions");para(terms);}
  ensure(72);
  page.drawText("Thank you for trusting GMU EduTrans.",{x:42,y:y-4,size:11,font:bold,color:GREEN});
  page.drawText("More Than a Trip, It's a Learning Journey.",{x:42,y:y-21,size:9,font:reg,color:MUTED});
  page.drawText("WhatsApp +62 877-8390-6545 | gmu.edutrans@gmail.com | @gmu.edutrans",{x:42,y:y-39,size:8,font:reg,color:MUTED});

  const pages=pdf.getPages();
  pages.forEach(function(p,i){const w=p.getWidth();p.drawLine({start:{x:42,y:39},end:{x:w-42,y:39},thickness:.7,color:LINE});p.drawText("GMU EduTrans - Brand of PT Garsyani Multi Usaha",{x:42,y:24,size:7.5,font:reg,color:MUTED});const tx="Page "+String(i+1)+" / "+String(pages.length);p.drawText(tx,{x:w-42-reg.widthOfTextAtSize(tx,7.5),y:24,size:7.5,font:reg,color:MUTED});});
  pdf.setTitle(kind+" "+clean(docNo)+" - GMU EduTrans"); pdf.setAuthor("GMU EduTrans - PT Garsyani Multi Usaha"); pdf.setCreator("GMU EduTrans ERP");
  return await pdf.save();
}
async function audit(userId,recordId,message,data){
  const r=await sb.from("audit_logs").insert({user_id:userId,action:"GENERATE_CUSTOMER_PDF",table_name:"documents",record_id:String(recordId||""),message:message,new_data:data||null});
  if(r.error) console.error("audit log error",r.error.message);
}
async function persist(bytes,doc,published=true){
  const path="customer/"+pathPart(doc.booking_code)+"/"+pathPart(doc.kind.toLowerCase())+"/"+pathPart(doc.document_no)+".pdf";
  const up=await sb.storage.from("gmu-trip-documents").upload(path,bytes,{contentType:"application/pdf",cacheControl:"3600",upsert:true}); if(up.error) throw up.error;
  const now=new Date().toISOString();
  const payload={booking_id:doc.booking_id||null,booking_request_id:doc.booking_request_id||null,document_type:doc.kind,document_no:doc.document_no,status:published?"Published":"Draft",customer_visible:published,customer_title:doc.title,storage_bucket:"gmu-trip-documents",storage_path:path,file_name:pathPart(doc.document_no)+".pdf",mime_type:"application/pdf",published_at:published?now:null,generated_at:now};
  const r=await sb.from("documents").upsert(payload,{onConflict:"document_no"}).select("id,booking_id,document_no,document_type,status,customer_visible,published_at,storage_bucket,storage_path,file_name,generated_at,customer_title").single(); if(r.error) throw r.error;
  const s=await sb.storage.from("gmu-trip-documents").createSignedUrl(path,3600);
  return {...r.data,file_url:s.data?.signedUrl||""};
}

Deno.serve(async function(req){
  if(req.method!=="POST") return respond(405,{error:"Method not allowed"});
  if(!URL||!SECRET) return respond(500,{error:"Server configuration error"});
  let body; try{body=await req.json();}catch(_){return respond(400,{error:"Format data tidak valid"});}
  const action=String(body&&body.action||"").toLowerCase(), id=body&&body.id;
  let au=null;
  if(body&&typeof body.internal_token==="string"){
    au=await authorizeInternalToken(body.internal_token);
    if(!au) return respond(401,{error:"Unauthorized"});
  } else {
    au=await authorize(req); if(au.error) return au.error;
  }
  if(action==="sales_list"){
    if(au.role!=="Sales") return respond(403,{error:"Khusus akun Sales"});
    try {
      await ensureSalesPdfs(au);
      return respond(200,{ok:true,items:await salesList(au.user.id)});
    } catch(e){
      console.error("sales list",e);
      return respond(500,{error:"Gagal menyiapkan dokumen Sales"});
    }
  }
  if(!["quotation","invoice","receipt"].includes(action)||!isUuid(id)) return respond(400,{error:"action / id tidak valid"});
  try{
    if(action==="quotation"){
      const mode=String(body&&body.mode||"publish").toLowerCase();
      if(!["auto","publish"].includes(mode)) return respond(400,{error:"Mode quotation tidak valid"});
      if(mode==="publish" && !ALLOWED.has(au.role)) return respond(403,{error:"Hanya Owner / Manager yang dapat menerbitkan quotation"});
      if(mode==="auto" && !SALES_ALLOWED.has(au.role)) return respond(403,{error:"Tidak memiliki akses membuat PDF quotation"});
      const ctx=await officialQuoteContext(id,au);
      const revisionNo=Number(ctx.q.revision_no||0);
      const bytes=await makeOfficialQuotationPdf(ctx);
      const publishNow=mode==="publish";
      const doc=await persist(bytes,{booking_code:ctx.br.booking_code,kind:"Quotation",title:revisionNo>0?"Quotation Revision R"+revisionNo+" - GMU EduTrans":"Quotation GMU EduTrans",document_no:ctx.q.quotation_no,booking_request_id:ctx.br.id,booking_id:ctx.q.booking_id},publishNow);
      if(publishNow && ctx.q.status==="DRAFT") await sb.from("quotations").update({status:"SENT",sent_at:new Date().toISOString(),sent_by:au.user.id}).eq("id",id);
      await audit(au.user.id,id,(publishNow?"Published":"Auto-generated")+" quotation PDF "+ctx.q.quotation_no,doc);
      return respond(200,{ok:true,mode,document:doc,quotation_no:ctx.q.quotation_no});
    }
    if(action==="invoice"){
      if(!ALLOWED.has(au.role)) return respond(403,{error:"Hanya Owner / Manager yang dapat menerbitkan invoice"});
      const inv=await sb.from("invoices").select("*").eq("id",id).single(); if(inv.error) throw inv.error;
      const br=await getRequest(inv.data.booking_request_id), program=await getProgram(br);
      const it=await sb.from("invoice_items").select("description,qty,unit,unit_price,amount").eq("invoice_id",id).order("sort_order"); if(it.error) throw it.error;
      const bytes=await makePdf("Invoice",inv.data.invoice_no,{name:br.institution_name,pic:br.pic_name,whatsapp:br.whatsapp,email:br.email,address:br.address,city:br.city},Object.assign({},inv.data,{program:program,trip_date:br.trip_date,pax:br.pax,companion_pax:br.companion_pax,meeting_point:br.meeting_point}),it.data||[],inv.data.notes_customer,null);
      const doc=await persist(bytes,{booking_code:br.booking_code,kind:"Invoice",title:"Invoice GMU EduTrans",document_no:inv.data.invoice_no,booking_request_id:br.id,booking_id:inv.data.booking_id});
      if(inv.data.status==="DRAFT") await sb.from("invoices").update({status:"ISSUED",issued_at:new Date().toISOString()}).eq("id",id);
      await audit(au.user.id,id,"Generated invoice PDF "+inv.data.invoice_no,doc);
      return respond(200,{ok:true,document:doc});
    }
    if(!ALLOWED.has(au.role)) return respond(403,{error:"Hanya Owner / Manager yang dapat menerbitkan payment receipt"});
    const pay=await sb.from("payments").select("*").eq("id",id).single(); if(pay.error) throw pay.error;
    const book=await sb.from("bookings").select("*").eq("id",pay.data.booking_id).single(); if(book.error) throw book.error;
    const cust=await sb.from("customers").select("name,pic_name,whatsapp,email,address").eq("id",book.data.customer_id).maybeSingle();
    const reqq=await sb.from("booking_requests").select("*").eq("converted_booking_id",book.data.id).order("created_at",{ascending:false}).limit(1).maybeSingle();
    const br=reqq.data, receiptNo="RCT-"+pathPart(book.data.booking_no)+"-"+String(pay.data.id).slice(0,8).toUpperCase();
    const bytes=await makePdf("Payment Receipt",receiptNo,{name:(cust.data&&cust.data.name)||(br&&br.institution_name)||"Customer",pic:(cust.data&&cust.data.pic_name)||(br&&br.pic_name)||"",whatsapp:(cust.data&&cust.data.whatsapp)||(br&&br.whatsapp)||"",email:(cust.data&&cust.data.email)||(br&&br.email)||"",address:(cust.data&&cust.data.address)||(br&&br.address)||"",city:(br&&br.city)||""},{program:book.data.program_name,trip_date:book.data.trip_date,pax:book.data.pax,companion_pax:(br&&br.companion_pax)||0,meeting_point:book.data.meeting_point,payment_type:pay.data.payment_type,payment_date:pay.data.payment_date,method:pay.data.method,amount:pay.data.amount},[],pay.data.notes,null);
    const doc=await persist(bytes,{booking_code:(br&&br.booking_code)||book.data.booking_no,kind:"Payment Receipt",title:"Payment Receipt - "+String(pay.data.payment_type),document_no:receiptNo,booking_request_id:(br&&br.id)||null,booking_id:book.data.id});
    await audit(au.user.id,id,"Generated payment receipt PDF "+receiptNo,doc);
    return respond(200,{ok:true,document:doc});
  }catch(e){console.error("commercial pdf error",e);return respond(500,{error:"Gagal membuat dokumen PDF"});}
});