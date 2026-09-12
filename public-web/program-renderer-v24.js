/* GMU EduTrans Public Web — Program Renderer v24
 * Program card media comes from ERP Program Master.
 * Expected public fields: cover_image_url, gallery_urls.
 */
(function () {
  const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
    '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
  }[char]));
  const safeMediaUrl = (value) => {
    const raw = String(value || '').trim();
    if (!raw) return '';
    if (raw.startsWith('/')) return raw;
    try {
      const u = new URL(raw, location.origin);
      return u.protocol === 'https:' ? u.href : '';
    } catch (_) { return ''; }
  };

  const FALLBACK = '/assets/gmu-cianjur-edutrip.jpg';

  function programCard(program) {
    const cover = safeMediaUrl(program.cover_image_url || program.image_url) || FALLBACK;
    return `<article class="card program">
      <img class="cover" src="${esc(cover)}" alt="${esc(program.name || 'Program GMU EduTrans')}" loading="lazy" decoding="async">
      <div class="programBody">
        <span class="badge">${esc(program.category || 'EDUCATIONAL')}</span>
        <h3>${esc(program.name || 'Program GMU EduTrans')}</h3>
        <p class="muted">${esc(program.short_description || 'Program edukasi GMU EduTrans.')}</p>
      </div>
    </article>`;
  }

  function renderPrograms(container, programs) {
    if (!container || !Array.isArray(programs)) return;
    container.innerHTML = programs.map(programCard).join('');
  }

  window.GMU_PROGRAM_RENDERER_V24 = { programCard, renderPrograms };
})();
