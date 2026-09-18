alter table public.program_packages
  add column if not exists sales_public_summary text,
  add column if not exists duration_text text,
  add column if not exists target_participants text[] not null default '{}',
  add column if not exists shared_rules text[] not null default '{}',
  add column if not exists shared_statuses text[] not null default '{}',
  add column if not exists booking_flow text[] not null default '{}',
  add column if not exists public_terms text[] not null default '{}';

update public.program_packages
set
  sales_public_summary = 'Program edukasi langsung di lingkungan stasiun untuk mengenalkan fasilitas stasiun, keselamatan perjalanan kereta api, alur perjalanan penumpang, serta profesi petugas perkeretaapian melalui kegiatan edukatif bersama GMU EduTrans.',
  duration_text = '±2 jam. Rundown menyesuaikan jadwal kereta, kondisi operasional stasiun, jumlah peserta, usia/jenjang peserta, dan kebijakan pihak stasiun.',
  facilities = array[
    'Pengenalan lingkungan dan fasilitas stasiun',
    'Pengenalan profesi di lingkungan perkeretaapian',
    'Pengenalan tugas Kepala Stasiun',
    'Pengenalan petugas pelayanan dan operasional stasiun',
    'Edukasi dasar keselamatan di area stasiun',
    'Tata tertib di lingkungan stasiun',
    'Pengenalan proses perjalanan penumpang di stasiun',
    'Interaksi dan sesi edukasi bersama petugas/narasumber',
    'Pendampingan kegiatan oleh tim GMU EduTrans',
    'MC / Tour Leader kegiatan',
    'Dokumentasi kegiatan',
    'Snack peserta',
    'Sertifikat peserta',
    'Worksheet / lembar aktivitas edukasi'
  ],
  target_participants = array[
    'PAUD','KOBER','TK','RA','SD/MI','SMP/MTs','Sekolah / Lembaga Pendidikan','Komunitas Anak'
  ],
  shared_rules = array[
    'Minimal registrasi masing-masing sekolah 20 peserta',
    'Beberapa sekolah/lembaga dapat digabung dalam satu jadwal edukasi',
    'Total gabungan peserta dalam satu sesi minimal 40 peserta',
    'PIC dan data peserta setiap sekolah tetap terpisah',
    'Booking, quotation, invoice, dan pembayaran setiap sekolah tetap terpisah',
    'Kegiatan dilaksanakan bersama pada tanggal dan sesi yang sama'
  ],
  shared_statuses = array[
    'OPEN — pendaftaran sesi sedang dibuka',
    'MINIMUM REACHED — total gabungan minimal 40 peserta tercapai',
    'CONFIRMED — tanggal dan sesi sudah dikonfirmasi',
    'FULL / CLOSED — kuota penuh atau pendaftaran ditutup',
    'COMPLETED — kegiatan selesai'
  ],
  booking_flow = array[
    'Pilih Program',
    'Tentukan Jumlah Peserta',
    'Pilih Private / Shared',
    'Tentukan Tanggal',
    'Quotation',
    'Pembayaran / DP',
    'Konfirmasi',
    'Pelaksanaan'
  ],
  public_terms = array[
    'Harga dihitung berdasarkan jumlah peserta yang dikonfirmasi',
    'Perubahan jumlah peserta dapat menyebabkan perubahan tier harga',
    'Harga Private Session mengikuti jumlah peserta aktual',
    'Harga Shared Session berlaku setelah total gabungan minimal 40 peserta terpenuhi',
    'Jadwal mengikuti ketersediaan stasiun dan operasional perjalanan kereta',
    'Quotation menjadi dasar harga resmi booking',
    'Program kerja sama organisasi/mitra menggunakan skema B2B/MOU terpisah',
    'Harga B2B tidak ditampilkan pada materi PUBLIC',
    'Diskon khusus di luar harga standar harus melalui persetujuan GMU EduTrans'
  ],
  updated_at = now()
where package_code = 'STATION-PROF-2026';
