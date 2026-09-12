from pathlib import Path
import sys
root=Path(sys.argv[1])
main=root/'app/src/main'
manifest=main/'AndroidManifest.xml'
s=manifest.read_text()
old='''    <application
        android:name=".GawoneApp"
        android:allowBackup="false"
        android:fullBackupContent="false"
        android:label="GAWONE Mitra"'''
new='''    <application
        android:name=".GawoneApp"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="false"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="GAWONE Mitra"'''
if old not in s:
    raise SystemExit('application manifest anchor not found')
manifest.write_text(s.replace(old,new,1))

xml=main/'res/xml'
xml.mkdir(parents=True,exist_ok=True)
(xml/'data_extraction_rules.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
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

values=main/'res/values'
values.mkdir(parents=True,exist_ok=True)
(values/'colors.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="gawone_icon_background">#22C55E</color>
</resources>
''')

drawable=main/'res/drawable'
drawable.mkdir(parents=True,exist_ok=True)
(drawable/'ic_gawone_foreground.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
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

mip=main/'res/mipmap-anydpi'
mip.mkdir(parents=True,exist_ok=True)
adaptive='''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/gawone_icon_background" />
    <foreground android:drawable="@drawable/ic_gawone_foreground" />
</adaptive-icon>
'''
(mip/'ic_launcher.xml').write_text(adaptive)
(mip/'ic_launcher_round.xml').write_text(adaptive)

mip33=main/'res/mipmap-anydpi-v33'
mip33.mkdir(parents=True,exist_ok=True)
themed='''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/gawone_icon_background" />
    <foreground android:drawable="@drawable/ic_gawone_foreground" />
    <monochrome android:drawable="@drawable/ic_gawone_foreground" />
</adaptive-icon>
'''
(mip33/'ic_launcher.xml').write_text(themed)
(mip33/'ic_launcher_round.xml').write_text(themed)
print('Mitra RC3 static hardening applied')