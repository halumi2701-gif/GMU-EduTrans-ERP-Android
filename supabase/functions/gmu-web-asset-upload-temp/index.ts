
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const URL=Deno.env.get("SUPABASE_URL")||"";
function serviceKey(){const j=Deno.env.get("SUPABASE_SECRET_KEYS");if(j){try{const p=JSON.parse(j);if(p&&p.default)return p.default}catch{}}return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||""}
const sb=createClient(URL,serviceKey(),{auth:{persistSession:false,autoRefreshToken:false}});
const hits=new Map();
function clean(v,max=1200){return typeof v==="string"?v.trim().replace(/\\s+/g," ").slice(0,max):""}
function out(status,body,type="application/json; charset=utf-8"){return new Response(type.startsWith("text/html")?body:JSON.stringify(body),{status,headers:{"content-type":type,"cache-control":"no-store","x-content-type-options":"nosniff"}})}
function limited(req){const ip=(req.headers.get("x-forwarded-for")||req.headers.get("cf-connecting-ip")||"unknown").split(",")[0].trim(),now=Date.now(),b=hits.get(ip);if(!b||b.reset<now){hits.set(ip,{count:1,reset:now+600000});return false}b.count++;return b.count>12}

const HTML=[
'<!doctype html><html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Recruitment GMU EduTrans</title>',
'<style>:root{--g:#0D7A57;--gd:#064D3B;--gold:#E7B600;--line:#DFE7E1}*{box-sizing:border-box}body{margin:0;font-family:Arial,sans-serif;background:#F4F8F5;color:#17211C}.w{max-width:880px;margin:auto;padding:18px}.hero{background:linear-gradient(135deg,var(--gd),var(--g));color:white;padding:24px;border-radius:24px}.hero h1{margin:6px 0}.card{background:white;border:1px solid var(--line);border-radius:18px;padding:18px;margin-top:14px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.wide{grid-column:1/-1}label{font-size:12px;font-weight:700}input,select,textarea{width:100%;margin-top:6px;padding:11px;border:1px solid #D5E0D8;border-radius:11px}textarea{min-height:90px}.role{display:none}.role.show{display:block}.btn{width:100%;border:0;border-radius:12px;padding:13px;background:var(--gold);font-weight:900;color:#173D31}.status{margin-top:10px;padding:11px;border-radius:10px;display:none}.ok{display:block;background:#EAF6F0;color:#146847}.bad{display:block;background:#FFF0F0;color:#982C2C}.small{font-size:11px;color:#65726B}@media(max-width:640px){.grid{grid-template-columns:1fr}.wide{grid-column:auto}}</style></head><body><div class="w">',
'<div class="hero"><b>GMU EDUTRANS</b><h1>Recruitment GMU EduTrans</h1><div>Learn • Explore • Experience</div></div>',
'<form id="f"><input name="website" style="position:absolute;left:-9999px"><input type="hidden" name="started_at" id="started">',
'<div class="card"><h2>Data Umum</h2><div class="grid">',
'<label>Nama lengkap*<input name="full_name" required></label><label>WhatsApp*<input name="whatsapp" required></label>',
'<label>Email*<input type="email" name="email" required></label><label>Domisili*<input name="domicile" required></label>',
'<label>Kecamatan*<input name="kecamatan" required></label><label>Pendidikan terakhir*<select name="education" required><option value="">Pilih</option><option>SMP</option><option>SMA/SMK/MA</option><option>D1-D3</option><option>D4-S1</option><option>S2+</option></select></label>',
'<label>Aktivitas saat ini*<input name="current_activity" required></label><label>Link CV/Portfolio<input type="url" name="portfolio_url"></label>',
'<label>Sumber lowongan<select name="source"><option>Instagram</option><option>Facebook</option><option>WhatsApp</option><option>Teman/Referral</option><option>Platform Lowongan</option><option>Lainnya</option></select></label>',
'<label>Posisi alternatif<select name="alternate_position"><option>Tidak ada</option><option>Admin Part Time</option><option>Finance Part Time</option><option>Marketing & Sales</option><option>Operasional Freelance</option><option>TL / MC / Edukator</option><option>Dokumentasi Freelance</option><option>Helper Freelance</option></select></label>',
'<label class="wide">Posisi utama*<select name="primary_position" id="pos" required><option value="">Pilih</option><option>Admin Part Time</option><option>Finance Part Time</option><option>Marketing & Sales</option><option>Operasional Freelance</option><option>TL / MC / Edukator</option><option>Dokumentasi Freelance</option><option>Helper Freelance</option></select></label>',
'</div></div><div id="role"></div>',
'<div class="card"><h2>Ketersediaan & Pengalaman</h2><div class="grid"><label>Hari/jam tersedia*<input name="availability" required></label><label>Bersedia weekend?*<select name="weekend"><option>Ya</option><option>Tidak</option><option>Kondisional</option></select></label><label class="wide">Area kerja*<input name="coverage_area" required></label><label class="wide">Pengalaman relevan*<textarea name="experience_summary" required></textarea></label><label class="wide">Mengapa ingin bergabung?*<textarea name="motivation" required></textarea></label></div></div>',
'<div class="card"><h2>Pernyataan</h2><label><input type="checkbox" required style="width:auto"> Data yang saya berikan benar.</label><br><label><input type="checkbox" required style="width:auto"> Saya memahami proses rekrutmen tidak menjamin diterima.</label><br><label><input type="checkbox" required style="width:auto"> Saya bersedia mengikuti SOP dan kebijakan GMU EduTrans.</label><p class="small">KTP, rekening bank, dan dokumen sensitif tidak diminta pada tahap awal.</p><button id="btn" class="btn">Kirim Lamaran</button><div id="st" class="status"></div></div></form></div>',
'<script>const qs={',
'"Admin Part Time":[["A002","Ceritakan pengalaman administrasi"],["A003","Bagaimana memastikan tidak salah input?"],["A007","Bagaimana menjaga kerahasiaan data?"]],',
'"Finance Part Time":[["FNC002","Jelaskan perbedaan pemasukan, biaya, dan laba"],["FNC005","Apa yang dilakukan jika saldo berbeda?"],["FNC008","Bagaimana menjaga kerahasiaan data keuangan?"]],',
'"Marketing & Sales":[["SAL005","Jika 30 calon customer belum merespons, apa langkah berikutnya?"],["SAL006","Bagaimana merespons penolakan?"],["SAL010","Strategi mendapatkan customer pertama?"]],',
'"Operasional Freelance":[["OPS002","Bagaimana memastikan perlengkapan tidak tertinggal?"],["OPS003","Jika rundown berubah mendadak?"],["OPS004","Jika instruksi belum jelas?"]],',
'"TL / MC / Edukator":[["TLE001","Pengalaman public speaking"],["TLE003","Bagaimana membuat 30 anak tetap fokus?"],["TLE007","Bagaimana menghadapi komplain guru/customer?"]],',
'"Dokumentasi Freelance":[["DOC001","Perangkat dokumentasi"],["DOC003","Link portfolio"],["DOC005","Bagaimana memastikan file aman?"]],',
'"Helper Freelance":[["HLP003","Bagaimana merespons instruksi mendadak?"],["HLP002","Bersedia on-call?"]]',
'};const f=document.getElementById("f"),p=document.getElementById("pos"),r=document.getElementById("role"),st=document.getElementById("st"),btn=document.getElementById("btn");document.getElementById("started").value=Date.now();function draw(){const a=qs[p.value]||[];r.innerHTML=a.length?"<div class=card><h2>Pertanyaan Posisi</h2>"+a.map(x=>"<label>"+x[1]+"*<textarea data-k="+x[0]+" required></textarea></label>").join("")+"</div>":""}p.onchange=draw;f.onsubmit=async e=>{e.preventDefault();const fd=new FormData(f),role={};r.querySelectorAll("[data-k]").forEach(x=>role[x.dataset.k]=x.value);const q=Object.fromEntries(fd.entries());q.response_id="WEB-"+crypto.randomUUID();q.submitted_at=new Date().toISOString();q.role_answers=role;q.general_answers={motivation:q.motivation};q.consent={all:true};q.started_at=Number(q.started_at||0);btn.disabled=true;btn.textContent="Mengirim...";try{const z=await fetch(location.href,{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify(q)}),j=await z.json();if(!z.ok||!j.ok)throw new Error(j.error||"Gagal");f.reset();draw();document.getElementById("started").value=Date.now();st.className="status ok";st.textContent="Lamaran diterima. Nomor kandidat: "+j.candidate_code}catch(err){st.className="status bad";st.textContent="Lamaran belum terkirim: "+(err.message||err)}finally{btn.disabled=false;btn.textContent="Kirim Lamaran"}};</script></body></html>'
].join("");

