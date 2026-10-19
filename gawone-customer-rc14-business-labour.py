from pathlib import Path
import sys

root=Path(sys.argv[1] if len(sys.argv)>1 else 'gawone-customer-production')
main=root/'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
repo=root/'app/src/main/java/site/garsyanimultiusaha/gawone/CustomerRepository.kt'
mapper=root/'app/src/main/java/site/garsyanimultiusaha/gawone/GawoneCustomerFailure.kt'
build=root/'app/build.gradle.kts'
for p in (main,repo,mapper,build):
    if not p.exists(): raise SystemExit(f'missing {p}')

s=main.read_text()
old='enum class AppScreen{HOME,ORDER_FORM,CONFIRM,ORDERS,ORDER_DETAIL,CHAT,SUPPORT,CANCEL,PAYMENT,RATING,ACCOUNT}'
new='enum class AppScreen{HOME,ORDER_FORM,CONFIRM,ORDERS,ORDER_DETAIL,CHAT,SUPPORT,CANCEL,PAYMENT,RATING,ACCOUNT,BUSINESS}'
if old not in s: raise SystemExit('AppScreen anchor missing')
s=s.replace(old,new,1)

old='AppScreen.HOME->HomeScreen(repo,runtime,rtStatus,onService={selectedService=it;orderDraft=OrderDraftUi();screen=AppScreen.ORDER_FORM},onOrders={screen=AppScreen.ORDERS},onAccount={screen=AppScreen.ACCOUNT})'
new='AppScreen.HOME->HomeScreen(repo,runtime,rtStatus,onService={selectedService=it;orderDraft=OrderDraftUi();screen=AppScreen.ORDER_FORM},onOrders={screen=AppScreen.ORDERS},onAccount={screen=AppScreen.ACCOUNT},onBusiness={screen=AppScreen.BUSINESS})'
if old not in s: raise SystemExit('Home root anchor missing')
s=s.replace(old,new,1)
old='AppScreen.ACCOUNT->AccountScreen(api,onBack={screen=AppScreen.HOME},onLogout={api.logout();currentOrderId=null;screen=AppScreen.HOME;logged=false})'
new='''AppScreen.ACCOUNT->AccountScreen(api,onBack={screen=AppScreen.HOME},onLogout={api.logout();currentOrderId=null;screen=AppScreen.HOME;logged=false})
                    AppScreen.BUSINESS->BusinessModeScreen(repo,runtime,onBack={screen=AppScreen.HOME},onOpenOrder={currentOrderId=it;screen=AppScreen.ORDER_DETAIL})'''
if old not in s: raise SystemExit('Account case anchor missing')
s=s.replace(old,new,1)

old='@Composable fun HomeScreen(repo:CustomerRepository,runtime:JSONObject?,rtStatus:String,onService:(ServiceUi)->Unit,onOrders:()->Unit,onAccount:()->Unit){'
new='@Composable fun HomeScreen(repo:CustomerRepository,runtime:JSONObject?,rtStatus:String,onService:(ServiceUi)->Unit,onOrders:()->Unit,onAccount:()->Unit,onBusiness:()->Unit){'
if old not in s: raise SystemExit('Home signature anchor missing')
s=s.replace(old,new,1)

old='''                Text("Pesan layanan dan pantau progresnya dari satu alur yang sama.",fontSize=11.sp,color=GMuted)
                Spacer(Modifier.height(14.dp))
                HomeSyncStrip(rtStatus,activeOrder)'''
new='''                Text("Pesan layanan dan pantau progresnya dari satu alur yang sama.",fontSize=11.sp,color=GMuted)
                Spacer(Modifier.height(12.dp))
                if(featureEnabled(runtime,"BUSINESS_MODE")){
                    BusinessModeStrip(onBusiness)
                    Spacer(Modifier.height(12.dp))
                }
                HomeSyncStrip(rtStatus,activeOrder)'''
if old not in s: raise SystemExit('Home business strip anchor missing')
s=s.replace(old,new,1)

