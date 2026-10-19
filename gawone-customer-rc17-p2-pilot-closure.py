from pathlib import Path

root=Path('gawone-customer-production')
build=root/'app/build.gradle.kts'
main=root/'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
realtime=root/'app/src/main/java/site/garsyanimultiusaha/gawone/RealtimeSocket.kt'
for p in (build,main,realtime):
    if not p.exists(): raise SystemExit(f'missing {p}')

# Official P2 closure version.
s=build.read_text()
if 'versionCode = 25' not in s or '1.0.15-p2-maps-live-mobility-rc16' not in s:
    raise SystemExit('RC16 build anchor missing')
s=s.replace('versionCode = 25','versionCode = 26',1)
s=s.replace('versionName = "1.0.15-p2-maps-live-mobility-rc16"','versionName = "1.0.16-p2-controlled-pilot-rc17"',1)
build.write_text(s)

# Allow Ride/Car/Delivery only when the account-level MAPS+MOBILITY+BOOKING+MATCHING gates resolve true.
s=main.read_text()
old='val controlledPilotCodes=setOf("CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER")'
new='val controlledPilotCodes=setOf("RIDE","CAR","DELIVERY","CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER")'
if old not in s: raise SystemExit('controlled pilot anchor missing')
s=s.replace(old,new,1)
s=s.replace('"Menunggu Maps + routing production"','"Maps belum aktif untuk akun ini"',1)
s=s.replace('"Flow mobilitas masih dikunci sampai endpoint pickup/tujuan siap"','"Live Mobility belum aktif untuk akun ini"',1)
main.write_text(s)

# Clean harmless compiler parameter-name warnings while preserving behavior.
s=realtime.read_text()
s=s.replace('override fun onOpen(w:WebSocket,response:Response)', 'override fun onOpen(webSocket:WebSocket,response:Response)')
s=s.replace('override fun onMessage(w:WebSocket,text:String)', 'override fun onMessage(webSocket:WebSocket,text:String)')
s=s.replace('override fun onClosed(w:WebSocket,code:Int,reason:String)', 'override fun onClosed(webSocket:WebSocket,code:Int,reason:String)')
s=s.replace('override fun onFailure(w:WebSocket,t:Throwable,response:Response?)', 'override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?)')
realtime.write_text(s)

checks={
    build:['versionCode = 26','1.0.16-p2-controlled-pilot-rc17'],
    main:['controlledPilotCodes=setOf("RIDE","CAR","DELIVERY"','featureEnabled(runtime,"MAPS")','featureEnabled(runtime,"MOBILITY")','MobilityTrackingCard'],
}
for path,tokens in checks.items():
    text=path.read_text()
    for token in tokens:
        if token not in text: raise SystemExit(f'RC17 token missing: {path}: {token}')
print('GAWONE Customer RC17 P2 controlled pilot closure applied')
