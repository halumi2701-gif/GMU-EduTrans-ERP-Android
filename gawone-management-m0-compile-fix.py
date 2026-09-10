from pathlib import Path
p=Path('gawone-management-pilot/app/src/main/java/site/garsyanimultiusaha/gawone/management/MainActivity.kt')
s=p.read_text()
old='@Composable private fun ManagementShell(api:ManagementApi,onLogout:()->Unit){'
new='@OptIn(ExperimentalMaterial3Api::class)\n@Composable private fun ManagementShell(api:ManagementApi,onLogout:()->Unit){'
if old not in s:
    raise SystemExit('ManagementShell anchor not found')
p.write_text(s.replace(old,new,1))
print('Management M0 ExperimentalMaterial3Api opt-in applied')
