package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val TREND_EXEC_PREFIX = "[EXEC]"
private const val TREND_OWNER_REVIEW = "Awaiting Owner Review"

private data class TrendSnapshot(val total:Int,val submitted:Int,val slaEvaluated:Int,val slaMet:Int,val returnEvents:Int,val score:Int)
private data class PicTrend(val id:String,val name:String,val role:String,val current:TrendSnapshot,val previous:TrendSnapshot,val delta:Int,val openOverdue:Int,val interventionReasons:List<String>)
private data class TrendIntervention(val key:String,val pic:String,val title:String,val reason:String,val due:String,val severity:Int)

@Composable
fun GmuNativeAppWithExecutiveTrendAccountability(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    var periodDays by remember { mutableIntStateOf(30) }
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithExecutivePerformanceScorecard(vm)
        val state = vm.state
        if (state is AppState.LoggedIn && state.session.profile.role == ErpRoles.OWNER && vm.currentPage == AppPage.DASHBOARD) {
            val trends = remember(vm.rows, periodDays) { buildPicTrends(vm, periodDays) }
            val interventions = remember(vm.rows, periodDays) { buildTrendInterventions(vm, trends, periodDays) }
            Surface(onClick={open=true},modifier=Modifier.align(Alignment.TopEnd).padding(top=242.dp,end=16.dp),shape=RoundedCornerShape(18.dp),color=if(interventions.any{it.severity>=3}) Color(0xFFFFECEC) else Color.White,shadowElevation=5.dp){
                Column(Modifier.padding(horizontal=12.dp,vertical=9.dp),horizontalAlignment=Alignment.End){
                    Text("Accountability Trend",fontWeight=FontWeight.Black,color=GmuDark,fontSize=11.sp)
                    Text(if(interventions.isEmpty()) "$periodDays hari • stabil" else "${interventions.size} perlu intervensi • $periodDays hari",color=if(interventions.isEmpty()) GmuGreen else GmuDanger,fontWeight=FontWeight.Bold,fontSize=9.sp)
                }
            }
            if(open) ExecutiveTrendDialog(vm,periodDays,{periodDays=it},trends,interventions){open=false}
        }
    }
}

@Composable
private fun ExecutiveTrendDialog(vm:MainViewModel,periodDays:Int,onPeriodChange:(Int)->Unit,trends:List<PicTrend>,interventions:List<TrendIntervention>,onDismiss:()->Unit){
    val allRows=trendAssignments(vm)
    val currentTeam=trendSnapshot(trendWindow(allRows,periodDays,false))
    val previousTeam=trendSnapshot(trendWindow(allRows,periodDays,true))
    val teamDelta=currentTeam.score-previousTeam.score
    val overdueOpen=allRows.count{trendIsOpenPicWork(it)&&trendDaysUntil(it.text("due_date"))?.let{d->d<0}==true}
    Dialog(onDismissRequest=onDismiss){
        Surface(modifier=Modifier.fillMaxWidth().fillMaxHeight(.92f),shape=RoundedCornerShape(26.dp),color=Color(0xFFF7F8FA)){
            Column(Modifier.fillMaxSize().padding(18.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Column(Modifier.weight(1f)){Text("Executive Trend & Accountability",fontWeight=FontWeight.Black,fontSize=19.sp,color=GmuDark);Text("Owner view • tren cohort Executive Action dan intervensi",fontSize=10.sp,color=Color.Gray)}
                    TextButton(onClick=onDismiss){Text("Tutup")}
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(7,30,90).forEach{days->FilterChip(selected=periodDays==days,onClick={onPeriodChange(days)},label={Text("$days hari",fontSize=9.sp)})}}
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    TrendMetric("Score",currentTeam.score.toString(),trendScoreColor(currentTeam.score),Modifier.weight(1f));TrendMetric("vs prev",trendSigned(teamDelta),trendDeltaColor(teamDelta),Modifier.weight(1f));TrendMetric("Overdue",overdueOpen.toString(),if(overdueOpen>0) GmuDanger else GmuGreen,Modifier.weight(1f));TrendMetric("Return",currentTeam.returnEvents.toString(),if(currentTeam.returnEvents>0) GmuWarn else GmuGreen,Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
                Surface(shape=RoundedCornerShape(14.dp),color=Color(0xFFF0F3EE)){Text("Periode memakai cohort berdasarkan tanggal assignment. Score dibandingkan dengan periode sebelumnya yang sama panjang. SLA PIC berhenti pada tanggal PIC COMPLETION; Owner Review tidak membebani PIC.",Modifier.fillMaxWidth().padding(11.dp),fontSize=9.sp,color=Color.DarkGray)}
                Spacer(Modifier.height(9.dp))
                LazyColumn(modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=8.dp)){
                    item{Text("Perlu Intervensi Owner",fontWeight=FontWeight.Black,fontSize=12.sp,color=GmuDark);Text("Hanya exception berulang/menurun yang dinaikkan ke sini.",fontSize=9.sp,color=Color.Gray)}
                    if(interventions.isEmpty()) item{Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFEAF7EF))){Text("Tidak ada pola accountability yang membutuhkan intervensi Owner saat ini.",Modifier.fillMaxWidth().padding(13.dp),fontSize=10.sp,color=GmuGreen)}} else items(interventions.take(12),key={it.key}){TrendInterventionCard(it)}
                    item{Spacer(Modifier.height(3.dp));Text("Trend per PIC",fontWeight=FontWeight.Black,fontSize=12.sp,color=GmuDark)}
                    if(trends.isEmpty()) item{Text("Belum ada Direktur/Manager aktif untuk trend accountability.",fontSize=10.sp,color=Color.Gray)} else items(trends,key={it.id}){PicTrendCard(it,periodDays)}
                }
                OutlinedButton(onClick={vm.loadAll()},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Text("Refresh Accountability",fontSize=10.sp)}
            }
        }
    }
}

