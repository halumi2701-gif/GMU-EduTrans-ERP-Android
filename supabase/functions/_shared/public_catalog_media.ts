// GMU EduTrans public catalog media projection.
// This helper is intentionally allow-list based: public endpoints must never spread
// raw Program/Package rows because those may contain internal pricing or cost fields.

type JsonRecord = Record<string, unknown>;

function stringList(value: unknown, maxItems = 50): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => String(item ?? '').trim())
    .filter(Boolean)
    .slice(0, maxItems);
}

function publicHttpsUrl(value: unknown): string | null {
  const raw = String(value ?? '').trim();
  if (!raw) return null;
  try {
    const url = new URL(raw);
    return url.protocol === 'https:' ? url.href : null;
  } catch {
    return null;
  }
}

export function publicProgram(row: JsonRecord) {
  return {
    id: row.id ?? null,
    slug: row.slug ?? null,
    name: row.name ?? null,
    category: row.category ?? null,
    short_description: row.short_description ?? null,
    min_pax: row.min_pax ?? null,
    cover_image_url: publicHttpsUrl(row.cover_image_url),
    gallery_urls: stringList(row.gallery_urls, 5)
      .map(publicHttpsUrl)
      .filter((url): url is string => Boolean(url)),
  };
}

export function publicPackage(row: JsonRecord, estimatedTotal?: number | null) {
  return {
    id: row.id ?? null,
    package_code: row.package_code ?? null,
    program_id: row.program_id ?? null,
    program_name: row.program_name ?? null,
    program_category: row.program_category ?? null,
    name: row.name ?? null,
    description: row.description ?? null,
    price_per_pax: row.price_per_pax ?? null,
    min_pax: row.min_pax ?? null,
    facilities: stringList(row.facilities, 50),
    effective_from: row.effective_from ?? null,
    effective_until: row.effective_until ?? null,
    cover_image_url: publicHttpsUrl(row.cover_image_url),
    gallery_urls: stringList(row.gallery_urls, 5)
      .map(publicHttpsUrl)
      .filter((url): url is string => Boolean(url)),
    estimated_total: estimatedTotal ?? null,
  };
}

// Explicit deny-list documentation for reviewers. These are never returned above:
// price_note, HPP/base_cost, cost templates, manager/sales/mitra fees,
// profit, margin, pricing policy, internal notes, created_by/updated_by.
