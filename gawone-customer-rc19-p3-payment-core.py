from pathlib import Path

root=Path('gawone-customer-production')
build=root/'app/build.gradle.kts'
repo=root/'app/src/main/java/site/garsyanimultiusaha/gawone/CustomerRepository.kt'
main=root/'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
failure=root/'app/src/main/java/site/garsyanimultiusaha/gawone/GawoneCustomerFailure.kt'
for p in (build,repo,main,failure):
    if not p.exists(): raise SystemExit(f'missing {p}')

b=build.read_text()
if 'versionCode = 27' not in b or 'versionName = "1.0.17-p2-live-tracking-rc18"' not in b:
    raise SystemExit('RC18 version anchor missing')
b=b.replace('versionCode = 27','versionCode = 28',1)
b=b.replace('versionName = "1.0.17-p2-live-tracking-rc18"','versionName = "1.0.18-p3-payment-core-rc19"',1)
build.write_text(b)

r=repo.read_text()
anchor='''    suspend fun mobilityTracking(orderId:String):JSONObject = api.edge("mobility-tracking",JSONObject().put("orderId",orderId)) as JSONObject\n'''
if anchor not in r: raise SystemExit('repository mobility anchor missing')
payment_repo='''    suspend fun paymentState(orderId:String):JSONObject = api.rpc("get_my_order_payment_state",JSONObject().put("p_order_id",orderId)) as JSONObject\n    suspend fun paymentMethods():JSONObject = api.rpc("get_my_payment_methods",JSONObject()) as JSONObject\n    suspend fun createPaymentIntent(orderId:String,method:String):JSONObject = api.rpc("create_my_payment_intent",JSONObject().put("p_order_id",orderId).put("p_payment_method",method.uppercase()).put("p_idempotency_key","pay-$orderId-${method.lowercase()}-${UUID.randomUUID()}")) as JSONObject\n    suspend fun paymentCheckout(intentId:String):JSONObject = api.edge("payment-checkout",JSONObject().put("action","checkout").put("intentId",intentId)) as JSONObject\n'''
r=r.replace(anchor,anchor+payment_repo,1)
repo.write_text(r)

f=failure.read_text()
anchor_failure='        if (has("MOBILITY_ENDPOINTS_REQUIRED", "ROUTE_ENDPOINTS_REQUIRED")) {\n'
payment_failure='        if (has("PAYMENT_PROVIDER_NOT_CONFIGURED", "PAYMENT_PROVIDER_ADAPTER_NOT_CONFIGURED")) {\n            return CustomerFailureUi("PAYMENT_PROVIDER_NOT_CONFIGURED","Provider pembayaran belum siap","Metode pembayaran belum dapat diproses oleh provider resmi. Tidak ada transaksi yang dianggap berhasil.",true,CustomerRecoveryAction.RETRY)\n        }\n        if (has("PAYMENT_ALREADY_PENDING")) {\n            return CustomerFailureUi("PAYMENT_ALREADY_PENDING","Pembayaran masih menunggu","Selesaikan atau tunggu transaksi sebelumnya kedaluwarsa sebelum membuat pembayaran baru.",false,CustomerRecoveryAction.NONE)\n        }\n        if (has("ORDER_ALREADY_PAID")) {\n            return CustomerFailureUi("ORDER_ALREADY_PAID","Pesanan sudah lunas","Status pembayaran sudah tercatat lunas di server GAWONE.",false,CustomerRecoveryAction.OPEN_ORDERS)\n        }\n        if (has("ORDER_NOT_PAYABLE", "FINAL_PRICE_REQUIRED")) {\n            return CustomerFailureUi("ORDER_NOT_PAYABLE","Pesanan belum dapat dibayar","Harga final atau status pesanan belum memenuhi syarat pembayaran.",true,CustomerRecoveryAction.OPEN_ORDERS)\n        }\n'
if anchor_failure not in f: raise SystemExit('payment failure anchor missing')
f=f.replace(anchor_failure,payment_failure+anchor_failure,1)
failure.write_text(f)

