(() => {
  'use strict';

  const VERSION = 'v10.3.1-sales-target-engine';
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

  const STATION_TARGET_DEFAULT = Object.freeze({
    programKey: 'EDU_STATION',
    programName: 'Edukasi Stasiun',
    periodMonth: '2026-09-01',
    periodLabel: 'September 2026',
    pricePerPax: 46_000,
    bepPax: 60,
    productivePax: 200,
    targetPax: 400,
    nextTargetPax: 600,
    salesRetainer: 600_000,
    salesFeePerPax: 2_500,
    targetBonus: 250_000,
    contributionTarget: 8_000_000,
    contributionMarginPct: 43.5,
  });

  let stationTarget = { ...STATION_TARGET_DEFAULT };

  const q = (s, root = document) => root.querySelector(s);
  const money = v => 'Rp ' + Number(v || 0).toLocaleString('id-ID');
  const integer = v => Number(v || 0).toLocaleString('id-ID');

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
    if (isSales()) return 'Sales fokus pada aktivitas hari ini: calon pelanggan, tindak lanjut, penawaran, negosiasi, paid pax, dan booking sehat.';
    return 'Target penjualan mengikuti sasaran perusahaan dan kewenangan role.';
  }

  function stationComputed() {
    const targetRevenue = Number(stationTarget.targetPax || 0) * Number(stationTarget.pricePerPax || 0);
    const feeAtTarget = Number(stationTarget.targetPax || 0) * Number(stationTarget.salesFeePerPax || 0);
    const salesIncomeAtTarget = Number(stationTarget.salesRetainer || 0) + feeAtTarget + Number(stationTarget.targetBonus || 0);
    return { targetRevenue, feeAtTarget, salesIncomeAtTarget };
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
      .gmu-station-target{border:1px solid #cfe3d7;background:#f7fbf8}
      .gmu-station-levels{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin-top:10px}
      .gmu-station-level{border:1px solid var(--line);border-radius:12px;background:#fff;padding:11px}
      .gmu-station-level small{display:block;font-size:8px;color:var(--muted);margin-bottom:4px}
      .gmu-station-level b{display:block;font-size:16px;color:var(--gd)}
      .gmu-station-level span{display:block;font-size:8px;color:var(--muted);margin-top:4px;line-height:1.45}
      .gmu-station-level.current{border-color:#93c7a7;background:#f2faf5}
      @media(max-width:920px){.gmu-target-grid{grid-template-columns:repeat(2,1fr)}.gmu-target-flow{grid-template-columns:1fr 1fr}.gmu-target-actions{grid-template-columns:1fr}.gmu-station-levels{grid-template-columns:repeat(2,1fr)}}
      @media(max-width:520px){.gmu-target-grid{grid-template-columns:1fr}.gmu-target-flow{grid-template-columns:1fr}.gmu-station-levels{grid-template-columns:1fr}}
    `;
    document.head.appendChild(style);
  }

  function stationSectionHtml() {
    const calc = stationComputed();
    return `
      <div class="card section gmu-station-target" id="gmuStationTargetSection">
        <div class="head">
          <div>
            <h3>Target Program • ${stationTarget.programName}</h3>
            <p>${stationTarget.periodLabel} • target dihitung dari paid pax, bukan quotation.</p>
          </div>
          <span class="badge ok">TARGET AKTIF</span>
        </div>
        <div class="gmu-target-grid">
          <div class="gmu-target-card"><small>Harga jual / pax</small><b>${money(stationTarget.pricePerPax)}</b><span>Harga resmi program; diskon wajib approval.</span></div>
          <div class="gmu-target-card"><small>Target bulan ini</small><b>${integer(stationTarget.targetPax)} paid pax</b><span>Target omzet ${money(calc.targetRevenue)}.</span></div>
          <div class="gmu-target-card"><small>Fee Sales / paid pax</small><b>${money(stationTarget.salesFeePerPax)}</b><span>= Rp50.000 per 20 pax = Rp100.000 per 40 pax.</span></div>
          <div class="gmu-target-card"><small>Penghasilan Sales di target</small><b>${money(calc.salesIncomeAtTarget)}</b><span>Retainer ${money(stationTarget.salesRetainer)} + fee ${money(calc.feeAtTarget)} + bonus ${money(stationTarget.targetBonus)}.</span></div>
          <div class="gmu-target-card"><small>Kontribusi program ke PT</small><b>${money(stationTarget.contributionTarget)}</b><span>Baseline target 400 pax; belum disebut laba bersih PT sebelum overhead korporat.</span></div>
          <div class="gmu-target-card"><small>Contribution margin baseline</small><b>± ${Number(stationTarget.contributionMarginPct || 0).toLocaleString('id-ID')}%</b><span>Harus tetap dijaga; bonus tidak boleh merusak margin.</span></div>
        </div>
        <div class="gmu-station-levels">
          <div class="gmu-station-level"><small>BEP TARGET</small><b>${integer(stationTarget.bepPax)} pax</b><span>Batas minimum untuk menutup retainer Sales.</span></div>
          <div class="gmu-station-level"><small>MINIMUM PRODUKTIF</small><b>${integer(stationTarget.productivePax)} pax</b><span>Sales mulai memberi kontribusi produktif.</span></div>
          <div class="gmu-station-level current"><small>TARGET BULAN INI</small><b>${integer(stationTarget.targetPax)} pax</b><span>Target resmi ${stationTarget.periodLabel}.</span></div>
          <div class="gmu-station-level"><small>TARGET NORMAL BERIKUTNYA</small><b>${integer(stationTarget.nextTargetPax)} pax</b><span>Naik setelah target 400 pax stabil dan margin aman.</span></div>
        </div>
        <div class="gmu-target-note" style="margin-top:10px">
          <b>Guardrail:</b> fee Sales hanya dari <b>paid pax</b>. Bonus target hanya cair jika pembayaran masuk, harga tidak didiskon tanpa approval, dan margin program tetap memenuhi kebijakan perusahaan.
        </div>
      </div>`;
  }

  function refreshStationSection() {
    const current = q('#gmuStationTargetSection');
    if (!current) return;
    const wrap = document.createElement('div');
    wrap.innerHTML = stationSectionHtml().trim();
    const next = wrap.firstElementChild;
    if (next) current.replaceWith(next);
  }

  async function loadStationTarget() {
    if (typeof sb === 'undefined' || !sb) return;
    try {
      const response = await sb
        .from('program_sales_targets')
        .select('program_key,program_name,period_month,price_per_pax,bep_pax,productive_pax,target_pax,next_target_pax,sales_retainer,sales_fee_per_pax,target_bonus,contribution_target,contribution_margin_pct')
        .eq('program_key', STATION_TARGET_DEFAULT.programKey)
        .eq('period_month', STATION_TARGET_DEFAULT.periodMonth)
        .eq('status', 'ACTIVE')
        .maybeSingle();
      if (response.error || !response.data) return;
      const row = response.data;
      stationTarget = {
        ...stationTarget,
        programKey: row.program_key || stationTarget.programKey,
        programName: row.program_name || stationTarget.programName,
        periodMonth: row.period_month || stationTarget.periodMonth,
        pricePerPax: Number(row.price_per_pax || stationTarget.pricePerPax),
        bepPax: Number(row.bep_pax || stationTarget.bepPax),
        productivePax: Number(row.productive_pax || stationTarget.productivePax),
        targetPax: Number(row.target_pax || stationTarget.targetPax),
        nextTargetPax: Number(row.next_target_pax || stationTarget.nextTargetPax),
        salesRetainer: Number(row.sales_retainer || stationTarget.salesRetainer),
        salesFeePerPax: Number(row.sales_fee_per_pax || stationTarget.salesFeePerPax),
        targetBonus: Number(row.target_bonus || stationTarget.targetBonus),
        contributionTarget: Number(row.contribution_target || stationTarget.contributionTarget),
        contributionMarginPct: Number(row.contribution_margin_pct || stationTarget.contributionMarginPct),
      };
      refreshStationSection();
    } catch (_) {
      // Fallback constants keep targeting visible when the migration has not been deployed yet.
    }
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
      <div class="notice">Target & Kinerja Penjualan ${VERSION} • Sasaran perusahaan diturunkan menjadi target tim, program, dan aktivitas harian Sales.</div>

      ${stationSectionHtml()}

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
          <div class="gmu-target-card"><small>Bulanan Program</small><b>${integer(stationTarget.targetPax)} paid pax</b><span>BEP ${integer(stationTarget.bepPax)} • minimum produktif ${integer(stationTarget.productivePax)} • next ${integer(stationTarget.nextTargetPax)} pax.</span></div>
        </div>
      </div>

      <div class="gmu-target-note">
        <b>Aturan sistem:</b> target program Edukasi Stasiun berjalan bersama baseline target perusahaan. Mesin target tetap menghitung <b>Target Laba Bersih → Required Contribution → Required Revenue → Required Booking/Pax → Product Mix → Target Tim → Target Sales → Aktivitas Harian</b>. Omzet tanpa cash-in dan margin sehat tidak dianggap pencapaian penuh.
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

    loadStationTarget();
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
    window.GmuSalesTargetEngine = Object.freeze({ version: VERSION, target: TARGET, stationTarget: STATION_TARGET_DEFAULT, showPage });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
