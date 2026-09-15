from pathlib import Path

root=Path('gawone-customer-production')
build=root/'app/build.gradle.kts'
repo=root/'app/src/main/java/site/garsyanimultiusaha/gawone/CustomerRepository.kt'
main=root/'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
fail=root/'app/src/main/java/site/garsyanimultiusaha/gawone/GawoneCustomerFailure.kt'
for p in (build,repo,main,fail):
    if not p.exists(): raise SystemExit(f'missing {p}')

s=build.read_text()
if 'versionCode = 27' not in s or 'versionName = "1.0.17-p2-live-tracking-rc18"' not in s:
    raise SystemExit('RC18 version anchor missing')
s=s.replace('versionCode = 27','versionCode = 28',1)
s=s.replace('versionName = "1.0.17-p2-live-tracking-rc18"','versionName = "1.0.18-p3-payment-foundation-rc19"',1)
build.write_text(s)

s=repo.read_text()
anchor='    suspend fun mobilityTracking(orderId:String):JSONObject = api.edge("mobility-tracking",JSONObject().put("orderId",orderId)) as JSONObject\n'
add='''    suspend fun paymentState(orderId:String):JSONObject = api.rpc("get_my_order_payment_state",JSONObject().put("p_order_id",orderId)) as JSONObject\n    suspend fun paymentMethods():JSONObject = api.rpc("get_my_payment_methods",JSONObject()) as JSONObject\n    suspend fun paymentCheckout(orderId:String,method:String):JSONObject = api.edge(\n        "payment-checkout",\n        JSONObject().put("action","checkout").put("orderId",orderId).put("method",method.uppercase()).put("idempotencyKey","checkout-$orderId-${UUID.randomUUID()}")\n    ) as JSONObject\n'''
if 'suspend fun paymentState(' not in s:
    if anchor not in s: raise SystemExit('repository mobility anchor missing')
    s=s.replace(anchor,anchor+add,1)
repo.write_text(s)

s=fail.read_text()
anchor='''        if (has("PAYMENT_UNAVAILABLE", "CUSTOMER_PAYMENT_FEATURE_DISABLED", "PAYMENT_FEATURE_DISABLED")) {\n'''
insert='''        if (has("PAYMENT_PROVIDER_NOT_CONFIGURED", "PAYMENT_PROVIDER_ADAPTER_NOT_CONFIGURED")) {\n            return CustomerFailureUi(\n                "PAYMENT_PROVIDER_NOT_CONFIGURED",\n                "Provider pembayaran belum terhubung",\n                "Pembayaran online belum dapat dimulai. Pesanan tetap aman dan tidak ada transaksi yang dibuat.",\n                false,\n                CustomerRecoveryAction.NONE\n            )\n        }\n        if (has("PAYMENT_ALREADY_PENDING")) {\n            return CustomerFailureUi(\n                "PAYMENT_ALREADY_PENDING",\n                "Pembayaran sedang diproses",\n                "Jangan membuat pembayaran baru. Periksa kembali status transaksi yang sedang berjalan.",\n                true,\n                CustomerRecoveryAction.RETRY\n            )\n        }\n        if (has("ORDER_ALREADY_PAID")) {\n            return CustomerFailureUi(\n                "ORDER_ALREADY_PAID",\n                "Pesanan sudah dibayar",\n                "Pembayaran untuk pesanan ini sudah lunas dan tidak perlu diulang.",\n                false,\n                CustomerRecoveryAction.OPEN_ORDERS\n            )\n        }\n        if (has("FINAL_PRICE_REQUIRED")) {\n            return CustomerFailureUi(\n                "FINAL_PRICE_REQUIRED",\n                "Harga final belum tersedia",\n                "Tunggu sampai harga pesanan dikunci server sebelum melakukan pembayaran.",\n                true,\n                CustomerRecoveryAction.RETRY\n            )\n        }\n        if (has("PAYMENT_METHOD_NOT_SUPPORTED")) {\n            return CustomerFailureUi(\n                "PAYMENT_METHOD_NOT_SUPPORTED",\n                "Metode pembayaran tidak tersedia",\n                "Pilih metode pembayaran lain yang ditampilkan oleh GAWONE.",\n                false,\n                CustomerRecoveryAction.NONE\n            )\n        }\n'''
if 'PAYMENT_PROVIDER_NOT_CONFIGURED' not in s:
    if anchor not in s: raise SystemExit('payment failure anchor missing')
    s=s.replace(anchor,insert+anchor,1)
fail.write_text(s)

