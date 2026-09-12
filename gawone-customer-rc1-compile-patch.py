from pathlib import Path
p=Path("gawone-customer-production/app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt")
s=p.read_text()
s=s.replace("@Composable fun GawoneTheme(content:@Composable()->Unit){","@Composable fun GawoneTheme(content: @Composable () -> Unit){")
s=s.replace("typography=Typography(defaultFontFamily=androidx.compose.ui.text.font.FontFamily.SansSerif)","typography=Typography()")
s=s.replace("fun jsonOrders(a:JSONArray):List<OrderUi>=","fun jsonOrders(a:JSONArray):List<OrderUi> = ")
lines=s.splitlines()
for prefix in ("@Composable fun PriceCard","@Composable fun StatusHero","@Composable fun OrderCard"):
    for i,line in enumerate(lines):
        if line.startswith(prefix) and line.count("{")-line.count("}")==1:
            lines[i]=line+"}"
            break
p.write_text("\n".join(lines)+"\n")
balance=sum(line.count("{")-line.count("}") for line in lines)
if balance != 0:
    raise SystemExit(f"unbalanced Kotlin braces: {balance}")
print("Customer RC1 compile patch applied; brace balance=0")
