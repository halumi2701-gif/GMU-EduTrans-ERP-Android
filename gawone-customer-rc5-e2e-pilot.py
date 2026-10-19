from pathlib import Path

root = Path("gawone-customer-production")
main = root / "app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt"
repo = root / "app/src/main/java/site/garsyanimultiusaha/gawone/CustomerRepository.kt"
build = root / "app/build.gradle.kts"

src = main.read_text()
old = 'else items(services.filter{it.availability=="AVAILABLE"}){s->ServiceCard(s){onService(s)}}'
new = '''else {
                val mapsReady=featureEnabled(runtime,"MAPS")
                val pilotServices=services.filter{it.availability=="AVAILABLE"}.filter{mapsReady || it.code=="CLEANING"}
                if(!mapsReady)item{PilotNoticeCard()}
                items(pilotServices){s->ServiceCard(s){onService(s)}}
            }'''
if old not in src and 'val pilotServices=services.filter' not in src:
    raise SystemExit("RC5 service-filter anchor not found")
if old in src:
    src = src.replace(old, new, 1)

marker = '@Composable fun ServiceCard(s:ServiceUi,onClick:()->Unit)'
notice = '''@Composable fun PilotNoticeCard(){
    Surface(color=Color(0xFFFFFBEB),shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFFDE68A)),modifier=Modifier.fillMaxWidth().padding(vertical=6.dp)){
        Column(Modifier.padding(13.dp)){
            Text("E2E Pilot • Cleaning",fontWeight=FontWeight.ExtraBold,fontSize=12.sp,color=Color(0xFF92400E))
            Text("Ride, Car, dan Delivery dibuka setelah Maps + trusted route production aktif. Pilot ini memakai transaksi backend nyata tanpa simulator.",fontSize=9.sp,color=Color(0xFF92400E),lineHeight=14.sp)
        }
    }
}

'''
if 'fun PilotNoticeCard()' not in src:
    if marker not in src:
        raise SystemExit("RC5 notice anchor not found")
    src = src.replace(marker, notice + marker, 1)

old_head = '''fun OrderDetailScreen(repo:CustomerRepository,orderId:String?,tick:Int,runtime:JSONObject?,onBack:()->Unit,onChat:()->Unit,onSupport:()->Unit,onCancel:()->Unit,onPayment:()->Unit,onRating:()->Unit){
    var d by remember{mutableStateOf<JSONObject?>(null)};var info by remember{mutableStateOf("")}'''
new_head = '''fun OrderDetailScreen(repo:CustomerRepository,orderId:String?,tick:Int,runtime:JSONObject?,onBack:()->Unit,onChat:()->Unit,onSupport:()->Unit,onCancel:()->Unit,onPayment:()->Unit,onRating:()->Unit){
    var d by remember{mutableStateOf<JSONObject?>(null)};var info by remember{mutableStateOf("")};var completing by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()'''
if old_head in src:
    src = src.replace(old_head, new_head, 1)
elif 'var completing by remember{mutableStateOf(false)}' not in src:
    raise SystemExit("RC5 order-detail state anchor not found")

payment_anchor = '''                if(status in listOf("WORK_COMPLETED","CUSTOMER_CONFIRMED","COMPLETED")){Spacer(Modifier.height(9.dp));Button(onClick=onPayment,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=GGreen)){Icon(Icons.Default.Payments,null);Spacer(Modifier.width(7.dp));Text("Pembayaran & Status")}}'''
completion_block = '''                if(status=="WORK_COMPLETED"){
                    Spacer(Modifier.height(9.dp))
                    Button(
                        enabled=!completing,
                        onClick={
                            val id=orderId
                            if(id!=null)scope.launch{
                                completing=true;info=""
                                try{repo.confirmCompletion(id);load()}catch(e:Exception){info=e.message?:"Konfirmasi penyelesaian gagal"}finally{completing=false}
                            }
                        },
                        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=GGreen)
                    ){
                        Icon(Icons.Default.TaskAlt,null);Spacer(Modifier.width(7.dp));Text(if(completing)"Mengonfirmasi…" else "Konfirmasi Pekerjaan Selesai")
                    }
                }
'''
if 'Konfirmasi Pekerjaan Selesai' not in src:
    if payment_anchor not in src:
        raise SystemExit("RC5 completion button anchor not found")
    src = src.replace(payment_anchor, completion_block + payment_anchor, 1)
main.write_text(src)

r = repo.read_text()
method_anchor = '    suspend fun startMatching(orderId:String):JSONObject = api.rpc("start_matching",JSONObject().put("p_order_id",orderId).put("p_slot_no",JSONObject.NULL).put("p_idempotency_key","match-$orderId")) as JSONObject\n'
completion_method = '    suspend fun confirmCompletion(orderId:String):JSONObject = api.rpc("confirm_order_completion",JSONObject().put("p_order_id",orderId)) as JSONObject\n'
if 'suspend fun confirmCompletion(' not in r:
    if method_anchor not in r:
        raise SystemExit("RC5 repository method anchor not found")
    r = r.replace(method_anchor, method_anchor + completion_method, 1)
repo.write_text(r)

b = build.read_text()
b = b.replace('versionCode = 13', 'versionCode = 14', 1)
b = b.replace('versionName = "1.0.3-customer-rc4"', 'versionName = "1.0.4-customer-e2e-pilot"', 1)
if 'versionCode = 14' not in b or '1.0.4-customer-e2e-pilot' not in b:
    raise SystemExit("RC5 version patch failed")
build.write_text(b)

print("GAWONE Customer RC5 E2E pilot patch applied: Cleaning gate + completion RPC")
