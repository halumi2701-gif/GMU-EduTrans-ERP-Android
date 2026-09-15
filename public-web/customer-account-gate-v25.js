/* GMU EduTrans Public Web — Customer Account Gate v25
 * Requires a Supabase browser client provided by the host page.
 * Never use service_role/secret keys in this file.
 * Flow: browse packages freely → choose package → require signed-in account → record interest → continue booking.
 */
(function(){
  'use strict';

  const VERSION='v25-customer-account-gate';
  const PENDING_KEY='gmu_pending_package_id';
  let client=null;
  let programResolver=null;
  let packageResolver=null;

  function configure(options={}){
    client=options.supabaseClient||client;
    programResolver=typeof options.programResolver==='function'?options.programResolver:programResolver;
    packageResolver=typeof options.packageResolver==='function'?options.packageResolver:packageResolver;
    if(!client?.auth) throw new Error('Supabase client belum dipasang pada GMU Customer Account Gate.');
    return api;
  }

  function ensureClient(){
    if(!client?.auth) throw new Error('Supabase client belum dikonfigurasi.');
    return client;
  }

  async function session(){
    const sb=ensureClient();
    const {data,error}=await sb.auth.getSession();
    if(error) throw error;
    return data?.session||null;
  }

  async function signUp({email,password,fullName,whatsapp,institutionName,city,followupConsent=false}){
    const sb=ensureClient();
    const {data,error}=await sb.auth.signUp({
      email:String(email||'').trim(),
      password:String(password||''),
      options:{
        data:{
          full_name:String(fullName||'').trim(),
          whatsapp:String(whatsapp||'').trim(),
          institution_name:String(institutionName||'').trim(),
          city:String(city||'').trim(),
          source:'PUBLIC_WEB_SIGNUP',
          followup_consent:!!followupConsent
        }
      }
    });
    if(error) throw error;
    window.dispatchEvent(new CustomEvent('gmu:account-created',{detail:{user:data?.user||null,session:!!data?.session}}));
    return data;
  }

  async function signIn({email,password}){
    const sb=ensureClient();
    const {data,error}=await sb.auth.signInWithPassword({email:String(email||'').trim(),password:String(password||'')});
    if(error) throw error;
    window.dispatchEvent(new CustomEvent('gmu:account-signed-in',{detail:{user:data?.user||null}}));
    return data;
  }

  async function signOut(){
    const sb=ensureClient();
    const {error}=await sb.auth.signOut();
    if(error) throw error;
    sessionStorage.removeItem(PENDING_KEY);
    window.dispatchEvent(new CustomEvent('gmu:account-signed-out'));
  }

  async function recordInterest(packageId){
    const sb=ensureClient();
    const program=programResolver?programResolver(packageId):null;
    const pkg=packageResolver?packageResolver(packageId):packageId;
    const {data,error}=await sb.rpc('gmu_customer_account_touch_interest',{
      p_program:program?String(program):null,
      p_package:pkg?String(pkg):null
    });
    if(error) throw error;
    return data;
  }

  async function requireAccount(packageId){
    const current=await session();
    if(!current?.user){
      if(packageId) sessionStorage.setItem(PENDING_KEY,String(packageId));
      window.dispatchEvent(new CustomEvent('gmu:auth-required',{detail:{packageId:packageId||null}}));
      return false;
    }
    if(packageId) await recordInterest(packageId);
    return true;
  }

  async function resumeAfterLogin(onAuthorizedChoose){
    const id=sessionStorage.getItem(PENDING_KEY);
    if(!id) return false;
    if(!(await requireAccount(id))) return false;
    sessionStorage.removeItem(PENDING_KEY);
    if(typeof onAuthorizedChoose==='function') await onAuthorizedChoose(id);
    return true;
  }

  function guardedChoose(onAuthorizedChoose){
    return async function(packageId){
      try{
        if(!(await requireAccount(packageId))) return false;
        if(typeof onAuthorizedChoose==='function') await onAuthorizedChoose(packageId);
        return true;
      }catch(error){
        console.error('GMU Customer Account Gate',error);
        window.dispatchEvent(new CustomEvent('gmu:auth-error',{detail:{message:error?.message||String(error)}}));
        return false;
      }
    };
  }

  const api={version:VERSION,configure,session,signUp,signIn,signOut,requireAccount,resumeAfterLogin,guardedChoose,recordInterest};
  window.GMU_CUSTOMER_ACCOUNT_GATE_V25=api;
})();
