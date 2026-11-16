(() => {
  'use strict';

  const VERSION = 'v23.2-google-drive-owner-sync-test';
  const SUPABASE_URL = 'https://gtgnwasijweewmaubvyg.supabase.co';
  const SUPABASE_KEY = 'sb_publishable_cbTtSEhcXsHKDdldocSw3Q_bTcfXtaW';
  const SYNC_TEST_ROLES = new Set(['Owner', 'Director', 'Direktur']);
  const BUTTON_ID = 'gmuDriveSyncTestButton';
  const STATUS_ID = 'gmuDriveSyncTestStatus';

  function currentRole() {
    const candidates = [
      (() => { try { return profile?.role; } catch (_) { return ''; } })(),
      window.__GMU_SESSION__?.role,
      window.gmuSession?.role,
      window.profile?.role,
    ];
    return String(candidates.find(Boolean) || '');
  }

  async function accessToken() {
    const candidates = [
      window.__GMU_SESSION__?.accessToken,
      window.gmuSession?.accessToken,
      localStorage.getItem('gmu_access_token'),
      sessionStorage.getItem('gmu_access_token'),
    ];
    const direct = candidates.find(Boolean);
    if (direct) return direct;

    try {
      if (typeof sb !== 'undefined' && sb?.auth?.getSession) {
        const { data } = await sb.auth.getSession();
        const token = data?.session?.access_token;
        if (token) return token;
      }
    } catch (_) {}

    try {
      if (window.sb?.auth?.getSession) {
        const { data } = await window.sb.auth.getSession();
        const token = data?.session?.access_token;
        if (token) return token;
      }
    } catch (_) {}

    return '';
  }

  async function invoke(payload) {
    const token = await accessToken();
    if (!token) throw new Error('Sesi ERP belum aktif. Silakan login ulang.');

    const response = await fetch(`${SUPABASE_URL}/functions/v1/gmu-drive-archive`, {
      method: 'POST',
      headers: {
        apikey: SUPABASE_KEY,
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });

    const body = await response.json().catch(() => ({}));
    if (!response.ok || body.ok === false) {
      throw new Error(body.error || `Google Drive HTTP ${response.status}`);
    }
    return body;
  }

  function ensureStatusStyle() {
    if (document.getElementById('gmuDriveSyncTestStyle')) return;
    const style = document.createElement('style');
    style.id = 'gmuDriveSyncTestStyle';
    style.textContent = `
      #${STATUS_ID}{
        position:fixed;right:18px;bottom:18px;z-index:99999;
        max-width:min(420px,calc(100vw - 36px));padding:11px 13px;
        border-radius:12px;border:1px solid #d8e7dc;background:#f6fbf7;
        color:#174d2f;font:600 12px/1.45 system-ui,-apple-system,Segoe UI,sans-serif;
        box-shadow:0 8px 28px rgba(18,64,38,.14);display:none
      }
      #${STATUS_ID}.bad{border-color:#efc5c5;background:#fff4f4;color:#963434}
      #${STATUS_ID}.show{display:block}
      #${BUTTON_ID}[data-state="ok"]{border-color:#acd6b9;background:#eef9f1;color:#1f6b3d}
      #${BUTTON_ID}[data-state="bad"]{border-color:#efc5c5;background:#fff4f4;color:#963434}
    `;
    document.head.appendChild(style);
  }

  function showStatus(message, bad = false) {
    ensureStatusStyle();
    let box = document.getElementById(STATUS_ID);
    if (!box) {
      box = document.createElement('div');
      box.id = STATUS_ID;
      box.setAttribute('role', 'status');
      box.setAttribute('aria-live', 'polite');
      document.body.appendChild(box);
    }
    box.textContent = message;
    box.classList.toggle('bad', bad);
    box.classList.add('show');
    clearTimeout(box.__gmuHideTimer);
    box.__gmuHideTimer = setTimeout(() => box.classList.remove('show'), 7000);
  }

  async function runSyncTest(button) {
    if (!SYNC_TEST_ROLES.has(currentRole())) {
      showStatus('Tes Sinkronisasi Drive hanya tersedia untuk Owner/Director.', true);
      return;
    }
    if (button?.disabled) return;

    const original = button?.textContent || 'Tes Sinkronisasi Drive';
    if (button) {
      button.disabled = true;
      button.dataset.state = 'checking';
      button.textContent = 'Mengecek Drive…';
    }

    try {
      const result = await api.health();
      const rootName = result?.folder?.name || result?.root?.name || 'arsip GMU';
      if (button) {
        button.dataset.state = 'ok';
        button.textContent = 'Drive ✓ Terhubung';
      }
      showStatus(`Sinkronisasi Google Drive aktif. ERP berhasil mengakses ${rootName}.`);
    } catch (error) {
      if (button) {
        button.dataset.state = 'bad';
        button.textContent = 'Drive ✕ Periksa';
      }
      showStatus(`Tes Drive gagal: ${error?.message || String(error)}`, true);
    } finally {
      if (button) {
        button.disabled = false;
        setTimeout(() => {
          if (!button.isConnected) return;
          button.textContent = original;
          delete button.dataset.state;
        }, 6000);
      }
    }
  }

  function installSyncTestButton() {
    const allowed = SYNC_TEST_ROLES.has(currentRole());
    const existing = document.getElementById(BUTTON_ID);

    if (!allowed) {
      existing?.remove();
      return false;
    }
    if (existing) return true;

    const actions =
      document.querySelector('#gmu110PageBar .gmu110-bar-actions') ||
      document.querySelector('.gmu110-bar-actions');

    if (!actions) return false;

    const button = document.createElement('button');
    button.id = BUTTON_ID;
    button.type = 'button';
    button.className = 'gmu110-action';
    button.textContent = 'Tes Sinkronisasi Drive';
    button.title = 'Cek koneksi ERP ke arsip Google Drive tanpa membuat booking atau folder baru.';
    button.addEventListener('click', () => runSyncTest(button));
    actions.appendChild(button);
    return true;
  }

  const api = {
    version: VERSION,
    health: () => invoke({ action: 'health' }),
    testSync: () => runSyncTest(document.getElementById(BUTTON_ID)),
    installSyncTestButton,
    ensureOrderFolder: ({ bookingNo, customerName, activityDate }) => invoke({
      action: 'ensure_order_folder',
      entity_id: bookingNo,
      booking_code: bookingNo,
      customer_name: customerName,
      activity_date: activityDate,
    }),
    uploadDocument: ({
      entityType = 'order',
      entityId,
      documentType,
      fileName,
      mimeType,
      contentBase64,
      targetSubfolder = '09 - DOKUMEN FINAL',
      source = 'erp-web',
    }) => invoke({
      action: 'upload_document',
      entity_type: entityType,
      entity_id: entityId,
      document_type: documentType,
      file_name: fileName,
      mime_type: mimeType,
      content_base64: contentBase64,
      target_subfolder: targetSubfolder,
      source,
    }),
  };

  window.GMUDriveArchive = api;

  function boot() {
    ensureStatusStyle();
    installSyncTestButton();

    let queued = false;
    const observer = new MutationObserver(() => {
      if (queued) return;
      queued = true;
      requestAnimationFrame(() => {
        queued = false;
        installSyncTestButton();
      });
    });
    observer.observe(document.body, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ['class', 'style'],
    });

    const timer = setInterval(installSyncTestButton, 750);
    setTimeout(() => clearInterval(timer), 30000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot, { once: true });
  } else {
    boot();
  }
})();