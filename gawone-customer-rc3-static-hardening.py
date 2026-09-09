from pathlib import Path

root=Path("gawone-customer-production/app/src/main")
manifest=root/"AndroidManifest.xml"
m=manifest.read_text()

m=m.replace('android:allowBackup="false"\n        android:label="GAWONE"\n        android:theme="@style/Theme.GawoneCustomer"',
'''android:allowBackup="false"
        android:fullBackupContent="@xml/backup_rules"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:theme="@style/Theme.GawoneCustomer"''')

old='''                <data android:scheme="gawone" android:host="order" />
                <data android:scheme="gawone" android:host="chat" />
                <data android:scheme="gawone" android:host="payment" />
                <data android:scheme="gawone" android:host="support" />'''
new='''                <data android:scheme="gawone" />
                <data android:host="order" />
                <data android:host="chat" />
                <data android:host="payment" />
                <data android:host="support" />'''
if old not in m:
    raise SystemExit("deep-link manifest pattern not found")
manifest.write_text(m.replace(old,new))

(root/"res/values/strings.xml").write_text('''<resources>
    <string name="app_name">GAWONE Customer</string>
</resources>
''')
(root/"res/values/colors.xml").write_text('''<resources>
    <color name="gawone_green">#22C55E</color>
    <color name="gawone_icon_background">#FFFFFF</color>
</resources>
''')

xml=root/"res/xml"
xml.mkdir(parents=True,exist_ok=True)
(xml/"backup_rules.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="root" path="." />
    <exclude domain="file" path="." />
    <exclude domain="database" path="." />
    <exclude domain="sharedpref" path="." />
    <exclude domain="external" path="." />
</full-backup-content>
''')
(xml/"data_extraction_rules.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
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

draw=root/"res/drawable"
draw.mkdir(parents=True,exist_ok=True)
(draw/"ic_gawone_foreground.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="@color/gawone_green"
        android:strokeWidth="12"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M78,48 C77,34 67,26 53,26 C36,26 25,38 25,54 C25,70 37,81 53,81 C65,81 74,75 78,67" />
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="@color/gawone_green"
        android:strokeWidth="12"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M78,49 L78,70 C78,82 70,88 59,88" />
    <path
        android:fillColor="@color/gawone_green"
        android:pathData="M69,26 C75,15 88,13 96,18 C91,30 81,36 69,31 C69,29 69,28 69,26 Z" />
</vector>
''')

mip26=root/"res/mipmap-anydpi-v26"
mip26.mkdir(parents=True,exist_ok=True)
adaptive='''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/gawone_icon_background" />
    <foreground android:drawable="@drawable/ic_gawone_foreground" />
</adaptive-icon>
'''
(mip26/"ic_launcher.xml").write_text(adaptive)
(mip26/"ic_launcher_round.xml").write_text(adaptive)

print("RC3 static hardening applied: launcher branding, backup policy, deep links, resource cleanup")
