import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const URL=Deno.env.get("SUPABASE_URL")||"";
function secretKey(){
  const raw=Deno.env.get("SUPABASE_SECRET_KEYS");
  if(raw){try{const p=JSON.parse(raw);if(p?.default)return p.default}catch{}}
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"";
}
const SECRET=secretKey();
const sb=createClient(URL,SECRET,{auth:{persistSession:false,autoRefreshToken:false}});
const ROLES=new Set(["Owner","Manager"]);

function cors(origin:string|null){
  const ok=!origin ||
    /^https:\/\/([a-z0-9-]+\.)*garsyanimultiusaha\.site$/i.test(origin) ||
    /^https:\/\/[a-z0-9-]+\.vercel\.app$/i.test(origin) ||
    /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/i.test(origin);
  return {
    "Access-Control-Allow-Origin":ok&&origin?origin:"https://erp.edutrans.garsyanimultiusaha.site",
    "Access-Control-Allow-Headers":"authorization, content-type, apikey, x-client-info",
    "Access-Control-Allow-Methods":"POST, OPTIONS","Vary":"Origin"
  };
}
function respond(status:number,body:unknown,origin:string|null){
  return new Response(JSON.stringify(body),{status,headers:{
    ...cors(origin),"Content-Type":"application/json; charset=utf-8",
    "Cache-Control":"no-store","X-Content-Type-Options":"nosniff"
  }});
}
function txt(v:unknown,max=1000){return typeof v==="string"?v.trim().slice(0,max):""}
function date(v:unknown){return typeof v==="string"&&/^\d{4}-\d{2}-\d{2}$/.test(v)?v:null}
function uuid(v:unknown){return typeof v==="string"&&/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v)?v:null}
function num(v:unknown,def:number|null=null){const n=Number(v);return Number.isFinite(n)?n:def}
function int(v:unknown,def:number|null=null){const n=Math.trunc(Number(v));return Number.isFinite(n)?n:def}

async function auth(req:Request){
  const h=req.headers.get("Authorization")||"";
  const token=h.startsWith("Bearer ")?h.slice(7):"";
  if(!token)return {error:"Unauthorized",status:401};
  const u=await sb.auth.getUser(token);
  if(u.error||!u.data.user)return {error:"Unauthorized",status:401};
  const p=await sb.from("profiles").select("role,is_active,full_name").eq("id",u.data.user.id).maybeSingle();
  if(p.error||!p.data?.is_active)return {error:"Akun internal tidak aktif",status:403};
  const role=String(p.data.role);
  if(!ROLES.has(role))return {error:"Planning & Business Control hanya dapat diakses Owner / Manager",status:403};
  return {user:u.data.user,role,full_name:p.data.full_name};
}

