(() => {
  'use strict';

  const VERSION = 'v10.3-sales-target-engine';
  const ALLOWED = new Set(['Owner','Director','Direktur','Manager','Manager EduTrans','Sales']);
  const TARGET = Object.freeze({
    netProfitMonthly: 15_000_000,
    prospectsMonthly: 200,
    qualifiedLeadsMonthly: 20,
    quotationsMonthly: 12,
    minimumBookingsMonthly: 3,
    idealBookingsMonthly: '4–6',
    quotationToBookingPct: 25,
    prospectsDaily: 10,
    followUpsDaily: 10,
    prospectsWeekly: 50,
    qualifiedLeadsWeekly: 5,
    quotationsWeekly: 3,
    healthyMarginPct: 25,
  });

  const q = (s, root = document) => root.querySelector(s);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');

  function role() {
    try { return String(profile?.role || ''); } catch (_) { return ''; }
  }
  function allowed() { return ALLOWED.has(role()); }
  function isDirector() { return ['Owner','Director','Direktur'].includes(role()); }
  function isManager() { return ['Manager','Manager EduTrans'].includes(role()); }
  function isSales() { return role() === 'Sales'; }

  function missionText() {
    if (isDirector()) return 'Direktur menetapkan hasil. Manager bertanggung jawab mengubah target perusahaan menjadi target penjualan dan tindakan tim.';
    if (isManager()) return 'Manager bertanggung jawab menjaga funnel, konversi, booking, margin, dan rencana pemulihan bila forecast di bawah target.';
    if (isSales()) return 'Sales fokus pada aktivitas hari ini: calon pelanggan, tindak lanjut, penawaran, negosiasi, dan booking sehat.';
    return 'Target penjualan mengikuti sasaran perusahaan dan kewenangan role.';
  }

  function installStyle() {
    if (q('#gmuSalesTargetStyle')) return;
    const style = document.createElement('style');
    style.id = 'gmuSalesTargetStyle';
    style.textContent = `
      .gmu-target-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}
      .gmu-target-card{border:1px solid var(--line);background:#fff;border-radius:14px;padding:12px}
      .gmu-target-card small{display:block;font-size:8px;color:var(--muted);margin-bottom:4px}
      .gmu-target-card b{display:block;font-size:17px;color:var(--gd)}
      .gmu-target-card span{display:block;font-size:8px;color:var(--muted);margin-top:4px;line-height:1.45}
      .gmu-target-flow{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8px}
      .gmu-target-flow div{border:1px solid var(--line);border-radius:12px;padding:10px;background:#f8fbf9;text-align:center}
      .gmu-target-flow b{display:block;color:var(--g);font-size:16px}.gmu-target-flow small{font-size:8px;color:var(--muted)}
      .gmu-target-note{font-size:9px;line-height:1.65;background:#f4f8f6;border-radius:14px;padding:12px}
      .gmu-target-actions{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}
      .gmu-target-actions .gmu-target-card b{font-size:14px}
      @media(max-width:920px){.gmu-target-grid{grid-template-columns:repeat(2,1fr)}.gmu-target-flow{grid-template-columns:1fr 1fr}.gmu-target-actions{grid-template-columns:1fr}}
      @media(max-width:520px){.gmu-target-grid{grid-template-columns:1fr}.gmu-target-flow{grid-template-columns:1fr}}
    `;
    document.head.appendChild(style);
  }

  function installPage() {
    if (!allowed() || q('#salesTargetControl')) return;
    const content = q('.content');
    const nav = q('#nav');
    if (!content || !nav) return;

    const page = document.createElement('section');
    page.id = 'salesTargetControl';
    page.className = 'page';
    page.innerHTML = `
      <div class="notice">Target & Kinerja Penjualan ${VERSION} • Sasaran perusahaan diturunkan menjadi target tim dan aktivitas harian Sales.</div>

      <div class="card section">
        <div class="head"><div><h3>Target Perusahaan → Target Penjualan</h3><p>${missionText()}</p></div><span class="badge info">Cianjur • Sukabumi</span></div>
        <div class="gmu-target-grid">
          <div class="gmu-target-card"><small>Target laba bersih perusahaan</small><b>${money(TARGET.netProfitMonthly)}</b><span>Sesudah seluruh HPP, SDM, komisi, overhead, utilitas, dan teknologi.</span></div>
          <div class="gmu-target-card"><small>Target prospek / bulan</small><b>${TARGET.prospectsMonthly}</b><span>Baseline awal sampai target dinamis dari unit economics aktif.</span></div>
          <div class="gmu-target-card"><small>Peluang potensial / bulan</small><b>${TARGET.qualifiedLeadsMonthly}</b><span>Target minimum lead yang sudah layak ditindaklanjuti serius.</span></div>
          <div class="gmu-target-card"><small>Penawaran harga / bulan</small><b>${TARGET.quotationsMonthly}</b><span>Penawaran hanya dari Master Program dan harus menjaga margin.</span></div>
          <div class="gmu-target-card"><small>Booking minimum / bulan</small><b>${TARGET.minimumBookingsMonthly}</b><span>Target awal; sasaran ideal ${TARGET.idealBookingsMonthly} booking/bulan.</span></div>
          <div class="gmu-target-card"><small>Konversi penawaran → booking</small><b>≥ ${TARGET.quotationToBookingPct}%</b><span>Jika turun, Manager wajib membuat rencana pemulihan.</span></div>
          <div class="gmu-target-card"><small>Margin kegiatan sehat</small><b>≥ ${TARGET.healthyMarginPct}%</b><span>Penjualan yang menambah omzet tetapi merusak margin bukan pertumbuhan sehat.</span></div>
          <div class="gmu-target-card"><small>Wilayah aktif</small><b>Cianjur + Sukabumi</b><span>Tidak ekspansi luas sebelum pasar inti, profit, cash, dan operasi stabil.</span></div>
        </div>
      </div>

      <div class="card section">
        <div class="head"><div><h3>Funnel Target Bulanan</h3><p>Baseline operasional yang wajib terlihat oleh Manager dan Sales.</p></div></div>
        <div class="gmu-target-flow">
          <div><small>Calon Pelanggan</small><b>${TARGET.prospectsMonthly}</b></div>
          <div><small>Peluang Potensial</small><b>${TARGET.qualifiedLeadsMonthly}</b></div>
          <div><small>Penawaran</small><b>${TARGET.quotationsMonthly}</b></div>
          <div><small>Booking Minimum</small><b>${TARGET.minimumBookingsMonthly}</b></div>
          <div><small>Booking Ideal</small><b>${TARGET.idealBookingsMonthly}</b></div>
        </div>
      </div>

      <div class="card section">
        <div class="head"><div><h3>${isSales() ? 'Target Saya' : 'Target Aktivitas Sales'}</h3><p>Dibuat sederhana agar Sales tahu persis apa yang harus dikerjakan hari ini.</p></div></div>
        <div class="gmu-target-actions">
          <div class="gmu-target-card"><small>Harian</small><b>${TARGET.prospectsDaily} prospek baru</b><span>${TARGET.followUpsDaily} tindak lanjut per hari.</span></div>
          <div class="gmu-target-card"><small>Mingguan</small><b>${TARGET.prospectsWeekly} prospek</b><span>${TARGET.qualifiedLeadsWeekly} peluang potensial + ${TARGET.quotationsWeekly} penawaran.</span></div>
          <div class="gmu-target-card"><small>Bulanan</small><b>${TARGET.minimumBookingsMonthly} booking minimum</b><span>Ideal ${TARGET.idealBookingsMonthly}; konversi ≥${TARGET.quotationToBookingPct}%.</span></div>
        </div>
      </div>

      <div class="gmu-target-note">
        <b>Aturan sistem:</b> target di atas adalah baseline GMU EduTrans. Tahap berikutnya mesin target menghitung otomatis dari <b>Target Laba Bersih → Required Contribution → Required Revenue → Required Booking/Pax → Product Mix → Target Tim → Target Sales → Aktivitas Harian</b>. Jika data biaya program belum lengkap, ERP tidak boleh mengarang required revenue atau booking.
      </div>
    `;
    content.appendChild(page);

    const button = document.createElement('button');
    button.dataset.page = 'salesTargetControl';
    button.innerHTML = '◎ &nbsp; Target & Kinerja';
    button.addEventListener('click', showPage);

    const marketButton = q('#nav [data-page="marketIntelligence"]');
    if (marketButton) nav.insertBefore(button, marketButton);
    else nav.appendChild(button);
  }

  function showPage() {
    document.querySelectorAll('.page').forEach(el => el.classList.remove('active'));
    q('#salesTargetControl')?.classList.add('active');
    document.querySelectorAll('#nav [data-page]').forEach(el => el.classList.toggle('active', el.dataset.page === 'salesTargetControl'));
    const title = q('#title');
    if (title) title.textContent = isSales() ? 'Target Saya' : 'Target & Kinerja Penjualan';
  }

  function init() {
    installStyle();
    const timer = setInterval(() => {
      if (typeof profile === 'undefined' || !profile) return;
      clearInterval(timer);
      installPage();
    }, 250);
    window.GmuSalesTargetEngine = Object.freeze({ version: VERSION, target: TARGET, showPage });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
