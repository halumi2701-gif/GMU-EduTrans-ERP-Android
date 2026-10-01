(() => {
  'use strict';

  const VERSION = 'v23.3-recruitment-system';
  const VIEW_ROLES = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Admin']);
  const WRITE_ROLES = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans']);
  const TRACKER_URL = 'https://docs.google.com/spreadsheets/d/1goJHZb0RwBJq8sCq3iNq34oo2EtpVEusnXdFDDpRuSw/edit';
  const SCORECARD_URL = 'https://docs.google.com/spreadsheets/d/110zAMthV69H_7ko6RMFPe1zOhFV3qp9t0HLLGyNtyQk/edit';
  const PIPELINE = ['APPLIED','SCREENING','INTERVIEW','PRACTICAL_TEST','SHORTLISTED','OFFERING','ONBOARDING','ACTIVE','TALENT_POOL','HOLD','REJECTED','INACTIVE'];
  const POSITIONS = [
    ['Admin Part Time','PART_TIME'],
    ['Finance Part Time','PART_TIME'],
    ['Marketing & Sales','TARGET_BASED'],
    ['Operasional Freelance','FREELANCER'],
    ['TL / MC / Edukator','FREELANCER'],
    ['Dokumentasi Freelance','FREELANCER'],
    ['Helper Freelance','ON_CALL'],
  ];

  const state = { rows: [], loading: false, error: null, filter: 'ALL' };
  const q = (s,r=document) => r.querySelector(s);
  const safe = v => String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const fmt = v => v == null || v === '' ? '—' : String(v);
  const role = () => { try { return String(profile?.role || ''); } catch (_) { return ''; } };
  const uid = () => { try { return String(profile?.id || ''); } catch (_) { return ''; } };
  const db = () => { try { return typeof sb !== 'undefined' && sb?.from ? sb : null; } catch (_) { return null; } };
  const canView = () => VIEW_ROLES.has(role());
  const canWrite = () => WRITE_ROLES.has(role());

  function candidateCode(){
    const d=new Date();
    return 'CAND-'+d.getFullYear()+String(d.getMonth()+1).padStart(2,'0')+String(d.getDate()).padStart(2,'0')+'-'+String(Date.now()).slice(-6);
  }

  function recommendation(score, red){
    if (String(red||'NO') !== 'NO') return 'REVIEW / RED FLAG';
    const n=Number(score);
    if (!Number.isFinite(n)) return '';
    if (n>=85) return 'Sangat sesuai';
    if (n>=75) return 'Sesuai';
    if (n>=65) return 'Dipertimbangkan / Talent Pool';
    return 'Tidak dilanjutkan';
  }

  function style(){
    if(q('#gmuRecruitmentV233Style')) return;
    const el=document.createElement('style');
    el.id='gmuRecruitmentV233Style';
    el.textContent=`
      .gmu-rec-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px;margin-top:10px}
      .gmu-rec-card{border:1px solid var(--line);background:#fff;border-radius:14px;padding:11px}
      .gmu-rec-card small{display:block;color:var(--muted);font-size:8px;text-transform:uppercase;letter-spacing:.04em}
      .gmu-rec-card b{display:block;font-size:18px;color:var(--gd);margin-top:3px}
      .gmu-rec-toolbar{display:flex;gap:7px;flex-wrap:wrap;align-items:center;margin:10px 0}
      .gmu-rec-btn{border:1px solid #17830d;background:#17830d;color:#fff;border-radius:9px;padding:8px 11px;font-size:10px;font-weight:700;cursor:pointer}
      .gmu-rec-btn.alt{background:#fff;color:#17830d}.gmu-rec-btn.gold{border-color:#d5a000;background:#d5a000}
      .gmu-rec-filter{border:1px solid var(--line);border-radius:9px;padding:7px 8px;background:#fff;font-size:10px}
      .gmu-rec-table{width:100%;border-collapse:separate;border-spacing:0;font-size:9px}
      .gmu-rec-table th{background:#f6faf6;color:#315a2d;text-align:left;padding:8px;border-bottom:1px solid var(--line);position:sticky;top:0}
      .gmu-rec-table td{padding:8px;border-bottom:1px solid #eef1ee;vertical-align:top}
      .gmu-rec-table-wrap{overflow:auto;max-height:520px;border:1px solid var(--line);border-radius:12px}
      .gmu-rec-status{display:inline-block;padding:4px 7px;border-radius:999px;background:#eef7ec;color:#286323;font-size:8px;font-weight:700}
      .gmu-rec-status.REJECTED,.gmu-rec-status.INACTIVE{background:#fff0f0;color:#9f3434}
      .gmu-rec-status.ACTIVE{background:#e7f7e5;color:#156c0d}.gmu-rec-status.TALENT_POOL{background:#fff6d8;color:#755600}
      .gmu-rec-actions{display:flex;gap:4px;flex-wrap:wrap}.gmu-rec-actions button{font-size:8px;padding:5px 7px}
      .gmu-rec-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
      .gmu-rec-form label{font-size:9px;color:var(--muted)}.gmu-rec-form input,.gmu-rec-form select,.gmu-rec-form textarea{width:100%;box-sizing:border-box;margin-top:3px;border:1px solid var(--line);border-radius:9px;padding:8px;font:inherit;background:#fff}
      .gmu-rec-form .wide{grid-column:1/-1}.gmu-rec-modal{position:fixed;inset:0;background:rgba(0,0,0,.38);z-index:99999;display:flex;align-items:center;justify-content:center;padding:14px}
      .gmu-rec-modal>div{width:min(760px,100%);max-height:88vh;overflow:auto;background:#fff;border-radius:18px;padding:16px;box-shadow:0 20px 60px rgba(0,0,0,.25)}
      @media(max-width:900px){.gmu-rec-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:560px){.gmu-rec-grid,.gmu-rec-form{grid-template-columns:1fr}}
    `;
    document.head.appendChild(el);
  }

  function stats(){
    const all=state.rows;
    const count=s=>all.filter(x=>x.pipeline_status===s).length;
    return {
      total:all.length,
      process:all.filter(x=>!['ACTIVE','TALENT_POOL','REJECTED','INACTIVE'].includes(x.pipeline_status)).length,
      active:count('ACTIVE'),
      pool:count('TALENT_POOL'),
      offering:count('OFFERING'),
      review:all.filter(x=>x.red_flag_status && x.red_flag_status!=='NO').length
    };
  }

  function filtered(){
    if(state.filter==='ALL') return state.rows;
    return state.rows.filter(x=>x.pipeline_status===state.filter);
  }

  function render(){
    if(!canView()) return;
    const anchor=q('#gmuCompanyControlCenter')||q('#gmuManagementDomains')||q('.content');
    if(!anchor) return;
    let root=q('#gmuRecruitmentCenterV233');
    if(!root){
      root=document.createElement('div');
      root.id='gmuRecruitmentCenterV233';
      root.className='card section';
      anchor.appendChild(root);
    }
    const s=stats();
    const rows=filtered().map(x=>`
      <tr>
        <td><b>${safe(x.candidate_code)}</b><br><span style="color:var(--muted)">${safe(x.full_name)}</span></td>
        <td>${safe(x.primary_position)}<br><small>${safe(x.employment_type)}</small></td>
        <td><span class="gmu-rec-status ${safe(x.pipeline_status)}">${safe(x.pipeline_status)}</span></td>
        <td>${fmt(x.final_score)}<br><small>${safe(x.recommendation||recommendation(x.final_score,x.red_flag_status))}</small></td>
        <td>${safe(x.whatsapp||x.email||'—')}</td>
        <td>${safe(x.next_action||'—')}<br><small>${safe(x.next_action_due||'')}</small></td>
        <td><div class="gmu-rec-actions">
          ${canWrite()?`<button class="gmu-rec-btn alt" data-edit="${safe(x.id)}">Edit</button>`:''}
          <button class="gmu-rec-btn alt" data-score="${safe(x.primary_position)}">Scorecard</button>
        </div></td>
      </tr>`).join('');

    root.innerHTML=`
      <div class="head"><div><h3>Recruitment Center • GMU EduTrans</h3><p>Pipeline kandidat, interview, practical test, offering, onboarding, dan Talent Pool.</p></div><span class="badge neutral">v23.3</span></div>
      <div class="gmu-rec-grid">
        <div class="gmu-rec-card"><small>Total Kandidat</small><b>${s.total}</b></div>
        <div class="gmu-rec-card"><small>Dalam Proses</small><b>${s.process}</b></div>
        <div class="gmu-rec-card"><small>Active Staff</small><b>${s.active}</b></div>
        <div class="gmu-rec-card"><small>Talent Pool</small><b>${s.pool}</b></div>
        <div class="gmu-rec-card"><small>Offering</small><b>${s.offering}</b></div>
        <div class="gmu-rec-card"><small>Red Flag Review</small><b>${s.review}</b></div>
      </div>
      <div class="gmu-rec-toolbar">
        ${canWrite()?'<button class="gmu-rec-btn" id="gmuRecAdd">+ Kandidat</button>':''}
        <button class="gmu-rec-btn alt" id="gmuRecRefresh">Refresh</button>
        <a class="gmu-rec-btn alt" href="${TRACKER_URL}" target="_blank" rel="noopener">Recruitment Tracker</a>
        <a class="gmu-rec-btn alt" href="${SCORECARD_URL}" target="_blank" rel="noopener">Form Interview</a>
        <select class="gmu-rec-filter" id="gmuRecFilter">
          <option value="ALL">Semua status</option>
          ${PIPELINE.map(x=>`<option value="${x}" ${state.filter===x?'selected':''}>${x}</option>`).join('')}
        </select>
      </div>
      <div class="gmu-rec-table-wrap"><table class="gmu-rec-table"><thead><tr><th>Kandidat</th><th>Posisi</th><th>Status</th><th>Skor</th><th>Kontak</th><th>Next Action</th><th>Aksi</th></tr></thead><tbody>
        ${rows||'<tr><td colspan="7">Belum ada kandidat pada filter ini.</td></tr>'}
      </tbody></table></div>
      ${state.error?`<div class="gmu-target-note" style="margin-top:8px;color:var(--bad)">${safe(state.error)}</div>`:''}
      ${!canWrite()?'<div class="gmu-target-note" style="margin-top:8px">Mode Admin: data kandidat dapat dilihat untuk kebutuhan administrasi; keputusan dan perubahan pipeline dilakukan Manager/Direktur.</div>':''}
    `;
    bind();
  }

  function form(candidate={}){
    if(!canWrite()) return;
    const old=q('#gmuRecModal'); if(old) old.remove();
    const m=document.createElement('div');m.id='gmuRecModal';m.className='gmu-rec-modal';
    m.innerHTML=`<div>
      <div class="head"><div><h3>${candidate.id?'Edit Kandidat':'Tambah Kandidat'}</h3><p>Isi data inti; scorecard terstruktur tetap dapat dibuka dari tombol Form Interview.</p></div><button class="gmu-rec-btn alt" id="gmuRecClose">Tutup</button></div>
      <form id="gmuRecForm" class="gmu-rec-form">
        <label>Nama lengkap<input name="full_name" required value="${safe(candidate.full_name||'')}"></label>
        <label>WhatsApp<input name="whatsapp" value="${safe(candidate.whatsapp||'')}"></label>
        <label>Email<input name="email" type="email" value="${safe(candidate.email||'')}"></label>
        <label>Domisili<input name="domicile" value="${safe(candidate.domicile||'')}"></label>
        <label>Posisi<select name="primary_position">${POSITIONS.map(([p,t])=>`<option value="${safe(p)}" data-type="${t}" ${candidate.primary_position===p?'selected':''}>${safe(p)}</option>`).join('')}</select></label>
        <label>Status<select name="pipeline_status">${PIPELINE.map(x=>`<option value="${x}" ${(candidate.pipeline_status||'APPLIED')===x?'selected':''}>${x}</option>`).join('')}</select></label>
        <label>Screening Score<input name="screening_score" type="number" min="0" max="100" value="${candidate.screening_score??''}"></label>
        <label>Interview Score<input name="interview_score" type="number" min="0" max="100" value="${candidate.interview_score??''}"></label>
        <label>Practical Score<input name="practical_score" type="number" min="0" max="100" value="${candidate.practical_score??''}"></label>
        <label>Red Flag<select name="red_flag_status"><option value="NO">No</option><option value="YES" ${candidate.red_flag_status==='YES'?'selected':''}>Yes</option><option value="NEEDS_REVIEW" ${candidate.red_flag_status==='NEEDS_REVIEW'?'selected':''}>Needs Review</option></select></label>
        <label class="wide">Catatan Red Flag<textarea name="red_flag_notes">${safe(candidate.red_flag_notes||'')}</textarea></label>
        <label class="wide">Next Action<input name="next_action" value="${safe(candidate.next_action||'')}"></label>
        <label>Next Action Due<input name="next_action_due" type="date" value="${safe(candidate.next_action_due||'')}"></label>
        <label>Final Decision<select name="final_decision"><option value="">—</option><option value="PROCEED">Proceed</option><option value="HOLD">Hold</option><option value="TALENT_POOL">Talent Pool</option><option value="REJECTED">Rejected</option><option value="NEED_DIRECTOR_REVIEW">Need Director Review</option></select></label>
        <label class="wide">Catatan<textarea name="notes">${safe(candidate.notes||'')}</textarea></label>
        <div class="wide"><button class="gmu-rec-btn gold" type="submit">Simpan</button></div>
      </form>
    </div>`;
    document.body.appendChild(m);
    q('#gmuRecClose').onclick=()=>m.remove();
    q('#gmuRecForm').onsubmit=e=>save(e,candidate);
  }

  async function save(e,candidate){
    e.preventDefault();
    if(!canWrite()||!db()) return;
    const fd=new FormData(e.currentTarget);
    const position=String(fd.get('primary_position')||'');
    const employment=POSITIONS.find(x=>x[0]===position)?.[1]||'FREELANCER';
    const num=v=>v===''||v==null?null:Number(v);
    const payload={
      full_name:String(fd.get('full_name')||'').trim(),
      whatsapp:String(fd.get('whatsapp')||'').trim()||null,
      email:String(fd.get('email')||'').trim()||null,
      domicile:String(fd.get('domicile')||'').trim()||null,
      primary_position:position,
      employment_type:employment,
      pipeline_status:String(fd.get('pipeline_status')||'APPLIED'),
      screening_score:num(fd.get('screening_score')),
      interview_score:num(fd.get('interview_score')),
      practical_score:num(fd.get('practical_score')),
      red_flag_status:String(fd.get('red_flag_status')||'NO'),
      red_flag_notes:String(fd.get('red_flag_notes')||'').trim()||null,
      next_action:String(fd.get('next_action')||'').trim()||null,
      next_action_due:String(fd.get('next_action_due')||'')||null,
      final_decision:String(fd.get('final_decision')||'')||null,
      notes:String(fd.get('notes')||'').trim()||null,
      updated_by:uid()||null
    };
    payload.recommendation=recommendation(
      [payload.screening_score,payload.interview_score,payload.practical_score].filter(v=>v!=null).reduce((a,v,_,arr)=>a+v/arr.length,0),
      payload.red_flag_status
    )||null;
    let res;
    if(candidate.id) res=await db().from('recruitment_candidates').update(payload).eq('id',candidate.id);
    else res=await db().from('recruitment_candidates').insert({...payload,candidate_code:candidateCode(),created_by:uid()||null});
    if(res.error){state.error=res.error.message;render();return;}
    q('#gmuRecModal')?.remove();await load();
  }

  function bind(){
    q('#gmuRecAdd')?.addEventListener('click',()=>form());
    q('#gmuRecRefresh')?.addEventListener('click',load);
    q('#gmuRecFilter')?.addEventListener('change',e=>{state.filter=e.target.value;render();});
    document.querySelectorAll('[data-edit]').forEach(b=>b.addEventListener('click',()=>{
      const x=state.rows.find(r=>r.id===b.dataset.edit); if(x) form(x);
    }));
    document.querySelectorAll('[data-score]').forEach(b=>b.addEventListener('click',()=>{
      const map={'Admin Part Time':'1048495667','Finance Part Time':'1585196625','Marketing & Sales':'37450216','Operasional Freelance':'1545924845','TL / MC / Edukator':'898637886','Dokumentasi Freelance':'711190487','Helper Freelance':'1797873980'};
      window.open(SCORECARD_URL+'#gid='+(map[b.dataset.score]||''),'_blank','noopener');
    }));
  }

  async function load(){
    if(!canView()||!db()||state.loading) return;
    state.loading=true;state.error=null;
    try{
      const res=await db().from('recruitment_candidates')
        .select('id,candidate_code,full_name,whatsapp,email,domicile,primary_position,employment_type,pipeline_status,screening_score,interview_score,practical_score,final_score,recommendation,red_flag_status,red_flag_notes,final_decision,next_action,next_action_due,notes,created_at')
        .order('created_at',{ascending:false})
        .limit(500);
      if(res.error) throw res.error;
      state.rows=Array.isArray(res.data)?res.data:[];
    }catch(e){state.error=e?.message||String(e);}finally{state.loading=false;render();}
  }

  function init(){
    style();
    const timer=setInterval(()=>{
      if(typeof profile==='undefined'||!profile) return;
      clearInterval(timer);
      if(!canView()) return;
      render();load();
      const obs=new MutationObserver(()=>{if(!q('#gmuRecruitmentCenterV233'))render();});
      obs.observe(document.body,{childList:true,subtree:true});
    },250);
    window.GmuRecruitmentSystem=Object.freeze({version:VERSION,refresh:load,tracker:TRACKER_URL,scorecard:SCORECARD_URL});
  }

  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',init,{once:true});
  else init();
})();
