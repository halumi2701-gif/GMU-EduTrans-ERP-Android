# GMU EduTrans Manager Natural Command Agent

Natural Command Agent adalah lapisan perintah bahasa sehari-hari untuk role Manager EduTrans.

Contoh perintah:
- `Siapkan semua kebutuhan trip SMP ABC hari Senin`
- `Buat rundown trip besok`
- `Susun crew EDU-xxxx`
- `Cek kesiapan trip terdekat`
- `Cek vendor trip SMP ABC`
- `Cek RAB trip besok`

## Alur
1. Agent mengenali intent operasional.
2. Agent mencocokkan nomor booking, customer/sekolah, program, atau tanggal relatif.
3. Jika ada lebih dari satu kandidat, Manager memilih trip yang dimaksud.
4. Agent membuat preview/draft.
5. Manager melakukan satu kali konfirmasi.
6. Mutation disimpan ke ERP dan dicatat sebagai `AI_AGENT_NATURAL_CONFIRM`.

## Guardrail
Agent tidak mengubah data sebelum konfirmasi Manager. Vendor tidak dipilih otomatis, manifest peserta tidak dibuat tanpa data valid, dan dokumen yang membutuhkan sumber asli hanya ditandai sebagai kekurangan. Akses keuangan tetap terbatas pada konteks RAB/biaya operasional trip yang tersedia untuk Manager.