@Composable private fun TrendMetric(label:String,value:String,accent:Color,modifier:Modifier=Modifier){Card(modifier=modifier,shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(10.dp)){Text(value,fontWeight=FontWeight.Black,fontSize=16.sp,color=accent);Text(label,fontSize=8.sp,color=Color.Gray)}}}

@Composable private fun TrendInterventionCard(item:TrendIntervention){
    val accent=when(item.severity){3->GmuDanger;2->GmuWarn;else->GmuGold}
    Card(shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.fillMaxWidth().padding(13.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(item.pic,fontSize=9.sp,fontWeight=FontWeight.Black,color=accent);Text(item.title,fontSize=11.sp,fontWeight=FontWeight.Black,color=GmuDark)};Text(when(item.severity){3->"CRITICAL";2->"WATCH";else->"REVIEW"},fontSize=8.sp,fontWeight=FontWeight.Black,color=accent)};Spacer(Modifier.height(4.dp));Text(item.reason,fontSize=9.sp,color=Color.Gray);if(item.due.isNotBlank()) Text("Deadline ${item.due}",fontSize=9.sp,color=accent,fontWeight=FontWeight.SemiBold)}}
}

@Composable private fun PicTrendCard(item:PicTrend,periodDays:Int){
    val accent=trendScoreColor(item.current.score);val current=item.current;val executionPct=if(current.total==0)0 else(current.submitted*100.0/current.total).roundToInt();val slaPct=if(current.slaEvaluated==0)null else(current.slaMet*100.0/current.slaEvaluated).roundToInt()
    Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.fillMaxWidth().padding(14.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){Column(Modifier.weight(1f)){Text(item.name,fontWeight=FontWeight.Black,fontSize=12.sp,color=GmuDark);Text("${ErpRoles.displayName(item.role)} • $periodDays hari",fontSize=9.sp,color=Color.Gray)};Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){Text(trendSigned(item.delta),fontSize=9.sp,fontWeight=FontWeight.Black,color=trendDeltaColor(item.delta));Surface(shape=RoundedCornerShape(50),color=accent.copy(alpha=.12f)){Text(if(current.total==0)"N/A" else "${current.score}/100",Modifier.padding(horizontal=8.dp,vertical=4.dp),fontSize=9.sp,fontWeight=FontWeight.Black,color=accent)}}};Spacer(Modifier.height(8.dp));LinearProgressIndicator(progress={if(current.total==0)0f else current.score/100f},modifier=Modifier.fillMaxWidth().height(6.dp),color=accent,trackColor=Color(0xFFE8EBE8));Spacer(Modifier.height(8.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TrendSmallMetric("Action",current.total.toString());TrendSmallMetric("Execution","$executionPct%");TrendSmallMetric("SLA",slaPct?.let{"$it%"}?:"N/A");TrendSmallMetric("Return",current.returnEvents.toString(),if(current.returnEvents>0)GmuWarn else GmuDark);TrendSmallMetric("Overdue",item.openOverdue.toString(),if(item.openOverdue>0)GmuDanger else GmuDark)};if(item.interventionReasons.isNotEmpty()){Spacer(Modifier.height(8.dp));Surface(shape=RoundedCornerShape(12.dp),color=Color(0xFFFFF4E5)){Text(item.interventionReasons.joinToString(" • "),Modifier.fillMaxWidth().padding(9.dp),fontSize=9.sp,color=GmuWarn,fontWeight=FontWeight.SemiBold)}}}}
}

