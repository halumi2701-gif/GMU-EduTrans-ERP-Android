const fs=require('fs');const vm=require('vm');
const files={autopilot:'erp-web/company-autopilot-v210.js',loader:'erp-web/unified-v10-loader.js',migration:'supabase/migrations/20260915050000_gmu_v21_company_autopilot_core.sql',builder:'scripts/build-erp-web-v10-release.js'};
const src={};for(const [k,f] of Object.entries(files)){const t=fs.readFileSync(f,'utf8');src[k]=t;if(f.endsWith('.js'))new vm.Script(t,{filename:f});}
function must(k,t,l){if(!src[k].includes(t))throw new Error(`Missing v21 contract [${k}]: ${l}`)}
for(const t of ["const VERSION = 'v21-company-autopilot'",'Company Autopilot v21','internal_company_autopilot_v21','TIDAK ADA TINDAKAN DIREKTUR','Sequence Follow-up Aktif','Resource Request Terbuka','Retention Aktif','Manager Accountability'])must('autopilot',t,t);
for(const t of ['company-autopilot-v210.js','v21-company-autopilot-layer','GMU EduTrans Company Autopilot v21 aktif'])must('loader',t,t);
for(const t of ['autopilot_inbound_events','sales_followup_sequences','sales_followup_steps','autopilot_resource_requests','customer_retention_cycles','gmu_v21_pick_sales','gmu_v21_seed_followup','gmu_v21_tick','AUTO_FOLLOWUP_D','AFTER_TRIP_FEEDBACK','RETENTION_NURTURE','AUTO-CLOSED v21','internal_company_autopilot_v21','gmu_v21_company_autopilot_tick'])must('migration',t,t);
for(const t of ['company-autopilot-v210.js','v21-company-autopilot','v21-company-autopilot-layer','Company Autopilot v21 — Enterprise Operating System'])must('builder',t,t);
console.log('GMU EduTrans Company Autopilot v21 contract passed.');
console.log('Lead → Follow-up → Quotation → Payment → Booking → Ops → Crew/Vendor → Trip → Finance → Payroll → Feedback → Repeat Order');