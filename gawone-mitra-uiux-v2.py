from pathlib import Path

root = Path('gawone-mitra-stage4j')
main = root / 'app/src/main/java/site/garsyanimultiusaha/gawone/mitra/MainActivity.kt'
root_ui = root / 'app/src/main/java/site/garsyanimultiusaha/gawone/mitra/Stage4IRoot.kt'
build = root / 'app/build.gradle.kts'

m = main.read_text()
replacements = {
    'private val GawoneGreen = Color(0xFF0A6B47)': 'private val GawoneGreen = Color(0xFF3B82F6)',
    'private val GawoneSoft = Color(0xFFE8F4EE)': 'private val GawoneSoft = Color(0xFFEFF6FF)',
    'private val GawoneBg = Color(0xFFF7FAF8)': 'private val GawoneBg = Color(0xFFF8FAFC)',
}
for old, new in replacements.items():
    if old in m:
        m = m.replace(old, new)

old_root = '''        setContent {
            Stage4IRoot {
                GawoneMitraStage4B()
            }
        }'''
new_root = '''        setContent {
            GawoneMitraTheme {
                Stage4IRoot {
                    GawoneMitraStage4B()
                }
            }
        }'''
if old_root in m:
    m = m.replace(old_root, new_root, 1)
elif 'GawoneMitraTheme {' not in m:
    raise SystemExit('Mitra root theme anchor missing')
main.write_text(m)

r = root_ui.read_text()
r = r.replace('color = Color(0xFFE8F4EE)', 'color = Color(0xFFEFF6FF)')
root_ui.write_text(r)

b = build.read_text()
b = b.replace('versionCode = 16', 'versionCode = 17', 1)
b = b.replace('versionName = "1.0.6-email-primary"', 'versionName = "1.0.7-uiux-v2"', 1)
if 'versionCode = 17' not in b or '1.0.7-uiux-v2' not in b:
    raise SystemExit('Mitra UIUX V2 version bump failed')
build.write_text(b)

print('GAWONE Mitra UIUX V2 architecture applied')
