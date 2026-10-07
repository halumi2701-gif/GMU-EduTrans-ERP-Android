(() => {
  'use strict';

  const VERSION = 'v10.8-role-playbook';
  const q = (s, r = document) => r.querySelector(s);
  const h = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);

  const PLAYBOOKS = {
    director: {
      label: 'Owner / Direktur',
      mission: 'Menentukan hasil perusahaan, arah, batas risiko, kebijakan, modal dan keputusan strategis. Direktur tidak menjadi operator harian.',
      reportsTo: 'Pemegang saham / pemilik perusahaan',
      supervises: 'Manager GMU EduTrans',
      daily: ['Membaca ringkasan eksekutif dan hanya menindaklanjuti pengecualian strategis/kritis.','Menilai approval yang memang melewati kewenangan Manager.','Memantau target laba, cash, risiko, dan tindakan pemulihan Manager tanpa mengerjakan pekerjaan staf.'],
      weekly: ['Menerima Executive Summary: target, aktual, forecast, gap, top issue, top opportunity, tindakan Manager.','Tidak wajib hadir rapat operasional mingguan jika tidak ada eskalasi strategis.'],
      monthly: ['Monthly Business Review dengan Manager + Finance.','Menetapkan/menyesuaikan target laba, cash/reserve, investasi, hiring strategis, ekspansi, dan kebijakan harga.','Menilai kinerja Manager berdasarkan hasil, bukan detail aktivitas staf.'],
      kpi: ['Laba bersih perusahaan vs target Rp15.000.000/bulan.','Cash tersedia, cadangan dan runway sehat.','Pertumbuhan omzet/profit tanpa menurunkan margin.','Kesehatan perusahaan dan risiko strategis.','Manager accountability dan berkurangnya ketergantungan operasional kepada Direktur.'],
      authority: ['Menetapkan strategi, target perusahaan dan kebijakan.','Approve diskon >5%, margin <20%, refund/cancellation besar, penggunaan reserve, investasi, CAPEX, hiring permanen strategis, harga master, partnership strategis.','Keputusan fraud, legal, safety kritis dan perubahan finansial strategis.'],
      prohibited: ['Tidak melakukan prospecting/follow-up Sales harian.','Tidak membuat rundown, manifest, operation sheet atau penugasan crew.','Tidak mengejar invoice customer secara rutin.','Tidak memerintah staf melewati Manager kecuali fraud, emergency, safety/legal kritis.'],
      sop: ['Baca Executive Summary → cek apakah perlu tindakan Direktur.','Jika normal: biarkan Manager menyelesaikan.','Jika strategis/kritis: lihat data → keputusan → catat alasan → approval/reject/revisi.','Review hasil tindakan pada Business Review berikutnya.'],
      escalation: ['Fraud/kecurangan.','Kas kritis atau reserve terancam.','Safety/legal/reputation critical.','Margin <20%.','Manager recovery plan gagal berulang.','Investasi/ekspansi/permanent hiring.'],
      outputs: ['Keputusan strategis terdokumentasi.','Target perusahaan.','Policy dan batas kewenangan.','Monthly Business Review decision log.','Persetujuan strategis.'],
      compensation: ['Penghasilan Direktur mengikuti kebijakan PT, bukan fee trip rutin.','Penggunaan uang perusahaan harus terpisah dari pengeluaran pribadi.']
    },
    manager: {
      label: 'Manager GMU EduTrans',
      mission: 'Single accountable operator unit GMU EduTrans: mengubah target perusahaan menjadi penjualan, booking, operasi, profit, cash discipline, mutu dan kinerja tim.',
      reportsTo: 'Owner / Direktur',
      supervises: 'Sales/BD, Admin/CS/Ops, Finance secara koordinatif, TL/Crew/Freelancer',
      daily: ['Cek target & forecast, lead/quote/booking, overdue follow-up, trip mendatang, pembayaran, RAB/margin, vendor/crew, readiness, approval dan task terlambat.','Delegasikan pekerjaan ke role yang tepat; Manager tidak mengambil semua tugas sendiri.','Pastikan masalah diselesaikan pada level terendah yang berwenang.'],
      weekly: ['Pimpin Rapat Mingguan GMU EduTrans max 60 menit: Sales → Ops → Finance → Action Items.','Review funnel, konversi, booking, margin, AR, readiness, vendor, beban kerja dan customer issue.','Buat Rencana Pemulihan bila forecast target tidak tercapai.'],
      monthly: ['Review unit P&L, margin, budget vs actual, closing seluruh trip, kinerja tim dan kebutuhan SDM.','Presentasi Business Review ke Direktur dengan data dan tindakan, bukan alasan umum.'],
      kpi: ['Qualified leads ≥20/bulan baseline.','Quotation ≥12/bulan.','Booking minimum 3/bulan; ideal 4–6 atau mengikuti target dinamis.','Quotation→booking ≥20–25%.','Margin kegiatan target ≥25%.','RAB vs actual ideal ≤±10%.','100% trip financial closing.','Tidak ada loss trip tanpa approval Direktur.'],
      authority: ['Biaya dalam RAB ≤Rp1.000.000/transaksi.','Biaya di luar RAB ≤Rp250.000 dengan alasan dan bukti.','Emergency trip ≤Rp500.000 untuk keselamatan/kelangsungan kegiatan.','Diskon ≤5%.','Margin 20–24,99% dapat direview Manager dengan alasan.','Assign crew, vendor operational selection, readiness dan workflow operasional sesuai policy.'],
      prohibited: ['Tidak boleh mengubah transaksi Finance untuk menutupi masalah.','Tidak boleh mengakses kredensial bank/strategic PT finance/unrelated unit finance.','Tidak approve margin <20%, reserve use, refund/cancellation strategis, diskon >5%, planned expense >Rp1 juta.','Tidak boleh menyembunyikan masalah atau memindahkan tanggung jawab tanpa data.'],
      sop: ['Target perusahaan → breakdown funnel/booking → assign PIC → daily control.','Jika gap: identifikasi penyebab → tindakan → PIC → deadline → target hasil.','Booking confirmed → pastikan Berkas Kegiatan + RAB + crew/vendor + H-7/H-3/H-1.','Trip selesai → pastikan H+1 operasional + H+3 finance closing.','Laporkan hanya pengecualian strategis ke Direktur.'],
      escalation: ['Margin <20%.','Planned RAB >Rp1 juta/transaksi.','Unplanned >Rp250 ribu.','Emergency >Rp500 ribu.','Refund/cancel/reserve/investment.','Fraud, serious incident, legal/safety critical.','Recovery plan gagal berulang.'],
      outputs: ['Weekly Manager Control Report.','Rencana Pemulihan.','Approved RAB/crew/vendor within authority.','Readiness status setiap trip.','Monthly unit performance review.'],
      compensation: ['Transport Manager Rp30.000 per hari hadir.','Retainer/gaji dan bonus harus mengikuti Master Compensation final; angka simulasi lama bukan otomatis final.']
    },
    sales: {
      label: 'Sales / Business Development',
      mission: 'Menghasilkan calon pelanggan dan mengubahnya menjadi booking sehat dengan margin terjaga di pasar Cianjur–Sukabumi.',
      reportsTo: 'Manager GMU EduTrans',
      supervises: 'Tidak ada; koordinasi dengan Admin/Manager',
      daily: ['10 prospek baru.','10 tindak lanjut.','Update status setiap lead dan next action di ERP.','Prioritaskan lead berdasarkan potensi, timing, fit program dan hubungan.','Follow-up quotation sesuai SLA D+1, D+3, D+7, D+14.'],
      weekly: ['50 prospek.','5 peluang potensial/qualified.','3 penawaran.','Review pipeline dan lost reason dengan Manager.'],
      monthly: ['200 prospek.','20 qualified leads.','12 quotation.','Minimum 3 booking; ideal 4–6 atau target dinamis.','Review sumber lead, conversion, revenue, margin dan repeat opportunity.'],
      kpi: ['Booking 30%.','Revenue 25%.','Margin 20%.','Qualified leads 10%.','Follow-up discipline 10%.','Repeat order 5%.','Overdue lead = 0.','Quotation→booking ≥25% baseline.'],
      authority: ['Prospecting, qualification, follow-up, draft quotation dari Master Program, negosiasi sesuai batas harga.','Mengusulkan diskon kepada Manager.','Mengelola pipeline dan lost reason.'],
      prohibited: ['Tidak membuat harga sendiri di luar Master Program.','Tidak memberi diskon/freebies tanpa approval.','Tidak mengatur fee vendor/crew.','Tidak menerima uang customer ke rekening pribadi.','Tidak menjanjikan fasilitas yang tidak ada di quotation.'],
      sop: ['Prospect → Contacted → Qualified → Quotation Sent → Negotiation → Awaiting DP → Won/Lost.','Lead wajib punya owner, next action dan deadline.','Quotation hanya dari Master Program + recalculation margin.','Lost wajib isi lost reason.','Won → handover lengkap ke Admin/Ops.'],
      escalation: ['Diskon >5%.','Customer minta harga di bawah floor.','Margin berpotensi <20%.','Komitmen fasilitas di luar paket.','Lead strategis/partnership besar.','Keluhan serius dari calon/customer.'],
      outputs: ['CRM/pipeline bersih.','Activity log.','Quotation.','Handover booking.','Lost reason.','Repeat/referral opportunity.'],
      compensation: ['Komisi berstatus Pending → Earned → Paid.','Komisi earned hanya setelah syarat booking/pembayaran terpenuhi.','Pembatalan sebelum eligibility = komisi tidak earned.']
    },
    admin: {
      label: 'Admin / Customer Service',
      mission: 'Menjaga data customer, booking, komunikasi administratif dan dokumen dasar tetap lengkap, benar dan tepat waktu.',
      reportsTo: 'Manager GMU EduTrans',
      supervises: 'Tidak ada',
      daily: ['Input/update data customer dan booking.','Periksa kelengkapan data PIC, kontak, program, tanggal, pax, titik kumpul dan kebutuhan khusus.','Kirim/arsipkan dokumen customer sesuai workflow.','Tandai data yang belum lengkap dan follow-up internal.'],
      weekly: ['Audit booking aktif dan kelengkapan Berkas Kegiatan.','Pastikan tidak ada booking tanpa PIC/status/next action.','Koordinasikan perubahan data ke Sales, Ops dan Finance.'],
      monthly: ['Audit kualitas data customer dan dokumen.','Laporan data tidak lengkap/duplikat dan perbaikan proses.'],
      kpi: ['Kelengkapan booking 100%.','Akurasi data tinggi; minim koreksi setelah operasional.','Manifest final H-1.','Berkas Kegiatan 100% untuk booking confirmed.','Tidak ada dokumen customer hilang/tidak terarsip.'],
      authority: ['Membuat/update data administratif sesuai sumber resmi/customer.','Menyusun Berkas Kegiatan dan dokumen non-finansial.','Koordinasi customer untuk data administratif.'],
      prohibited: ['Tidak menetapkan harga/diskon.','Tidak memverifikasi pembayaran sebagai Finance.','Tidak mengubah transaksi keuangan.','Tidak membagikan HPP/margin/payroll atau data rahasia kepada pihak yang tidak berhak.'],
      sop: ['Booking masuk → cek mandatory fields → lengkapi → buat folder/dokumen.','Perubahan customer → catat sumber/perubahan → sinkronkan ke role terkait.','H-1 → manifest final + contact list + dokumen peserta.','Trip selesai → arsip dokumen final dan handover evaluasi.'],
      escalation: ['Data peserta/kebutuhan khusus belum lengkap menjelang H-1.','Perubahan besar customer yang berdampak biaya/operasi.','Dokumen legal/izin bermasalah.','Customer complaint yang bukan sekadar administratif.'],
      outputs: ['Booking record lengkap.','Customer master.','Manifest.','Berkas Kegiatan.','Dokumen komunikasi/administrasi.'],
      compensation: ['Gaji/retainer mengikuti Master Compensation.','Reimbursement hanya dengan bukti dan approval.']
    },
    finance: {
      label: 'Finance / Accounting',
      mission: 'Menjaga tagihan, pembayaran, piutang, hutang, biaya aktual, kas, payroll dan closing tetap akurat, terkontrol dan dapat diaudit.',
      reportsTo: 'Direktur secara kontrol; koordinasi operasional dengan Manager',
      supervises: 'Tidak ada; menjaga independensi kontrol keuangan',
      daily: ['Buat/monitor invoice.','Verifikasi pembayaran dan bukti transaksi.','Update AR/AP dan follow-up due/overdue.','Catat biaya aktual dan bukti.','Jaga rekonsiliasi data transaksi.'],
      weekly: ['Review collection, vendor payable, cash requirement trip, biaya vs RAB.','Laporkan transaksi unmatched/bermasalah.'],
      monthly: ['Close periode: revenue, direct cost, payroll, overhead, marketing, tech, profit, cash, AR/AP.','Rekonsiliasi dan payroll calendar.','Bandingkan current/last/3mo/6mo/YTD.'],
      kpi: ['100% transaksi memiliki bukti.','Tidak ada selisih kas tanpa penjelasan.','Closing trip maksimal H+3.','Tidak ada AR overdue tanpa follow-up.','Tidak ada double payment.','Rekonsiliasi tepat waktu.'],
      authority: ['Verifikasi pembayaran customer.','Mencatat transaksi, actual cost, AP/AR.','Membayar transaksi setelah approval sesuai matrix.','Menolak pembayaran yang bukti/approval-nya tidak lengkap.'],
      prohibited: ['Tidak mengubah transaksi demi memenuhi target.','Tidak membayar tanpa approval yang diwajibkan.','Tidak menerima instruksi Manager untuk menghapus/mengubah bukti secara tidak sah.','Tidak membuka kredensial bank ke role lain.'],
      sop: ['Invoice → payment evidence → verify → post.','Vendor invoice → Ops verify → approval → Finance pay → evidence.','Trip completed → actual cost → variance → profit → H+3 closing.','Month end → reconcile → lock/report.'],
      escalation: ['Unmatched transaction.','Cash shortage / inability to fund confirmed trip.','Overdue AR critical.','RAB variance >10%.','Refund/cancellation.','Suspected fraud or duplicate payment.'],
      outputs: ['Invoice.','Payment verification.','AR/AP aging.','Actual cost.','Trip closing.','Payroll/commission status.','Monthly finance report.'],
      compensation: ['Gaji/retainer mengikuti Master Compensation final.','Tidak ada fee transaksi pribadi.']
    },
    ops: {
      label: 'Admin Ops / Trip Coordinator',
      mission: 'Mengubah booking confirmed menjadi kegiatan yang siap, aman, terdokumentasi dan dapat dieksekusi TL/crew tanpa kekacauan.',
      reportsTo: 'Manager GMU EduTrans',
      supervises: 'Koordinasi vendor, TL, MC, dokumentasi dan crew per kegiatan',
      daily: ['Cek trip H-7/H-3/H-1.','Update vendor/crew confirmation.','Susun/refresh rundown, Operation Sheet, manifest, contact list dan checklist.','Tandai critical missing item.','Update progress/readiness di ERP.'],
      weekly: ['Review seluruh upcoming trip dan kebutuhan vendor/crew/peralatan.','Koordinasi perubahan booking ke Manager, Finance dan TL.'],
      monthly: ['Evaluasi vendor, issue operasional, recurring problem dan kebutuhan asset/inventory.'],
      kpi: ['Readiness 100 sebelum trip.','H-7/H-3/H-1 checklist complete.','Manifest final H-1.','Dokumen operasional 100%.','Vendor/crew confirmed sesuai deadline.','Insiden akibat kelalaian administrasi = 0.'],
      authority: ['Koordinasi vendor/crew yang sudah approved.','Draft rundown/operation sheet.','Menjaga checklist dan dokumen kegiatan.','Meminta koreksi bila data belum siap.'],
      prohibited: ['Tidak menetapkan harga customer.','Tidak approve biaya di luar kewenangan Manager.','Tidak mengubah actual Finance.','Tidak menandai Ready jika critical item belum terpenuhi.'],
      sop: ['Confirmed booking → create Berkas Kegiatan.','H-7: vendor/crew/rundown/RAB/documents.','H-3: reconfirmation + participant/vendor/transport.','H-1: manifest final + call time + emergency contacts + readiness 100.','H+1: collect report/docs/incident/evaluation.'],
      escalation: ['Readiness <80%.','Critical missing item.','Vendor/transport failure.','Participant data/safety need unresolved.','Cost change impacting margin/RAB.'],
      outputs: ['Rundown.','Operation Sheet.','Manifest.','Crew/vendor confirmation.','Readiness checklist.','Trip document handover.'],
      compensation: ['Fee staf Ops/trip mengikuti Master Compensation per program.','Reimbursement terpisah dari fee.']
    },
    tl: {
      label: 'Tour Leader / PIC Lapangan',
      mission: 'Memimpin pelaksanaan trip di lapangan sesuai rundown, safety, service standard dan keputusan operasional Manager.',
      reportsTo: 'Manager / Trip Coordinator',
      supervises: 'Crew lapangan sesuai penugasan',
      daily: ['Hanya pada hari persiapan/trip: baca seluruh dokumen, briefing, cek call time, peserta, vendor, emergency contact dan rundown.','Update kondisi lapangan dan issue melalui jalur yang ditetapkan.'],
      weekly: ['Tidak ada rutinitas kantor; mengikuti jadwal assignment dan briefing.'],
      monthly: ['Evaluasi kualitas penugasan bila aktif berkala.'],
      kpi: ['Hadir sesuai call time.','Briefing terlaksana.','≥95% rundown compliance kecuali perubahan terotorisasi.','Serious incident akibat kelalaian = 0.','Laporan dan handover H+1.','Kualitas pelayanan peserta.'],
      authority: ['Mengambil keputusan lapangan minor untuk menjaga kelancaran.','Emergency response awal sesuai safety SOP.','Mengkoordinasikan crew saat kegiatan.'],
      prohibited: ['Tidak mengubah harga/biaya customer.','Tidak menjanjikan refund/kompensasi besar.','Tidak meninggalkan peserta tanpa handover.','Tidak menyembunyikan incident.'],
      sop: ['Pre-trip briefing → participant count → vendor/transport check → execute rundown → periodic count/safety check → incident escalation → closing briefing → H+1 report.'],
      escalation: ['Medium incident → Manager.','High: illness/vendor failure/serious conflict → Manager command + Owner informed.','Critical: accident/missing participant/serious medical/legal → safety first → emergency service → Manager → Owner.'],
      outputs: ['Briefing record.','Attendance/count.','Incident report bila ada.','H+1 trip report.','Crew/vendor feedback.'],
      compensation: ['Fee assignment menjadi Earned setelah trip completed dan attendance verified.','Status: Assignment → Accepted → Completed → Verified → Earned → Paid.']
    },
    mc: {
      label: 'MC / Fasilitator / Narasumber',
      mission: 'Menyampaikan sesi edukasi/fasilitasi sesuai rundown, tujuan pembelajaran, standar layanan dan waktu yang ditetapkan.',
      reportsTo: 'TL / Manager / Trip Coordinator',
      supervises: 'Peserta selama sesi secara fungsional',
      daily: ['Pada hari assignment: pahami materi, audience, durasi, alat bantu dan flow.','Hadir sebelum sesi untuk sound/material check.','Jaga engagement dan waktu.'],
      weekly: ['Mengikuti briefing bila ada assignment.'],
      monthly: ['Review feedback kualitas fasilitasi bila penugasan berulang.'],
      kpi: ['On-time.','Materi sesuai program.','Durasi terkendali.','Feedback peserta baik.','Tidak ada materi/statement yang melanggar kebijakan/safety.'],
      authority: ['Mengatur metode penyampaian selama tetap dalam program/rundown.','Meminta bantuan TL untuk crowd/safety.'],
      prohibited: ['Tidak mengubah program/agenda utama tanpa koordinasi.','Tidak membuat janji komersial ke customer.','Tidak menggunakan data/foto peserta di luar izin.'],
      sop: ['Terima brief → review material → check equipment → deliver → Q&A → handover ke TL → feedback singkat.'],
      escalation: ['Materi tidak siap.','Equipment gagal.','Peserta/safety issue.','Permintaan customer di luar scope.'],
      outputs: ['Sesi terlaksana.','Materi/worksheet bila ditugaskan.','Catatan issue dan feedback.'],
      compensation: ['Fee sesuai assignment/Master Compensation program dan attendance verified.']
    },
    documentation: {
      label: 'Dokumentasi',
      mission: 'Menghasilkan bukti visual kegiatan yang lengkap, rapi, aman, dan siap dipakai untuk laporan/customer/marketing sesuai izin.',
      reportsTo: 'Trip Coordinator / TL',
      supervises: 'Tidak ada',
      daily: ['Pada hari trip: cek shot list, storage, battery, perangkat dan titik penting.','Dokumentasikan opening, learning activity, group, key moment dan closing.','Backup file setelah kegiatan.'],
      weekly: ['Serahkan file sesuai deadline assignment.'],
      monthly: ['Rapikan arsip dan evaluasi kualitas dokumentasi bila aktif rutin.'],
      kpi: ['Coverage momen wajib lengkap.','File tidak hilang/corrupt.','Handover tepat waktu.','Kualitas visual layak.','Privasi/izin peserta dipatuhi.'],
      authority: ['Mengatur teknis pengambilan gambar selama tidak mengganggu kegiatan/safety.'],
      prohibited: ['Tidak mempublikasikan peserta/customer tanpa izin.','Tidak menyimpan data sensitif di kanal pribadi tanpa kebutuhan.','Tidak menghapus file sebelum backup/handover.'],
      sop: ['Brief/shot list → equipment check → capture → backup → select/organize → upload/handover → confirm receipt.'],
      escalation: ['Storage/equipment failure.','Prohibition from venue/customer.','Sensitive incident footage.','File loss/corruption.'],
      outputs: ['Folder foto/video terstruktur.','Selected highlights.','Bukti dokumentasi trip.'],
      compensation: ['Fee per assignment sesuai Master Compensation; reimbursement alat/transport harus terpisah bila disetujui.']
    },
    crew: {
      label: 'Crew / Freelancer',
      mission: 'Menjalankan tugas lapangan spesifik sesuai assignment, call time, SOP dan arahan TL/Manager.',
      reportsTo: 'TL / Trip Coordinator',
      supervises: 'Tidak ada kecuali ditunjuk',
      daily: ['Cek assignment, call time, dress code/peralatan dan titik kumpul.','Konfirmasi Accept/Decline sebelum deadline.','Jalankan checklist tugas dan laporkan selesai/issue.'],
      weekly: ['Tidak ada rutinitas kantor kecuali ada assignment/briefing.'],
      monthly: ['Rating penugasan dan eligibility talent pool diperbarui berdasarkan performa.'],
      kpi: ['Accept/decline tepat waktu.','On-time.','Tugas selesai.','Disiplin/safety/service.','Tidak ada kelalaian serius.'],
      authority: ['Melaksanakan tugas sesuai assignment dan meminta arahan bila scope berubah.'],
      prohibited: ['Tidak mengambil keputusan finansial/customer promise.','Tidak meninggalkan area tugas tanpa handover.','Tidak membagikan data customer/peserta.'],
      sop: ['Assignment → Accept → briefing → execute checklist → evidence → supervisor verify → fee earned.'],
      escalation: ['Tidak dapat hadir.','Scope tidak jelas.','Safety issue.','Peralatan/vendor bermasalah.'],
      outputs: ['Checklist tugas.','Bukti kerja.','Catatan issue.'],
      compensation: ['Assignment → Accepted → Trip Completed → Attendance Verified → Fee Earned → Finance Approval → Paid.']
    }
  };

  function currentRole(){ try{return String(profile?.role||'')}catch(_){return ''} }
  function canInspectAll(){ return ['Owner','Director','Direktur','Manager','Manager EduTrans'].includes(currentRole()); }
  function keyForRole(r=currentRole()){
    const s=String(r||'').toLowerCase();
    if(['owner','director','direktur'].includes(s)) return 'director';
    if(s.includes('manager')) return 'manager';
    if(s.includes('sales')||s.includes('business')) return 'sales';
    if(s.includes('finance')||s.includes('keuangan')||s.includes('account')) return 'finance';
    if(s.includes('tour leader')||s==='tl'||s.includes('pic lapangan')) return 'tl';
    if(s.includes('mc')||s.includes('facilitator')||s.includes('fasilitator')||s.includes('narasumber')) return 'mc';
    if(s.includes('dokument')) return 'documentation';
    if(s.includes('crew')||s.includes('freelancer')) return 'crew';
    if(s.includes('operation')||s.includes('operasional')||s.includes('trip coordinator')||s.includes('ops')) return 'ops';
    if(s.includes('admin')) return 'admin';
    return 'crew';
  }

  function installStyle(){
    if(q('#gmuPlaybookStyle')) return;
    const s=document.createElement('style'); s.id='gmuPlaybookStyle'; s.textContent=`
      .gmu-pb-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;flex-wrap:wrap}.gmu-pb-head h3{margin:0;color:var(--gd)}.gmu-pb-head p{font-size:9px;color:var(--muted);margin:4px 0 0;max-width:760px;line-height:1.6}.gmu-pb-select{padding:8px 10px;border:1px solid var(--line);border-radius:10px;background:#fff;font:inherit;font-size:9px}.gmu-pb-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:12px}.gmu-pb-card{border:1px solid var(--line);border-radius:14px;background:#fff;padding:12px}.gmu-pb-card h4{margin:0 0 7px;color:var(--gd);font-size:12px}.gmu-pb-card ul{margin:0;padding-left:18px;font-size:9px;line-height:1.7}.gmu-pb-meta{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:10px}.gmu-pb-meta div{border:1px solid var(--line);border-radius:12px;padding:10px;background:#f8fbf9}.gmu-pb-meta small{display:block;color:var(--muted);font-size:8px}.gmu-pb-meta b{font-size:10px;color:var(--gd)}.gmu-pb-flow{display:flex;gap:6px;flex-wrap:wrap}.gmu-pb-step{font-size:8px;border:1px solid var(--line);background:#f7faf8;border-radius:999px;padding:5px 8px}.gmu-pb-alert{background:#fff7ed;border:1px solid #f3d6ad;border-radius:12px;padding:10px;font-size:9px;line-height:1.6}.gmu-pb-good{background:#f3faf6;border:1px solid #cfe7d7;border-radius:12px;padding:10px;font-size:9px;line-height:1.6}.gmu-pb-actions{display:flex;gap:7px;flex-wrap:wrap;margin-top:10px}.gmu-pb-btn{border:1px solid var(--line);background:#fff;border-radius:10px;padding:8px 10px;font:inherit;font-size:9px;cursor:pointer;color:var(--gd)}@media(max-width:760px){.gmu-pb-grid,.gmu-pb-meta{grid-template-columns:1fr}}
    `; document.head.appendChild(s);
  }

  function list(title,items){ return `<div class="gmu-pb-card"><h4>${h(title)}</h4><ul>${items.map(x=>`<li>${h(x)}</li>`).join('')}</ul></div>`; }
  function render(key){
    const p=PLAYBOOKS[key]||PLAYBOOKS.crew;
    const host=q('#gmuPlaybookBody'); if(!host)return;
    host.innerHTML=`
      <div class="gmu-pb-head"><div><h3>${h(p.label)}</h3><p>${h(p.mission)}</p></div><span class="badge info">${h(VERSION)}</span></div>
      <div class="gmu-pb-meta"><div><small>Melapor kepada</small><b>${h(p.reportsTo)}</b></div><div><small>Mengkoordinasikan</small><b>${h(p.supervises)}</b></div><div><small>Role akun aktif</small><b>${h(currentRole()||'-')}</b></div></div>
      <div class="gmu-pb-grid">${list('Tugas Harian',p.daily)}${list('Tugas Mingguan',p.weekly)}${list('Tugas Bulanan',p.monthly)}${list('KPI / Ukuran Kinerja',p.kpi)}${list('Wewenang',p.authority)}${list('Larangan / Batasan',p.prohibited)}${list('Kapan Harus Eskalasi',p.escalation)}${list('Output Wajib',p.outputs)}</div>
      <div class="card section"><div class="head"><div><h3>SOP Inti Jabatan</h3><p>Urutan kerja default. Detail program/trip tetap mengikuti SOP kegiatan yang aktif.</p></div></div><div class="gmu-pb-flow">${p.sop.map((x,i)=>`<span class="gmu-pb-step">${i+1}. ${h(x)}</span>`).join('')}</div></div>
      <div class="gmu-pb-grid"><div class="gmu-pb-good"><b>Penghasilan & Imbalan</b><br>${p.compensation.map(x=>`• ${h(x)}`).join('<br>')}</div><div class="gmu-pb-alert"><b>Prinsip ERP:</b><br>Setiap pekerjaan harus punya PIC, deadline, status, bukti/output dan jalur eskalasi. Jika pekerjaan bisa diselesaikan role ini sesuai kewenangan, jangan dilempar kembali ke Direktur.</div></div>
      <div class="gmu-pb-actions"><button class="gmu-pb-btn" data-pb-go="myTasksHub">Tugas Saya</button><button class="gmu-pb-btn" data-pb-go="roleWorkspace">Workspace Saya</button><button class="gmu-pb-btn" data-pb-go="salesTargetControl">Target & Kinerja</button><button class="gmu-pb-btn" data-pb-go="workflow">Persetujuan</button><button class="gmu-pb-btn" data-pb-go="companySystemCenter">Sistem Perusahaan</button></div>`;
  }

  function go(page){
    try{ if(typeof navTo==='function'&&q(`#${page}`)){navTo(page);return;} }catch(_){}
    document.querySelectorAll('.page').forEach(x=>x.classList.remove('active')); q(`#${page}`)?.classList.add('active');
    document.querySelectorAll('#nav [data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page===page));
  }

  function install(){
    if(q('#rolePlaybook')) return;
    const content=q('.content'); const nav=q('#nav'); if(!content||!nav)return;
    const page=document.createElement('section'); page.id='rolePlaybook'; page.className='page';
    const options=Object.entries(PLAYBOOKS).map(([k,v])=>`<option value="${h(k)}">${h(v.label)}</option>`).join('');
    page.innerHTML=`<div class="notice">Jobdesk & SOP ${VERSION} • setiap jabatan melihat pekerjaan, target, batas kewenangan, KPI, output dan eskalasinya.</div><div class="card section"><div class="gmu-pb-head"><div><h3>Jobdesk & SOP Saya</h3><p>Karyawan melihat playbook jabatannya. Direktur/Manager dapat memilih posisi lain untuk audit dan coaching.</p></div>${canInspectAll()?`<select class="gmu-pb-select" id="gmuPlaybookRole">${options}</select>`:''}</div><div id="gmuPlaybookBody"></div></div>`;
    content.appendChild(page);
    const b=document.createElement('button'); b.dataset.page='rolePlaybook'; b.innerHTML='▤ &nbsp; Jobdesk & SOP Saya'; b.addEventListener('click',()=>go('rolePlaybook')); nav.appendChild(b);
    const own=keyForRole(); const sel=q('#gmuPlaybookRole'); if(sel){sel.value=own;sel.addEventListener('change',()=>render(sel.value));} render(own);
  }

  function init(){
    installStyle();
    document.addEventListener('click',e=>{const b=e.target.closest('[data-pb-go]');if(!b)return;e.preventDefault();go(b.dataset.pbGo);});
    const t=setInterval(()=>{if(!currentRole())return;install();try{window.GmuRoleNavigationGuard?.sync?.()}catch(_){}},250);
    setTimeout(()=>clearInterval(t),15000);
    window.GmuRolePlaybook=Object.freeze({version:VERSION,playbooks:PLAYBOOKS,keyForRole});
  }

  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
