# GMU EduTrans — Media Sync Production Order

Deploy in this order. Do not deploy `public-package-catalog` v10 before the media columns exist.

1. Apply `migrations/20260912153500_add_program_package_media.sql`.
2. Verify `programs` and `program_packages` contain `cover_image_url` and `gallery_urls`.
3. Apply `migrations/20260912153800_create_edutrans_media_bucket.sql`.
4. Verify bucket `edutrans-media` is public-read, max 8 MB, JPG/PNG/WebP, with authenticated role-restricted INSERT.
5. Deploy `internal-media-master` with `verify_jwt = true`.
6. Test authenticated ERP actions: `catalog`, `load`, `save` using Owner/Director/Manager EduTrans/Admin; reject other roles.
7. From ERP Media tab upload one test cover, save it, reopen Media, and confirm the URL is loaded back.
8. Deploy `public-package-catalog` v10 with `verify_jwt = false`.
9. Verify station package contract at 20 pax:
   - `PKG-GMU-00008`
   - Rp46.000/pax
   - minimum 20
   - estimated total Rp920.000
   - all 10 facilities present
   - cover/gallery present when configured
   - no `price_note`, HPP/base cost, Manager/Sales/Mitra fees, profit, margin, pricing policy, or internal notes.
10. Verify 19 pax returns no eligible station package.
11. Only after backend verification, load `public-web` v23/v24 renderer in the customer site and test cover/gallery + checklist + booking CTA.
12. Keep current production alias unchanged until preview passes login, booking, payment, account/customer portal, and package selection smoke tests.

Rollback rule: if any verification fails, keep/restore the current public catalog v9 and existing customer production deployment; media fields are additive and may remain unused safely.
