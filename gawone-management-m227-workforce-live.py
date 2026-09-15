from pathlib import Path

pkg = Path('app/src/main/java/site/garsyanimultiusaha/gawone/management')
main = pkg / 'FullMainActivity.kt'
build = Path('app/build.gradle.kts')
for p in (main, build):
    if not p.exists():
        raise SystemExit(f'missing {p}')

s = main.read_text()

old = '''data class LabourDispatchSlotUi(
    val slotId: String,
    val slotNo: Int,
    val slotStatus: String,
    val dispatchId: String,
    val dispatchStatus: String,
    val assignmentStatus: String,
    val partnerId: String,
    val partnerName: String,
    val partnerAvailability: String,
    val partnerRating: Double,
    val recoveryActions: List<String>
)'''
new = '''data class LabourDispatchSlotUi(
    val slotId: String,
    val slotNo: Int,
    val slotStatus: String,
    val dispatchId: String,
    val dispatchStatus: String,
    val assignmentStatus: String,
    val partnerId: String,
    val partnerName: String,
    val partnerAvailability: String,
    val partnerRating: Double,
    val recoveryActions: List<String>,
    val lastAssignmentStatus: String,
    val lastEventType: String,
    val lastEventAt: String,
    val openIssueCount: Int
)'''
if old not in s:
    raise SystemExit('M2.26 slot model anchor missing')
s = s.replace(old, new, 1)

old = '''data class LabourDispatchBoardUi(
    val orderNo: String,
    val orderStatus: String,
    val requiredWorkers: Int,
    val assignedWorkers: Int,
    val openWorkers: Int,
    val waitingDispatch: Int,
    val coveragePercent: Double,
    val canAssign: Boolean,
    val slots: List<LabourDispatchSlotUi>
)'''
new = '''data class LabourDispatchBoardUi(
    val orderNo: String,
    val orderStatus: String,
    val requiredWorkers: Int,
    val assignedWorkers: Int,
    val openWorkers: Int,
    val waitingDispatch: Int,
    val coveragePercent: Double,
    val attendancePercent: Double,
    val completionPercent: Double,
    val enRouteWorkers: Int,
    val arrivedWorkers: Int,
    val checkedInWorkers: Int,
    val workingWorkers: Int,
    val completedWorkers: Int,
    val noShowEvents: Int,
    val openIssues: Int,
    val canAssign: Boolean,
    val slots: List<LabourDispatchSlotUi>
)'''
if old not in s:
    raise SystemExit('M2.26 board model anchor missing')
s = s.replace(old, new, 1)

old = '''                partnerAvailability = first(item, "partnerAvailability"),
                partnerRating = item.optDouble("partnerRating", 0.0),
                recoveryActions = recoveryActions
            )'''
new = '''                partnerAvailability = first(item, "partnerAvailability"),
                partnerRating = item.optDouble("partnerRating", 0.0),
                recoveryActions = recoveryActions,
                lastAssignmentStatus = first(item, "lastAssignmentStatus"),
                lastEventType = first(item, "lastEventType"),
                lastEventAt = first(item, "lastEventAt"),
                openIssueCount = item.optInt("openIssueCount", 0)
            )'''
if old not in s:
    raise SystemExit('M2.26 slot parser anchor missing')
s = s.replace(old, new, 1)

old = '''            waitingDispatch = result.optInt("waitingDispatch", slots.count { it.dispatchStatus == "WAITING" }),
            coveragePercent = result.optDouble("coveragePercent", 0.0),
            canAssign = result.optBoolean("canAssign", false),'''
new = '''            waitingDispatch = result.optInt("waitingDispatch", slots.count { it.dispatchStatus == "WAITING" }),
            coveragePercent = result.optDouble("coveragePercent", 0.0),
            attendancePercent = result.optDouble("attendancePercent", 0.0),
            completionPercent = result.optDouble("completionPercent", 0.0),
            enRouteWorkers = result.optInt("enRouteWorkers", 0),
            arrivedWorkers = result.optInt("arrivedWorkers", 0),
            checkedInWorkers = result.optInt("checkedInWorkers", 0),
            workingWorkers = result.optInt("workingWorkers", 0),
            completedWorkers = result.optInt("completedWorkers", 0),
            noShowEvents = result.optInt("noShowEvents", 0),
            openIssues = result.optInt("openIssues", 0),
            canAssign = result.optBoolean("canAssign", false),'''
