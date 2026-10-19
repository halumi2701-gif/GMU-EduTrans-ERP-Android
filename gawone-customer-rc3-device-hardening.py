from pathlib import Path

root=Path("gawone-customer-production/app/src/main")
manifest=root/"AndroidManifest.xml"
s=manifest.read_text()
s=s.replace(
'''        android:allowBackup="false"
        android:label="GAWONE"
        android:theme="@style/Theme.GawoneCustomer"
        android:usesCleartextTraffic="false">''',
'''        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="GAWONE"
        android:theme="@style/Theme.GawoneCustomer"
        android:usesCleartextTraffic="false">'''
)
old='''            <intent-filter android:autoVerify="false">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="gawone" android:host="order" />
                <data android:scheme="gawone" android:host="chat" />
                <data android:scheme="gawone" android:host="payment" />
                <data android:scheme="gawone" android:host="support" />
            </intent-filter>'''
filters=[]
for host in ("order","chat","payment","support"):
    filters.append(f'''            <intent-filter android:autoVerify="false">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="gawone" android:host="{host}" />
            </intent-filter>''')
if old not in s:
    raise SystemExit("deep-link manifest block not found")
manifest.write_text(s.replace(old,"\n".join(filters)))

(root/"xml").mkdir(parents=True,exist_ok=True)
(root/"xml"/"data_extraction_rules.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup disableIfNoEncryptionCapabilities="true">
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </device-transfer>
</data-extraction-rules>
''')

(root/"drawable").mkdir(parents=True,exist_ok=True)
(root/"drawable"/"ic_launcher_foreground.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="M77,35 A31,31 0,1 0,81,69 L61,69"
        android:fillColor="@android:color/transparent"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="9"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
    <path
        android:pathData="M68,35 C74,23 86,19 91,22 C90,34 83,43 71,44 C68,42 67,39 68,35 Z"
        android:fillColor="#FFFFFF" />
</vector>
''')

(root/"mipmap-anydpi-v26").mkdir(parents=True,exist_ok=True)
adaptive='''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/gawone_green" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
(root/"mipmap-anydpi-v26"/"ic_launcher.xml").write_text(adaptive)
(root/"mipmap-anydpi-v26"/"ic_launcher_round.xml").write_text(adaptive)

colors=root/"values"/"colors.xml"
colors.write_text('''<resources>
    <color name="gawone_green">#22C55E</color>
</resources>
''')

print("RC3 physical-device hardening applied")
