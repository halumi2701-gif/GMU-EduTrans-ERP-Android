from pathlib import Path

root = Path('gawone-mitra-stage4j')
main = root / 'app/src/main/java/site/garsyanimultiusaha/gawone/mitra/MainActivity.kt'
root_ui = root / 'app/src/main/java/site/garsyanimultiusaha/gawone/mitra/Stage4IRoot.kt'

m = main.read_text()
replacements = {
    'private val GawoneGreen = Color(0xFF0A6B47)': 'private val GawoneGreen = Color(0xFF3B82F6)',
    'private val GawoneSoft = Color(0xFFE8F4EE)': 'private val GawoneSoft = Color(0xFFEFF6FF)',
    'private val GawoneBg = Color(0xFFF7FAF8)': 'private val GawoneBg = Color(0xFFF8FAFC)',
}
for old, new in replacements.items():
    if old in m:
        m = m.replace(old, new)
main.write_text(m)

r = root_ui.read_text()
r = r.replace('color = Color(0xFFE8F4EE)', 'color = Color(0xFFEFF6FF)')
root_ui.write_text(r)

print('GAWONE Mitra UIUX V2 color architecture applied')
