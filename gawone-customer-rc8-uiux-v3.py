from pathlib import Path

root = Path('gawone-customer-production')
main = root / 'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
build = root / 'app/build.gradle.kts'

s = main.read_text()

home_start = s.index('@Composable fun HomeScreen(')
home_end = s.index('\n@Composable\nfun OrderFormScreen', home_start)
new_home = r'''@Composable fun HomeScreen(repo:CustomerRepository,runtime:JSONObject?,rtStatus:String,onService:(ServiceUi)->Unit,onOrders:()->Unit,onAccount:()->Unit){
    var services by remember{mutableStateOf<List<ServiceUi>>(emptyList())}
    var recentOrders by remember{mutableStateOf<List<OrderUi>>(emptyList())}
    var busy by remember{mutableStateOf(true)}
    var info by remember{mutableStateOf("")}
    suspend fun refreshHome(){
        busy=true
        try{
            if(!featureEnabled(runtime,"SERVICE_CATALOG")) throw IllegalStateException("Katalog layanan sementara dinonaktifkan melalui runtime gate.")
            val c=repo.catalog()
            services=jsonServices(c.optJSONArray("services"))
            recentOrders=runCatching{jsonOrders(repo.orders())}.getOrDefault(emptyList())
            info=""
        }catch(e:Exception){info=e.message?:"Gagal memuat beranda"}finally{busy=false}
    }
    LaunchedEffect(runtime?.toString()){refreshHome()}
    val activeOrder=recentOrders.firstOrNull{it.status !in listOf("COMPLETED","CANCELLED")}
    Scaffold(bottomBar={BottomNav("home",onHome={},onOrders=onOrders,onAccount=onAccount)}){pad->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=18.dp),contentPadding=PaddingValues(bottom=20.dp)){
            item{
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment=Alignment.CenterVertically){
                    BrandMark();Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)){Text("gawone",fontSize=22.sp,fontWeight=FontWeight.ExtraBold);Text("Works for a better you",fontSize=9.sp,color=GMuted)}
                    Surface(color=GSoft,shape=RoundedCornerShape(999.dp)){Text("Customer",Modifier.padding(horizontal=10.dp,vertical=5.dp),fontSize=9.sp,fontWeight=FontWeight.Bold,color=Color(0xFF15803D))}
                    Spacer(Modifier.width(4.dp));IconButton(onClick=onAccount){Icon(Icons.Default.AccountCircle,null)}
                }
                Spacer(Modifier.height(18.dp))
                Text("Butuh bantuan apa hari ini?",fontSize=24.sp,fontWeight=FontWeight.ExtraBold)
                Text("Pesan layanan dan pantau progresnya dari satu alur yang sama.",fontSize=11.sp,color=GMuted)
                Spacer(Modifier.height(14.dp))
                HomeSyncStrip(rtStatus,activeOrder)
                Spacer(Modifier.height(14.dp))
                if(activeOrder!=null){ActiveOrderCard(activeOrder,onOrders);Spacer(Modifier.height(14.dp))}
                RuntimeCard(runtime,rtStatus)
                Spacer(Modifier.height(18.dp));SectionTitle("Layanan untuk Anda")
            }
            if(busy)item{LoadingCard("Memuat layanan & aktivitas…")} else if(info.isNotBlank())item{InfoBox(info)} else {
                val mapsReady=featureEnabled(runtime,"MAPS")
                val pilotServices=services.filter{it.availability=="AVAILABLE"}.filter{mapsReady || it.code=="CLEANING"}
                if(!mapsReady)item{PilotNoticeCard()}
                items(pilotServices){service->ServiceCard(service){onService(service)}}
            }
            item{
                Spacer(Modifier.height(14.dp))
                Surface(color=Color.White,shape=RoundedCornerShape(18.dp),border=androidx.compose.foundation.BorderStroke(1.dp,GBorder),modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.Sync,null,tint=GGreen);Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)){Text("Satu status di semua aplikasi",fontWeight=FontWeight.ExtraBold,fontSize=11.sp);Text("Perubahan order dari Mitra dan Management dibaca kembali dari backend GAWONE.",fontSize=9.sp,color=GMuted,lineHeight=14.sp)}
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
'''
s = s[:home_start] + new_home + s[home_end:]

