(() => {
  'use strict';

  const VERSION = 'v10.0-company-operating-system-id';
  const POLICY = Object.freeze({
    targetNetProfitMonthly: 15_000_000,
    healthyMarginPct: 25,
    criticalMarginPct: 20,
    managerPlannedRabLimit: 1_000_000,
    managerUnplannedLimit: 250_000,
    managerEmergencyLimit: 500_000,
    managerMaxDiscountPct: 5,
    managerTransportPerAttendance: 30_000,
    officeUtilitiesMonthly: 1_000_000,
    priorityRegions: ['Cianjur', 'Sukabumi'],
    technologyCosts: [
      'Server & Hosting', 'Domain', 'Cloud & Database', 'AI & Otomatisasi',
      'Email & Notifikasi', 'Penyimpanan & Backup', 'Software & Langganan',
      'Keamanan Digital', 'API Pihak Ketiga'
    ],
    marketSegments: [
      'Sekolah & Madrasah', 'Pesantren', 'Perguruan Tinggi', 'Instansi Pemerintah',
      'Komunitas & Organisasi', 'Perusahaan / Corporate', 'Partner & Mitra'
    ]
  });

  const q = (sel, root = document) => root.querySelector(sel);
  const h = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'})[c]);
  const money = value => 'Rp ' + Number(value || 0).toLocaleString('id-ID');

  function role() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }

  function isDirector() { return ['Owner', 'Director', 'Direktur'].includes(role()); }
  function isManager() { return ['Manager', 'Manager EduTrans'].includes(role()); }

  function roleMission() {
    if (isDirector()) return 'Menentukan hasil, arah, batas risiko, dan keputusan strategis. Pekerjaan harian dikelola Manager.';
    if (isManager()) return 'Bertanggung jawab atas laba unit, penjualan, pemesanan, operasional, biaya, tim, pelanggan, dan rencana pemulihan.';
    if (role() === 'Sales') return 'Menghasilkan calon pelanggan, tindak lanjut, penawaran, negosiasi, dan pemesanan dengan margin sehat.';
    if (['Finance', 'Keuangan'].includes(role())) return 'Menjaga tagihan, pembayaran, piutang, kewajiban, biaya aktual, penggajian, kas, dan penutupan keuangan.';
    if (['Admin', 'Operation', 'Operasional'].includes(role())) return 'Menjaga dokumen, manifest, rundown, vendor, crew, dan kesiapan kegiatan.';
    if (role() === 'TL') return 'Menjalankan kegiatan sesuai penugasan, rundown, keselamatan, pelayanan, dan laporan.';
    return 'Menjalankan tugas sesuai jabatan, SOP, target, dan kewenangan.';
  }

  function parseMoney(text) {
    const m = String(text || '').match(/(?:rp\s*)?([0-9][0-9.]{3,})/i);
    return m ? Number(m[1].replace(/\./g, '')) : 0;
  }

  function parsePct(text, keys) {
    const s = String(text || '').toLowerCase();
    for (const key of keys) {
      const m = s.match(new RegExp(`${key}\\s*[:=]?\\s*([0-9]+(?:[.,][0-9]+)?)\\s*%?`, 'i'));
      if (m) return Number(m[1].replace(',', '.'));
    }
    return null;
  }

  function authorityFor(text) {
    const s = String(text || '').toLowerCase();
    const amount = parseMoney(s);
    const discount = parsePct(s, ['diskon', 'discount']);
    const margin = parsePct(s, ['margin']);
    const strategic = ['refund', 'pembatalan', 'cancellation', 'rekening', 'jurnal', 'cadangan kas', 'reserve', 'investasi', 'capex', 'pegawai tetap', 'fraud', 'legal'];

    if (strategic.some(k => s.includes(k))) return { level: 'DIRECTOR', reason: 'Keputusan strategis / sensitif' };
    if (margin !== null && margin < POLICY.criticalMarginPct) return { level: 'DIRECTOR', reason: 'Margin di bawah 20%' };
    if (discount !== null && discount > POLICY.managerMaxDiscountPct) return { level: 'DIRECTOR', reason: 'Diskon di atas 5%' };

    const emergency = s.includes('darurat') || s.includes('emergency');
    const unplanned = s.includes('luar rab') || s.includes('di luar rab') || s.includes('unplanned');
    if (emergency && amount > POLICY.managerEmergencyLimit) return { level: 'DIRECTOR', reason: 'Biaya darurat di atas Rp500.000' };
    if (unplanned && amount > POLICY.managerUnplannedLimit) return { level: 'DIRECTOR', reason: 'Biaya di luar RAB di atas Rp250.000' };
    if (amount > POLICY.managerPlannedRabLimit) return { level: 'DIRECTOR', reason: 'Biaya dalam RAB di atas Rp1.000.000' };

    return { level: isManager() ? 'MANAGER' : 'NORMAL', reason: 'Masuk batas kewenangan operasional' };
  }

  function installStyle() {
    if (q('#gmuCompanyOsStyle')) return;
    const style = document.createElement('style');
    style.id = 'gmuCompanyOsStyle';
    style.textContent = `
      .gmu-company-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
      .gmu-company-kpi{border:1px solid var(--line);border-radius:14px;padding:12px;background:#fff}
      .gmu-company-kpi b{display:block;font-size:15px;color:var(--gd)}
      .gmu-company-kpi small{font-size:8px;color:var(--muted)}
      .gmu-company-list{font-size:9px;line-height:1.65;color:var(--text)}
      .gmu-company-policy{background:#f4f8f6;border-radius:14px;padding:12px;font-size:9px;line-height:1.6}
      @media(max-width:760px){.gmu-company-grid{grid-template-columns:1fr}}
    `;
    document.head.appendChild(style);
  }

  function installPage() {
    if (q('#companyControl')) return;
    const content = q('.content');
    if (!content) return;
    const page = document.createElement('section');
    page.id = 'companyControl';
    page.className = 'page';
    page.innerHTML = `
      <div class="notice">Sistem Kendali GMU EduTrans ${VERSION} • Bahasa Indonesia • Direktur fokus pada hasil dan pengecualian strategis.</div>
      <div class="card section">
        <div class="head"><div><h3>Kendali Perusahaan</h3><p id="gmuCompanyMission"></p></div><span class="badge info">Cianjur • Sukabumi</span></div>
        <div class="gmu-company-grid">
          <div class="gmu-company-kpi"><small>Target laba bersih bulanan</small><b>${money(POLICY.targetNetProfitMonthly)}</b></div>
          <div class="gmu-company-kpi"><small>Target margin sehat</small><b>≥ ${POLICY.healthyMarginPct}%</b></div>
          <div class="gmu-company-kpi"><small>Transport Manager</small><b>${money(POLICY.managerTransportPerAttendance)}/hari hadir</b></div>
          <div class="gmu-company-kpi"><small>Utilitas kantor</small><b>${money(POLICY.officeUtilitiesMonthly)}/bulan</b></div>
        </div>
      </div>
      <div class="gmu-company-grid">
        <div class="card section" style="margin-top:0"><div class="head"><div><h3>Kewenangan Manager</h3><p>Batas tindakan tanpa persetujuan Direktur</p></div></div>
          <div class="gmu-company-list">• Biaya dalam RAB ≤ ${money(POLICY.managerPlannedRabLimit)}<br>• Biaya di luar RAB ≤ ${money(POLICY.managerUnplannedLimit)}<br>• Darurat saat trip ≤ ${money(POLICY.managerEmergencyLimit)}<br>• Diskon ≤ ${POLICY.managerMaxDiscountPct}%<br>• Margin 20–24,99%: tinjauan Manager<br>• Margin &lt;20%: persetujuan Direktur</div>
        </div>
        <div class="card section" style="margin-top:0"><div class="head"><div><h3>Pasar Prioritas</h3><p>Fokus tahap pertama</p></div></div>
          <div class="gmu-company-list">${POLICY.marketSegments.map(x => `• ${h(x)}`).join('<br>')}</div>
        </div>
      </div>
      <div class="card section"><div class="head"><div><h3>Biaya Teknologi & Infrastruktur Digital</h3><p>Wajib masuk overhead sebelum laba bersih dinyatakan tercapai</p></div></div>
        <div class="gmu-company-list">${POLICY.technologyCosts.map(x => `• ${h(x)}`).join('<br>')}</div>
      </div>
      <div class="gmu-company-policy"><b>Prinsip manajemen:</b> Direktur menentukan hasil → Manager mengelola bisnis → ERP mengatur proses → AI membantu analisis → Tim menjalankan tugas. Masalah normal berhenti di Manager; Direktur menerima pengecualian strategis/kritis.</div>
    `;
    content.appendChild(page);
    const mission = q('#gmuCompanyMission');
    if (mission) mission.textContent = roleMission();
  }

  function installNav() {
    if (q('#nav [data-page="companyControl"]')) return;
    const nav = q('#nav');
    if (!nav) return;
    const button = document.createElement('button');
    button.dataset.page = 'companyControl';
    button.innerHTML = '⌁ &nbsp; Kendali Perusahaan';
    nav.appendChild(button);
  }

  function correctOpsPolicyText() {
    const page = q('#opsAgent');
    if (!page) return;
    const policyText = [...page.querySelectorAll('.gmu-ops-policy')].find(el => /Transaksi di atas|approval Direktur|Batas Manager/i.test(el.textContent || ''));
    if (!policyText) return;
    const desired = 'Batas Manager: dalam RAB ≤ Rp1.000.000; di luar RAB ≤ Rp250.000; darurat trip ≤ Rp500.000; diskon ≤5%. Margin <20%, refund, perubahan harga strategis, rekening, jurnal dan keputusan sensitif wajib persetujuan Direktur.';
    if (policyText.textContent !== desired) policyText.textContent = desired;
  }

  function blockOutOfAuthorityOpsCommand(event) {
    const send = event.target?.closest?.('#gmuOpsSend');
    if (!send) return;
    const input = q('#gmuOpsCommand');
    const text = input?.value || '';
    const auth = authorityFor(text);
    if (auth.level !== 'DIRECTOR') return;

    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    const badge = q('#gmuOpsAuthority');
    const reply = q('#gmuOpsReply');
    if (badge) badge.textContent = 'Perlu Persetujuan Direktur';
    if (reply) {
      reply.dataset.tone = 'warn';
      reply.textContent = `${auth.reason}. Sistem tidak meneruskan tindakan ini sebagai kewenangan Manager.`;
    }
  }

  function blockOutOfAuthorityEnter(event) {
    if (event.key !== 'Enter' || event.target?.id !== 'gmuOpsCommand') return;
    const auth = authorityFor(event.target.value || '');
    if (auth.level !== 'DIRECTOR') return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    const badge = q('#gmuOpsAuthority');
    const reply = q('#gmuOpsReply');
    if (badge) badge.textContent = 'Perlu Persetujuan Direktur';
    if (reply) {
      reply.dataset.tone = 'warn';
      reply.textContent = `${auth.reason}. Sistem tidak meneruskan tindakan ini sebagai kewenangan Manager.`;
    }
  }

  function relabelCommonNavigation() {
    const labels = {
      dashboard: 'Ringkasan', booking: 'Pemesanan', customer: 'Pelanggan', finance: 'Keuangan',
      operation: 'Operasional', reports: 'Laporan', workflow: 'Persetujuan'
    };
    Object.entries(labels).forEach(([page, label]) => {
      const btn = q(`#nav [data-page="${page}"]`);
      if (!btn) return;
      const raw = (btn.textContent || '').trim();
      const icon = raw.split(/\s+/)[0] || '';
      const desired = `${icon}  ${label}`.trim();
      if (raw !== desired) btn.textContent = desired;
    });
  }

  function init() {
    installStyle();
    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      installPage();
      installNav();
      relabelCommonNavigation();
      correctOpsPolicyText();
    }, 250);

    document.addEventListener('click', blockOutOfAuthorityOpsCommand, true);
    document.addEventListener('keydown', blockOutOfAuthorityEnter, true);

    let queued = false;
    const observer = new MutationObserver(() => {
      if (queued) return;
      queued = true;
      requestAnimationFrame(() => {
        queued = false;
        correctOpsPolicyText();
        relabelCommonNavigation();
      });
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });

    window.GmuCompanyOperatingSystem = Object.freeze({
      version: VERSION,
      policy: POLICY,
      authorityFor,
      roleMission,
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
