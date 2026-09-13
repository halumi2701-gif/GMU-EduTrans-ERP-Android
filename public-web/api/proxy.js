const PUBLIC_FUNCTIONS = new Set([
  'public-package-catalog',
  'public-booking-submit',
  'public-customer-portal',
  'public-payment-checkout',
  'public-customer-document',
  'public-web-customer-v2',
]);

const PROJECT_REF = 'gtgnwasijweewmaubvyg';
const DEFAULT_SUPABASE_URL = `https://${PROJECT_REF}.supabase.co`;
const TRANSIENT_STATUS = new Set([500, 502, 503, 504]);
const SAFE_RETRY_METHODS = new Set(['GET', 'HEAD']);

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function copyQueryWithoutSlug(req) {
  const url = new URL(req.url, 'https://gmu-edutrans.local');
  url.searchParams.delete('slug');
  return url.search;
}

function requestHeaders(req) {
  const headers = {
    accept: req.headers.accept || 'application/json',
    'content-type': req.headers['content-type'] || 'application/json',
    'x-client-info': 'gmu-edutrans-public-web-proxy-v1',
  };

  const authorization = req.headers.authorization;
  if (authorization) headers.authorization = authorization;

  // Public/publishable key only. Never place service_role/secret keys in this proxy.
  const publishableKey = process.env.SUPABASE_PUBLISHABLE_KEY || process.env.SUPABASE_ANON_KEY;
  if (publishableKey) headers.apikey = publishableKey;

  return headers;
}

async function fetchWithTimeout(url, init, timeoutMs) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

async function proxyFetch(url, init, retryable) {
  const attempts = retryable ? 2 : 1;
  let lastError;

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetchWithTimeout(url, init, 8000);
      if (!retryable || !TRANSIENT_STATUS.has(response.status) || attempt === attempts) {
        return response;
      }
    } catch (error) {
      lastError = error;
      if (!retryable || attempt === attempts) throw error;
    }

    await sleep(250 * attempt);
  }

  throw lastError || new Error('Upstream request failed.');
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,PATCH,DELETE,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Cache-Control', 'no-store');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  const slug = String(req.query?.slug || '').trim();
  if (!PUBLIC_FUNCTIONS.has(slug)) {
    return res.status(404).json({ error: 'Public function tidak tersedia.' });
  }

  const method = String(req.method || 'GET').toUpperCase();
  const supabaseUrl = String(process.env.SUPABASE_URL || DEFAULT_SUPABASE_URL).replace(/\/$/, '');
  const target = `${supabaseUrl}/functions/v1/${encodeURIComponent(slug)}${copyQueryWithoutSlug(req)}`;
  const retryable = SAFE_RETRY_METHODS.has(method);

  let body;
  if (!['GET', 'HEAD'].includes(method) && req.body !== undefined && req.body !== null) {
    body = typeof req.body === 'string' || Buffer.isBuffer(req.body)
      ? req.body
      : JSON.stringify(req.body);
  }

  try {
    const upstream = await proxyFetch(target, {
      method,
      headers: requestHeaders(req),
      body,
    }, retryable);

    const text = await upstream.text();
    const contentType = upstream.headers.get('content-type');
    if (contentType) res.setHeader('Content-Type', contentType);

    // Preserve upstream status. Do not turn valid 4xx responses into generic 500s.
    return res.status(upstream.status).send(text);
  } catch (error) {
    console.error('GMU public proxy upstream failure', {
      slug,
      method,
      message: error instanceof Error ? error.message : String(error),
    });

    return res.status(504).json({
      error: 'Layanan customer sedang mengalami gangguan sementara.',
      detail: 'Gateway Timeout',
    });
  }
};
