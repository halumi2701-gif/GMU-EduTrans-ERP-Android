from pathlib import Path
p=Path("gawone-customer-production/app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt")
s=p.read_text()
s=s.replace("@Composable fun GawoneTheme(content:@Composable()->Unit){","@Composable fun GawoneTheme(content: @Composable () -> Unit){")
s=s.replace("typography=Typography(defaultFontFamily=androidx.compose.ui.text.font.FontFamily.SansSerif)","typography=Typography()")
s=s.replace("fun jsonOrders(a:JSONArray):List<OrderUi>=","fun jsonOrders(a:JSONArray):List<OrderUi> = ")
p.write_text(s)
print("Customer RC1 compile patch applied")