if old not in s:
    raise SystemExit('M2.26 board parser anchor missing')
s = s.replace(old, new, 1)

old = '''                            Text("${board.assignedWorkers}/${board.requiredWorkers} tenaga terisi • ${board.coveragePercent}% coverage", fontWeight = FontWeight.Bold)
                            Text("${board.openWorkers} slot terbuka • ${board.waitingDispatch} menunggu dispatch", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            Text(if (board.canAssign) "Pilih Mitra satu per satu atau isi otomatis. Recovery aman tersedia sebelum pekerjaan dimulai." else "Mode pantau. Akun ini tidak memiliki izin assignment.", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)'''
new = '''                            Text("${board.assignedWorkers}/${board.requiredWorkers} tenaga terisi • ${board.coveragePercent}% coverage", fontWeight = FontWeight.Bold)
                            Text("Kehadiran ${board.attendancePercent}% • Selesai ${board.completionPercent}%", fontWeight = FontWeight.Bold, color = GawoneManagementTokens.Primary)
                            Text("Menuju ${board.enRouteWorkers} • Tiba ${board.arrivedWorkers} • Check-in ${board.checkedInWorkers} • Bekerja ${board.workingWorkers} • Selesai ${board.completedWorkers}", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            Text("${board.openWorkers} slot terbuka • ${board.waitingDispatch} menunggu dispatch", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            if (board.noShowEvents > 0 || board.openIssues > 0) {
                                Text("Perhatian: ${board.noShowEvents} no-show • ${board.openIssues} isu terbuka", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                            Text(if (board.canAssign) "Status diperbarui otomatis dari backend. Pilih Mitra, isi otomatis, atau gunakan recovery bila perlu." else "Mode pantau. Status tetap diperbarui dari backend.", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)'''
if old not in s:
    raise SystemExit('M2.26 live summary anchor missing')
s = s.replace(old, new, 1)

old = '''                            if (slot.partnerName.isNotBlank()) {
                                Text(slot.partnerName, fontWeight = FontWeight.Bold)
                                Text("${slot.partnerAvailability.ifBlank { "UNKNOWN" }} • Rating ${slot.partnerRating}", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            } else {
                                Text("Belum ada Mitra", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            }
                            if (slot.dispatchStatus == "WAITING" && board.canAssign && slot.dispatchId.isNotBlank()) {'''
new = '''                            if (slot.partnerName.isNotBlank()) {
                                Text(slot.partnerName, fontWeight = FontWeight.Bold)
                                Text("${slot.partnerAvailability.ifBlank { "UNKNOWN" }} • Rating ${slot.partnerRating}", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            } else {
                                Text("Belum ada Mitra", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            }
                            if (slot.lastAssignmentStatus.isNotBlank() || slot.lastEventType.isNotBlank()) {
                                Text("Aktivitas: ${slot.lastEventType.ifBlank { slot.lastAssignmentStatus }}${if (slot.lastEventAt.isNotBlank()) " • ${slot.lastEventAt.take(19)}" else ""}", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            }
                            if (slot.openIssueCount > 0) {
                                Text("${slot.openIssueCount} isu terbuka pada slot ini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                            if (slot.dispatchStatus == "WAITING" && board.canAssign && slot.dispatchId.isNotBlank()) {'''
if old not in s:
    raise SystemExit('M2.26 slot live anchor missing')
s = s.replace(old, new, 1)

main.write_text(s)

b = build.read_text()
if 'versionCode = 11' not in b or 'versionName = "1.0.9-labour-recovery-m2.26"' not in b:
    raise SystemExit('M2.26 version anchor missing')
b = b.replace('versionCode = 11', 'versionCode = 12', 1)
b = b.replace('versionName = "1.0.9-labour-recovery-m2.26"', 'versionName = "1.0.10-workforce-live-m2.27"', 1)
build.write_text(b)

out = main.read_text()
for token in ('attendancePercent', 'completionPercent', 'noShowEvents', 'openIssues', 'Status diperbarui otomatis dari backend'):
    if token not in out:
        raise SystemExit(f'M2.27 token missing: {token}')
if 'versionCode = 12' not in build.read_text():
    raise SystemExit('M2.27 version bump failed')
print('GAWONE Management M2.27 Workforce Live Operations applied')
