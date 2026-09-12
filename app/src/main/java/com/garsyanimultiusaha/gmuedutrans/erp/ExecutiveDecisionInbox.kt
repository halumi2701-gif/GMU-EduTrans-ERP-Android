package com.garsyanimultiusaha.gmuedutrans.erp

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ExecDecision(val key:String,val title:String,val body:String,val severity:String,val bookingId:String)

@Composable
fun GmuNativeAppWithExecutiveDecisionInbox(vm: MainViewModel) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithOwnerReviewGate(vm)
        val state = vm.state
        if (state is AppState.LoggedIn && vm.currentPage == AppPage.DASHBOARD) {
            val s = state.session
            if (ErpRoles.isDirector(s.profile.role) || ErpRoles.isManagerEduTrans(s.profile.role)) {
                ExecutiveDecisionInboxDock(vm, s)
            }
        }
    }
}

@Composable
private fun BoxScope.ExecutiveDecisionInboxDock(vm: MainViewModel, session: SessionState) {
    val prefs = LocalContext.current.getSharedPreferences("exec_decisions_${session.userId}", Context.MODE_PRIVATE)
    var open by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    val events = remember(vm.rows, session.userId, version) { buildExecDecisions(vm.table("staff_assignments"), session) }
    val unread = events.filterNot { prefs.getBoolean(it.key, false) }
    val critical = unread.any { it.severity == "CRITICAL" }

    if (unread.isNotEmpty()) {
        Surface(
            onClick = { open = true },
            modifier = Modifier.align(Alignment.TopStart).padding(top = 126.dp, start = 14.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (critical) Color(0xFFFFEDEC) else Color.White,
            shadowElevation = 5.dp
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text("Executive Decisions", fontSize = 10.sp, fontWeight = FontWeight.Black, color = GmuDark)
                Text("${unread.size} Decision Inbox", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (critical) GmuDanger else GmuGreen)
            }
        }
    }

    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Decision Inbox • Executive") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Keputusan Owner dan SLA untuk Executive Action Anda.", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.height(9.dp))
                events.forEach { e ->
                    val isUnread = !prefs.getBoolean(e.key, false)
                    val accent = when(e.severity){"CRITICAL"->GmuDanger;"WARNING"->GmuWarn;else->GmuGreen}
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if(isUnread) Color(0xFFFFFBF2) else Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(e.title, Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 11.sp, color = GmuDark)
                                Text(if(isUnread) "NEW" else e.severity, fontSize = 8.sp, fontWeight = FontWeight.Black, color = accent)
                            }
                            if(e.bookingId.isNotBlank()) Text(bookingLabel(vm,e.bookingId), fontSize = 9.sp, color = Color.Gray)
                            Spacer(Modifier.height(5.dp)); Text(e.body, fontSize = 10.sp, color = Color.DarkGray)
                            if(isUnread) TextButton(onClick={ prefs.edit().putBoolean(e.key,true).apply(); version++ }) { Text("Dibaca", fontSize = 9.sp) }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick={ events.forEach{ prefs.edit().putBoolean(it.key,true).apply() }; version++ }) { Text("Tandai semua dibaca") } },
        dismissButton = { TextButton(onClick={open=false}) { Text("Tutup") } }
    )
}

private fun buildExecDecisions(rows:List<ErpRow>, s:SessionState):List<ExecDecision>{
    val ids=setOf(s.userId,s.profile.id); val out=mutableListOf<ExecDecision>()
    rows.filter{it.text("title").startsWith("[EXEC]") && it.text("staff_id") in ids}.forEach{r->
        val notes=r.text("notes"); val label=r.text("title").removePrefix("[EXEC]").trim()
        val ri=notes.lastIndexOf("OWNER RETURN"); val ai=notes.lastIndexOf("OWNER APPROVED")
        if(ri>=0||ai>=0){
            val approved=ai>ri; val i=if(approved)ai else ri; val line=notes.substring(notes.lastIndexOf('\n',(i-1).coerceAtLeast(0)).let{if(it<0)0 else it+1}, notes.indexOf('\n',i).let{if(it<0)notes.length else it}).trim()
            out+=ExecDecision("owner:${r.id}:${line}", if(approved)"Action ditutup Owner" else "Action dikembalikan Owner", "$label • ${line.substringAfter(':',line).trim()}", if(approved)"INFO" else "WARNING", r.text("booking_id"))
        }
        val due=r.text("due_date"); val days=execDaysUntil(due); val st=r.text("status")
        if(days!=null && days<0 && st !in setOf("Done","Completed","Closed","Resolved","Awaiting Owner Review")) out+=ExecDecision("sla:${r.id}:$due","Executive Action melewati SLA","$label • deadline $due terlewat ${-days} hari.","CRITICAL",r.text("booking_id"))
    }
    return out.distinctBy{it.key}.take(20)
}

private fun execDaysUntil(date:String):Int?=runCatching{
    if(date.isBlank()) return@runCatching null
    val f=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}; val t=f.parse(date)?:return@runCatching null; val n=f.parse(f.format(Date()))?:return@runCatching null; ((t.time-n.time)/86400000L).toInt()
}.getOrNull()