orders_start = s.index('@Composable fun OrdersScreen(')
orders_end = s.index('\n@Composable\nfun OrderDetailScreen', orders_start)
new_orders = r'''@Composable fun OrdersScreen(repo:CustomerRepository,tick:Int,onBack:()->Unit,onOpen:(String)->Unit){
    var orders by remember{mutableStateOf<List<OrderUi>>(emptyList())}
    var info by remember{mutableStateOf("")}
    var loading by remember{mutableStateOf(true)}
    var showHistory by remember{mutableStateOf(false)}
    suspend fun load(){try{orders=jsonOrders(repo.orders());info=""}catch(e:Exception){info=e.message?:"Gagal memuat pesanan"}finally{loading=false}}
    LaunchedEffect(tick){load()};LaunchedEffect(Unit){while(true){delay(10000);load()}}
    val visible=orders.filter{if(showHistory) it.status in listOf("COMPLETED","CANCELLED") else it.status !in listOf("COMPLETED","CANCELLED")}
    Column(Modifier.fillMaxSize()){
        TopBar("Pesanan Saya",onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=!showHistory,onClick={showHistory=false},label={Text("Aktif")})
            FilterChip(selected=showHistory,onClick={showHistory=true},label={Text("Riwayat")})
        }
        Spacer(Modifier.height(6.dp))
        when{
            loading->Box(Modifier.padding(18.dp)){LoadingCard("Memuat pesanan…")}
            info.isNotBlank()->Box(Modifier.padding(18.dp)){InfoBox(info)}
            visible.isEmpty()->Box(Modifier.padding(18.dp)){EmptyOrdersCard(showHistory)}
            else->LazyColumn(Modifier.padding(horizontal=18.dp),contentPadding=PaddingValues(bottom=18.dp)){items(visible){o->OrderCard(o){onOpen(o.id)}}}
        }
    }
}
'''
s = s[:orders_start] + new_orders + s[orders_end:]

anchor = '                StatusHero(status);Spacer(Modifier.height(10.dp));d?.optJSONObject("price")?.let{PriceCard(it.optDouble("totalAmount",0.0),true)}'
if anchor not in s:
    raise SystemExit('order detail anchor missing')
s = s.replace(anchor, '                StatusHero(status);Spacer(Modifier.height(10.dp));OrderTimeline(status);Spacer(Modifier.height(10.dp));d?.optJSONObject("price")?.let{PriceCard(it.optDouble("totalAmount",0.0),true)}', 1)

insert_at = s.index('@Composable fun BrandMark()')
helpers = r'''@Composable fun HomeSyncStrip(rtStatus:String,activeOrder:OrderUi?){
    Surface(color=GSoft,shape=RoundedCornerShape(18.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFBBF7D0)),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(36.dp).background(Color(0xFFDCFCE7),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.CloudDone,null,tint=GGreen)}
            Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("GAWONE Live",fontWeight=FontWeight.ExtraBold,fontSize=11.sp);Text(if(activeOrder==null) rtStatus else "${activeOrder.no} • ${statusCopy(activeOrder.status).first}",fontSize=9.sp,color=GMuted)}
            Surface(color=Color.White,shape=RoundedCornerShape(999.dp)){Text("SYNC",Modifier.padding(horizontal=9.dp,vertical=4.dp),fontSize=8.sp,fontWeight=FontWeight.Bold,color=GGreen)}
        }
    }
}

@Composable fun ActiveOrderCard(order:OrderUi,onOpen:()->Unit){
    val copy=statusCopy(order.status)
    Surface(modifier=Modifier.fillMaxWidth().clickable(onClick=onOpen),color=Color.White,shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,GBorder)){
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Pesanan aktif",fontWeight=FontWeight.ExtraBold,fontSize=12.sp,modifier=Modifier.weight(1f));Text(order.status,fontSize=8.sp,fontWeight=FontWeight.Bold,color=GGreen)}
            Text(order.service,fontWeight=FontWeight.ExtraBold,fontSize=16.sp);Text(copy.first,fontSize=11.sp,color=GDark);Text(copy.second,fontSize=9.sp,color=GMuted,lineHeight=14.sp)
            LinearProgressIndicator(progress={orderProgress(order.status)},modifier=Modifier.fillMaxWidth(),color=GGreen,trackColor=GNeutral)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(order.no,fontSize=8.sp,color=GMuted);Text("Lihat detail",fontSize=9.sp,color=GGreen,fontWeight=FontWeight.Bold)}
        }
    }
}

@Composable fun EmptyOrdersCard(history:Boolean){
    Surface(color=Color.White,shape=RoundedCornerShape(18.dp),border=androidx.compose.foundation.BorderStroke(1.dp,GBorder),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(if(history) Icons.Default.History else Icons.Default.TaskAlt,null,tint=GGreen,modifier=Modifier.size(34.dp));Spacer(Modifier.height(8.dp));Text(if(history)"Belum ada riwayat" else "Tidak ada pesanan aktif",fontWeight=FontWeight.ExtraBold);Text(if(history)"Pesanan yang selesai atau dibatalkan akan muncul di sini." else "Saat membuat pesanan baru, progresnya akan tampil di sini.",fontSize=9.sp,color=GMuted)}
    }
}

@Composable fun OrderTimeline(status:String){
    val steps=listOf("BOOKED" to "Dipesan","ASSIGNED" to "Mitra","EN_ROUTE" to "Menuju","IN_PROGRESS" to "Dikerjakan","COMPLETED" to "Selesai")
    val current=orderProgressIndex(status)
    Surface(color=Color.White,shape=RoundedCornerShape(18.dp),border=androidx.compose.foundation.BorderStroke(1.dp,GBorder),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp)){Text("Perjalanan pesanan",fontWeight=FontWeight.ExtraBold,fontSize=11.sp);Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){steps.forEachIndexed{i,step->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(22.dp).background(if(i<=current) GGreen else GNeutral,CircleShape),contentAlignment=Alignment.Center){if(i<=current)Icon(Icons.Default.Check,null,tint=Color.White,modifier=Modifier.size(14.dp))else Text((i+1).toString(),fontSize=8.sp,color=GMuted)};Spacer(Modifier.height(4.dp));Text(step.second,fontSize=7.sp,color=if(i<=current)GDark else GMuted,maxLines=1)}}}
        }
    }
}

fun orderProgressIndex(status:String):Int=when(status){"DRAFT","PRICE_PENDING","READY_TO_BOOK"->0;"BOOKED","SEARCHING"->0;"PARTIALLY_ASSIGNED","ASSIGNED"->1;"EN_ROUTE","ARRIVED","CHECKED_IN"->2;"IN_PROGRESS","WORK_COMPLETED","CUSTOMER_CONFIRMED"->3;"COMPLETED"->4;else->0}
fun orderProgress(status:String):Float=((orderProgressIndex(status)+1)/5f).coerceIn(0f,1f)

'''
s = s[:insert_at] + helpers + s[insert_at:]

