const fs=require('fs');
const vm=require('vm');

const files={
  compensation:'erp-web/compensation-autopilot-v213.js',
  loader:'erp-web/unified-v10-loader.js',
  build:'scripts/build-erp-web-v10-release.js',
  migration:'supabase/migrations/20260915062732_gmu_v213_compensation_capacity_autopilot.sql'
};
for(const [k,f] of Object.entries(files)){
  if(!fs.existsSync(f)) throw new Error(`Missing v21.3 source [${k}]: ${f}`);
  if(f.endsWith('.js')) new vm.Script(fs.readFileSync(f,'utf8'),{filename:f});
}
const src=Object.fromEntries(Object.entries(files).map(([k,f])=>[k,fs.readFileSync(f,'utf8')]));
const must=(k,n,l)=>{if(!src[k].includes(n))throw new Error(`Missing v21.3 contract [${k}]: ${l}`)};

must('compensation',"const VERSION='v21.3-compensation-capacity-autopilot'",'UI version');
must('compensation','internal_compensation_autopilot_status','read RPC');
must('compensation','Compensation Autopilot v21.3','page title');
must('compensation','Kompensasi Sehat','navigation label');
must('compensation','sample trip sehat','healthy trip evidence');
must('compensation','ERP tidak mengarang gaji atau fee','no fabricated compensation');
must('loader',"'compensation-autopilot-v213.js'",'loader module');
must('loader','v21.3-compensation-capacity-autopilot-layer','v21.3 release marker');
must('loader','v21.2-target-cascade-workforce-autopilot-layer','v21.2 compatibility layer');
must('loader','GMU EduTrans Company Autopilot v21 aktif','v21 compatibility marker');
must('build',"'compensation-autopilot-v213.js'",'release bundle module');
must('build','X-GMU-ERP-Compensation-Layer','release header');
must('build','v21.3-compensation-capacity-autopilot','release version');

for(const marker of [
  'compensation_capacity_snapshots','compensation_recommendations','gmu_v213_refresh_compensation',
  'internal_compensation_autopilot_status','VARIABLE_ONLY','HOLD_CURRENT','REVIEW_FIXED','REVIEW_VARIABLE',
  'percentile_cont(0.5)','tc.margin_pct>=25','v_samples>=3','approval_required',
  'gmu_v213_compensation_autopilot','compensation_recalculated','TARGET_PROFIT_FIRST_COMPENSATION_SECOND',
  'Budget capacity is not a substitute for applicable wage/employment law.'
]) must('migration',marker,`migration ${marker}`);

console.log('GMU EduTrans v21.3 Compensation Capacity Autopilot contract passed.');
console.log('Target Profit → Compensation Capacity → Evidence-based Fee Recommendation → Human Approval');