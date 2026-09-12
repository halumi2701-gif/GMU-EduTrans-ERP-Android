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

  function programMedia(program) {
    const cover = safeMediaUrl(program.cover_image_url || program.image_url);
    const gallery = (Array.isArray(program.gallery_urls) ? program.gallery_urls : [])
      .map(safeMediaUrl)
      .filter(Boolean);
    const all = [cover, ...gallery].filter(Boolean).filter((url, index, list) => list.indexOf(url) === index).slice(0, 6);
    return all.length ? all : [FALLBACK];
  }

  function programCard(program) {
    const media = programMedia(program);
    const cycleAttrs = media.length > 1
      ? ` data-program-media="${esc(JSON.stringify(media))}" role="button" tabindex="0" aria-label="Lihat ${media.length} foto ${esc(program.name || 'Program GMU EduTrans')}" title="Klik untuk melihat foto berikutnya"`
      : '';
    return `<article class="card program">
      <img class="cover" src="${esc(media[0])}" alt="${esc(program.name || 'Program GMU EduTrans')}" loading="lazy" decoding="async"${cycleAttrs}>
      <div class="programBody">
        <span class="badge">${esc(program.category || 'EDUCATIONAL')}</span>
        <h3>${esc(program.name || 'Program GMU EduTrans')}</h3>
        <p class="muted">${esc(program.short_description || 'Program edukasi GMU EduTrans.')}</p>
        ${media.length > 1 ? `<small class="muted">${media.length} foto • klik gambar untuk melihat galeri</small>` : ''}
      </div>
    </article>`;
  }

  function wireProgramMedia(container) {
    container.querySelectorAll('img[data-program-media]').forEach((image) => {
      let media = [];
      try { media = JSON.parse(image.dataset.programMedia || '[]').map(safeMediaUrl).filter(Boolean); } catch (_) {}
      if (media.length < 2) return;
      let index = Math.max(0, media.indexOf(safeMediaUrl(image.src)));
      const next = () => {
        index = (index + 1) % media.length;
        image.src = media[index];
      };
      image.addEventListener('click', next);
      image.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          next();
        }
      });
    });
  }

  function renderPrograms(container, programs) {
    if (!container || !Array.isArray(programs)) return;
    container.innerHTML = programs.map(programCard).join('');
    wireProgramMedia(container);
  }

  window.GMU_PROGRAM_RENDERER_V24 = { programCard, renderPrograms };
})();
