import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

function allowed(origin:string|null){
  if(!origin) return true;
  return origin === "https://edutrans.garsyanimultiusaha.site"
    || origin === "https://gtgnwasijweewmaubvyg.supabase.co"
    || /^https:\/\/gmu-edutrans-public-[a-z0-9-]+\.vercel\.app$/i.test(origin)
    || /^https:\/\/[a-z0-9-]+\.vercel\.app$/i.test(origin)
    || /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/i.test(origin);
}
function cors(origin:string|null){ return {
  "Access-Control-Allow-Origin": origin && allowed(origin) ? origin : "https://edutrans.garsyanimultiusaha.site",
  "Access-Control-Allow-Headers":"content-type, apikey, x-client-info",
  "Access-Control-Allow-Methods":"GET, POST, OPTIONS",
  "Vary":"Origin"
}; }
function json(status:number, body:unknown, origin:string|null){
  return new Response(JSON.stringify(body), {status, headers:{...cors(origin),"Content-Type":"application/json; charset=utf-8","Cache-Control":"public, max-age=60","X-Content-Type-Options":"nosniff"}});
}
function uuid(v:unknown){ return typeof v === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v) ? v : null; }
function date(v:unknown){ return typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : null; }
function int(v:unknown){ const n=Number(v); return Number.isInteger(n) ? n : null; }
function jakartaToday(){ return new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Jakarta",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date()); }
function getSecretKey(){
  const raw=Deno.env.get("SUPABASE_SECRET_KEYS");
  if(raw){ try { const x=JSON.parse(raw); if(x?.default) return String(x.default); } catch {} }
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
}

Deno.serve(async (req:Request) => {
  const origin=req.headers.get("origin");
  if(req.method === "OPTIONS") return new Response("ok",{headers:cors(origin)});
  if(!allowed(origin)) return json(403,{error:"Origin not allowed"},origin);
  if(!["GET","POST"].includes(req.method)) return json(405,{error:"Method not allowed"},origin);

  try {
    const SUPABASE_URL=Deno.env.get("SUPABASE_URL") || "";
    const KEY=getSecretKey();
    if(!SUPABASE_URL) return json(500,{error:"SUPABASE_URL unavailable"},origin);
    if(!KEY) return json(500,{error:"Supabase service key unavailable"},origin);
    const sb=createClient(SUPABASE_URL,KEY,{auth:{persistSession:false,autoRefreshToken:false}});

    let body:any={};
    if(req.method === "POST"){
      try { body=await req.json(); } catch { return json(400,{error:"Format data tidak valid"},origin); }
    }
    const requestUrl=new globalThis.URL(req.url);
    const programId=uuid(body?.program_id || requestUrl.searchParams.get("program_id"));
    const tripDate=date(body?.trip_date || requestUrl.searchParams.get("trip_date")) || jakartaToday();
    const pax=int(body?.pax || requestUrl.searchParams.get("pax"));
    const sessionType=String(body?.session_type || requestUrl.searchParams.get("session_type") || "PRIVATE").toUpperCase()==="SHARED"?"SHARED":"PRIVATE";

    const programsRes=await sb.from("programs")
      .select("id,slug,name,category,short_description,min_pax,sort_order,marketing_start_price,marketing_price_note")
      .eq("is_active",true)
      .order("sort_order",{ascending:true})
      .order("name",{ascending:true});
    if(programsRes.error) throw programsRes.error;

    const programs=programsRes.data||[];
    const customProgramIds=new Set(programs.filter((p:any)=>String(p.slug||"").toLowerCase()==="custom-educational-trip").map((p:any)=>String(p.id)));
    const browseAllActivePackages=programId ? customProgramIds.has(String(programId)) : true;

    // Intentionally load all currently ACTIVE packages first, then filter by program in-memory.
    // This avoids intermittent gateway timeouts observed on program_id-filtered PostgREST requests.
    let q=sb.from("program_packages")
      .select("id,package_code,program_id,name,description,price_per_pax,min_pax,facilities,effective_from,effective_until,sort_order")
      .eq("status","ACTIVE")
      .eq("is_active",true)
      .gt("price_per_pax",0)
      .or(`effective_from.is.null,effective_from.lte.${tripDate}`)
      .or(`effective_until.is.null,effective_until.gte.${tripDate}`)
      .order("sort_order",{ascending:true})
      .order("name",{ascending:true});

    if(pax !== null && pax > 0) q=q.lte("min_pax",pax);

    const pkgRes=await q;
    if(pkgRes.error) throw pkgRes.error;

    const programMap=new Map(programs.map((p:any)=>[String(p.id),p]));

    const packageCodes=[...new Set((pkgRes.data||[]).map((x:any)=>String(x.package_code||"")).filter(Boolean))];
    let tiers:any[]=[];
    if(packageCodes.length){
      let tq=sb.from("program_package_price_tiers")
        .select("package_code,channel,session_type,min_pricing_pax,max_pricing_pax,unit_price,school_cashback_per_pax,sales_commission_per_pax,effective_from,effective_until")
        .in("package_code",packageCodes)
        .eq("channel","DIRECT_PUBLIC")
        .eq("is_active",true)
        .lte("effective_from",tripDate)
        .or(`effective_until.is.null,effective_until.gte.${tripDate}`)
        .order("min_pricing_pax",{ascending:true});
      const tr=await tq;
      if(tr.error) throw tr.error;
      tiers=tr.data||[];
    }

    function packageTiers(code:string){
      return tiers.filter((t:any)=>String(t.package_code)===String(code));
    }
    function applicableTier(code:string){
      if(pax===null || pax<=0) return null;
      return packageTiers(code).find((t:any)=>{
        if(String(t.session_type||"ANY")!=="ANY" && String(t.session_type)!==sessionType) return false;
        const min=Number(t.min_pricing_pax||1), max=t.max_pricing_pax==null?null:Number(t.max_pricing_pax);
        return pax>=min && (max===null || pax<=max);
      }) || null;
    }

    const items=(pkgRes.data||[])
      .filter((x:any)=>programMap.has(String(x.program_id)))
      .filter((x:any)=>browseAllActivePackages || !programId || String(x.program_id)===String(programId))
      .map((x:any)=>{
        const p:any=programMap.get(String(x.program_id));
        return {
          id:x.id,
          package_code:x.package_code,
          program_id:x.program_id,
          program_name:p?.name||null,
          program_category:p?.category||null,
          name:x.name,
          description:x.description,
          price_per_pax:(()=>{const t=applicableTier(String(x.package_code));return t?Number(t.unit_price):Number(x.price_per_pax||0)})(),
          base_price_per_pax:Number(x.price_per_pax||0),
          starting_price:(()=>{const a=packageTiers(String(x.package_code)).map((t:any)=>Number(t.unit_price||0)).filter((n:number)=>n>0);return a.length?Math.min(...a):Number(x.price_per_pax||0)})(),
          public_price:(()=>{const t=applicableTier(String(x.package_code));if(t)return Number(t.unit_price);const a=packageTiers(String(x.package_code)).map((q:any)=>Number(q.unit_price||0)).filter((n:number)=>n>0);return a.length?Math.min(...a):Number(x.price_per_pax||0)})(),
          session_type:sessionType,
          min_pax:Number(x.min_pax||1),
          facilities:Array.isArray(x.facilities)?x.facilities:[],
          effective_from:x.effective_from,
          effective_until:x.effective_until,
          price_tiers:packageTiers(String(x.package_code)).map((t:any)=>({
            session_type:t.session_type,
            min_pax:Number(t.min_pricing_pax||1),
            max_pax:t.max_pricing_pax==null?null:Number(t.max_pricing_pax),
            unit_price:Number(t.unit_price||0)
          })),
          estimated_total:pax!==null&&pax>0?((()=>{const t=applicableTier(String(x.package_code));return t?Number(t.unit_price):Number(x.price_per_pax||0)})()*pax):null
        };
      });

    return json(200,{
      ok:true,
      catalog_status:items.length?"AVAILABLE":"NO_ACTIVE_PACKAGE",
      catalog_scope:browseAllActivePackages?"ALL_ACTIVE_PACKAGES":"PROGRAM",
      custom_trip_available:true,
      as_of:tripDate,
      pax:pax??null,
      session_type:sessionType,
      programs:programs.map((p:any)=>({
        id:p.id,
        slug:p.slug,
        name:p.name,
        category:p.category,
        short_description:p.short_description,
        min_pax:Number(p.min_pax||1),
        sort_order:Number(p.sort_order||0),
        marketing_start_price:Number(p.marketing_start_price||0),
        marketing_price_note:p.marketing_price_note||null,
        public_price:Number(p.marketing_start_price||0)
      })),
      items
    },origin);
  } catch(e:any){
    console.error("public package catalog", e);
    return json(500,{error:"Katalog paket belum dapat dimuat.",detail:String(e?.message||e).slice(0,180)},origin);
  }
});