anchor='''@Composable
fun OrderFormScreen(service:ServiceUi?,draft:OrderDraftUi,onBack:()->Unit,onContinue:(OrderDraftUi)->Unit){'''
block=r'''@Composable
fun BusinessModeStrip(onBusiness:()->Unit){
    Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,GBorder),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(6.dp),verticalAlignment=Alignment.CenterVertically){
            Surface(color=GSoft,shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f)){
                Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
                    Icon(Icons.Default.Person,null,tint=GGreen,modifier=Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Personal",fontWeight=FontWeight.Bold,fontSize=10.sp,color=GDark)
                }
            }
            Spacer(Modifier.width(6.dp))
            TextButton(onClick=onBusiness,modifier=Modifier.weight(1f)){
                Icon(Icons.Default.Work,null,tint=GMuted,modifier=Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Business",fontWeight=FontWeight.Bold,fontSize=10.sp,color=GMuted)
            }
        }
    }
}

@Composable
fun BusinessModeScreen(repo:CustomerRepository,runtime:JSONObject?,onBack:()->Unit,onOpenOrder:(String)->Unit){
    if(!featureEnabled(runtime,"BUSINESS_MODE")){
        FeatureUnavailableScreen("Business Mode",onBack)
        return
    }
    val context=androidx.compose.ui.platform.LocalContext.current
    val scope=rememberCoroutineScope()
    var businesses by remember{mutableStateOf<JSONArray?>(null)}
    var requests by remember{mutableStateOf<JSONArray?>(null)}
    var loading by remember{mutableStateOf(true)}
    var busy by remember{mutableStateOf(false)}
    var info by remember{mutableStateOf("")}
    var legalName by remember{mutableStateOf("")}
    var displayName by remember{mutableStateOf("")}
    var roleTitle by remember{mutableStateOf("")}
    var skillNotes by remember{mutableStateOf("")}
    var workAddress by remember{mutableStateOf("")}
    var workerCount by remember{mutableIntStateOf(10)}
    var scheduledStart by remember{mutableStateOf("")}
    var scheduledEnd by remember{mutableStateOf("")}
    var latitude by remember{mutableStateOf<Double?>(null)}
    var longitude by remember{mutableStateOf<Double?>(null)}

    suspend fun load(){
        loading=true
        try{
            businesses=repo.businesses()
            requests=repo.workforceRequests()
            info=""
        }catch(e:Exception){info=customerSafeError(e,"Business Mode belum dapat dimuat")}finally{loading=false}
    }
    LaunchedEffect(Unit){load()}

    val list=businesses?.let{a->(0 until a.length()).mapNotNull{a.optJSONObject(it)}}?:emptyList()
    val active=list.firstOrNull{it.optString("status").uppercase()=="ACTIVE" && it.optBoolean("isActive",true)}
    val pending=list.firstOrNull{it.optString("status").uppercase()!="ACTIVE"}

    fun pickTime(setValue:(String)->Unit){
        val base=java.util.Calendar.getInstance()
        android.app.DatePickerDialog(context,{_,year,month,day->
            android.app.TimePickerDialog(context,{_,hour,minute->
                val selected=java.util.Calendar.getInstance().apply{
                    set(java.util.Calendar.YEAR,year);set(java.util.Calendar.MONTH,month);set(java.util.Calendar.DAY_OF_MONTH,day)
                    set(java.util.Calendar.HOUR_OF_DAY,hour);set(java.util.Calendar.MINUTE,minute);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)
                }
                setValue(java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(selected.time))
            },base.get(java.util.Calendar.HOUR_OF_DAY),base.get(java.util.Calendar.MINUTE),true).show()
        },base.get(java.util.Calendar.YEAR),base.get(java.util.Calendar.MONTH),base.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }
    fun capture(){
        if(LocationBridge.hasLocationPermission(context)){
            val loc=LocationBridge.bestLastKnown(context)
            if(loc!=null){latitude=loc.latitude;longitude=loc.longitude;info="Lokasi kerja GPS tersimpan."} else info="Lokasi GPS belum tersedia."
        }else info="Izinkan lokasi dari pengaturan aplikasi bila ingin menyimpan koordinat kerja."
    }

    Column(Modifier.fillMaxSize()){
        TopBar("GAWONE Business",onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal=18.dp),contentPadding=PaddingValues(bottom=24.dp)){
            item{
                Spacer(Modifier.height(12.dp))
                Surface(color=GSoft,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.Work,null,tint=GGreen);Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)){Text("Business Mode",fontWeight=FontWeight.ExtraBold,fontSize=14.sp);Text("Permintaan tenaga kerja 10–500 orang dengan review, quotation, dan dispatch terkontrol.",fontSize=9.sp,color=GMuted,lineHeight=13.sp)}
                        AssistChip(onClick={},label={Text("BUSINESS",fontSize=8.sp)})
                    }
                }
                Spacer(Modifier.height(14.dp))
                if(loading) LoadingCard("Memuat akun bisnis…")
                if(info.isNotBlank()){Spacer(Modifier.height(8.dp));InfoBox(info)}
            }

            if(!loading && list.isEmpty()){
                item{
                    SectionTitle("Buat akun perusahaan")
                    GTextField(legalName,{legalName=it},"Nama legal perusahaan",KeyboardType.Text)
                    Spacer(Modifier.height(8.dp));GTextField(displayName,{displayName=it},"Nama tampilan",KeyboardType.Text)
                    Spacer(Modifier.height(10.dp))
                    Button(enabled=!busy&&legalName.length>=3&&displayName.length>=2,onClick={scope.launch{
                        busy=true;info=""
                        try{repo.createBusiness(legalName,displayName);legalName="";displayName="";load();info="Akun Business dibuat dan menunggu verifikasi Management."}
                        catch(e:Exception){info=customerSafeError(e,"Gagal membuat akun Business")}finally{busy=false}
                    }},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=GGreen)){
                        Text(if(busy)"Membuat…" else "Buat Akun Business")
                    }
                    Spacer(Modifier.height(14.dp));InfoBox("Akun perusahaan harus berstatus ACTIVE sebelum permintaan Labour dapat dikirim.")
                }
            } else if(!loading && active==null && pending!=null){
                item{
                    SectionTitle("Status perusahaan")
                    DataCard("Perusahaan",pending.optString("displayName"))
                    DataCard("Status",pending.optString("status"))
                    Spacer(Modifier.height(8.dp));WarningCard("Menunggu verifikasi","Management akan memverifikasi akun Business. Labour tetap dikunci sampai status perusahaan ACTIVE.")
                }
            } else if(!loading && active!=null){
                item{
                    SectionTitle("Akun aktif")
                    DataCard("Perusahaan",active.optString("displayName"))
                    DataCard("Peran",active.optString("memberRole"))
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Labour 10–500 tenaga")
                    GTextField(roleTitle,{roleTitle=it},"Posisi / kebutuhan tenaga",KeyboardType.Text)
                    Spacer(Modifier.height(8.dp));GTextField(skillNotes,{skillNotes=it},"Keterampilan / catatan kerja",KeyboardType.Text)
                    Spacer(Modifier.height(8.dp));GTextField(workAddress,{workAddress=it},"Alamat lokasi kerja",KeyboardType.Text)
                    Spacer(Modifier.height(10.dp))
                    Text("Jumlah tenaga",fontSize=10.sp,fontWeight=FontWeight.Bold,color=GDark)
                    Spacer(Modifier.height(6.dp));Stepper(workerCount,{workerCount=(workerCount+it).coerceIn(10,500)})
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={pickTime{scheduledStart=it}},modifier=Modifier.weight(1f)){Icon(Icons.Default.Event,null);Spacer(Modifier.width(5.dp));Text(if(scheduledStart.isBlank())"Mulai" else "Mulai ✓",fontSize=9.sp)}
                        OutlinedButton(onClick={pickTime{scheduledEnd=it}},modifier=Modifier.weight(1f)){Icon(Icons.Default.Event,null);Spacer(Modifier.width(5.dp));Text(if(scheduledEnd.isBlank())"Selesai" else "Selesai ✓",fontSize=9.sp)}
                    }
                    Spacer(Modifier.height(8.dp));OutlinedButton(onClick={capture()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.MyLocation,null);Spacer(Modifier.width(6.dp));Text(if(latitude!=null&&longitude!=null)"GPS lokasi kerja tersimpan" else "Simpan GPS lokasi kerja")}
                    Spacer(Modifier.height(10.dp))
                    val ready=roleTitle.trim().length>=2&&workAddress.trim().length>=5&&workerCount in 10..500&&scheduledStart.isNotBlank()&&scheduledEnd.isNotBlank()
                    Button(enabled=ready&&!busy,onClick={scope.launch{
                        busy=true;info=""
                        try{
                            val out=repo.createWorkforceRequest(active.getString("id"),workerCount,roleTitle,skillNotes,workAddress,scheduledStart,scheduledEnd,latitude,longitude)
                            info="Permintaan ${out.optInt("workerCount",workerCount)} tenaga terkirim. Status: ${out.optString("status","SUBMITTED")}."
                            roleTitle="";skillNotes="";workAddress="";workerCount=10;scheduledStart="";scheduledEnd="";latitude=null;longitude=null;load()
                        }catch(e:Exception){info=customerSafeError(e,"Permintaan Labour gagal dikirim")}finally{busy=false}
                    }},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=GGreen)){
                        Icon(Icons.Default.Groups,null);Spacer(Modifier.width(7.dp));Text(if(busy)"Mengirim…" else "Kirim Permintaan Labour")
                    }
                    Spacer(Modifier.height(18.dp));SectionTitle("Permintaan terbaru")
                }
            }

            if(!loading && active!=null){
                val reqs=requests?.let{a->(0 until a.length()).mapNotNull{a.optJSONObject(it)}}?:emptyList()
                if(reqs.isEmpty()) item{EmptyBusinessRequestCard()}
                else items(reqs){r->WorkforceRequestCard(r,onOpenOrder)}
            }
        }
    }
}

@Composable
fun WorkforceRequestCard(r:JSONObject,onOpenOrder:(String)->Unit){
    val status=r.optString("status").uppercase()
    val orderId=r.optString("converted_order_id").takeIf{it.isNotBlank()&&it!="null"}
    Card(modifier=Modifier.fillMaxWidth().padding(vertical=5.dp),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=androidx.compose.foundation.BorderStroke(1.dp,GBorder)){
        Column(Modifier.padding(14.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.Groups,null,tint=GGreen);Spacer(Modifier.width(8.dp));Text(r.optString("role_title","Labour"),fontWeight=FontWeight.ExtraBold,fontSize=12.sp,modifier=Modifier.weight(1f))
                Surface(color=if(status in listOf("APPROVED","CONVERTED"))GSoft else GNeutral,shape=RoundedCornerShape(999.dp)){Text(status,Modifier.padding(horizontal=8.dp,vertical=4.dp),fontSize=7.sp,fontWeight=FontWeight.Bold,color=if(status in listOf("APPROVED","CONVERTED"))GGreen else GMuted)}
            }
            Spacer(Modifier.height(6.dp));Text("${r.optInt("worker_count")} tenaga • ${r.optString("work_address")}",fontSize=9.sp,color=GMuted)
            val quote=r.optDouble("quoted_amount",0.0);val fee=r.optDouble("management_fee",0.0)
            if(quote>0){Spacer(Modifier.height(6.dp));Text("Quotation ${rupiah(quote)}${if(fee>0)" + fee ${rupiah(fee)}" else ""}",fontSize=9.sp,fontWeight=FontWeight.Bold,color=GDark)}
            if(orderId!=null){Spacer(Modifier.height(8.dp));OutlinedButton(onClick={onOpenOrder(orderId)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.ReceiptLong,null);Spacer(Modifier.width(6.dp));Text("Buka Order Labour")}}
        }
    }
}

@Composable
fun EmptyBusinessRequestCard(){
    Surface(color=GNeutral,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.History,null,tint=GMuted);Spacer(Modifier.width(9.dp));Text("Belum ada permintaan Labour.",fontSize=9.sp,color=GMuted)}
    }
}

'''+anchor
if anchor not in s: raise SystemExit('OrderForm anchor missing')
s=s.replace(anchor,block,1)
main.write_text(s)