s=main.read_text()
start=s.index('@Composable\nfun PaymentStatusScreen(')
end=s.index('\n@Composable\nfun RatingScreen',start)
new='''@Composable
fun PaymentStatusScreen(repo:CustomerRepository,orderId:String?,runtime:JSONObject?,onBack:()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    val scope=rememberCoroutineScope()
    var state by remember{mutableStateOf<JSONObject?>(null)}
    var info by remember{mutableStateOf("")}
    var busyMethod by remember{mutableStateOf<String?>(null)}
    var refreshTick by remember{mutableIntStateOf(0)}
    val runtimeEnabled=runtime?.optJSONObject("features")?.optBoolean("PAYMENT",false)==true

    LaunchedEffect(orderId,refreshTick){
        val id=orderId?:return@LaunchedEffect
        try{state=repo.paymentState(id)}catch(e:Exception){info=customerSafeError(e,"Gagal memuat status pembayaran")}
    }
    LaunchedEffect(orderId){
        while(orderId!=null){delay(10000);refreshTick++}
    }

    val enabled=state?.optBoolean("featureEnabled",runtimeEnabled)?:runtimeEnabled
    val total=state?.optDouble("totalAmount",0.0)?:0.0
    val settled=state?.optDouble("settledAmount",0.0)?:0.0
    val remaining=state?.optDouble("remainingAmount",0.0)?:0.0
    val fullyPaid=state?.optBoolean("fullyPaid",false)==true
    val latest=state?.optJSONObject("latestIntent")
    val latestStatus=latest?.optString("status","")?:""
    val methods=state?.optJSONArray("methods")?:JSONArray()
    val pending=latestStatus in listOf("PENDING_PROVIDER","PENDING_PAYMENT","PROCESSING")

    Column(Modifier.fillMaxSize()){
        TopBar("Pembayaran",onBack)
        LazyColumn(Modifier.weight(1f).padding(18.dp)){
            item{
                when{
                    fullyPaid->InfoBox("Pembayaran lunas dan sudah direkonsiliasi server • ${rupiah(settled)}")
                    pending->WarningCard("Pembayaran sedang diproses: $latestStatus","Jangan membuat pembayaran baru sebelum transaksi ini selesai atau kedaluwarsa.")
                    !enabled->WarningCard("Payment provider belum diaktifkan","GAWONE sudah memakai kontrak pembayaran server-side, tetapi transaksi production tetap dikunci sampai provider resmi terhubung.")
                    methods.length()==0->WarningCard("Metode pembayaran belum tersedia","Feature PAYMENT aktif, tetapi belum ada route provider yang lolos konfigurasi backend.")
                    else->InfoBox("Pilih metode pembayaran. Nominal final selalu berasal dari server GAWONE.")
                }
                Spacer(Modifier.height(12.dp))
                DataCard("Total order",rupiah(total))
                DataCard("Sudah dibayar",rupiah(settled))
                DataCard("Sisa pembayaran",rupiah(remaining))

                latest?.let{itn->
                    Spacer(Modifier.height(12.dp));SectionTitle("Transaksi terakhir")
                    DataCard("Provider",itn.optString("provider","-"))
                    DataCard("Metode",itn.optString("method","-"))
                    DataCard("Status",itn.optString("status","-"))
                    DataCard("Nominal",rupiah(itn.optDouble("amount",0.0)))
                    val va=itn.optString("virtualAccountNumber","")
                    val qr=itn.optString("qrString","")
                    val url=itn.optString("checkoutUrl","")
                    if(va.isNotBlank())DataCard("Virtual Account",va)
                    if(qr.isNotBlank())WarningCard("QRIS siap","Kode QR pembayaran tersedia dari provider. Tampilan QR visual akan diaktifkan bersama adapter provider production.")
                    if(url.startsWith("https://")){
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.OpenInNew,null);Spacer(Modifier.width(7.dp));Text("Buka halaman pembayaran")}
                    }
                }

                if(enabled && !fullyPaid && !pending && methods.length()>0){
                    Spacer(Modifier.height(14.dp));SectionTitle("Pilih metode")
                    for(i in 0 until methods.length()){
                        val m=methods.optJSONObject(i)?:continue
                        val method=m.optString("method")
                        val display=m.optString("displayName",method)
                        Spacer(Modifier.height(7.dp))
                        Button(onClick={
                            scope.launch{
                                val id=orderId?:return@launch
                                busyMethod=method;info=""
                                try{
                                    val intent=repo.createPaymentIntent(id,method)
                                    val intentId=intent.optString("intentId")
                                    if(intentId.isBlank())throw IllegalStateException("PAYMENT_INTENT_ID_MISSING")
                                    val checkout=repo.paymentCheckout(intentId)
                                    info=if(checkout.optBoolean("ok",false))"Checkout provider berhasil dibuat." else "Checkout belum tersedia."
                                    refreshTick++
                                }catch(e:Exception){info=customerSafeError(e,"Pembayaran belum dapat dibuat")}
                                finally{busyMethod=null}
                            }
                        },enabled=busyMethod==null,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=GGreen)){
                            Icon(Icons.Default.Payments,null);Spacer(Modifier.width(8.dp));Text(if(busyMethod==method)"Menyiapkan…" else display)
                        }
                    }
                }
                if(info.isNotBlank()){Spacer(Modifier.height(10.dp));InfoBox(info)}
                Spacer(Modifier.height(10.dp))
                Text("Status diperbarui otomatis setiap 10 detik. Aplikasi tidak menganggap pembayaran berhasil sebelum backend menandai dana settled.",fontSize=8.sp,color=GMuted,lineHeight=12.sp)
            }
        }
    }
}
'''
s=s[:start]+new+s[end:]
main.write_text(s)

out_repo=repo.read_text();out_main=main.read_text()
for token in ['get_my_order_payment_state','create_my_payment_intent','payment-checkout']:
    if token not in out_repo: raise SystemExit('RC19 repository verification missing '+token)
for token in ['PAYMENT_PROVIDER_NOT_CONFIGURED','PAYMENT_ALREADY_PENDING','ORDER_ALREADY_PAID','ORDER_NOT_PAYABLE']:
    if token not in failure.read_text(): raise SystemExit('RC19 failure verification missing '+token)
for token in ['Pilih metode','Sisa pembayaran','PENDING_PROVIDER','Checkout provider berhasil dibuat','Status diperbarui otomatis setiap 10 detik']:
    if token not in out_main: raise SystemExit('RC19 UI verification missing '+token)
print('GAWONE Customer RC19 P3 payment core applied')
