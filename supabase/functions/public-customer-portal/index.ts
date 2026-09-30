import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
function getSecretKey(): string {const json=Deno.env.get("SUPABASE_SECRET_KEYS");if(json){try{const parsed=JSON.parse(json);if(parsed?.default)return parsed.default}catch{}}return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??""}
const SECRET_KEY=getSecretKey();
const WINDOW_MS=10*60*1000,MAX_ATTEMPTS=20;
const rate=new Map<string,{count:number;reset:number}>();
const STATUS_LABELS:Record<string,string>={NEW_REQUEST:"Pengajuan diterima",VERIFICATION:"Sedang diverifikasi",QUOTATION:"Penawaran disiapkan",WAITING_DP:"Menunggu DP",CONFIRMED:"Booking terkonfirmasi",PREPARATION:"Persiapan perjalanan",READY:"Siap berangkat",ON_TRIP:"Perjalanan berlangsung",WAITING_PAYMENT:"Menunggu pelunasan",PAID:"Pembayaran lunas",COMPLETED:"Perjalanan selesai",CLOSED:"Booking ditutup",REJECTED:"Pengajuan tidak dilanjutkan"};
function originAllowed(origin:string|null){if(!origin)return true;if(origin==="https://edutrans.garsyanimultiusaha.site")return true;if(origin==="https://gtgnwasijweewmaubvyg.supabase.co")return true;if(/^https:\/\/gmu-edutrans-public-[a-z0-9-]+\.vercel\.app$/i.test(origin))return true;if(/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/i.test(origin))return true;return false}
function cors(origin:string|null){return{"Access-Control-Allow-Origin":origin&&originAllowed(origin)?origin:"https://edutrans.garsyanimultiusaha.site","Access-Control-Allow-Headers":"content-type, x-client-info, apikey, authorization","Access-Control-Allow-Methods":"POST, OPTIONS","Vary":"Origin"}}
function respond(status:number,body:unknown,origin:string|null){return new Response(JSON.stringify(body),{status,headers:{...cors(origin),"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store","X-Content-Type-Options":"nosniff","Referrer-Policy":"no-referrer"}})}
function bookingCode(v:unknown){return typeof v==="string"&&/^GMU-\d{4}-\d{5}$/.test(v.trim().toUpperCase())?v.trim().toUpperCase():null}
function uuid(v:unknown){return typeof v==="string"&&/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v.trim())?v.trim():null}
async function get(path:string){return fetch(SUPABASE_URL+path,{headers:{apikey:SECRET_KEY,"Content-Type":"application/json"}})}
async function rows(path:string){const r=await get(path);if(!r.ok){const t=await r.text().catch(()=>"");console.error("db error",r.status,t.slice(0,500));throw new Error("db:"+r.status)}const data=await r.json();return Array.isArray(data)?data:[]}
function customerDocStatusAllowed(v:unknown){return !["DRAFT","INTERNAL","REJECTED","CANCELLED"].includes(String(v??"").trim().toUpperCase())}
async function lifecycleStatus(requestId:string){const r=await fetch(SUPABASE_URL+"/rest/v1/rpc/internal_customer_lifecycle_status",{method:"POST",headers:{apikey:SECRET_KEY,"Content-Type":"application/json"},body:JSON.stringify({p_booking_request_id:requestId})});if(!r.ok){console.error("lifecycle status error",r.status,await r.text());return null}return await r.json().catch(()=>null)}
async function authenticateStaff(req:Request){
  const h=req.headers.get("Authorization")||"";
  if(!h.startsWith("Bearer "))return null;
  const r=await fetch(SUPABASE_URL+"/auth/v1/user",{headers:{apikey:SECRET_KEY,Authorization:h}});
  if(!r.ok)return null;
  const user=await r.json().catch(()=>null);
  if(!user?.id)return null;
  const ps=await rows("/rest/v1/profiles?id=eq."+encodeURIComponent(user.id)+"&is_active=eq.true&select=id,role,full_name&limit=1");
  const profile=ps[0];
  if(!profile)return null;
  return {user,profile};
}
async function serviceRpc(name:string,body:Record<string,unknown>){
  const r=await fetch(SUPABASE_URL+"/rest/v1/rpc/"+name,{
    method:"POST",
    headers:{apikey:SECRET_KEY,"Content-Type":"application/json"},
    body:JSON.stringify(body)
  });
  const t=await r.text();
  if(!r.ok){console.error("rpc",name,r.status,t.slice(0,800));throw new Error(name+":"+r.status)}
  if(!t)return null;
  try{return JSON.parse(t)}catch{return t}
}
async function xenditConfig(){
  const x=await serviceRpc("gmu_xendit_backend_config",{});
  return x&&typeof x==="object"?x:{};
}
async function verifyInternalXenditToken(token:unknown){
  if(typeof token!=="string"||token.length<32||token.length>256)return false;
  const x=await serviceRpc("gmu_verify_internal_xendit_worker_token",{p_token:token});
  return x===true;
}
function safeIso(v:unknown){const s=String(v??"");return /^\d{4}-\d{2}-\d{2}T/.test(s)?s:null}
async function createXenditCheckout(invoiceId:string){
  const cfg:any=await xenditConfig();
  const secret=String(cfg?.secret_key||"");
  if(!secret)return {ok:false,ready:false,reason:"XENDIT_CREDENTIALS_MISSING"};

  const invoiceRows=await rows("/rest/v1/invoices?id=eq."+encodeURIComponent(invoiceId)+"&select="+encodeURIComponent("id,invoice_no,invoice_type,status,total,currency,due_date,booking_request_id,booking_id")+"&limit=1");
  const inv=invoiceRows[0];
  if(!inv)throw new Error("INVOICE_NOT_FOUND");
  if(!["ISSUED","PARTIAL","OVERDUE"].includes(String(inv.status)))throw new Error("INVOICE_NOT_PAYABLE");
  if(String(inv.currency||"IDR")!=="IDR")throw new Error("XENDIT_IDR_ONLY");

  const reqRows=await rows("/rest/v1/booking_requests?id=eq."+encodeURIComponent(inv.booking_request_id)+"&select="+encodeURIComponent("id,booking_code,institution_name,pic_name,whatsapp,email")+"&limit=1");
  const br=reqRows[0];
  if(!br)throw new Error("BOOKING_REQUEST_NOT_FOUND");

  const existingRows=await rows("/rest/v1/payment_gateway_orders?invoice_id=eq."+encodeURIComponent(inv.id)+"&provider=eq.XENDIT&status=in.(CREATED,PENDING)&select=*&order=created_at.desc&limit=1");
  const existing=existingRows[0];
  if(existing?.status==="PENDING"&&existing?.checkout_url&&(!existing.expires_at||new Date(existing.expires_at).getTime()>Date.now()+30000)){
    return {ok:true,ready:true,reused:true,order:existing,checkout_url:existing.checkout_url};
  }

  const created:any=await serviceRpc("internal_payment_gateway_create_order",{
    p_invoice_id:inv.id,
    p_channel_code:"XENDIT",
    p_idempotency_key:crypto.randomUUID()
  });
  const order=created?.order;
  if(!order?.id)throw new Error("PAYMENT_ORDER_CREATE_FAILED");
  if(order.status==="PENDING"&&order.checkout_url){
    return {ok:true,ready:true,reused:true,order,checkout_url:order.checkout_url};
  }

  const portalBase="https://edutrans.garsyanimultiusaha.site";
  const sessionBody:any={
    reference_id:String(order.order_no).slice(0,64),
    session_type:"PAY",
    mode:"PAYMENT_LINK",
    amount:Number(order.amount),
    currency:"IDR",
    country:"ID",
    capture_method:"AUTOMATIC",
    locale:"en",
    description:(String(inv.invoice_type)==="DP"?"Down Payment ":"Pelunasan ")+String(inv.invoice_no)+" - GMU EduTrans",
    success_return_url:portalBase+"/?payment=success&booking="+encodeURIComponent(String(br.booking_code||"")),
    cancel_return_url:portalBase+"/?payment=cancel&booking="+encodeURIComponent(String(br.booking_code||"")),
    metadata:{
      payment_order_id:String(order.id),
      invoice_no:String(inv.invoice_no),
      booking_code:String(br.booking_code)
    }
  };

  const xr=await fetch("https://api.xendit.co/sessions",{
    method:"POST",
    headers:{
      "Authorization":"Basic "+btoa(secret+":"),
      "Content-Type":"application/json"
    },
    body:JSON.stringify(sessionBody)
  });
  const raw=await xr.text();
  let xd:any={};try{xd=raw?JSON.parse(raw):{}}catch{xd={raw:raw.slice(0,1500)}}
  if(!xr.ok){
    await serviceRpc("internal_payment_gateway_mark_terminal",{
      p_order_id:order.id,p_status:"FAILED",p_provider_status:"SESSION_CREATE_FAILED",
      p_reason:"Xendit HTTP "+xr.status,p_provider_response:xd
    }).catch(()=>null);
    console.error("xendit session create failed",xr.status,raw.slice(0,1000));
    throw new Error("XENDIT_SESSION_CREATE_FAILED");
  }

  const sessionId=String(xd.payment_session_id||"");
  const checkoutUrl=String(xd.payment_link_url||"");
  if(!sessionId||!checkoutUrl)throw new Error("XENDIT_SESSION_RESPONSE_INCOMPLETE");

  const pending:any=await serviceRpc("internal_payment_gateway_mark_pending",{
    p_order_id:order.id,
    p_provider_order_id:sessionId,
    p_provider_transaction_id:xd.payment_id||null,
    p_provider_status:xd.status||"ACTIVE",
    p_payment_code:null,
    p_qr_string:null,
    p_qr_url:null,
    p_checkout_url:checkoutUrl,
    p_expires_at:safeIso(xd.expires_at),
    p_provider_response:xd
  });

  return {
    ok:true,ready:true,reused:false,
    order:pending?.order||order,
    checkout_url:checkoutUrl,
    expires_at:xd.expires_at||null,
    provider_order_id:sessionId
  };
}
async function handleXenditWebhook(req:Request,body:any,origin:string|null){
  const cfg:any=await xenditConfig();
  const expected=String(cfg?.webhook_token||"");
  if(!expected)return respond(503,{error:"Xendit webhook belum dikonfigurasi."},origin);
  const got=req.headers.get("x-callback-token")||"";
  if(got!==expected)return respond(401,{error:"Webhook tidak valid."},origin);

  const event=String(body?.event||"");
  const data=body?.data||{};
  const sessionId=String(data?.payment_session_id||"");
  const referenceId=String(data?.reference_id||"");
  let found:any[]=sessionId?await rows("/rest/v1/payment_gateway_orders?provider=eq.XENDIT&provider_order_id=eq."+encodeURIComponent(sessionId)+"&select=*&limit=1"):[];
  if(!found.length&&referenceId){
    found=await rows("/rest/v1/payment_gateway_orders?provider=eq.XENDIT&order_no=eq."+encodeURIComponent(referenceId)+"&select=*&limit=1");
  }
  const order=found[0];
  if(!order)return respond(200,{ok:true,ignored:"payment_order_not_found"},origin);

  if(event==="payment_session.completed"){
    if(String(data?.currency||"IDR")!==String(order.currency||"IDR")){
      return respond(409,{error:"Currency mismatch."},origin);
    }
    const amount=Number(data?.amount||0);
    const result=await serviceRpc("internal_payment_gateway_finalize_paid",{
      p_order_id:order.id,
      p_provider_transaction_id:data?.payment_id||data?.payment_request_id||null,
      p_provider_status:data?.status||"COMPLETED",
      p_paid_amount:amount,
      p_paid_at:safeIso(data?.updated)||safeIso(body?.created)||new Date().toISOString(),
      p_reference_no:data?.payment_id||referenceId||order.order_no,
      p_provider_response:body
    });
    return respond(200,{ok:true,event,result},origin);
  }

  if(event==="payment_session.expired"){
    const result=await serviceRpc("internal_payment_gateway_mark_terminal",{
      p_order_id:order.id,p_status:"EXPIRED",
      p_provider_status:data?.status||"EXPIRED",
      p_reason:"Xendit payment session expired",
      p_provider_response:body
    });
    return respond(200,{ok:true,event,result},origin);
  }

  if(event==="payment.capture"){
    const amount=Number(data?.request_amount||data?.capture_amount||0);
    if(amount<=0)return respond(200,{ok:true,ignored:"capture_without_amount"},origin);
    const result=await serviceRpc("internal_payment_gateway_finalize_paid",{
      p_order_id:order.id,
      p_provider_transaction_id:data?.payment_id||null,
      p_provider_status:data?.status||"SUCCEEDED",
      p_paid_amount:amount,
      p_paid_at:safeIso(data?.updated)||safeIso(body?.created)||new Date().toISOString(),
      p_reference_no:data?.payment_id||referenceId||order.order_no,
      p_provider_response:body
    });
    return respond(200,{ok:true,event,result},origin);
  }

  return respond(200,{ok:true,ignored:"unsupported_event",event},origin);
}