s=main.read_text()
start=s.index('fun PaymentStatusScreen(')
end=s.index('\n@Composable\nfun RatingScreen',start)
new='''fun PaymentStatusScreen(repo:CustomerRepository,orderId:String?,runtime:JSONObject?,onBack:()->Unit){\n    var state by remember(orderId){mutableStateOf<JSONObject?>(null)}\n    var methods by remember{mutableStateOf(JSONArray())}\n    var selectedMethod by remember{mutableStateOf("")}\n    var info by remember{mutableStateOf("")}\n    var loading by remember{mutableStateOf(true)}\n    var busy by remember{mutableStateOf(false)}\n    val scope=rememberCoroutineScope()\n    val enabled=runtime?.optJSONObject("features")?.optBoolean("PAYMENT",false)==true\n\n    suspend fun load(){\n        val id=orderId?:run{loading=false;return}\n        loading=true\n        try{\n            state=repo.paymentState(id)\n            val envelope=repo.paymentMethods()\n            methods=envelope.optJSONArray("methods")?:JSONArray()\n            if(selectedMethod.isBlank() && methods.length()>0) selectedMethod=methods.optJSONObject(0)?.optString("method").orEmpty()\n            info=""\n        }catch(e:Exception){info=customerSafeError(e,"Status pembayaran belum dapat dimuat")}\n        finally{loading=false}\n    }\n    LaunchedEffect(orderId,enabled){load()}\n\n    val total=state?.optDouble("totalAmount",0.0)?:0.0\n    val settled=state?.optDouble("settledAmount",0.0)?:0.0\n    val remaining=state?.optDouble("remainingAmount",0.0)?:0.0\n    val fullyPaid=state?.optBoolean("fullyPaid",false)==true\n    val latest=state?.optJSONObject("latestIntent")\n    val latestStatus=latest?.optString("status").orEmpty().uppercase()\n    val canStart=enabled && !fullyPaid && remaining>0 && methods.length()>0 && selectedMethod.isNotBlank() && latestStatus !in setOf("PENDING_PROVIDER","PENDING_PAYMENT")\n\n    Column(Modifier.fillMaxSize()){\n        TopBar("Pembayaran",onBack)\n        LazyColumn(Modifier.weight(1f).padding(18.dp)){\n            item{\n                SectionTitle("Ringkasan pembayaran")\n                Spacer(Modifier.height(10.dp))\n                DataCard("Total pesanan",rupiah(total))\n                DataCard("Sudah dibayar",rupiah(settled))\n                DataCard("Sisa pembayaran",rupiah(remaining))\n                Spacer(Modifier.height(10.dp))\n                when{\n                    loading->LinearProgressIndicator(Modifier.fillMaxWidth(),color=GGreen)\n                    fullyPaid->InfoBox("Pembayaran telah lunas dan sudah direkonsiliasi server GAWONE.")\n                    latestStatus in setOf("PENDING_PROVIDER","PENDING_PAYMENT")->WarningCard("Pembayaran sedang diproses","Jangan bayar ulang. Status provider akan direkonsiliasi ke pesanan ini.")\n                    latestStatus=="FAILED"->WarningCard("Pembayaran belum berhasil","Pilih metode tersedia dan coba kembali. Pesanan tidak dibuat ganda.")\n                    !enabled->WarningCard("Pembayaran online belum diaktifkan","Provider pembayaran production belum dibuka untuk akun ini. GAWONE tidak membuat transaksi simulasi.")\n                    methods.length()==0->WarningCard("Metode pembayaran belum tersedia","Belum ada provider pembayaran resmi yang aktif. Pesanan tetap aman.")\n                    else->InfoBox("Pilih metode pembayaran yang tersedia dari server.")\n                }\n                latest?.let{p->\n                    Spacer(Modifier.height(12.dp));SectionTitle("Transaksi terakhir")\n                    DataCard("Metode",p.optString("method","-"))\n                    DataCard("Provider",p.optString("provider","-"))\n                    DataCard("Status",p.optString("status","-"))\n                    DataCard("Nominal",rupiah(p.optDouble("amount",0.0)))\n                }\n                if(enabled && methods.length()>0 && !fullyPaid){\n                    Spacer(Modifier.height(14.dp));SectionTitle("Metode pembayaran");Spacer(Modifier.height(8.dp))\n                    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){\n                        for(i in 0 until methods.length()){\n                            val m=methods.optJSONObject(i)?:continue\n                            val code=m.optString("method");val label=m.optString("displayName",code)\n                            FilterChip(selected=selectedMethod==code,onClick={selectedMethod=code},label={Text(label,fontSize=10.sp,fontWeight=FontWeight.Bold)})\n                        }\n                    }\n                }\n                if(info.isNotBlank()){Spacer(Modifier.height(10.dp));InfoBox(info)}\n                Spacer(Modifier.height(16.dp))\n                OutlinedButton(onClick={scope.launch{load()}},enabled=!loading&&!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(7.dp));Text("Perbarui Status")}\n            }\n        }\n        if(canStart){\n            BottomAction("Lanjut Bayar",enabled=!busy){\n                scope.launch{\n                    val id=orderId?:return@launch\n                    busy=true;info=""\n                    try{\n                        repo.paymentCheckout(id,selectedMethod)\n                        load()\n                    }catch(e:Exception){info=customerSafeError(e,"Pembayaran belum dapat dimulai")}\n                    finally{busy=false}\n                }\n            }\n        }\n    }\n}\n'''
s=s[:start]+new+s[end:]
main.write_text(s)

checks={
    build:['versionCode = 28','1.0.18-p3-payment-foundation-rc19'],
    repo:['get_my_order_payment_state','get_my_payment_methods','payment-checkout'],
    fail:['PAYMENT_PROVIDER_NOT_CONFIGURED','PAYMENT_ALREADY_PENDING','ORDER_ALREADY_PAID','FINAL_PRICE_REQUIRED'],
    main:['Ringkasan pembayaran','Sisa pembayaran','Metode pembayaran belum tersedia','Lanjut Bayar','repo.paymentState'],
}
for path,tokens in checks.items():
    text=path.read_text()
    for token in tokens:
        if token not in text: raise SystemExit(f'RC19 token missing: {path}: {token}')
print('GAWONE Customer RC19 P3 payment foundation applied')