r=repo.read_text()
anchor='''    suspend fun support(orderId:String?,category:String,subject:String,message:String):JSONObject = api.rpc("create_my_customer_support_ticket",JSONObject().put("p_category",category).put("p_subject",subject).put("p_message",message).put("p_order_id",orderId?:JSONObject.NULL)) as JSONObject
}'''
new='''    suspend fun support(orderId:String?,category:String,subject:String,message:String):JSONObject = api.rpc("create_my_customer_support_ticket",JSONObject().put("p_category",category).put("p_subject",subject).put("p_message",message).put("p_order_id",orderId?:JSONObject.NULL)) as JSONObject
    suspend fun businesses():JSONArray = api.rpc("get_my_businesses",JSONObject()) as JSONArray
    suspend fun createBusiness(legalName:String,displayName:String):JSONObject = api.rpc("create_business_account",JSONObject().put("p_legal_name",legalName).put("p_display_name",displayName).put("p_registration_no",JSONObject.NULL).put("p_tax_id",JSONObject.NULL)) as JSONObject
    suspend fun workforceRequests():JSONArray = api.rpc("get_my_bulk_workforce_requests",JSONObject().put("p_limit",50)) as JSONArray
    suspend fun createWorkforceRequest(businessId:String,workerCount:Int,roleTitle:String,skillNotes:String,workAddress:String,scheduledStart:String,scheduledEnd:String,latitude:Double?,longitude:Double?):JSONObject{
        val clientRef="ANDROID-LABOUR-${UUID.randomUUID()}"
        val p=JSONObject()
            .put("p_business_id",businessId)
            .put("p_worker_count",workerCount)
            .put("p_role_title",roleTitle)
            .put("p_work_address",workAddress)
            .put("p_scheduled_start",scheduledStart)
            .put("p_scheduled_end",scheduledEnd)
            .put("p_skill_notes",skillNotes.ifBlank{JSONObject.NULL})
            .put("p_latitude",latitude?:JSONObject.NULL)
            .put("p_longitude",longitude?:JSONObject.NULL)
            .put("p_client_reference",clientRef)
        return api.rpc("create_bulk_workforce_request",p) as JSONObject
    }
}'''
if anchor not in r: raise SystemExit('Repository tail anchor missing')
r=r.replace(anchor,new,1)
repo.write_text(r)

