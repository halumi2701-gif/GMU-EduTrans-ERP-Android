from pathlib import Path

pkg = Path('app/src/main/java/site/garsyanimultiusaha/gawone/management')
main = pkg / 'FullMainActivity.kt'
build = Path('app/build.gradle.kts')

if not main.exists() or not build.exists():
    raise SystemExit('GAWONE Management source missing')

s = main.read_text()

if 'import androidx.compose.foundation.lazy.LazyRow' not in s:
    s = s.replace(
        'import androidx.compose.foundation.lazy.LazyColumn\n',
        'import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.LazyRow\n',
        1
    )

start = s.find('    private fun parseWorkspaces(nav: JSONObject): List<String> {')
end = s.find('\n\n    private fun loadQueue', start)
if start < 0 or end < 0:
    raise SystemExit('parseWorkspaces block missing')

new_parse = '''    private fun parseWorkspaces(nav: JSONObject): List<String> {
        val navigation = nav.optJSONArray("navigation") ?: JSONArray()
        val allowedCodes = buildSet {
            for (i in 0 until navigation.length()) {
                val code = navigation.optJSONObject(i)?.optString("code").orEmpty().uppercase()
                if (code.isNotBlank()) add(code)
            }
        }
        val workspaceOrder = listOf(
            "PARTNER_REVIEW", "BUSINESS", "LABOUR", "DISPATCH", "SUPPORT", "PAYOUT", "AUDIT"
        )
        return workspaceOrder.filter { it in allowedCodes }
    }'''

s = s[:start] + new_parse + s[end:]

s = s.replace(
    'val navWorkspaces = ui.workspaces.take(5)',
    'val navWorkspaces = ui.workspaces.filter { it in setOf("PARTNER_REVIEW", "BUSINESS", "LABOUR", "DISPATCH", "SUPPORT") }.take(5)',
    1
)

old_icon = '''                        val icon = when (workspace) {
                            "PARTNER_REVIEW" -> Icons.Default.Groups
                            "DISPATCH" -> Icons.Default.LocalShipping
                            "SUPPORT" -> Icons.Default.SupportAgent
                            "PAYOUT" -> Icons.Default.AccountBalanceWallet
                            else -> Icons.Default.History
                        }
                        val label = when (workspace) {
                            "PARTNER_REVIEW" -> "Mitra"
                            "DISPATCH" -> "Order"
                            "SUPPORT" -> "Support"
                            "PAYOUT" -> "Keuangan"
                            else -> "Audit"
                        }'''
new_icon = '''                        val icon = when (workspace) {
                            "PARTNER_REVIEW" -> Icons.Default.Groups
                            "BUSINESS" -> Icons.Default.Business
                            "LABOUR" -> Icons.Default.Engineering
                            "DISPATCH" -> Icons.Default.LocalShipping
                            "SUPPORT" -> Icons.Default.SupportAgent
                            "PAYOUT" -> Icons.Default.AccountBalanceWallet
                            else -> Icons.Default.History
                        }
                        val label = when (workspace) {
                            "PARTNER_REVIEW" -> "Mitra"
                            "BUSINESS" -> "Bisnis"
                            "LABOUR" -> "Labour"
                            "DISPATCH" -> "Order"
                            "SUPPORT" -> "Support"
                            "PAYOUT" -> "Keuangan"
                            else -> "Audit"
                        }'''
if old_icon not in s:
    raise SystemExit('bottom navigation mapping missing')
s = s.replace(old_icon, new_icon, 1)

header_anchor = '''            item {
                GawoneManagementBrandHeader(
                    subtitle = "${ui.backendContract} • Live Operations",
                    trailing = { IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Keluar") } }
                )
            }
            item {
                Surface('''
workspace_strip = '''            item {
                GawoneManagementBrandHeader(
                    subtitle = "${ui.backendContract} • Live Operations",
                    trailing = { IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Keluar") } }
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.workspaces, key = { it }) { workspace ->
                        FilterChip(
                            selected = ui.activeWorkspace == workspace,
                            onClick = { onWorkspace(workspace) },
                            label = {
                                Text(
                                    when (workspace) {
                                        "PARTNER_REVIEW" -> "Mitra & KYC"
                                        "BUSINESS" -> "Bisnis"
                                        "LABOUR" -> "Labour"
                                        "DISPATCH" -> "Dispatch"
                                        "SUPPORT" -> "Support"
                                        "PAYOUT" -> "Payout"
                                        "AUDIT" -> "Audit"
                                        else -> workspace.replace('_', ' ')
                                    }
                                )
                            }
                        )
                    }
                }
            }
            item {
                Surface('''
if header_anchor not in s:
    raise SystemExit('dashboard header anchor missing')
s = s.replace(header_anchor, workspace_strip, 1)

main.write_text(s)

b = build.read_text()
if 'versionCode = 8' not in b or 'versionName = "1.0.6-business-labour-m2.23"' not in b:
    raise SystemExit('M2.23 version anchor missing')
b = b.replace('versionCode = 8', 'versionCode = 9', 1)
b = b.replace('versionName = "1.0.6-business-labour-m2.23"', 'versionName = "1.0.7-gated-nav-m2.24"', 1)
build.write_text(b)

out = main.read_text()
if 'nav.optJSONArray("navigation")' not in out:
    raise SystemExit('authoritative navigation parsing missing')
if '"BUSINESS" -> "Bisnis"' not in out or '"LABOUR" -> "Labour"' not in out:
    raise SystemExit('Business/Labour labels missing')
if 'LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp))' not in out:
    raise SystemExit('all-workspace strip missing')
if 'versionCode = 9' not in build.read_text():
    raise SystemExit('M2.24 version bump missing')

print('GAWONE Management M2.24 gated navigation applied')
