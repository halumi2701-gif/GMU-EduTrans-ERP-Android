(() => {
  'use strict';

  const SUPABASE_URL = 'https://gtgnwasijweewmaubvyg.supabase.co';
  const SUPABASE_KEY = 'sb_publishable_cbTtSEhcXsHKDdldocSw3Q_bTcfXtaW';

  function accessToken() {
    const candidates = [
      window.__GMU_SESSION__?.accessToken,
      window.gmuSession?.accessToken,
      localStorage.getItem('gmu_access_token'),
      sessionStorage.getItem('gmu_access_token'),
    ];
    return candidates.find(Boolean) || '';
  }

  async function invoke(payload) {
    const token = accessToken();
    if (!token) throw new Error('Sesi ERP belum aktif.');
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

  const api = {
    version: 'v23.1-google-drive-archive',
    health: () => invoke({ action: 'health' }),
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
})();