m=mapper.read_text()
anchor='''        if (has("MAPS_UNAVAILABLE", "CUSTOMER_MAPS_FEATURE_DISABLED", "MAPS_FEATURE_DISABLED")) {'''
block='''        if (has("BUSINESS_NOT_ACTIVE", "BUSINESS_ACCOUNT_REQUIRED")) {
            return CustomerFailureUi("BUSINESS_NOT_ACTIVE","Akun Business belum aktif","Akun perusahaan harus diverifikasi dan berstatus ACTIVE sebelum mengirim permintaan Labour.",false,CustomerRecoveryAction.CONTACT_SUPPORT)
        }
        if (has("BUSINESS_ACCESS_DENIED")) {
            return CustomerFailureUi("BUSINESS_ACCESS_DENIED","Akses Business tidak tersedia","Akun ini tidak memiliki akses aktif ke perusahaan tersebut.",false,CustomerRecoveryAction.CONTACT_SUPPORT)
        }
        if (has("WORKER_COUNT_OUT_OF_RANGE", "INVALID_WORKER_COUNT")) {
            return CustomerFailureUi("WORKER_COUNT_OUT_OF_RANGE","Jumlah tenaga tidak sesuai","Permintaan Labour harus berisi 10 sampai 500 tenaga.",false,CustomerRecoveryAction.RETRY)
        }
        if (has("WORK_SCHEDULE_IN_PAST", "INVALID_WORK_SCHEDULE", "BOOKING_NOTICE_TOO_SHORT")) {
            return CustomerFailureUi("INVALID_WORK_SCHEDULE","Jadwal perlu diperbaiki","Pilih waktu mulai di masa depan dan pastikan waktu selesai setelah waktu mulai.",false,CustomerRecoveryAction.CHANGE_SCHEDULE)
        }
        if (has("BULK_WORKFORCE_UNAVAILABLE")) {
            return CustomerFailureUi("BULK_WORKFORCE_UNAVAILABLE","Labour belum tersedia","Layanan Labour sedang disiapkan untuk akun Business ini.",true,CustomerRecoveryAction.RETRY)
        }
        if (has("MAPS_UNAVAILABLE", "CUSTOMER_MAPS_FEATURE_DISABLED", "MAPS_FEATURE_DISABLED")) {'''
