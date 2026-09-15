from pathlib import Path
p=Path("gawone-customer-production/app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt")
s=p.read_text()
old='rupiah(payment.optDouble("amount",0.0))'
new='rupiah(payment?.optDouble("amount",0.0) ?: 0.0)'
if old not in s:
    raise SystemExit("expected RC3 payment pattern not found")
p.write_text(s.replace(old,new,1))
print("RC3 nullable payment compile fix applied")
