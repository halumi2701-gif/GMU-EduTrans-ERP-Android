from pathlib import Path

root = Path('gawone-mitra-stage4j')
home = root / 'app/src/main/java/site/garsyanimultiusaha/gawone/mitra/Stage4JHomeShell.kt'
build = root / 'app/build.gradle.kts'

s = home.read_text()
anchor = '''                    Stage4JHero(dashboard, plan)
                    Stage4JStatusSummary(dashboard, plan)
'''
replacement = '''                    Stage4JHero(dashboard, plan)
                    Stage4JStatusSummary(dashboard, plan)
                    Stage4JLiveJourney(dashboard, plan)
                    Stage4JCrossAppSyncCard()
'''
if anchor not in s:
    raise SystemExit('Mitra home V3 anchor missing')
s = s.replace(anchor, replacement, 1)

insert = s.index('@Composable\nprivate fun MitraMessageCard')
helpers = r'''@Composable
private fun Stage4JLiveJourney(dashboard: Dashboard?, plan: KycPlan?) {
    val accountReady = dashboard?.accountStatus.equals("ACTIVE", true)
    val onboardingReady = dashboard?.onboardingStatus.equals("ACTIVE", true) || dashboard?.onboardingStatus.equals("VERIFIED", true)
    val online = dashboard?.availabilityStatus.equals("ONLINE", true)
    val steps = listOf(
        Triple("Akun", accountReady, "Akun Mitra aktif"),
        Triple("KYC", onboardingReady, "Verifikasi & layanan siap"),
        Triple("Online", online, "Siap menerima order"),
        Triple("Order", false, "Offer & pekerjaan aktif"),
        Triple("Selesai", false, "Pendapatan tercatat")
    )
    GawoneMitraCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Alur kerja Mitra", fontWeight = FontWeight.ExtraBold)
                Text(
                    plan?.serviceName ?: plan?.serviceCode ?: "Lengkapi layanan terlebih dahulu",
                    color = GawoneMitraTokens.Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            GawoneMitraStatusPill(if (online) "ONLINE" else "SIAPKAN", positive = online)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEach { step ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = if (step.second) GawoneMitraTokens.Primary else GawoneMitraTokens.PrimarySoft,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (step.second) {
                                Text("✓", color = Color.White, fontWeight = FontWeight.ExtraBold)
                            } else {
                                Text((steps.indexOf(step) + 1).toString(), color = GawoneMitraTokens.PrimaryStrong, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(step.first, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GawoneMitraTokens.Ink)
                }
            }
        }
        Text(
            "Status di atas mengikuti akun, onboarding, dan availability yang dibaca dari backend GAWONE.",
            color = GawoneMitraTokens.Muted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun Stage4JCrossAppSyncCard() {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, GawoneMitraTokens.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = GawoneMitraTokens.PrimarySoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("↔", color = GawoneMitraTokens.PrimaryStrong, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Sinkron dengan Customer & Management", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Offer, lifecycle pekerjaan, chat, wallet, dan payout tetap memakai backend authoritative yang sama.",
                    color = GawoneMitraTokens.Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            GawoneMitraStatusPill("LIVE", positive = true)
        }
    }
}

'''
s = s[:insert] + helpers + s[insert:]
home.write_text(s)

b = build.read_text()
b = b.replace('versionCode = 17', 'versionCode = 18', 1)
b = b.replace('versionName = "1.0.7-uiux-v2"', 'versionName = "1.0.8-uiux-v3"', 1)
if 'versionCode = 18' not in b or '1.0.8-uiux-v3' not in b:
    raise SystemExit('Mitra UIUX V3 version bump failed')
build.write_text(b)
print('GAWONE Mitra UIUX V3 applied')