if anchor not in m: raise SystemExit('Mapper maps anchor missing')
m=m.replace(anchor,block,1)
mapper.write_text(m)

b=build.read_text()
if 'versionCode = 21' not in b or 'versionName = "1.0.11-mobility-foundation-rc12"' not in b:
    raise SystemExit('RC12 version anchor missing')
b=b.replace('versionCode = 21','versionCode = 23',1)
b=b.replace('versionName = "1.0.11-mobility-foundation-rc12"','versionName = "1.0.13-business-labour-rc14"',1)
build.write_text(b)

checks={
    main:['AppScreen.BUSINESS','BusinessModeScreen','BusinessModeStrip','Labour 10–500 tenaga','Kirim Permintaan Labour','WorkforceRequestCard'],
    repo:['get_my_businesses','create_business_account','get_my_bulk_workforce_requests','create_bulk_workforce_request'],
    mapper:['BUSINESS_NOT_ACTIVE','WORKER_COUNT_OUT_OF_RANGE','BULK_WORKFORCE_UNAVAILABLE'],
    build:['versionCode = 23','versionName = "1.0.13-business-labour-rc14"']
}
for path,tokens in checks.items():
    text=path.read_text()
    for token in tokens:
        if token not in text: raise SystemExit(f'verification missing {token} in {path}')
print('GAWONE Customer RC14 Business Mode + Labour applied')
