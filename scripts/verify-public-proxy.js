const assert = require('node:assert/strict');
const handler = require('../public-web/api/proxy.js');

function createRes() {
  return {
    headers: {},
    statusCode: 200,
    body: undefined,
    ended: false,
    setHeader(name, value) { this.headers[String(name).toLowerCase()] = value; },
    status(code) { this.statusCode = code; return this; },
    send(value) { this.body = value; return this; },
    json(value) { this.body = value; return this; },
    end() { this.ended = true; return this; },
  };
}

async function run() {
  const originalFetch = global.fetch;
  const originalPublishable = process.env.SUPABASE_PUBLISHABLE_KEY;
  const originalAnon = process.env.SUPABASE_ANON_KEY;
  const originalService = process.env.SUPABASE_SERVICE_ROLE_KEY;

  try {
    process.env.SUPABASE_PUBLISHABLE_KEY = 'sb_publishable_test';
    process.env.SUPABASE_SERVICE_ROLE_KEY = 'server_secret_must_never_forward';
    delete process.env.SUPABASE_ANON_KEY;

    // GET catalog: retry one transient 504, then succeed.
    {
      let calls = 0;
      let seenUrl = '';
      let seenHeaders;
      global.fetch = async (url, init) => {
        calls += 1;
        seenUrl = String(url);
        seenHeaders = init.headers;
        if (calls === 1) return new Response('temporary', { status: 504 });
        return new Response('{"ok":true}', { status: 200, headers: { 'content-type': 'application/json' } });
      };

      const req = {
        method: 'GET',
        url: '/api/proxy?slug=public-package-catalog&program_id=abc&pax=20',
        query: { slug: 'public-package-catalog', program_id: 'abc', pax: '20' },
        headers: {},
      };
      const res = createRes();
      await handler(req, res);

      assert.equal(calls, 2, 'GET transient failure must retry exactly once');
      assert.equal(res.statusCode, 200);
      assert.equal(res.body, '{"ok":true}');
      assert.match(seenUrl, /\/functions\/v1\/public-package-catalog\?/);
      assert.match(seenUrl, /program_id=abc/);
      assert.match(seenUrl, /pax=20/);
      assert.doesNotMatch(seenUrl, /slug=/, 'slug must not be forwarded as query data');
      assert.equal(seenHeaders.apikey, 'sb_publishable_test');
      assert.notEqual(seenHeaders.apikey, process.env.SUPABASE_SERVICE_ROLE_KEY);
      assert.equal(seenHeaders.authorization, undefined);
    }

    // POST booking: never retry even on a transient 503.
    {
      let calls = 0;
      let seenInit;
      global.fetch = async (_url, init) => {
        calls += 1;
        seenInit = init;
        return new Response('{"error":"temporary"}', { status: 503, headers: { 'content-type': 'application/json' } });
      };

      const req = {
        method: 'POST',
        url: '/api/proxy?slug=public-booking-submit',
        query: { slug: 'public-booking-submit' },
        headers: { 'content-type': 'application/json' },
        body: { package_id: 'pkg-1', pax: 20 },
      };
      const res = createRes();
      await handler(req, res);

      assert.equal(calls, 1, 'POST booking must never retry');
      assert.equal(res.statusCode, 503);
      assert.equal(seenInit.method, 'POST');
      assert.equal(seenInit.body, JSON.stringify(req.body));
    }

    // Unknown slug: reject locally without touching Supabase.
    {
      let calls = 0;
      global.fetch = async () => { calls += 1; throw new Error('must not be called'); };
      const req = {
        method: 'GET',
        url: '/api/proxy?slug=internal-media-master',
        query: { slug: 'internal-media-master' },
        headers: {},
      };
      const res = createRes();
      await handler(req, res);
      assert.equal(calls, 0);
      assert.equal(res.statusCode, 404);
    }

    console.log('GMU public proxy contract passed.');
    console.log('GET/HEAD retry only | POST no retry | public allowlist | publishable key only');
  } finally {
    global.fetch = originalFetch;
    if (originalPublishable === undefined) delete process.env.SUPABASE_PUBLISHABLE_KEY;
    else process.env.SUPABASE_PUBLISHABLE_KEY = originalPublishable;
    if (originalAnon === undefined) delete process.env.SUPABASE_ANON_KEY;
    else process.env.SUPABASE_ANON_KEY = originalAnon;
    if (originalService === undefined) delete process.env.SUPABASE_SERVICE_ROLE_KEY;
    else process.env.SUPABASE_SERVICE_ROLE_KEY = originalService;
  }
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
