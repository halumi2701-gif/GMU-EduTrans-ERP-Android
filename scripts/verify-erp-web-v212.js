const fs=require('fs');
const vm=require('vm');

const files={
  target:'erp-web/target-cascade-v212.js',
  loader:'erp-web/unified-v10-loader.js',
  build:'scripts/build-erp-web-v10-release.js',
  migration:'supabase/migrations/20260915061804_gmu_v212_target_cascade_workforce_autopilot.sql'
};
for(const [k,f] of Object.entries(files)){
  if(!fs.existsSync(f)) throw new Error(`Missing v21.2 source [${k}]: ${f}`);
  if(f.endsWith('.js')) new vm.Script(fs.readFileSync(f,'utf8'),{filename:f});
}
const src=Object.fromEntries(Object.entries(files).map(([k,f])=>[k,fs.readFileSync(f,'utf8')]));
const must=(k,n,l)=>{if(!src[k].includes(n))throw new Error(`Missing v21.2 contract [${k}]: ${l}`)};

must('target',"const VERSION = 'v21.2-target-cascade-workforce-autopilot'",'UI version');
must('target','internal_target_cascade_status','read RPC');
must('target','internal_set_executive_profit_target','director target RPC');
must('target','Target Cascade & Workforce Autopilot v21.2','target cascade page');
must('target','Workforce & Compensation Guardrail','compensation guardrail');
must('target','Belum cukup histori closing; ERP tidak mengarang jumlah booking.','truthful booking estimate');
must('loader',"'target-cascade-v212.js'",'loader module');
must('loader','v21.2-target-cascade-workforce-autopilot-layer','release marker');
must('build',"'target-cascade-v212.js'",'release bundle module');
must('build','X-GMU-ERP-Target-Cascade-Layer','release header');

for(const marker of [
  'executive_profit_targets','target_cascade_snapshots','target_cascade_assignments',
  'gmu_v212_refresh_target_cascade','internal_set_executive_profit_target','internal_target_cascade_status',
  "'RECOVERY_CUMULATIVE'","50000000","15000000","25",
  "'TARGET_CASCADE'","gmu_v212_target_cascade"
]) must('migration',marker,`migration ${marker}`);
must('migration','KEEP_FIXED_PAYROLL_LEAN_USE_VARIABLE_FEE','lean compensation guardrail');
must('migration','INSUFFICIENT_CLOSING_HISTORY','no fabricated booking requirement');
must('migration','fallback_used','role fallback contract');

console.log('GMU EduTrans v21.2 Target Cascade & Workforce Autopilot contract passed.');
console.log('Director Target → Profit Gap → Revenue Need → Manager/Sales/Admin/Ops/Finance/TL → Actual Profit → Reforecast');