old_bottom='@Composable fun BottomNav(active:String,onHome:()->Unit,onOrders:()->Unit,onAccount:()->Unit){NavigationBar(containerColor=Color.White){NavigationBarItem(active=="home",onClick=onHome,icon={Icon(Icons.Default.Home,null)},label={Text("Beranda")});NavigationBarItem(active=="orders",onClick=onOrders,icon={Icon(Icons.Default.ReceiptLong,null)},label={Text("Pesanan")});NavigationBarItem(active=="account",onClick=onAccount,icon={Icon(Icons.Default.Person,null)},label={Text("Akun")})}}'
new_bottom='''@Composable fun BottomNav(active:String,onHome:()->Unit,onOrders:()->Unit,onAccount:()->Unit){NavigationBar(containerColor=Color.White,tonalElevation=8.dp){NavigationBarItem(active=="home",onClick=onHome,icon={Icon(Icons.Default.Home,null)},label={Text("Beranda")},colors=NavigationBarItemDefaults.colors(selectedIconColor=Color(0xFF15803D),selectedTextColor=Color(0xFF15803D),indicatorColor=GSoft));NavigationBarItem(active=="orders",onClick=onOrders,icon={Icon(Icons.Default.ReceiptLong,null)},label={Text("Pesanan")},colors=NavigationBarItemDefaults.colors(selectedIconColor=Color(0xFF15803D),selectedTextColor=Color(0xFF15803D),indicatorColor=GSoft));NavigationBarItem(active=="account",onClick=onAccount,icon={Icon(Icons.Default.Person,null)},label={Text("Akun")},colors=NavigationBarItemDefaults.colors(selectedIconColor=Color(0xFF15803D),selectedTextColor=Color(0xFF15803D),indicatorColor=GSoft))}}'''
if old_bottom not in s:
    raise SystemExit('bottom nav anchor missing')
s=s.replace(old_bottom,new_bottom,1)

main.write_text(s)

b=build.read_text()
b=b.replace('versionCode = 16','versionCode = 17',1)
b=b.replace('versionName = "1.0.6-uiux-v2"','versionName = "1.0.7-uiux-v3"',1)
if 'versionCode = 17' not in b or '1.0.7-uiux-v3' not in b:
    raise SystemExit('version bump failed')
build.write_text(b)
print('GAWONE Customer RC8 UIUX V3 applied')