Deno.serve(async(req)=>{
 if(req.method==="GET")return out(200,HTML,"text/html; charset=utf-8");
 if(req.method!=="POST")return out(405,{ok:false,error:"Method not allowed"});
 if(limited(req))return out(429,{ok:false,error:"Terlalu banyak permintaan."});
 let body;try{body=await req.json()}catch{return out(400,{ok:false,error:"Format data tidak valid."})}
 if(clean(body.website,200))return out(400,{ok:false,error:"Submission tidak valid."});
 const started=Number(body.started_at||0);if(!started||Date.now()-started<1500)return out(400,{ok:false,error:"Submission terlalu cepat."});
 const payload={
  response_id:clean(body.response_id,100),submitted_at:clean(body.submitted_at,80),full_name:clean(body.full_name,180),whatsapp:clean(body.whatsapp,80),email:clean(body.email,180),domicile:clean(body.domicile,180),kecamatan:clean(body.kecamatan,180),
  education:clean(body.education,80),current_activity:clean(body.current_activity,400),portfolio_url:clean(body.portfolio_url,600),source:clean(body.source,120),alternate_position:clean(body.alternate_position,120),primary_position:clean(body.primary_position,120),
  availability:clean(body.availability,300),weekend:clean(body.weekend,40),coverage_area:clean(body.coverage_area,500),experience_summary:clean(body.experience_summary,2000),
  role_answers:body.role_answers&&typeof body.role_answers==="object"?body.role_answers:{},general_answers:body.general_answers&&typeof body.general_answers==="object"?body.general_answers:{},consent:{all:true},source_channel:"GMU EduTrans Public Recruitment Form"
 };
 const x=await sb.rpc("gmu_recruitment_form_ingest_trusted",{p_payload:payload});
 if(x.error){console.error(x.error.message);return out(500,{ok:false,error:"Lamaran belum dapat diproses."})}
 return out(200,x.data);
});