@Composable private fun TrendSmallMetric(label:String,value:String,color:Color=GmuDark){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(value,fontSize=9.sp,fontWeight=FontWeight.Black,color=color);Text(label,fontSize=7.sp,color=Color.Gray)}}

private fun buildPicTrends(vm:MainViewModel,days:Int):List<PicTrend>{
    val all=trendAssignments(vm);val profiles=vm.table("profiles").filter{it.text("is_active")!="false"&&(ErpRoles.isDirector(it.text("role"))||ErpRoles.isManagerEduTrans(it.text("role")))}
    return profiles.map{profile->val owned=all.filter{it.text("staff_id")==profile.id};val current=trendSnapshot(trendWindow(owned,days,false));val previous=trendSnapshot(trendWindow(owned,days,true));val delta=current.score-previous.score;val openOverdue=owned.count{trendIsOpenPicWork(it)&&trendDaysUntil(it.text("due_date"))?.let{d->d<0}==true};val reasons=buildList{if(openOverdue>=2)add("Repeat overdue: $openOverdue action");if(current.returnEvents>=2)add("Repeat return: ${current.returnEvents}x");if(current.total>=2&&current.score<70)add("Score periode <70");if(current.total>0&&previous.total>0&&delta<=-10)add("Score turun ${-delta} poin")};PicTrend(profile.id,profile.text("full_name").ifBlank{"Staff ${profile.id.take(8)}"},profile.text("role"),current,previous,delta,openOverdue,reasons)}.sortedWith(compareByDescending<PicTrend>{it.interventionReasons.isNotEmpty()}.thenByDescending{it.openOverdue}.thenBy{if(it.current.total==0)101 else it.current.score}.thenBy{it.name})
}

private fun buildTrendInterventions(vm:MainViewModel,trends:List<PicTrend>,days:Int):List<TrendIntervention>{
    val all=trendAssignments(vm);val profiles=vm.table("profiles").associateBy{it.id};val currentRows=trendWindow(all,days,false);val items=mutableListOf<TrendIntervention>()
    all.filter{trendIsOpenPicWork(it)}.filter{trendDaysUntil(it.text("due_date"))?.let{d->d<0}==true}.forEach{row->val late=-(trendDaysUntil(row.text("due_date"))?:0);val pic=profiles[row.text("staff_id")]?.text("full_name").orEmpty().ifBlank{"PIC ${row.text("staff_id").take(8)}"};items+=TrendIntervention("overdue:${row.id}",pic,row.text("title").removePrefix(TREND_EXEC_PREFIX).trim().ifBlank{"Executive action"},"SLA terlewat $late hari dan belum diajukan ke Owner.",row.text("due_date"),if(late>=3||row.text("priority").equals("Critical",true))3 else 2)}
    currentRows.filter{trendReturnCount(it)>=2&&!trendDone(it)}.forEach{row->val pic=profiles[row.text("staff_id")]?.text("full_name").orEmpty().ifBlank{"PIC ${row.text("staff_id").take(8)}"};items+=TrendIntervention("returns:${row.id}",pic,row.text("title").removePrefix(TREND_EXEC_PREFIX).trim().ifBlank{"Executive action"},"Action yang sama sudah dikembalikan Owner ${trendReturnCount(row)} kali.",row.text("due_date"),2)}
    trends.filter{it.interventionReasons.isNotEmpty()}.forEach{trend->val severity=when{trend.openOverdue>=2||(trend.current.total>=2&&trend.current.score<55)->3;trend.delta<=-10||trend.current.returnEvents>=2||(trend.current.total>=2&&trend.current.score<70)->2;else->1};items+=TrendIntervention("pic:${trend.id}:$days",trend.name,"Pola accountability ${ErpRoles.displayName(trend.role)}",trend.interventionReasons.joinToString(" • "),"",severity)}
    return items.distinctBy{it.key}.sortedWith(compareByDescending<TrendIntervention>{it.severity}.thenBy{it.due}.thenBy{it.pic})
}

