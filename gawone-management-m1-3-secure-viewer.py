from pathlib import Path
import sys,re
root=Path(sys.argv[1] if len(sys.argv)>1 else 'gawone-management-pilot')

p=root/'app/build.gradle.kts'; s=p.read_text()
s=s.replace('versionCode = 4','versionCode = 5',1)
s=s.replace('versionName = "0.2.2-pilot-m1.2"','versionName = "0.2.3-pilot-m1.3"',1)
p.write_text(s)

p=root/'app/src/main/AndroidManifest.xml'; s=p.read_text()
s=re.sub(r'\n\s*<provider\n\s*android:name="androidx\.core\.content\.FileProvider".*?</provider>','',s,flags=re.S)
p.write_text(s)
fp=root/'app/src/main/res/xml/file_paths.xml'
if fp.exists(): fp.unlink()

p=root/'app/src/main/java/site/garsyanimultiusaha/gawone/management/ManagementApi.kt'; s=p.read_text()
s=s.replace('import java.io.File\n','')
s=s.replace('''class ManagementApi(context: Context) {\n    private val appContext=context.applicationContext\n    private val store=SessionStore(appContext)''','''class ManagementApi(context: Context) {\n    private val store=SessionStore(context.applicationContext)''')
s=s.replace('''    fun hasSession()=store.load()!=null\n    fun logout(){\n        File(appContext.cacheDir,"kyc-review").deleteRecursively()\n        store.clear()\n    }''','''    fun hasSession()=store.load()!=null\n    fun logout()=store.clear()''')
p.write_text(s)

p=root/'app/src/main/java/site/garsyanimultiusaha/gawone/management/MainActivity.kt'; s=p.read_text()
s=s.replace('import android.content.Intent\n','')
s=s.replace('import androidx.core.content.FileProvider\n','')
s=s.replace('import java.io.File\n','')
s=s.replace('import androidx.compose.foundation.layout.*\n','''import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.Image\n''')
s=s.replace('import androidx.compose.ui.graphics.Color\n','''import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.asImageBitmap\nimport androidx.compose.ui.layout.ContentScale\nimport androidx.compose.ui.window.Dialog\nimport android.graphics.Bitmap\nimport android.graphics.BitmapFactory\nimport android.graphics.pdf.PdfRenderer\nimport android.os.ParcelFileDescriptor\nimport java.io.File\n''')

