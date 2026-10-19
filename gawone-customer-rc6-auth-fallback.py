from pathlib import Path

root = Path('gawone-customer-production')
main = root / 'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
api = root / 'app/src/main/java/site/garsyanimultiusaha/gawone/SupabaseApi.kt'
build = root / 'app/build.gradle.kts'

m = main.read_text()
start = m.index('@Composable fun LoginScreen(api:SupabaseApi,otpEnabled:Boolean,onLogin:()->Unit){')
end = m.index('\n@Composable fun HomeScreen', start)
new_login = r'''@Composable fun LoginScreen(api:SupabaseApi,otpEnabled:Boolean,onLogin:()->Unit){
    var emailMode by remember{mutableStateOf(true)}
    var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")}
    var phone by remember{mutableStateOf("")};var otp by remember{mutableStateOf("")};var sent by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)};var info by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.Center){
        BrandHeader("Customer","WORKS FOR A BETTER YOU")
        Spacer(Modifier.height(28.dp));Text("Masuk ke GAWONE",fontSize=26.sp,fontWeight=FontWeight.ExtraBold,color=GDark)
        Text(if(emailMode)"Gunakan Email + Password untuk pilot saat SMS provider belum aktif." else "Gunakan nomor HP aktif untuk menerima OTP.",fontSize=12.sp,color=GMuted)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick={emailMode=true;info=""},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=if(emailMode)GGreen else GMuted)){Text("Email")}
            OutlinedButton(onClick={emailMode=false;info=""},modifier=Modifier.weight(1f)){Text("Phone OTP")}
        }
        Spacer(Modifier.height(18.dp))
        if(emailMode){
            GTextField(email,{email=it},"Email",KeyboardType.Email)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value=password,onValueChange={password=it},label={Text("Password (min. 6 karakter)")},singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp))
            if(info.isNotBlank()){Spacer(Modifier.height(10.dp));InfoBox(info)}
            Spacer(Modifier.height(18.dp))
            Button(onClick={scope.launch{busy=true;info="";try{api.signInEmail(email,password);onLogin()}catch(e:Exception){info=e.message?:"Gagal masuk"}finally{busy=false}}},enabled=!busy&&email.contains("@")&&password.length>=6,modifier=Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(15.dp),colors=ButtonDefaults.buttonColors(containerColor=GGreen)){if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=Color.White)else Text("Masuk dengan Email",fontWeight=FontWeight.Bold)}
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick={scope.launch{busy=true;info="";try{val out=api.signUpEmail(email,password);if(out.optString("access_token").isNotBlank())onLogin() else info="Pendaftaran berhasil. Cek email konfirmasi, lalu kembali dan tekan Masuk."}catch(e:Exception){info=e.message?:"Gagal mendaftar"}finally{busy=false}}},enabled=!busy&&email.contains("@")&&password.length>=6,modifier=Modifier.fillMaxWidth().height(48.dp),shape=RoundedCornerShape(15.dp)){Text("Daftar Customer")}
        }else{
            if(!otpEnabled){WarningCard("Phone OTP belum tersedia","SMS provider belum aktif. Gunakan Email + Password untuk pilot.");Spacer(Modifier.height(12.dp))}
            GTextField(phone,{phone=it},"Nomor HP",KeyboardType.Phone)
            if(sent){Spacer(Modifier.height(10.dp));GTextField(otp,{otp=it},"6 digit OTP",KeyboardType.Number)}
            if(info.isNotBlank()){Spacer(Modifier.height(10.dp));InfoBox(info)}
            Spacer(Modifier.height(18.dp));Button(onClick={scope.launch{busy=true;info="";try{if(!sent){api.requestOtp(phone);sent=true;info="OTP dikirim. Masukkan kode yang Anda terima."}else{api.verifyOtp(phone,otp);onLogin()}}catch(e:Exception){info=if((e.message?:"").contains("unsupported phone provider",true))"SMS provider belum aktif. Gunakan Email + Password." else (e.message?:"Gagal masuk")}finally{busy=false}}},enabled=otpEnabled&&!busy&&phone.isNotBlank()&&(!sent||otp.length>=4),modifier=Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(15.dp),colors=ButtonDefaults.buttonColors(containerColor=GGreen)){if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=Color.White)else Text(if(sent)"Verifikasi & Masuk" else "Kirim OTP",fontWeight=FontWeight.Bold)}
        }
    }
}
'''
m = m[:start] + new_login + m[end:]
main.write_text(m)

s = api.read_text()
anchor = '''    suspend fun verifyOtp(phone:String,token:String):JSONObject=withContext(Dispatchers.IO){
        val body=JSONObject().put("phone",normalizePhone(phone)).put("token",token.trim()).put("type","sms")
        val out=request("POST","$base/auth/v1/verify",body,false) as JSONObject
        store.save(out.getString("access_token"),out.getString("refresh_token")); out
    }
'''
addition = anchor + '''    suspend fun signInEmail(email:String,password:String):JSONObject=withContext(Dispatchers.IO){
        val out=request("POST","$base/auth/v1/token?grant_type=password",JSONObject().put("email",email.trim()).put("password",password),false) as JSONObject
        store.save(out.getString("access_token"),out.getString("refresh_token")); out
    }
    suspend fun signUpEmail(email:String,password:String):JSONObject=withContext(Dispatchers.IO){
        val out=request("POST","$base/auth/v1/signup",JSONObject().put("email",email.trim()).put("password",password),false) as JSONObject
        val access=out.optString("access_token");val refresh=out.optString("refresh_token")
        if(access.isNotBlank()&&refresh.isNotBlank())store.save(access,refresh)
        out
    }
'''
if 'suspend fun signInEmail' not in s:
    if anchor not in s: raise SystemExit('SupabaseApi auth anchor missing')
    s = s.replace(anchor, addition, 1)
api.write_text(s)

b = build.read_text()
b = b.replace('versionCode = 14', 'versionCode = 15', 1)
b = b.replace('versionName = "1.0.4-customer-e2e-pilot"', 'versionName = "1.0.5-auth-fallback"', 1)
if 'versionCode = 15' not in b or '1.0.5-auth-fallback' not in b:
    raise SystemExit('version bump failed')
build.write_text(b)
print('RC6 auth fallback patch applied')
