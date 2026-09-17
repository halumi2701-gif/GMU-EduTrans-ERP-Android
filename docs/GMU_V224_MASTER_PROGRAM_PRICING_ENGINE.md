# GMU EduTrans v22.4 — Master Program & Pricing Engine Integration

## Objective

v22.4 makes the Program Master the commercial source of truth for Sales → Quotation → Booking → Finance → Operations → Profitability.

The migration is additive and preserves historical rows. `Company / Factory Visit` is deactivated rather than deleted.

## Canonical active portfolio

1. Edukasi di Atas Kereta Api — marketing start Rp29.000/pax
2. Edukasi Lingkungan & Profesi Stasiun — pricing governed by the 2026 public/B2B station master below
3. Edukasi Bertani Padi Pandanwangi — marketing start Rp75.000/pax
4. Edukasi Membatik — marketing Special School start Rp99.000/pax (quotation-only condition), standard packages below
5. Edukasi Melukis — marketing start Rp85.000/pax
6. Private & Custom EduTrip — By Quotation

`marketing_start_price` is deliberately separated from `program_packages.price_per_pax`. A marketing “mulai” price cannot automatically become a quotation price.

## Edukasi Lingkungan & Profesi Stasiun — public master

Program code: `EDU-STATION-PROF`.

Public/direct pricing:

| Pax | Public price |
|---:|---:|
| 20–24 | Rp65.000/pax |
| 25–39 | Rp58.000/pax |
| 40+ | Rp49.500/pax |

Shared Education Session: **Rp49.500/pax** with combined session minimum **40 pax**.

Public and B2B pricing must remain separate price books.

## Edukasi Lingkungan & Profesi Stasiun — B2B master

School selling price: **Rp49.500/pax**.

| B2B volume | Net GMU → Partner | School cashback | Partner net margin |
|---:|---:|---:|---:|
| 40–59 | Rp45.000 | Rp2.500/pax | Rp2.000/pax |
| 60–79 | Rp44.500 | Rp2.500/pax | Rp2.500/pax |
| 80–99 | Rp44.000 | Rp2.500/pax | Rp3.000/pax |
| 100+ | Rp43.500 | Rp2.500/pax | Rp3.500/pax |

School cashback is funded from the Partner gross spread and must **not** be counted again as GMU HPP.

Sales GMU commission remains **Rp5.000/pax** on eligible B2B transactions and is an internal GMU HPP component.

Canonical detail files:

- `docs/GMU_MASTER_B2B_EDU_STATION_2026.md`
- `docs/gmu-master-b2b-edu-station-2026.json`

## Batik standard packages

| Package | Public | B2B MoU net | Public resale | Min pax |
|---|---:|---:|---:|---:|
| Basic Kids | Rp140.000 | Rp130.000 | Rp140.000 | 20 |
| Regular | Rp175.000 | Rp160.000 | Rp175.000 | 20 |
| Experience 30×30 cm | Rp245.000 | Rp230.000 | Rp245.000 | 20 |
| Full Experience 210×115 cm | Rp495.000 | Rp460.000 | Rp495.000 | 20 |

Vendor cashback (15K / 20K / 30K / 50K) is stored in economics notes as a panitia/customer benefit and is not GMU profit or an HPP reduction.

## B2B commercial rule

B2B net pricing is only valid when:

- partner exists in `b2b_partners`,
- partner status is ACTIVE,
- a MoU/PKS is ACTIVE for the quotation date,
- a package B2B rate is configured and active,
- minimum pax is met,
- GMU margin guard passes.

The end-customer/organization member price remains the official GMU public/school selling price. The difference between school selling price and B2B net price is the partner gross spread. Where a program defines a school cashback, cashback is funded from that spread before Partner net margin is calculated.

## Margin guard

Global minimum margin is locked at **25%** in `program_pricing_guardrails`.

- SAFE: estimated margin >= 25%
- REVIEW: margin below 25% and Owner/Manager approval required
- BLOCKED: invalid pax, inactive package, missing active MoU/PKS, or missing B2B rate

`resolve_program_package_price(...)` is safe for Sales because it never returns HPP/profit numbers.

`resolve_program_package_price_internal(...)` is restricted to Owner/Director/Manager roles and returns estimated cost, revenue, profit, and margin.

## Role privacy

Sales can see program/package, public price, eligible B2B net price, partner markup/spread, cashback rule, and SAFE/REVIEW/BLOCKED status.

Only Owner/Director/Manager can retrieve internal HPP, profit, and margin details.

## Integration contract

Quotation/Booking should call the pricing resolver and persist a pricing snapshot including package code, channel, public/school price, net B2B price, cashback rate, partner id/agreement id, margin status, and effective date.

If the result is `REVIEW`, quotation publish requires Owner/Manager approval. If `BLOCKED`, the quote must not be published.

## Next wiring step

The next patch should make `internal-quotation-workflow` consume the resolver before draft suggestion/publish and persist the pricing snapshot. This protects historical quotations from later master-price changes.
