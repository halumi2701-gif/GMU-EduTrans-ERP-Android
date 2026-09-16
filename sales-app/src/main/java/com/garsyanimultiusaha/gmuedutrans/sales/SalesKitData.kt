package com.garsyanimultiusaha.gmuedutrans.sales

object SalesKitData {
    val templates = listOf(
        SalesKitTemplate(
            id = "wa_intro_school",
            category = "Prospecting",
            title = "WhatsApp Pembuka Sekolah",
            body = """Selamat pagi/siang Bapak/Ibu.\n\nPerkenalkan saya [Nama Sales] dari GMU EduTrans, penyelenggara program edukasi, edutrip, perjalanan rombongan, dan kegiatan pembelajaran luar kelas untuk sekolah.\n\nApakah semester ini sekolah Bapak/Ibu memiliki agenda seperti outing class, field trip, P5, edukasi profesi, atau perjalanan edukasi siswa?\n\nJika ada, saya bisa bantu rekomendasikan program sesuai usia siswa dan kisaran budget sekolah."""
        ),
        SalesKitTemplate(
            id = "wa_needs",
            category = "Qualification",
            title = "Gali Kebutuhan",
            body = """Baik Bapak/Ibu. Supaya saya rekomendasikan paket yang tepat, boleh diinformasikan:\n• Jenjang/kelas peserta\n• Perkiraan jumlah peserta\n• Rencana bulan/tanggal kegiatan\n• Jenis kegiatan yang diminati\n• Kisaran budget per peserta bila sudah ada\n\nSetelah itu saya bantu pilihkan maksimal 2–3 opsi yang paling sesuai."""
        ),
        SalesKitTemplate(
            id = "wa_followup_proposal",
            category = "Follow-up",
            title = "Follow-up Proposal",
            body = """Selamat siang Bapak/Ibu, izin memastikan proposal GMU EduTrans yang saya kirim sudah diterima.\n\nDari pilihan yang ada, program mana yang paling sesuai dengan kebutuhan sekolah? Jika ada kendala budget atau jumlah peserta, boleh diinformasikan agar saya bantu sesuaikan opsi paketnya."""
        ),
        SalesKitTemplate(
            id = "wa_closing_date",
            category = "Closing",
            title = "Closing Pilihan Tanggal",
            body = """Untuk tanggal yang Bapak/Ibu rencanakan sementara masih tersedia. Jika programnya sudah sesuai, saya bisa bantu reservasi jadwal dan menyiapkan quotation resminya.\n\nLebih memungkinkan minggu kedua atau minggu ketiga bulan tersebut, Bapak/Ibu?"""
        ),
        SalesKitTemplate(
            id = "wa_price_objection",
            category = "Objection",
            title = "Keberatan Harga",
            body = """Baik Bapak/Ibu. Supaya saya tidak salah menyesuaikan, boleh diketahui kisaran budget yang direncanakan sekolah per peserta?\n\nSaya akan cek pilihan paket resmi yang paling mendekati kebutuhan tersebut. Untuk diskon atau harga khusus, saya perlu mengikuti approval harga GMU EduTrans."""
        ),
        SalesKitTemplate(
            id = "wa_repeat",
            category = "After Sales",
            title = "Repeat Order & Referral",
            body = """Terima kasih Bapak/Ibu sudah mempercayakan kegiatan bersama GMU EduTrans. Semoga kegiatan memberikan pengalaman yang bermanfaat untuk peserta.\n\nKami juga memiliki program lain untuk agenda semester berikutnya. Jika berkenan, saya juga sangat terbantu apabila Bapak/Ibu merekomendasikan GMU EduTrans ke sekolah atau komunitas pendidikan lain yang membutuhkan program serupa."""
        )
    )
}
