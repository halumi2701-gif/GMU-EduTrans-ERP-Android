package site.garsyanimultiusaha.gawone.management;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PURPLE = Color.rgb(109, 74, 255);
    private static final List<String> DEFAULT_MODULES = Arrays.asList("PARTNER_REVIEW", "DISPATCH", "SUPPORT", "PAYOUT", "AUDIT");
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Row> rows = new ArrayList<>();
    private final SupabaseManagementClient api = new SupabaseManagementClient();
    private SecureTokenStore tokens;
    private LinearLayout root;
    private Spinner modules;
    private ListView list;
    private TextView state;
    private ArrayAdapter<String> listAdapter;
    private boolean polling;
    private long cursor;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        tokens = new SecureTokenStore(this);
        String access = tokens.accessToken();
        if (access == null || access.isEmpty()) showLogin(null);
        else {
            api.setSession(access, tokens.refreshToken());
            showMessage("Memulihkan sesi…");
            io.execute(() -> openSession(true));
        }
    }

    @Override protected void onDestroy() {
        polling = false;
        io.shutdownNow();
        super.onDestroy();
    }

    private void showLogin(String error) {
        runUi(() -> {
            polling = false;
            root.removeAllViews();
            heading("GAWONE Management");
            label("Internal Operations • Backend M2.22");
            if (error != null) label(error);
            EditText email = field("Email internal", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            EditText pass = field("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            root.addView(email); root.addView(pass);
            Button login = button("Masuk"); root.addView(login);
            login.setOnClickListener(v -> {
                if (email.getText().toString().trim().isEmpty() || pass.getText().toString().isEmpty()) { toast("Email dan password wajib diisi"); return; }
                showMessage("Memverifikasi akun…");
                io.execute(() -> {
                    try {
                        api.signIn(email.getText().toString().trim(), pass.getText().toString());
                        tokens.save(api.accessToken(), api.refreshToken());
                        openSession(false);
                    } catch (Exception e) { showLogin(friendly(e)); }
                });
            });
        });
    }

    private void openSession(boolean restoring) {
        try {
            JSONObject runtime = api.runtime();
            if (runtime.optBoolean("maintenance_mode", false)) throw new IllegalStateException(runtime.optString("maintenance_message", "Maintenance aktif"));
            int min = runtime.optInt("min_supported_version_code", 1);
            String contract = runtime.optString("backend_contract_version", BuildConfig.BACKEND_CONTRACT);
            if (BuildConfig.VERSION_CODE < min) throw new IllegalStateException("Update wajib. Minimum versionCode " + min);
            if (!BuildConfig.BACKEND_CONTRACT.equals(contract)) throw new IllegalStateException("Backend contract berubah: " + contract);
            JSONObject manifest = api.manifest();
            String manifestContract = manifest.optString("backendContractVersion", manifest.optString("backend_contract_version", BuildConfig.BACKEND_CONTRACT));
            if (!BuildConfig.BACKEND_CONTRACT.equals(manifestContract)) throw new IllegalStateException("Manifest contract berubah: " + manifestContract);
            JSONObject boot = api.bootstrap();
            JSONObject nav = api.navigation();
            tokens.save(api.accessToken(), api.refreshToken());
            showDashboard(boot, nav);
        } catch (Exception e) {
            if (restoring) { tokens.clear(); api.setSession(null, null); showLogin("Sesi berakhir. Silakan masuk kembali."); }
            else showLogin(friendly(e));
        }
    }

    private void showDashboard(JSONObject boot, JSONObject nav) {
        runUi(() -> {
            root.removeAllViews();
            heading("GAWONE Management");
            label("M2.22 • v" + BuildConfig.VERSION_NAME + " • server-authoritative actions");
            modules = new Spinner(this);
            List<String> allowed = allowedModules(nav);
            modules.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, allowed));
            root.addView(modules);
            Button reload = button("Muat antrean"); root.addView(reload);
            state = label("Siap");
            list = new ListView(this);
            listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
            list.setAdapter(listAdapter);
            root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
            Button logout = button("Keluar"); root.addView(logout);
            reload.setOnClickListener(v -> loadQueue());
            logout.setOnClickListener(v -> { polling = false; tokens.clear(); api.setSession(null, null); showLogin(null); });
            list.setOnItemClickListener((p, v, pos, id) -> loadDetail(rows.get(pos)));
            polling = true;
            scheduleChanges();
            loadQueue();
        });
    }

    private List<String> allowedModules(JSONObject nav) {
        String raw = nav.toString().toUpperCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String m : DEFAULT_MODULES) if (raw.contains(m)) out.add(m);
        return out.isEmpty() ? new ArrayList<>(DEFAULT_MODULES) : out;
    }

    private void loadQueue() {
        if (modules == null) return;
        String module = String.valueOf(modules.getSelectedItem());
        state.setText("Memuat " + module + "…");
        io.execute(() -> {
            try {
                JSONObject result = api.queue(module, null, 50, 0);
                JSONArray array = firstArray(result);
                List<Row> parsed = new ArrayList<>();
                if (array != null) for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.optJSONObject(i); if (o == null) continue;
                    String id = first(o, "resource_id", "id", "partner_id", "order_id", "ticket_id", "payout_id");
                    if (id.isEmpty()) continue;
                    String resource = first(o, "resource", "resource_type", "queue", "type");
                    if (resource.isEmpty()) resource = module;
                    String title = first(o, "title", "name", "full_name", "ticket_no", "order_no", "status");
                    if (title.isEmpty()) title = resource + " • " + id.substring(0, Math.min(8, id.length()));
                    parsed.add(new Row(resource, id, title));
                }
                runUi(() -> {
                    rows.clear(); rows.addAll(parsed);
                    List<String> labels = new ArrayList<>(); for (Row r : rows) labels.add(r.title);
                    listAdapter.clear(); listAdapter.addAll(labels); listAdapter.notifyDataSetChanged();
                    state.setText(rows.size() + " item • " + module);
                });
            } catch (Exception e) { runUi(() -> state.setText(friendly(e))); }
        });
    }

    private void loadDetail(Row row) {
        state.setText("Memuat detail…");
        io.execute(() -> {
            try {
                JSONObject detail = api.detail(row.resource, row.id);
                List<String> actions = SupabaseManagementClient.extractActions(detail);
                runUi(() -> showDetail(row, detail, actions));
            } catch (Exception e) { runUi(() -> state.setText(friendly(e))); }
        });
    }

    private void showDetail(Row row, JSONObject detail, List<String> actions) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(14), dp(8), dp(14), dp(8));
        TextView body = new TextView(this); body.setText(pretty(detail)); body.setTextIsSelectable(true); box.addView(body);
        for (String action : actions) {
            Button b = button(action); box.addView(b);
            b.setOnClickListener(v -> confirmAction(row, action));
        }
        new AlertDialog.Builder(this).setTitle(row.title).setView(box).setPositiveButton("Tutup", null).show();
        state.setText(actions.size() + " aksi tersedia");
    }

    private void confirmAction(Row row, String action) {
        EditText reason = field("Alasan/catatan (opsional)", InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Konfirmasi " + action).setView(reason)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Jalankan", (d, w) -> executeAction(row, action, reason.getText().toString().trim())).show();
    }

    private void executeAction(Row row, String action, String reason) {
        state.setText("Menjalankan " + action + "…");
        io.execute(() -> {
            try {
                JSONObject payload = new JSONObject(); if (!reason.isEmpty()) payload.put("reason", reason);
                JSONObject out = api.action(action, row.id, payload);
                runUi(() -> new AlertDialog.Builder(this).setTitle("Berhasil").setMessage(out.toString()).setPositiveButton("OK", (d,w) -> loadQueue()).show());
            } catch (Exception e) { runUi(() -> state.setText(friendly(e))); }
        });
    }

    private void scheduleChanges() {
        ui.postDelayed(() -> {
            if (!polling || api.accessToken() == null) return;
            io.execute(() -> {
                try {
                    JSONArray events = api.changes(cursor);
                    if (events.length() > 0) {
                        for (int i = 0; i < events.length(); i++) { JSONObject ev = events.optJSONObject(i); if (ev != null) cursor = Math.max(cursor, ev.optLong("event_id", 0)); }
                        runUi(this::loadQueue);
                    }
                } catch (Exception ignored) { }
                runUi(this::scheduleChanges);
            });
        }, 5000);
    }

    private void showMessage(String message) { runUi(() -> { root.removeAllViews(); heading("GAWONE Management"); label(message); }); }
    private TextView heading(String s) { TextView v = new TextView(this); v.setText(s); v.setTextSize(25); v.setTextColor(Color.rgb(31,41,55)); root.addView(v); return v; }
    private TextView label(String s) { TextView v = new TextView(this); v.setText(s); v.setTextColor(Color.rgb(107,114,128)); root.addView(v); return v; }
    private EditText field(String hint, int type) { EditText e = new EditText(this); e.setHint(hint); e.setInputType(type); e.setPadding(dp(10),dp(8),dp(10),dp(8)); return e; }
    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setBackgroundColor(PURPLE); b.setAllCaps(false); return b; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void runUi(Runnable r) { if (Looper.myLooper() == Looper.getMainLooper()) r.run(); else ui.post(r); }
    private String friendly(Exception e) { String s = e.getMessage(); return s == null || s.isEmpty() ? "Terjadi kesalahan." : (s.length() > 600 ? s.substring(0,600) : s); }
    private static String pretty(JSONObject o) { try { return o.toString(2); } catch (Exception e) { return o.toString(); } }
    private static JSONArray firstArray(JSONObject o) { for (String k : new String[]{"items","data","queue","rows","results"}) { Object v=o.opt(k); if(v instanceof JSONArray) return (JSONArray)v; } return null; }
    private static String first(JSONObject o, String... keys) { for(String k:keys){Object v=o.opt(k); if(v!=null&&v!=JSONObject.NULL&&!(v instanceof JSONObject)&&!(v instanceof JSONArray)){String s=String.valueOf(v); if(!s.isEmpty()) return s;}} return ""; }
    private static final class Row { final String resource,id,title; Row(String r,String i,String t){resource=r;id=i;title=t;} }
}
