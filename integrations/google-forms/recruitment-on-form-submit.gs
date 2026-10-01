function first_(named, label) {
  var v = named[label];
  if (!v || !v.length) return "";
  return String(v[0] || "").trim();
}

function pick_(named, labels) {
  for (var i = 0; i < labels.length; i++) {
    var v = first_(named, labels[i]);
    if (v) return v;
  }
  return "";
}

function onRecruitmentFormSubmit(e) {
  var secret = PropertiesService.getScriptProperties().getProperty("GMU_RECRUITMENT_WEBHOOK_SECRET");
  if (!secret) throw new Error("GMU_RECRUITMENT_WEBHOOK_SECRET belum diset di Script Properties.");

  var named = e.namedValues || {};
  var responseSheet = e.range.getSheet();
  var responseId = "GF-" + responseSheet.getSheetId() + "-" + e.range.getRow();
  var submittedAt = (e.values && e.values.length ? e.values[0] : new Date());

  var payload = {
    response_id: responseId,
    submitted_at: new Date(submittedAt).toISOString(),
    full_name: first_(named, "Nama lengkap"),
    whatsapp: first_(named, "Nomor WhatsApp aktif"),
    email: first_(named, "Email"),
    domicile: first_(named, "Domisili"),
    kecamatan: first_(named, "Kecamatan"),
    education: first_(named, "Pendidikan terakhir"),
    major: first_(named, "Jurusan"),
    current_activity: first_(named, "Aktivitas/pekerjaan saat ini"),
    has_laptop: first_(named, "Apakah memiliki laptop?"),
    has_smartphone: first_(named, "Apakah memiliki smartphone?"),
    has_vehicle: first_(named, "Apakah memiliki kendaraan pribadi?"),
    portfolio_url: pick_(named, ["Link portfolio","Upload/link video public speaking jika ada.","Link CV"]),
    source: first_(named, "Sumber mengetahui lowongan GMU EduTrans"),
    alternate_position: first_(named, "Posisi alternatif yang bersedia dijalankan"),
    primary_position: first_(named, "Posisi utama yang dilamar"),
    availability: first_(named, "Hari/jam yang tersedia"),
    weekend: first_(named, "Bersedia bekerja pada weekend?"),
    coverage_area: first_(named, "Area kerja yang dapat dijangkau"),
    experience_summary: first_(named, "Ceritakan pengalaman kerja/organisasi yang relevan."),
    raw_named_values: named
  };

  var res = UrlFetchApp.fetch(
    "https://gtgnwasijweewmaubvyg.supabase.co/rest/v1/rpc/gmu_recruitment_form_ingest",
    {
      method: "post",
      contentType: "application/json",
      muteHttpExceptions: true,
      headers: {apikey: "sb_publishable_cbTtSEhcXsHKDdldocSw3Q_bTcfXtaW"},
      payload: JSON.stringify({p_secret: secret, p_payload: payload})
    }
  );

  var code = res.getResponseCode();
  var body = res.getContentText();
  if (code < 200 || code >= 300) throw new Error("ERP sync gagal HTTP " + code + ": " + body);

  var result = JSON.parse(body);
  if (!result.ok) throw new Error("ERP sync gagal: " + body);
  writeRecruitmentTracker_(result.tracker_row, responseId);
}

function writeRecruitmentTracker_(trackerRow, responseId) {
  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Candidates");
  if (!sh) throw new Error("Sheet Candidates tidak ditemukan.");

  var headers = sh.getRange(1,1,1,sh.getLastColumn()).getValues()[0];
  var index = {};
  headers.forEach(function(h,i){ index[String(h).trim()] = i+1; });

  var candidateId = String(trackerRow["Candidate ID"] || "");
  var existing = candidateId ? sh.createTextFinder(candidateId).matchEntireCell(true).findNext() : null;
  var row;

  if (existing && existing.getColumn() === index["Candidate ID"]) {
    row = existing.getRow();
  } else {
    var names = sh.getRange(2,index["Full Name"],sh.getMaxRows()-1,1).getDisplayValues();
    var offset = names.findIndex(function(v){ return !String(v[0] || "").trim(); });
    row = offset >= 0 ? offset + 2 : sh.getLastRow() + 1;
  }

  Object.keys(trackerRow).forEach(function(key){
    if (index[key]) sh.getRange(row,index[key]).setValue(trackerRow[key] == null ? "" : trackerRow[key]);
  });

  if (index["Next Action"]) sh.getRange(row,index["Next Action"]).setValue("Admin Screening");
  if (index["Offer Status"]) sh.getRange(row,index["Offer Status"]).setValue("NOT_SENT");
  if (index["Red Flag"]) sh.getRange(row,index["Red Flag"]).setValue("No");
  if (index["Notes"]) sh.getRange(row,index["Notes"]).setValue("Form Response: " + responseId);
  if (index["ERP Sync Status"]) sh.getRange(row,index["ERP Sync Status"]).setValue("Synced");
}