private fun trendAssignments(vm:MainViewModel)=vm.table("staff_assignments").filter{it.text("title").startsWith(TREND_EXEC_PREFIX)}
private fun trendWindow(rows:List<ErpRow>,days:Int,previous:Boolean):List<ErpRow>{val today=trendToday();val start=trendShiftDay(today,if(previous)-(days*2-1) else -(days-1)).time;val end=trendShiftDay(today,if(previous)-days else 0).time;return rows.filter{trendCreatedDay(it)?.time?.let{x->x in start..end}==true}}
private fun trendSnapshot(rows:List<ErpRow>):TrendSnapshot{val total=rows.size;val submitted=rows.count{trendCompletionDay(it)!=null||it.text("status")==TREND_OWNER_REVIEW||trendDone(it)};val sla=rows.mapNotNull(::trendSlaResult);val met=sla.count{it};val returns=rows.sumOf(::trendReturnCount);val execution=if(total==0)0.0 else submitted.toDouble()/total;val slaRate=if(sla.isEmpty())1.0 else met.toDouble()/sla.size;val quality=if(total==0)1.0 else(1.0-returns.toDouble()/total).coerceIn(0.0,1.0);val score=if(total==0)0 else(execution*50+slaRate*35+quality*15).roundToInt().coerceIn(0,100);return TrendSnapshot(total,submitted,sla.size,met,returns,score)}
private fun trendSlaResult(row:ErpRow):Boolean?{val due=trendDueDay(row)?:return null;trendCompletionDay(row)?.let{return !it.after(due)};if(row.text("status")==TREND_OWNER_REVIEW||trendDone(row))return null;return if(trendToday().after(due))false else null}
private fun trendCreatedDay(row:ErpRow)=trendParseDay(row.text("created_at").take(10));private fun trendDueDay(row:ErpRow)=trendParseDay(row.text("due_date").take(10))
private fun trendCompletionDay(row:ErpRow):Date?{val m=Regex("PIC COMPLETION (\\d{4}-\\d{2}-\\d{2}) ").findAll(row.text("notes")).lastOrNull()?:return null;return trendParseDay(m.groupValues[1])}
private fun trendReturnCount(row:ErpRow)=Regex("OWNER RETURN").findAll(row.text("notes")).count()
private fun trendDone(row:ErpRow)=row.text("status") in setOf("Done","Completed","Closed","Resolved")
private fun trendIsOpenPicWork(row:ErpRow)=!trendDone(row)&&row.text("status")!=TREND_OWNER_REVIEW
private fun trendDaysUntil(date:String):Int?{val due=trendParseDay(date.take(10))?:return null;return((due.time-trendToday().time).toDouble()/86_400_000.0).roundToInt()}
private fun trendParseDay(value:String):Date?=runCatching{if(value.length<10)return@runCatching null;SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}.parse(value.take(10))}.getOrNull()
private fun trendToday():Date{val f=SimpleDateFormat("yyyy-MM-dd",Locale.US);return f.parse(f.format(Date()))?:Date()}
private fun trendShiftDay(base:Date,offset:Int):Date=Calendar.getInstance().run{time=base;add(Calendar.DAY_OF_MONTH,offset);time}
private fun trendScoreColor(score:Int)=when{score>=85->GmuGreen;score>=70->GmuGold;score>=55->GmuWarn;else->GmuDanger}
private fun trendDeltaColor(delta:Int)=when{delta>0->GmuGreen;delta<0->GmuDanger;else->Color.Gray}
private fun trendSigned(value:Int)=if(value>0)"+$value" else value.toString()
