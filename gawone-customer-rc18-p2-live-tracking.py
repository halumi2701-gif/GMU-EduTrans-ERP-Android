from pathlib import Path

root=Path('gawone-customer-production')
build=root/'app/build.gradle.kts'
mobility=root/'app/src/main/java/site/garsyanimultiusaha/gawone/MobilityUi.kt'
for p in (build,mobility):
    if not p.exists(): raise SystemExit(f'missing {p}')

b=build.read_text()
if 'versionCode = 26' not in b or 'versionName = "1.0.16-p2-controlled-pilot-rc17"' not in b:
    raise SystemExit('RC17 version anchor missing')
b=b.replace('versionCode = 26','versionCode = 27',1)
b=b.replace('versionName = "1.0.16-p2-controlled-pilot-rc17"','versionName = "1.0.17-p2-live-tracking-rc18"',1)
build.write_text(b)

s=mobility.read_text()
if 'delay(5000)' not in s: raise SystemExit('tracking poll anchor missing')
s=s.replace('delay(5000)','delay(10000)',1)
old='''                if(trackingEta>0)Text("ETA Mitra ${trackingEta} mnt • ${String.format(Locale.US,"%.1f",trackingDistance)} km ke titik berikutnya",fontSize=9.sp,color=MobilityGreen)'''
new='''                if(trackingEta>0){
                    val trackingPrefix=if(tracking?.optBoolean("approximate",false)==true)"Perkiraan Mitra" else "ETA Mitra"
                    Text("$trackingPrefix ${trackingEta} mnt • ${String.format(Locale.US,"%.1f",trackingDistance)} km ke titik berikutnya",fontSize=9.sp,color=MobilityGreen)
                }'''
if old not in s: raise SystemExit('tracking ETA label anchor missing')
s=s.replace(old,new,1)
old2='''                Text(if(partnerObj.optBoolean("locationFresh"))"Lokasi live" else "Lokasi Mitra perlu diperbarui",fontSize=8.sp,color=if(partnerObj.optBoolean("locationFresh"))MobilityGreen else MobilityMuted)'''
new2='''                Text(if(partnerObj.optBoolean("locationFresh"))"Lokasi live" else "Lokasi Mitra perlu diperbarui",fontSize=8.sp,color=if(partnerObj.optBoolean("locationFresh"))MobilityGreen else MobilityMuted)
                if(tracking?.optBoolean("approximate",false)==true)Text("Jarak/ETA Mitra adalah perkiraan posisi langsung; rute perjalanan tetap memakai routing tepercaya.",fontSize=7.sp,color=MobilityMuted)'''
if old2 not in s: raise SystemExit('freshness anchor missing')
s=s.replace(old2,new2,1)
mobility.write_text(s)

out=mobility.read_text()
for token in ['delay(10000)','Perkiraan Mitra','Jarak/ETA Mitra adalah perkiraan posisi langsung','optBoolean("approximate",false)']:
    if token not in out: raise SystemExit('RC18 verification missing '+token)
print('GAWONE Customer RC18 efficient P2 live tracking applied')
