package site.garsyanimultiusaha.gawone.management;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureTokenStore {
    private static final String PREF = "gawone_management_secure";
    private static final String ALIAS = "gawone_management_session_v1";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";
    private static final String ACCESS_IV = "access_iv";
    private static final String REFRESH_IV = "refresh_iv";
    private final SharedPreferences prefs;

    SecureTokenStore(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    void save(String access, String refresh) throws Exception {
        SecretKey key = getOrCreateKey();
        Enc a = encrypt(key, access == null ? "" : access);
        Enc r = encrypt(key, refresh == null ? "" : refresh);
        prefs.edit()
                .putString(ACCESS, a.data)
                .putString(ACCESS_IV, a.iv)
                .putString(REFRESH, r.data)
                .putString(REFRESH_IV, r.iv)
                .apply();
    }

    String accessToken() {
        try { return decrypt(getOrCreateKey(), prefs.getString(ACCESS, null), prefs.getString(ACCESS_IV, null)); }
        catch (Exception e) { return null; }
    }

    String refreshToken() {
        try { return decrypt(getOrCreateKey(), prefs.getString(REFRESH, null), prefs.getString(REFRESH_IV, null)); }
        catch (Exception e) { return null; }
    }

    void clear() { prefs.edit().clear().apply(); }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return kg.generateKey();
    }

    private Enc encrypt(SecretKey key, String text) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] data = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        return new Enc(Base64.encodeToString(data, Base64.NO_WRAP), Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
    }

    private String decrypt(SecretKey key, String data, String iv) throws Exception {
        if (data == null || iv == null) return null;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    private static final class Enc {
        final String data;
        final String iv;
        Enc(String data, String iv) { this.data = data; this.iv = iv; }
    }
}
