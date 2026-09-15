from pathlib import Path
import sys

root=Path(sys.argv[1] if len(sys.argv)>1 else 'gawone-customer-production')
main=root/'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
repo=root/'app/src/main/java/site/garsyanimultiusaha/gawone/CustomerRepository.kt'
build=root/'app/build.gradle.kts'
for p in (main,repo,build):
    if not p.exists(): raise SystemExit(f'missing {p}')

s=main.read_text()
old='''    LaunchedEffect(Unit){load()}'''
new='''    LaunchedEffect(Unit){
        load()
        while(true){
            delay(8000)
            runCatching{requests=repo.workforceRequests()}
        }
    }'''
if old not in s: raise SystemExit('Business load anchor missing')
s=s.replace(old,new,1)

old='''            Spacer(Modifier.height(6.dp));Text("${r.optInt("worker_count")} tenaga • ${r.optString("work_address")}",fontSize=9.sp,color=GMuted)
            val quote=r.optDouble("quoted_amount",0.0);val fee=r.optDouble("management_fee",0.0)'''
new='''            Spacer(Modifier.height(6.dp));Text("${r.optInt("worker_count")} tenaga • ${r.optString("work_address")}",fontSize=9.sp,color=GMuted)
            val required=r.optInt("worker_count",0)
            val covered=r.optInt("covered_workers",0)
            val activeWorkers=r.optInt("active_workers",0)
            val completedWorkers=r.optInt("completed_workers",0)
            val coverage=r.optDouble("coverage_percent",0.0)
            val orderStatus=r.optString("order_status")
            if(orderId!=null){
                Spacer(Modifier.height(6.dp))
                Surface(color=GSoft,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(10.dp)){
                        Text("Staffing ${covered}/${required} • ${coverage}% terisi",fontSize=10.sp,fontWeight=FontWeight.ExtraBold,color=GDark)
                        Text("Aktif $activeWorkers • Selesai $completedWorkers${if(orderStatus.isNotBlank())" • $orderStatus" else ""}",fontSize=8.sp,color=GMuted)
                    }
                }
            }
            val quote=r.optDouble("quoted_amount",0.0);val fee=r.optDouble("management_fee",0.0)'''
if old not in s: raise SystemExit('WorkforceRequestCard status anchor missing')
s=s.replace(old,new,1)
main.write_text(s)

b=build.read_text()
if 'versionCode = 23' not in b or 'versionName = "1.0.13-business-labour-rc14"' not in b:
    raise SystemExit('RC14 version anchor missing')
b=b.replace('versionCode = 23','versionCode = 24',1)
b=b.replace('versionName = "1.0.13-business-labour-rc14"','versionName = "1.0.14-p1-nine-services-complete-rc15"',1)
build.write_text(b)

out=main.read_text()
for token in ('coverage_percent','Staffing ${covered}/${required}','completed_workers','delay(8000)'):
    if token not in out: raise SystemExit(f'RC15 token missing: {token}')
for token in ('get_my_bulk_workforce_requests','create_bulk_workforce_request'):
    if token not in repo.read_text(): raise SystemExit(f'Labour repository contract missing: {token}')
if 'versionCode = 24' not in build.read_text(): raise SystemExit('RC15 version bump failed')
print('GAWONE Customer RC15 P1 closure applied')
