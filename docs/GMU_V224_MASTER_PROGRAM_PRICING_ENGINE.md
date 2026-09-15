# GMU EduTrans v22.4 — Master Program & Pricing Engine Integration

## Objective

v22.4 makes the Program Master the commercial source of truth for Sales → Quotation → Booking → Finance → Operations → Profitability.

The migration is additive and preserves historical rows. `Company / Factory Visit` is deactivated rather than deleted.

## Canonical active portfolio

1. Edukasi di Atas Kereta Api — marketing start Rp29.000/pax
2. Edukasi Lingkungan & Profesi Stasiun — public package Rp46.000/pax, min 20
3. Edukasi Bertani Padi Pandanwangi — marketing start Rp75.000/pax
4. Edukasi Membatik — marketing Special School start Rp99.000/pax (quotation-only condition), standard packages below
5. Edukasi Melukis — marketing start Rp85.000/pax
6. Private & Custom EduTrip — By Quotation

`marketing_start_price` is deliberately separated from `program_packages.price_per_pax`. A marketing “mulai” price cannot automatically become a quotation price.

## Batik standard packages

| Package | Public | B2B MoU net | Public resale | Min pax |
|---|---:|---:|---:|---:|
| Basic Kids | Rp140.000 | Rp130.000 | Rp140.000 | 20 |
| Regular | Rp175.000 | Rp160.000 | Rp175.000 | 20 |
| Experience 30×30 cm | Rp245.000 | Rp230.000 | Rp245.000 | 20 |
| Full Experience 210×115 cm | Rp495.000 | Rp460.000 | Rp495.000 | 20 |

Vendor cashback (15K / 20K / 30K / 50K) is stored in the economics notes as a panitia/customer benefit and is not GMU profit or an HPP reduction.

## B2B commercial rule

B2B net pricing is only valid when:

- partner exists in `b2b_partners`,
- partner status is ACTIVE,
- a MoU/PKS is ACTIVE for the quotation date,
- a package B2B rate is configured and active,
- minimum pax is met,
- GMU margin guard passes.

The end-customer/organization member price remains the official GMU public price. The difference between public price and B2B net price is the partner’s commercial margin. No additional partner commission is embedded in the B2B net rate.

## Margin guard

Global minimum margin is locked at **25%** in `program_pricing_guardrails`.

- SAFE: estimated margin >= 25%
- REVIEW: margin below 25% and Owner/Manager approval required
- BLOCKED: invalid pax, inactive package, missing active MoU/PKS, or missing B2B rate

`resolve_program_package_price(...)` is safe for Sales because it never returns HPP/profit numbers.

`resolve_program_package_price_internal(...)` is restricted to Owner/Director/Manager roles and returns estimated cost, revenue, profit, and margin.

## Role privacy

Sales can see program/package, public price, eligible B2B net price, partner markup, and SAFE/REVIEW/BLOCKED status.

Only Owner/Director/Manager can retrieve internal HPP, profit, and margin details.

## Integration contract

Quotation/Booking should call:

```sql
select public.resolve_program_package_price(
  p_package_code := 'BATIK-BASIC-140',
  p_pax := 20,
  p_channel := 'DIRECT_PUBLIC',
  p_partner_id := null,
  p_as_of := current_date
);
```

For B2B:

```sql
select public.resolve_program_package_price(
  p_package_code := 'BATIK-BASIC-140',
  p_pax := 20,
  p_channel := 'B2B_MOU',
  p_partner_id := '<active-partner-uuid>',
  p_as_of := current_date
);
```

If the result is `REVIEW`, quotation publish must require Owner/Manager approval. If `BLOCKED`, the quote must not be published.

## Next wiring step

The next patch should make `internal-quotation-workflow` consume the resolver before draft suggestion/publish and persist a pricing snapshot (package code, channel, public price, net price, partner id/agreement id, margin status, effective date). This protects historical quotations from later master-price changes.
