from pathlib import Path
import re

root = Path('gawone-customer-production')
main = root / 'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
build = root / 'app/build.gradle.kts'

s = main.read_text()

# Brand palette from the approved GAWONE board.
def replace_color(name: str, hex_value: str):
    global s
    pattern = rf'(val\s+{name}\s*=\s*Color\()0x[0-9A-Fa-f]+(\))'
    s, _ = re.subn(pattern, rf'\g<1>{hex_value}\g<2>', s, count=1)

replace_color('GGreen', '0xFF22C55E')
replace_color('GDark', '0xFF1F2937')
replace_color('GMuted', '0xFF64748B')
replace_color('GSoft', '0xFFF0FDF4')
replace_color('GBg', '0xFFF8FAFC')

# Replace common brand header with the ecosystem visual language.
brand_start = s.find('@Composable fun BrandHeader')
if brand_start >= 0:
    next_comp = s.find('\n@Composable fun ', brand_start + 20)
    if next_comp < 0:
        raise SystemExit('Customer BrandHeader end marker missing')
    new_brand = r'''@Composable fun BrandHeader(title:String,subtitle:String){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Surface(color=GGreen,shape=RoundedCornerShape(16.dp),modifier=Modifier.size(48.dp)){
            Box(contentAlignment=Alignment.Center){Text("g",color=Color.White,fontWeight=FontWeight.Black,fontSize=27.sp)}
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("gawone",fontSize=25.sp,fontWeight=FontWeight.Black,color=GDark)
                Spacer(Modifier.width(8.dp))
                Surface(color=Color(0xFFF0FDF4),shape=RoundedCornerShape(999.dp)){
                    Text(title,Modifier.padding(horizontal=10.dp,vertical=4.dp),fontSize=11.sp,fontWeight=FontWeight.Bold,color=Color(0xFF15803D))
                }
            }
            Text(subtitle,fontSize=9.sp,color=GMuted,fontWeight=FontWeight.Medium)
        }
    }
}
'''
    s = s[:brand_start] + new_brand + s[next_comp:]

# Email-first, Phone OTP only as an optional secondary route.
login_start = s.index('@Composable fun LoginScreen(api:SupabaseApi,otpEnabled:Boolean,onLogin:()->Unit){')
login_end = s.index('\n@Composable fun HomeScreen', login_start)
new_login = r'''@Composable fun LoginScreen(api:SupabaseApi,otpEnabled:Boolean,onLogin:()->Unit){
    var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")}
    var showPhone by remember{mutableStateOf(false)};var phone by remember{mutableStateOf("")};var otp by remember{mutableStateOf("")};var sent by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)};var info by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp,vertical=28.dp),verticalArrangement=Arrangement.Center){
        BrandHeader("Customer","WORKS FOR A BETTER YOU")
        Spacer(Modifier.height(34.dp))
        Text("Masuk ke GAWONE",fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=GDark)
        Spacer(Modifier.height(5.dp))
        Text("Pesan layanan, pantau Mitra, chat, pembayaran, dan riwayat dalam satu aplikasi.",fontSize=13.sp,color=GMuted,lineHeight=19.sp)
        Spacer(Modifier.height(22.dp))
        if(!showPhone){
            GTextField(email,{email=it},"Email",KeyboardType.Email)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value=password,onValueChange={password=it},label={Text("Password")},singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
            if(info.isNotBlank()){Spacer(Modifier.height(10.dp));InfoBox(info)}
            Spacer(Modifier.height(18.dp))
            Button(onClick={scope.launch{busy=true;info="";try{api.signInEmail(email,password);onLogin()}catch(e:Exception){info=e.message?:"Gagal masuk"}finally{busy=false}}},enabled=!busy&&email.contains("@")&&password.length>=6,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=GGreen)){
                if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=Color.White)else Text("Masuk",fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick={scope.launch{busy=true;info="";try{val out=api.signUpEmail(email,password);if(out.optString("access_token").isNotBlank())onLogin() else info="Pendaftaran berhasil. Konfirmasi email bila diminta, lalu tekan Masuk."}catch(e:Exception){info=e.message?:"Gagal mendaftar"}finally{busy=false}}},enabled=!busy&&email.contains("@")&&password.length>=6,modifier=Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(16.dp)){Text("Daftar Customer",fontWeight=FontWeight.Bold)}
            Spacer(Modifier.height(10.dp))
            TextButton(onClick={showPhone=true;info=""},modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Opsi lain: masuk dengan nomor HP")}
        }else{
            Surface(color=Color(0xFFF0FDF4),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(14.dp)){
                    Text("Phone OTP",fontWeight=FontWeight.ExtraBold,color=GDark)
                    Text("Fitur ini opsional dan baru aktif penuh setelah SMS provider production tersedia.",fontSize=11.sp,color=GMuted)
                }
            }
            Spacer(Modifier.height(14.dp))
            if(!otpEnabled){WarningCard("Phone OTP belum tersedia","Gunakan Email + Password untuk saat ini.");Spacer(Modifier.height(12.dp))}
            GTextField(phone,{phone=it},"Nomor HP",KeyboardType.Phone)
            if(sent){Spacer(Modifier.height(10.dp));GTextField(otp,{otp=it},"6 digit OTP",KeyboardType.Number)}
            if(info.isNotBlank()){Spacer(Modifier.height(10.dp));InfoBox(info)}
            Spacer(Modifier.height(18.dp))
            Button(onClick={scope.launch{busy=true;info="";try{if(!sent){api.requestOtp(phone);sent=true;info="OTP dikirim."}else{api.verifyOtp(phone,otp);onLogin()}}catch(e:Exception){info=if((e.message?:"").contains("unsupported phone provider",true))"SMS provider belum aktif. Gunakan Email + Password." else (e.message?:"Gagal masuk")}finally{busy=false}}},enabled=otpEnabled&&!busy&&phone.isNotBlank()&&(!sent||otp.length>=4),modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=GGreen)){if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=Color.White)else Text(if(sent)"Verifikasi & Masuk" else "Kirim OTP",fontWeight=FontWeight.Bold)}
            Spacer(Modifier.height(8.dp));TextButton(onClick={showPhone=false;info=""},modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Kembali ke Email + Password")}
        }
    }
}
'''
s = s[:login_start] + new_login + s[login_end:]

# Brand copy cleanup in visible pilot notices.
s = s.replace('E2E Pilot • Cleaning', 'Pilot GAWONE • Cleaning')
s = s.replace('Ride, Car, dan Delivery dibuka setelah Maps + trusted route production aktif. Pilot ini memakai transaksi backend nyata tanpa simulator.', 'Layanan dibuka bertahap sesuai kesiapan area dan sistem. Semua transaksi pilot memakai backend GAWONE yang sama dengan Mitra dan Management.')

main.write_text(s)

b = build.read_text()
b = b.replace('versionCode = 15', 'versionCode = 16', 1)
b = b.replace('versionName = "1.0.5-auth-fallback"', 'versionName = "1.0.6-uiux-v2"', 1)
if 'versionCode = 16' not in b or '1.0.6-uiux-v2' not in b:
    raise SystemExit('Customer UIUX V2 version bump failed')
build.write_text(b)
print('GAWONE Customer RC7 UIUX V2 applied')
