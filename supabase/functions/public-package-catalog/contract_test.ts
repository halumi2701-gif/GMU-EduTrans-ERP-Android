import { publicPackage } from "../_shared/public_catalog_media.ts";

function assert(condition: unknown, message: string) {
  if (!condition) throw new Error(message);
}

Deno.test("public package keeps all station inclusions and limits gallery", () => {
  const facilities = [
    "Edukasi stasiun & perkeretaapian",
    "6 sesi profesi / narasumber",
    "Observasi keberangkatan kereta",
    "Worksheet",
    "Sertifikat",
    "Snack",
    "Dokumentasi",
    "Pendampingan TL / MC",
    "Pendampingan staf operasional",
    "Kegiatan selama ±2 jam",
  ];

  const row = {
    id: "pkg-test",
    package_code: "PKG-GMU-00008",
    program_id: "program-test",
    program_name: "Edukasi Lingkungan Stasiun",
    program_category: "Transportasi & Perkeretaapian",
    name: "Paket Edukasi Lingkungan Stasiun",
    description: "Paket edukasi stasiun selama ±2 jam.",
    price_per_pax: 46000,
    min_pax: 20,
    facilities,
    effective_from: "2026-09-12",
    effective_until: null,
    cover_image_url: "https://example.com/cover.webp",
    gallery_urls: Array.from({ length: 8 }, (_, i) => `https://example.com/${i + 1}.webp`),
    // Internal fields intentionally supplied to prove projection strips them.
    price_note: "INTERNAL",
    base_cost: 685000,
    manager_fee: 130000,
    sales_fee: 50000,
    mitra_fee: 50000,
    profit: 235000,
    margin: 25.54,
  };

  const item = publicPackage(row, 920000) as Record<string, unknown>;
  assert(Array.isArray(item.facilities) && item.facilities.length === 10, "10 fasilitas harus tetap lengkap");
  assert(Array.isArray(item.gallery_urls) && item.gallery_urls.length === 5, "galeri publik maksimal 5");
  assert(item.price_per_pax === 46000, "harga publik harus Rp46.000");
  assert(item.min_pax === 20, "minimum pax harus 20");
  assert(item.estimated_total === 920000, "estimasi 20 pax harus Rp920.000");

  for (const forbidden of [
    "price_note", "base_cost", "manager_fee", "sales_fee", "mitra_fee", "profit", "margin",
  ]) {
    assert(!(forbidden in item), `${forbidden} tidak boleh ada di response publik`);
  }
});
