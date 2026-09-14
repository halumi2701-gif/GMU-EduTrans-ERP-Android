package site.garsyanimultiusaha.gawone.management;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class SupabaseManagementClient {
    static final String CONTRACT = "M2.22";
    private static final int VERSION_CODE = BuildConfig.VERSION_CODE;
    private final String base = BuildConfig.SUPABASE_URL;
    private final String key = BuildConfig.SUPABASE_PUBLISHABLE_KEY;
    private String accessToken;
    private String refreshToken;

    void setSession(String access, String refresh) { this.accessToken = access; this.refreshToken = refresh; }
    String accessToken() { return accessToken; }
    String refreshToken() { return refreshToken; }

    JSONObject signUp(String email, String password) throws Exception {
        JSONObject body = new JSONObject().put("email", email).put("password", password);
        return request("POST", "/auth/v1/signup", body, false);
    }

    JSONObject resendSignupConfirmation(String email) throws Exception {
        JSONObject body = new JSONObject().put("type", "signup").put("email", email);
        return request("POST", "/auth/v1/resend", body, false);
    }

    JSONObject signIn(String email, String password) throws Exception {
        JSONObject body = new JSONObject().put("email", email).put("password", password);
        JSONObject out = request("POST", "/auth/v1/token?grant_type=password", body, false);
        setSession(out.optString("access_token", null), out.optString("refresh_token", null));
        if (accessToken == null || accessToken.isEmpty()) throw new ApiException(401, "Login tidak menghasilkan access token");
        return out;
    }

    JSONObject refreshSession() throws Exception {
        if (refreshToken == null || refreshToken.isEmpty()) throw new ApiException(401, "Refresh token tidak tersedia");
        JSONObject out = request("POST", "/auth/v1/token?grant_type=refresh_token", new JSONObject().put("refresh_token", refreshToken), false);
        setSession(out.optString("access_token", null), out.optString("refresh_token", refreshToken));
        return out;
    }

    JSONObject runtime() throws Exception { return rpc("get_management_runtime_config", new JSONObject().put("p_version_code", VERSION_CODE)); }
    JSONObject bootstrap() throws Exception { return rpc("get_my_management_bootstrap", new JSONObject().put("p_version_code", VERSION_CODE)); }
    JSONObject navigation() throws Exception { return rpc("get_my_management_navigation", new JSONObject().put("p_version_code", VERSION_CODE)); }
    JSONObject manifest() throws Exception { return rpc("get_management_contract_manifest", new JSONObject()); }

    JSONObject queue(String queue, String status, int limit, int offset) throws Exception {
        JSONObject p = new JSONObject().put("p_queue", queue).put("p_limit", limit).put("p_offset", offset).put("p_version_code", VERSION_CODE);
        if (status != null && !status.isEmpty()) p.put("p_status", status);
        return rpc("get_management_queue", p);
    }

    JSONObject detail(String resource, String id) throws Exception {
        return rpc("get_management_detail", new JSONObject().put("p_resource", resource).put("p_resource_id", id).put("p_version_code", VERSION_CODE));
    }

    JSONObject action(String action, String resourceId, JSONObject payload) throws Exception {
        return rpc("execute_management_action", new JSONObject()
                .put("p_action", action)
                .put("p_resource_id", resourceId)
                .put("p_idempotency_key", UUID.randomUUID().toString())
                .put("p_payload", payload == null ? new JSONObject() : payload)
                .put("p_version_code", VERSION_CODE));
    }

    JSONArray changes(long afterEventId) throws Exception {
        String path = "/rest/v1/management_change_events?select=event_id,event_type,resource,resource_id,source_table,created_at"
                + "&event_id=gt." + afterEventId + "&order=event_id.asc&limit=200";
        String raw = rawRequest("GET", path, null, true);
        return new JSONArray(raw);
    }

    private JSONObject rpc(String name, JSONObject body) throws Exception {
        return request("POST", "/rest/v1/rpc/" + name, body, true);
    }

    private JSONObject request(String method, String path, JSONObject body, boolean auth) throws Exception {
        String raw = rawRequest(method, path, body, auth);
        if (raw == null || raw.trim().isEmpty()) return new JSONObject();
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) return new JSONObject(trimmed);
        if (trimmed.startsWith("[")) return new JSONObject().put("items", new JSONArray(trimmed));
        return new JSONObject().put("value", trimmed);
    }

    private String rawRequest(String method, String path, JSONObject body, boolean auth) throws Exception {
        return rawRequest(method, path, body, auth, false);
    }

    private String rawRequest(String method, String path, JSONObject body, boolean auth, boolean retried) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("apikey", key);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        if (auth) {
            if (accessToken == null || accessToken.isEmpty()) throw new ApiException(401, "AUTH_REQUIRED");
            c.setRequestProperty("Authorization", "Bearer " + accessToken);
        }
        if (body != null && !"GET".equals(method)) {
            c.setDoOutput(true);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String text = read(in);
        c.disconnect();
        if (code < 200 || code >= 300) {
            if (code == 401 && auth && !retried && refreshToken != null && !refreshToken.isEmpty() && !path.contains("refresh_token")) {
                refreshSession();
                return rawRequest(method, path, body, true, true);
            }
            throw new ApiException(code, text == null || text.isEmpty() ? ("HTTP " + code) : text);
        }
        return text;
    }

    private String read(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    static List<String> extractActions(JSONObject detail) {
        List<String> out = new ArrayList<>();
        collectActions(detail, out);
        return out;
    }

    private static void collectActions(Object node, List<String> out) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            JSONArray names = o.names();
            if (names == null) return;
            for (int i = 0; i < names.length(); i++) {
                String k = names.optString(i);
                Object v = o.opt(k);
                String lower = k.toLowerCase();
                if ((lower.contains("action") || lower.contains("capabilit")) && v instanceof JSONArray) {
                    JSONArray a = (JSONArray) v;
                    for (int j = 0; j < a.length(); j++) {
                        Object item = a.opt(j);
                        if (item instanceof String && !out.contains(item)) out.add((String) item);
                        else if (item instanceof JSONObject) {
                            JSONObject io = (JSONObject) item;
                            String code = io.optString("code", io.optString("action", io.optString("id", "")));
                            if (!code.isEmpty() && !out.contains(code)) out.add(code);
                        }
                    }
                }
                collectActions(v, out);
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collectActions(a.opt(i), out);
        }
    }

    static final class ApiException extends Exception {
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }
}