Deno.serve(async(req:Request)=>{
  const origin=req.headers.get("origin");
  if(req.method==="OPTIONS"){
    if(!originAllowed(origin))return new Response(null,{status:403,headers:cors(origin)});
    return new Response("ok",{headers:cors(origin)});
  }
  if(req.method!=="POST")return respond(405,{error:"Method not allowed"},origin);
  if(!SUPABASE_URL||!SECRET_KEY)return respond(500,{error:"Server configuration error"},origin);
  const len=Number(req.headers.get("content-length")||"0");
  if(len>65536)return respond(413,{error:"Payload terlalu besar."},origin);
  let body:any;try{body=await req.json()}catch{return respond(400,{error:"Format data tidak valid."},origin)}

  const event=String(body?.event||"");
  if(req.headers.get("x-callback-token")||event.startsWith("payment_session.")||event.startsWith("payment.")){
    try{return await handleXenditWebhook(req,body,origin)}
    catch(e){console.error("xendit webhook exception",e instanceof Error?e.message:String(e));return respond(500,{error:"Webhook gagal diproses."},origin)}
  }

  const action=String(body?.action||"portal").toLowerCase();
  if(action==="sales_xendit_checkout"){
    try{
      const staff=await authenticateStaff(req);
      if(!staff)return respond(401,{error:"Session tidak valid."},origin);
      const role=String(staff.profile?.role||"");
      if(!["Sales","Owner","Manager","Manager EduTrans","Director","Direktur"].includes(role))return respond(403,{error:"Akses ditolak."},origin);
      const invoiceId=uuid(body?.invoice_id);if(!invoiceId)return respond(400,{error:"Invoice ID tidak valid."},origin);
      const invRows=await rows("/rest/v1/invoices?id=eq."+encodeURIComponent(invoiceId)+"&select=id,booking_request_id&limit=1");
      const inv=invRows[0];if(!inv)return respond(404,{error:"Invoice tidak ditemukan."},origin);
      const brRows=await rows("/rest/v1/booking_requests?id=eq."+encodeURIComponent(inv.booking_request_id)+"&select=id,assigned_sales&limit=1");
      const br=brRows[0];if(!br)return respond(404,{error:"Booking tidak ditemukan."},origin);
      if(role==="Sales"&&String(br.assigned_sales||"")!==String(staff.user.id))return respond(403,{error:"Invoice bukan milik Sales ini."},origin);
      const result=await createXenditCheckout(invoiceId);
      return respond(result?.ready?200:503,result,origin);
    }catch(e){
      console.error("sales xendit checkout exception",e instanceof Error?e.message:String(e));
      return respond(500,{error:e instanceof Error?e.message:"Gagal menyiapkan checkout Xendit."},origin);
    }
  }

  if(action==="xendit_create_internal"){
    try{
      if(!await verifyInternalXenditToken(body?.internal_token))return respond(401,{error:"Unauthorized"},origin);
      const invoiceId=uuid(body?.invoice_id);if(!invoiceId)return respond(400,{error:"Invoice ID tidak valid."},origin);
      const result=await createXenditCheckout(invoiceId);
      return respond(result?.ready?200:503,result,origin);
    }catch(e){
      console.error("xendit internal create exception",e instanceof Error?e.message:String(e));
      return respond(500,{error:e instanceof Error?e.message:"Gagal membuat checkout Xendit."},origin);
    }
  }

  if(!originAllowed(origin))return respond(403,{error:"Origin not allowed"},origin);
  const ip=(req.headers.get("x-forwarded-for")?.split(",")[0]||req.headers.get("cf-connecting-ip")||req.headers.get("x-real-ip")||"unknown").trim();
  const now=Date.now(),bucket=rate.get(ip);
  if(!bucket||bucket.reset<=now)rate.set(ip,{count:1,reset:now+WINDOW_MS});
  else{bucket.count+=1;if(bucket.count>MAX_ATTEMPTS)return respond(429,{error:"Terlalu banyak percobaan. Coba lagi beberapa menit."},origin)}
  const code=bookingCode(body?.booking_code),token=uuid(body?.access_token);if(!code||!token)return respond(400,{error:"Booking ID atau token akses tidak valid."},origin);
try{const brSelect=["id","booking_code","status","institution_name","trip_date","pax","companion_pax","participant_group","meeting_point","facilities_requested","programs(name)","custom_program","converted_booking_id","commercial_revision_required","commercial_revision_state","commercial_revision_reason","commercial_revision_version","freeze_exception_required","freeze_exception_state","freeze_window_level","freeze_exception_updated_at","created_at","updated_at"].join(",");const requestRows=await rows("/rest/v1/booking_requests?booking_code=eq."+encodeURIComponent(code)+"&access_token=eq."+encodeURIComponent(token)+"&select="+encodeURIComponent(brSelect)+"&limit=1");const br=requestRows[0];if(!br)return respond(404,{error:"Booking tidak ditemukan."},origin);
if(action==="xendit_checkout"){
  const invoiceId=uuid(body?.invoice_id);
  if(!invoiceId)return respond(400,{error:"Invoice ID tidak valid."},origin);
  const own=await rows("/rest/v1/invoices?id=eq."+encodeURIComponent(invoiceId)+"&booking_request_id=eq."+encodeURIComponent(br.id)+"&select=id,status,invoice_no&limit=1");
  if(!own[0])return respond(404,{error:"Invoice tidak ditemukan untuk booking ini."},origin);
  try{
    const result=await createXenditCheckout(invoiceId);
    return respond(result?.ready?200:503,result,origin);
  }catch(e){
    console.error("xendit checkout exception",e instanceof Error?e.message:String(e));
    return respond(500,{error:e instanceof Error?e.message:"Gagal membuat checkout Xendit."},origin);
  }
}const program=Array.isArray(br.programs)?br.programs?.[0]?.name:br.programs?.name;const lifecycle=await lifecycleStatus(String(br.id));const publicStatus=lifecycle?.lifecycle_status||br.status;const base={booking_code:br.booking_code,status:publicStatus,status_label:STATUS_LABELS[publicStatus]||publicStatus,workflow_status:br.status,payment_status:lifecycle?.payment_status??null,quotation_status:lifecycle?.quotation_status??null,operation_status:lifecycle?.trip_status??null,progress_percent:Number(lifecycle?.progress_percent??0),next_action:lifecycle?.next_action??null,commercial_revision:{required:Boolean(br.commercial_revision_required),state:br.commercial_revision_state||"NONE",message:br.commercial_revision_reason||null,version:Number(br.commercial_revision_version||0)},freeze_control:{required:Boolean(br.freeze_exception_required),state:br.freeze_exception_state||"NONE",level:br.freeze_window_level||null,message:Boolean(br.freeze_exception_required)?"Permintaan perubahan mendekati tanggal perjalanan sedang menunggu persetujuan internal.":null},institution_name:br.institution_name,program_name:program||br.custom_program||"Custom Educational Trip",trip_date:br.trip_date,pax:br.pax,companion_pax:br.companion_pax,participant_group:br.participant_group,meeting_point:br.meeting_point,facilities:Array.isArray(br.facilities_requested)?br.facilities_requested:[],created_at:br.created_at,updated_at:br.updated_at};
const [quotationRows,invoiceRows,requestDocumentRows,reminderRows,gatewayOrderRows]=await Promise.all([
 rows("/rest/v1/quotations?booking_request_id=eq."+encodeURIComponent(br.id)+"&status=in.(SENT,ACCEPTED,EXPIRED)&superseded_at=is.null&select="+encodeURIComponent("id,quotation_no,status,valid_until,currency,subtotal,discount,tax,total,notes_customer,terms,revision_no,sent_at,accepted_at,created_at,updated_at")+"&order=created_at.desc&limit=1"),
 rows("/rest/v1/invoices?booking_request_id=eq."+encodeURIComponent(br.id)+"&status=in.(ISSUED,PARTIAL,PAID,OVERDUE)&select="+encodeURIComponent("id,invoice_no,invoice_type,quotation_id,status,issue_date,due_date,currency,subtotal,discount,tax,total,source_amount,dp_percent,notes_customer,issued_at,paid_at,created_at,updated_at")+"&order=created_at.desc&limit=1"),
 rows("/rest/v1/documents?booking_request_id=eq."+encodeURIComponent(br.id)+"&customer_visible=eq.true&published_at=not.is.null&select="+encodeURIComponent("id,document_type,document_no,status,customer_title,storage_bucket,storage_path,file_name,mime_type,published_at,generated_at")+"&order=published_at.desc"),
 rows("/rest/v1/payment_reminders?booking_request_id=eq."+encodeURIComponent(br.id)+"&customer_visible=eq.true&status=in.(PENDING,SENT)&select="+encodeURIComponent("id,invoice_id,reminder_date,days_relative,status,channel,customer_message,created_at")+"&order=reminder_date.desc&limit=10"),
 rows("/rest/v1/payment_gateway_orders?booking_request_id=eq."+encodeURIComponent(br.id)+"&select="+encodeURIComponent("id,order_no,invoice_id,channel_code,provider,payment_type,amount,currency,status,payment_code,payment_aux_code,qr_string,qr_url,checkout_url,expires_at,paid_at,provider_status,created_at,updated_at")+"&order=created_at.desc&limit=1")]);
const quotation=quotationRows[0]??null,invoice=invoiceRows[0]??null;const [quotationItems,invoiceItems]=await Promise.all([quotation?rows("/rest/v1/quotation_items?quotation_id=eq."+encodeURIComponent(quotation.id)+"&select="+encodeURIComponent("description,qty,unit,unit_price,amount")+"&order=sort_order.asc,created_at.asc"):Promise.resolve([]),invoice?rows("/rest/v1/invoice_items?invoice_id=eq."+encodeURIComponent(invoice.id)+"&select="+encodeURIComponent("description,qty,unit,unit_price,amount")+"&order=sort_order.asc,created_at.asc"):Promise.resolve([])]);
let confirmedBooking:any=null,payments:any[]=[],tripRows:any[]=[],rundownRows:any[]=[],customerTripInfoRows:any[]=[],tripReportRows:any[]=[],feedbackRows:any[]=[],bookingDocumentRows:any[]=[];if(br.converted_booking_id){const id=encodeURIComponent(String(br.converted_booking_id));const results=await Promise.all([
 rows("/rest/v1/bookings?id=eq."+id+"&select="+encodeURIComponent("booking_no,program_name,trip_date,pax,price_per_pax,status,participant_group,meeting_point,facilities,created_at,updated_at")+"&limit=1"),
 rows("/rest/v1/payments?booking_id=eq."+id+"&verified_at=not.is.null&select="+encodeURIComponent("invoice_id,payment_type,amount,payment_date,method,reference_no,verified_at,created_at")+"&order=payment_date.asc,created_at.asc"),
 rows("/rest/v1/trips?booking_id=eq."+id+"&select="+encodeURIComponent("trip_status,operational_progress,started_at,completed_at,updated_at")+"&limit=1"),
 rows("/rest/v1/rundown_items?booking_id=eq."+id+"&audience=eq.CUSTOMER&customer_visible=eq.true&select="+encodeURIComponent("sort_order,activity_time,activity,location")+"&order=sort_order.asc,activity_time.asc"),
 rows("/rest/v1/trip_customer_info?booking_id=eq."+id+"&status=eq.PUBLISHED&select="+encodeURIComponent("meeting_point,meeting_time,departure_instruction,what_to_bring,customer_notes,public_contact_label,public_contact_phone,published_at,updated_at")+"&limit=1"),
 rows("/rest/v1/trip_reports?booking_id=eq."+id+"&status=eq.FINAL&customer_visible=eq.true&published_at=not.is.null&select="+encodeURIComponent("customer_summary,published_at,finalized_at")+"&limit=1"),
 rows("/rest/v1/customer_trip_feedback?booking_id=eq."+id+"&select="+encodeURIComponent("id,submitted_at")+"&limit=1"),
 rows("/rest/v1/documents?booking_id=eq."+id+"&customer_visible=eq.true&published_at=not.is.null&select="+encodeURIComponent("id,document_type,document_no,status,customer_title,storage_bucket,storage_path,file_name,mime_type,published_at,generated_at")+"&order=published_at.desc")]);confirmedBooking=results[0][0]??null;payments=results[1];tripRows=results[2];rundownRows=results[3];customerTripInfoRows=results[4];tripReportRows=results[5];feedbackRows=results[6];bookingDocumentRows=results[7]}
const quotationSafe=quotation?{quotation_no:quotation.quotation_no,status:quotation.status,valid_until:quotation.valid_until,currency:quotation.currency,subtotal:Number(quotation.subtotal||0),discount:Number(quotation.discount||0),tax:Number(quotation.tax||0),total:Number(quotation.total||0),notes_customer:quotation.notes_customer,terms:quotation.terms,sent_at:quotation.sent_at,accepted_at:quotation.accepted_at,revision_no:Number(quotation.revision_no||0),items:quotationItems.map((x:any)=>({description:x.description,qty:Number(x.qty||0),unit:x.unit,unit_price:Number(x.unit_price||0),amount:Number(x.amount||0)}))}:null;
const invoiceSafe=invoice?{invoice_no:invoice.invoice_no,invoice_type:invoice.invoice_type,status:invoice.status,issue_date:invoice.issue_date,due_date:invoice.due_date,currency:invoice.currency,subtotal:Number(invoice.subtotal||0),discount:Number(invoice.discount||0),tax:Number(invoice.tax||0),total:Number(invoice.total||0),source_amount:invoice.source_amount==null?null:Number(invoice.source_amount),dp_percent:invoice.dp_percent==null?null:Number(invoice.dp_percent),notes_customer:invoice.notes_customer,issued_at:invoice.issued_at,paid_at:invoice.paid_at,items:invoiceItems.map((x:any)=>({description:x.description,qty:Number(x.qty||0),unit:x.unit,unit_price:Number(x.unit_price||0),amount:Number(x.amount||0)}))}:null;
const pricePerPax=confirmedBooking?Number(confirmedBooking.price_per_pax||0):0,bookingPax=confirmedBooking?Number(confirmedBooking.pax||0):0,bookingTotal=confirmedBooking?pricePerPax*bookingPax:null;let paidAmount=0;const paymentItems=payments.map((p:any)=>{const amount=Number(p.amount||0);paidAmount+=String(p.payment_type)==="Refund"?-amount:amount;return{payment_type:p.payment_type,amount,payment_date:p.payment_date,method:p.method,reference_no:p.reference_no,verified_at:p.verified_at,created_at:p.created_at}});const financialTotal=quotationSafe?.total??bookingTotal??invoiceSafe?.source_amount??invoiceSafe?.total??null;const seen=new Set<string>();const documentSafe=[...requestDocumentRows,...bookingDocumentRows].filter((d:any)=>customerDocStatusAllowed(d.status)).filter((d:any)=>{if(seen.has(d.id))return false;seen.add(d.id);return true}).map((d:any)=>({id:d.id,title:d.customer_title||d.document_type,document_type:d.document_type,document_no:d.document_no,status:d.status,file_name:d.file_name,mime_type:d.mime_type,published_at:d.published_at,download_available:d.storage_bucket==="gmu-trip-documents"&&typeof d.storage_path==="string"&&d.storage_path.length>0}));
const gatewayOrder=gatewayOrderRows[0]??null;const gatewaySafe=gatewayOrder?{order_no:gatewayOrder.order_no,invoice_id:gatewayOrder.invoice_id,channel_code:gatewayOrder.channel_code,provider:gatewayOrder.provider,payment_type:gatewayOrder.payment_type,amount:Number(gatewayOrder.amount||0),currency:gatewayOrder.currency,status:gatewayOrder.status,payment_code:gatewayOrder.payment_code,payment_aux_code:gatewayOrder.payment_aux_code,qr_string:gatewayOrder.qr_string,qr_url:gatewayOrder.qr_url,checkout_url:gatewayOrder.checkout_url,expires_at:gatewayOrder.expires_at,paid_at:gatewayOrder.paid_at,provider_status:gatewayOrder.provider_status,created_at:gatewayOrder.created_at,updated_at:gatewayOrder.updated_at}:null;
return respond(200,{booking:base,quotation:quotationSafe,invoice:invoiceSafe,confirmed_booking:confirmedBooking?{booking_no:confirmedBooking.booking_no,status:confirmedBooking.status,program_name:confirmedBooking.program_name,trip_date:confirmedBooking.trip_date,pax:confirmedBooking.pax,price_per_pax:pricePerPax,participant_group:confirmedBooking.participant_group,meeting_point:confirmedBooking.meeting_point,facilities:confirmedBooking.facilities}:null,payment:{available:Boolean(confirmedBooking||invoiceSafe),total_amount:financialTotal,paid_amount:invoiceSafe||confirmedBooking?paidAmount:0,outstanding_amount:financialTotal==null?null:Math.max(Number(financialTotal)-paidAmount,0),gateway_order:gatewaySafe,checkout_available:Boolean(invoiceSafe&&["ISSUED","PARTIAL","OVERDUE"].includes(String(invoiceSafe.status))&&gatewaySafe?.status==="PENDING"&&gatewaySafe?.checkout_url),items:paymentItems},trip:{available:Boolean(tripRows[0]||rundownRows.length||customerTripInfoRows[0]),status:tripRows[0]?.trip_status??null,operational_progress:tripRows[0]?.operational_progress??null,started_at:tripRows[0]?.started_at??null,completed_at:tripRows[0]?.completed_at??null,updated_at:tripRows[0]?.updated_at??null,customer_info:customerTripInfoRows[0]?{meeting_point:customerTripInfoRows[0].meeting_point,meeting_time:customerTripInfoRows[0].meeting_time,departure_instruction:customerTripInfoRows[0].departure_instruction,what_to_bring:customerTripInfoRows[0].what_to_bring,customer_notes:customerTripInfoRows[0].customer_notes,contact_label:customerTripInfoRows[0].public_contact_label,contact_phone:customerTripInfoRows[0].public_contact_phone,published_at:customerTripInfoRows[0].published_at}:null,rundown:rundownRows.map((r:any)=>({sort_order:r.sort_order,activity_time:r.activity_time,activity:r.activity,location:r.location})),report:tripReportRows[0]?{summary:tripReportRows[0].customer_summary,published_at:tripReportRows[0].published_at,finalized_at:tripReportRows[0].finalized_at}:null},evaluation:{available:["COMPLETED","CLOSED"].includes(String(publicStatus)),submitted:Boolean(feedbackRows[0]),submitted_at:feedbackRows[0]?.submitted_at??null},documents:documentSafe,reminders:reminderRows.map((r:any)=>({id:r.id,invoice_id:r.invoice_id,reminder_date:r.reminder_date,days_relative:r.days_relative,status:r.status,channel:r.channel,message:r.customer_message}))},origin)}catch(e){console.error("portal exception",e instanceof Error?e.message:String(e));return respond(500,{error:"Gagal memuat Customer Portal."},origin)}});