Deno.serve(async(req:Request)=>{
  const origin=req.headers.get("origin");
  if(req.method==="OPTIONS")return new Response("ok",{headers:cors(origin)});
  if(req.method!=="POST")return respond(405,{error:"Method not allowed"},origin);
  if(!URL||!SECRET)return respond(500,{error:"Server configuration error"},origin);

  const a=await auth(req);
  if(a.error)return respond(a.status||403,{error:a.error},origin);
  let body:any={};
  try{body=await req.json()}catch{return respond(400,{error:"Format data tidak valid"},origin)}
  const action=txt(body?.action,80).toLowerCase();
  const actor=a.user!.id;

  try{
    if(action==="dashboard"){
      const month=date(body?.month);
      const start=date(body?.start_date)||month;
      const end=date(body?.end_date)||new Date().toISOString().slice(0,10);
      const [score,budgets,weights,programs,pnl,settings,payroll,profiles,compProfiles]=await Promise.all([
        sb.rpc("internal_planning_management_scorecard",{p_month:month,p_actor:actor}),
        sb.rpc("internal_planning_budgets",{p_month:month,p_actor:actor}),
        sb.rpc("internal_planning_pipeline_weights",{p_actor:actor}),
        sb.rpc("internal_planning_program_performance",{p_start:start,p_end:end,p_actor:actor}),
        sb.rpc("internal_gl_profit_loss",{p_start:start,p_end:end}),
        sb.from("company_control_settings")
          .select("setting_key,numeric_value")
          .in("setting_key",[
            "MONTHLY_PAX_TARGET","MONTHLY_UTILITY_BUDGET","LOSS_RECOVERY_TARGET","LOSS_RECOVERY_PAID_TO_DATE",
            "PROFIT_SPLIT_RECOVERY_PCT","PROFIT_SPLIT_COMPANY_CASH_PCT","PROFIT_SPLIT_OWNER_PCT"
          ]),
        sb.from("payroll_entries")
          .select("staff_id,amount,state,component_type,earned_at")
          .gte("earned_at",start+"T00:00:00+00:00")
          .lte("earned_at",end+"T23:59:59+00:00")
          .neq("state","VOID"),
        sb.from("profiles").select("id,full_name,role,is_active"),
        sb.from("staff_compensation_profiles").select("staff_id,base_monthly,status,compensation_type")
      ]);
      for(const x of [score,budgets,weights,programs,pnl,settings,payroll,profiles,compProfiles])if(x.error)throw x.error;

      const cfg:Record<string,number>={};
      for(const row of settings.data||[])cfg[String(row.setting_key)]=Number(row.numeric_value||0);
      const pnlRow=Array.isArray(pnl.data)?(pnl.data[0]||{}):(pnl.data||{});
      const netProfit=Math.max(Number(pnlRow?.net_profit||0),0);
      const recoveryPct=cfg.PROFIT_SPLIT_RECOVERY_PCT||50;
      const cashPct=cfg.PROFIT_SPLIT_COMPANY_CASH_PCT||30;
      const ownerPct=cfg.PROFIT_SPLIT_OWNER_PCT||20;
      const recoveryTarget=cfg.LOSS_RECOVERY_TARGET||50000000;
      const recoveryPaid=cfg.LOSS_RECOVERY_PAID_TO_DATE||0;
      const recoveryAllocation=netProfit*recoveryPct/100;
      const companyCash=netProfit*cashPct/100;
      const ownerShare=netProfit*ownerPct/100;
      const recoveryRemaining=Math.max(recoveryTarget-recoveryPaid,0);
      const rp=(v:number)=>"Rp"+Math.round(v).toLocaleString("id-ID");
      const profileNames:Record<string,string>={};
      for(const p of profiles.data||[])profileNames[String(p.id)]=String(p.full_name||"Staff");
      const outstanding=(payroll.data||[]).filter((x:any)=>x.state==="EARNED"||x.state==="APPROVED");
      const paidRows=(payroll.data||[]).filter((x:any)=>x.state==="PAID");
      const dueByStaff:Record<string,number>={};
      for(const x of outstanding){
        const id=String(x.staff_id||"");
        dueByStaff[id]=(dueByStaff[id]||0)+Number(x.amount||0);
      }
      const outstandingTotal=Object.values(dueByStaff).reduce((a:number,b:any)=>a+Number(b||0),0);
      const paidTotal=paidRows.reduce((a:number,x:any)=>a+Number(x.amount||0),0);
      const payablePeople=Object.entries(dueByStaff)
        .sort((a,b)=>Number(b[1])-Number(a[1]))
        .slice(0,6)
        .map(([id,amt])=>(profileNames[id]||"Staff")+" "+rp(Number(amt)))
        .join(", ");
      const payableNote=outstandingTotal>0
        ?" | Internal belum dibayar "+rp(outstandingTotal)+(payablePeople?": "+payablePeople:"")+" | Internal sudah dibayar "+rp(paidTotal)
        :" | Internal belum dibayar Rp0 | Internal sudah dibayar "+rp(paidTotal);
      const activeProfiles=(profiles.data||[]).filter((p:any)=>p.is_active===true);
      const compMap:Record<string,any>={};
      for(const c of compProfiles.data||[])compMap[String(c.staff_id)]=c;
      const roleCount=(role:string)=>activeProfiles.filter((p:any)=>String(p.role||"")===role).length;
      const fixedActive=(role:string)=>activeProfiles.filter((p:any)=>String(p.role||"")===role && compMap[String(p.id)]?.status==="ACTIVE" && Number(compMap[String(p.id)]?.base_monthly||0)>0).length;
      const fixedMasterNote=" | Fixed payroll: Sales Rp600.000 kandidat "+roleCount("Sales")+" / aktif "+fixedActive("Sales")
        +" | Admin Rp450.000 profil aktif "+roleCount("Admin")+" / penerima "+fixedActive("Admin")
        +" | Finance Rp550.000 profil aktif "+roleCount("Finance")+" / penerima "+fixedActive("Finance");

      if(month){
        const companyTarget=await sb.from("planning_targets")
          .select("id,notes")
          .eq("period_month",month)
          .eq("scope_type","COMPANY")
          .is("sales_id",null)
          .is("program_id",null)
          .maybeSingle();
        if(companyTarget.error)throw companyTarget.error;
        if(companyTarget.data?.id){
          const prior=String(companyTarget.data.notes||"")
            .replace(/\s*\[AUTO FINANCE\][\s\S]*$/,"").trim();
          const autoNote="[AUTO FINANCE] Laba bersih GL "+rp(netProfit)
            +" | Recovery "+recoveryPct+"% "+rp(recoveryAllocation)
            +" | Kas "+cashPct+"% "+rp(companyCash)
            +" | Owner "+ownerPct+"% "+rp(ownerShare)
            +" | Sisa recovery tercatat "+rp(recoveryRemaining)
            +" | Utilitas bulanan "+rp(cfg.MONTHLY_UTILITY_BUDGET||1755000)
            +" | Target "+Math.round(cfg.MONTHLY_PAX_TARGET||400)+" pax"
            +payableNote
            +fixedMasterNote
            +". Basis pembagian: laba bersih positif setelah biaya yang sudah dibukukan.";
          const upd=await sb.from("planning_targets")
            .update({notes:(prior?prior+" • ":"")+autoNote,updated_at:new Date().toISOString()})
            .eq("id",companyTarget.data.id);
          if(upd.error)throw upd.error;
        }
      }

      const targets=await sb.rpc("internal_planning_targets",{p_month:month,p_actor:actor});
      if(targets.error)throw targets.error;

      return respond(200,{
        role:a.role,
        scorecard:score.data,
        targets:targets.data||[],
        budgets:budgets.data||[],
        pipeline_weights:weights.data||[],
        program_performance:programs.data||[]
      },origin);
    }

    if(action==="save_target"){
      const r=await sb.rpc("internal_save_planning_target",{
        p_target_id:uuid(body?.target_id),
        p_period_month:date(body?.period_month),
        p_scope_type:txt(body?.scope_type,20).toUpperCase(),
        p_sales_id:uuid(body?.sales_id),
        p_program_id:uuid(body?.program_id),
        p_target_revenue:num(body?.target_revenue,0),
        p_target_bookings:int(body?.target_bookings,0),
        p_target_pax:int(body?.target_pax,0),
        p_target_profit:num(body?.target_profit,0),
        p_target_margin_pct:num(body?.target_margin_pct,0),
        p_target_cash_in:num(body?.target_cash_in,0),
        p_notes:txt(body?.notes,1000)||null,
        p_actor:actor
      });
      if(r.error)throw r.error;
      return respond(200,{ok:true,id:r.data},origin);
    }

    if(action==="save_budget"){
      const r=await sb.rpc("internal_save_planning_budget",{
        p_budget_id:uuid(body?.budget_id),
        p_period_month:date(body?.period_month),
        p_budget_type:txt(body?.budget_type,30).toUpperCase(),
        p_category:txt(body?.category,160),
        p_program_id:uuid(body?.program_id),
        p_amount:num(body?.amount,0),
        p_notes:txt(body?.notes,1000)||null,
        p_actor:actor
      });
      if(r.error)throw r.error;
      return respond(200,{ok:true,id:r.data},origin);
    }

    if(action==="delete_budget"){
      const id=uuid(body?.budget_id); if(!id)return respond(400,{error:"Budget ID tidak valid"},origin);
      const r=await sb.rpc("internal_delete_planning_budget",{p_budget_id:id,p_actor:actor});
      if(r.error)throw r.error;
      return respond(200,{ok:r.data===true},origin);
    }

    if(action==="set_pipeline_weight"){
      const r=await sb.rpc("internal_set_planning_pipeline_weight",{
        p_booking_status:txt(body?.booking_status,30),
        p_win_probability_pct:num(body?.win_probability_pct,0),
        p_notes:txt(body?.notes,500)||null,
        p_actor:actor
      });
      if(r.error)throw r.error;
      return respond(200,{ok:true},origin);
    }

    if(action==="scenario"){
      const bookingId=txt(body?.booking_id,140);
      if(!bookingId)return respond(400,{error:"Booking wajib dipilih"},origin);
      const r=await sb.rpc("internal_planning_scenario_simulator",{
        p_booking_id:bookingId,
        p_pax:int(body?.pax,null),
        p_price_per_pax:num(body?.price_per_pax,null),
        p_vendor_increase_pct:num(body?.vendor_increase_pct,0),
        p_transport_increase_pct:num(body?.transport_increase_pct,0),
        p_discount_pct:num(body?.discount_pct,0),
        p_variable_cost_share_pct:num(body?.variable_cost_share_pct,50),
        p_actor:actor
      });
      if(r.error)throw r.error;
      return respond(200,{role:a.role,result:r.data},origin);
    }

    const routes:Record<string,{rpc:string,args:Record<string,unknown>}> = {
      target_vs_actual:{rpc:"internal_planning_target_vs_actual",args:{p_month:date(body?.month),p_actor:actor}},
      budget_control:{rpc:"internal_planning_budget_control",args:{p_month:date(body?.month),p_actor:actor}},
      sales_forecast:{rpc:"internal_planning_sales_forecast",args:{p_as_of:date(body?.as_of),p_actor:actor}},
      profit_forecast:{rpc:"internal_planning_profit_forecast",args:{p_as_of:date(body?.as_of),p_actor:actor}},
      scorecard:{rpc:"internal_planning_management_scorecard",args:{p_month:date(body?.month),p_actor:actor}}
    };
    const route=routes[action];
    if(route){
      const r=await sb.rpc(route.rpc,route.args);
      if(r.error)throw r.error;
      return respond(200,{role:a.role,result:r.data},origin);
    }

    return respond(400,{error:"Action tidak dikenali"},origin);
  }catch(e){
    console.error("planning control",e);
    return respond(500,{error:e instanceof Error?e.message:"Planning & Business Control gagal diproses"},origin);
  }
});