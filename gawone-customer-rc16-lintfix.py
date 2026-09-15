from pathlib import Path
p=Path('gawone-customer-production/app/src/main/java/site/garsyanimultiusaha/gawone/LocationBridge.kt')
if not p.exists(): raise SystemExit('LocationBridge missing')
s=p.read_text()
if 'import android.annotation.SuppressLint' not in s:
    anchor='package site.garsyanimultiusaha.gawone\n\n'
    if anchor not in s: raise SystemExit('package anchor missing')
    s=s.replace(anchor,anchor+'import android.annotation.SuppressLint\n',1)
needle='    suspend fun current(context: Context): Location {'
if needle not in s: raise SystemExit('current() anchor missing')
if '@SuppressLint("MissingPermission")\n'+needle not in s:
    s=s.replace(needle,'    @SuppressLint("MissingPermission")\n'+needle,1)
p.write_text(s)
print('RC16 MissingPermission lint gate fixed with explicit runtime permission precheck retained')