start=s.index('@Composable private fun DocumentReviewRow')
end=s.index('@Composable private fun ServiceReviewRow', start)
new=r'''@Composable private fun SecureEvidenceDialog(bytes:ByteArray,mime:String,title:String,onDismiss:()->Unit){
    val context=LocalContext.current
    var page by remember(bytes){mutableStateOf(0)}
    var error by remember(bytes){mutableStateOf("")}
    val rendered by produceState<Pair<Bitmap?,Int>>(initialValue=null to 1,bytes,mime,page){
        value=withContext(Dispatchers.IO){
            try{
                if(mime.contains("pdf",true)){
                    val temp=File.createTempFile("kyc_",".pdf",context.cacheDir)
                    try{
                        temp.writeBytes(bytes)
                        ParcelFileDescriptor.open(temp,ParcelFileDescriptor.MODE_READ_ONLY).use{pfd->
                            PdfRenderer(pfd).use{renderer->
                                val count=renderer.pageCount.coerceAtLeast(1)
                                val safe=page.coerceIn(0,count-1)
                                renderer.openPage(safe).use{pdfPage->
                                    val scale=(1600f/pdfPage.width.toFloat()).coerceIn(.5f,2f)
                                    val bmp=Bitmap.createBitmap((pdfPage.width*scale).toInt(),(pdfPage.height*scale).toInt(),Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(android.graphics.Color.WHITE)
                                    pdfPage.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bmp to count
                                }
                            }
                        }
                    } finally { temp.delete() }
                }else{
                    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                    var sample=1
                    while(bounds.outWidth/sample>2048 || bounds.outHeight/sample>2048) sample*=2
                    val bmp=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})
                        ?: throw IllegalArgumentException("Format gambar tidak didukung")
                    bmp to 1
                }
            }catch(e:Exception){ error=e.message?:"Gagal merender bukti"; null to 1 }
        }
    }
    val bitmap=rendered.first
    val pageCount=rendered.second
    Dialog(onDismissRequest=onDismiss){
        Surface(shape=RoundedCornerShape(18.dp),color=Color.White,modifier=Modifier.fillMaxWidth().fillMaxHeight(.9f)){
            Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    Column{Text(title,fontWeight=FontWeight.Bold);Text("Private evidence • tidak diekspor",style=MaterialTheme.typography.bodySmall,color=Color.Gray)}
                    TextButton(onClick=onDismiss){Text("Tutup")}
                }
                if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
                else if(bitmap==null) CircularProgressIndicator()
                else Image(bitmap.asImageBitmap(),contentDescription="Bukti KYC",contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().weight(1f))
                if(mime.contains("pdf",true)&&pageCount>1) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    OutlinedButton(onClick={if(page>0)page--},enabled=page>0){Text("Sebelumnya")}
                    Text("Halaman ${page+1} / $pageCount")
                    OutlinedButton(onClick={if(page<pageCount-1)page++},enabled=page<pageCount-1){Text("Berikutnya")}
                }
            }
        }
    }
}

@Composable private fun DocumentReviewRow(api:ManagementApi,d:JSONObject,enabled:Boolean,onChanged:()->Unit){
    val scope=rememberCoroutineScope();var busy by remember{mutableStateOf(false)};var msg by remember{mutableStateOf("")};var action by remember{mutableStateOf<String?>(null)};var reason by remember{mutableStateOf("")}
    var evidence by remember{mutableStateOf<ByteArray?>(null)}
    val status=d.optString("status","-");val reviewable=status in setOf("SUBMITTED","IN_REVIEW","REQUIRES_UPDATE")
    val storagePath=d.optString("storage_path").trim();val mime=d.optString("mime_type").ifBlank{"application/octet-stream"};val title=d.optString("document_type","Dokumen")
    Surface(shape=RoundedCornerShape(14.dp),color=Soft,modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(title,fontWeight=FontWeight.Medium)
            Text(status,style=MaterialTheme.typography.bodySmall,color=Color.Gray)
            if(storagePath.isNotBlank()) Text("Bukti private tersedia",style=MaterialTheme.typography.bodySmall,color=Green)
            if(enabled && storagePath.isNotBlank()) OutlinedButton(onClick={scope.launch{busy=true;msg="";try{
                evidence=api.downloadStorage("partner-kyc-private",storagePath)
                msg="Bukti dimuat secara private"
            }catch(e:Exception){msg="Gagal membuka bukti: ${e.message}"}finally{busy=false}}},enabled=!busy){Icon(Icons.Default.Visibility,null);Spacer(Modifier.width(6.dp));Text("Lihat Bukti") }
            if(enabled&&reviewable) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={action="VERIFIED"},enabled=!busy){Text("Verifikasi")}
                OutlinedButton(onClick={action="REJECTED"},enabled=!busy){Text("Tolak")}
            }
            if(enabled && storagePath.isBlank()) Text("Storage path belum tersedia pada row ini; reviewer tidak boleh memverifikasi tanpa meninjau bukti.",style=MaterialTheme.typography.bodySmall,color=Color(0xFF9A3412))
            if(msg.isNotBlank()) Text(msg,style=MaterialTheme.typography.bodySmall,color=if(msg.startsWith("Gagal"))MaterialTheme.colorScheme.error else Green)
        }
    }
    evidence?.let{data->SecureEvidenceDialog(data,mime,title){evidence=null}}
    if(action!=null) AlertDialog(onDismissRequest={if(!busy)action=null},title={Text(if(action=="VERIFIED")"Verifikasi dokumen?" else "Tolak dokumen?")},text={Column{Text("Keputusan dicatat server dan tunduk pada permission partner.verify.");if(storagePath.isBlank())Text("Peringatan: bukti private belum dapat dibuka pada row ini.",color=Color(0xFF9A3412));if(action=="REJECTED")OutlinedTextField(reason,{reason=it},label={Text("Alasan penolakan")},modifier=Modifier.fillMaxWidth().padding(top=10.dp))}},confirmButton={Button(onClick={scope.launch{busy=true;try{api.rpc("review_partner_document",JSONObject().put("p_document_id",d.optString("id")).put("p_decision",action).put("p_reason",if(reason.isBlank())JSONObject.NULL else reason));msg="Berhasil: ${action}";action=null;reason="";evidence=null;onChanged()}catch(e:Exception){msg="Gagal: ${e.message}";action=null}finally{busy=false}}},enabled=!busy&&storagePath.isNotBlank()&&(action!="REJECTED"||reason.isNotBlank())){Text("Konfirmasi")}},dismissButton={TextButton(onClick={action=null},enabled=!busy){Text("Batal")}})
}

'''
s=s[:start]+new+s[end:]
p.write_text(s)

p=root/'README.md'; s=p.read_text()
s=s.replace('Version code: 4','Version code: 5')
s=s.replace('0.2.2-pilot-m1.2','0.2.3-pilot-m1.3')
s += '''\n\n## M1.3 — Secure In-App Evidence Viewer\n\n- KYC image/PDF evidence is rendered inside GAWONE Management; no external ACTION_VIEW handoff.\n- PDF pages are rendered with Android PdfRenderer and temporary files are deleted immediately after rendering.\n- MainActivity remains FLAG_SECURE.\n- No public URL, FileProvider export, or service-role secret is used.\n- Application approval and mutable Pilot Control remain locked until server contracts are verified.\n'''
p.write_text(s)
print('M1.3 secure in-app evidence viewer applied')
