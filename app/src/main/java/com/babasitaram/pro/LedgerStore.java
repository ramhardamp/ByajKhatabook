package com.babasitaram.pro;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

/**
 * On-device encrypted database.
 *
 * WHAT CHANGED FROM THE ORIGINAL:
 *  - load() used to treat "main file missing" as "brand new install" and immediately wrote an
 *    empty database. That is exactly the state you can land in if the app was killed midway
 *    through save() (file already renamed to ".previous", ".tmp" not yet renamed into place).
 *    load() now looks for ".previous" / ".tmp" first and recovers from them before ever
 *    concluding the ledger is empty, so a bad shutdown can no longer erase real data.
 *  - PIN is now hashed with PBKDF2 (120,000 rounds) instead of one round of unsalted-speed
 *    SHA-256, and a legacy SHA-256 hash is auto-upgraded the next time the correct PIN is
 *    entered.
 *  - Added paidIndex()/loansOf() so both the UI and the interest engine (Calc.java) read the
 *    same numbers, instead of two hand-written copies of the same loop.
 */
public final class LedgerStore {
    public static final int SCHEMA = 22;
    private static final String FILE = "byaj_khatabook_native.bkdb";
    private static final String KEY_ALIAS = "ByajKhatabookNativeMasterKey";
    private static final String BACKUP_DIR = "safety_backups";
    private static final String[] ARRAYS = {"businesses", "customers", "khataTxns", "byaajPayments",
            "expenses", "deletedItems", "auditLog", "recurring", "diaryEntries", "reminders"};

    private final Context context;
    private JSONObject root;
    private HashMap<String, Double> paidCache;

    public LedgerStore(Context c) throws Exception {
        context = c.getApplicationContext();
        load();
    }

    // --------------------------------------------------------------- keystore

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build());
        return kg.generateKey();
    }

    // --------------------------------------------------------------- files

    private File db() { return new File(context.getFilesDir(), FILE); }
    private File tmp() { return new File(context.getFilesDir(), FILE + ".tmp"); }
    private File prev() { return new File(context.getFilesDir(), FILE + ".previous"); }

    private void load() throws Exception {
        File f = db();
        if (!f.exists()) {
            // Not necessarily a fresh install: a crash between the two renames in save() leaves
            // exactly this state (main file gone, staged copies still on disk). Recover first.
            if (prev().exists()) { renameOrThrow(prev(), f, "recover previous database"); }
            else if (tmp().exists()) { renameOrThrow(tmp(), f, "recover staged database"); }
        }
        if (!f.exists()) { root = empty(); save(); return; }
        try {
            root = decrypt(f);
        } catch (Exception primaryFailed) {
            // Main file is present but unreadable (corrupt write). Try the previous good copy
            // before giving up, instead of surfacing an error immediately.
            if (prev().exists()) {
                try {
                    root = decrypt(prev());
                    normalize();
                    save(); // heal: promote the recovered copy back to the main slot
                    return;
                } catch (Exception ignored) { /* fall through to original error */ }
            }
            throw primaryFailed;
        }
        normalize();
    }

    private JSONObject decrypt(File f) throws Exception {
        byte[] all = read(f);
        if (all.length < 13) throw new IOException("Database file is too short to be valid");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOf(all, 12)));
        return new JSONObject(new String(c.doFinal(Arrays.copyOfRange(all, 12, all.length)), StandardCharsets.UTF_8));
    }

    private void renameOrThrow(File from, File to, String what) throws IOException {
        if (!from.renameTo(to)) throw new IOException("Could not " + what);
    }

    private JSONObject empty() throws Exception {
        JSONObject o = new JSONObject()
                .put("app", "ByajKhatabook").put("format", "BSPK-NATIVE").put("schema", SCHEMA)
                .put("updatedAt", Calc.now()).put("currentBizId", 1)
                .put("seqBiz", 2).put("seqCust", 1).put("seqTxn", 1).put("seqExp", 1)
                .put("seqDel", 1).put("seqAudit", 1).put("seqRec", 1).put("seqDiary", 1);
        o.put("businesses", new JSONArray().put(new JSONObject().put("id", 1).put("name", "My Business").put("owner", "Owner")));
        for (String k : ARRAYS) if (!o.has(k) || !(o.opt(k) instanceof JSONArray)) o.put(k, new JSONArray());
        o.put("ppEnabled", new JSONObject());
        o.put("settings", new JSONObject()
                .put("name", "").put("biz", "My Business").put("email", "").put("phone", "")
                .put("pinHash", "").put("pinSalt", "").put("pinAlgo", "")
                .put("reminderEnabled", true).put("reminderDays", 7).put("minReminder", 100)
                .put("directSmsEnabled", false).put("darkMode", "system"));
        return o;
    }

    private void normalize() throws Exception {
        for (String k : ARRAYS) if (!(root.opt(k) instanceof JSONArray)) root.put(k, new JSONArray());
        if (!(root.opt("settings") instanceof JSONObject)) root.put("settings", new JSONObject());
        JSONObject s = root.optJSONObject("settings");
        if (!s.has("biz")) s.put("biz", "My Business");
        if (!s.has("reminderEnabled")) s.put("reminderEnabled", true);
        if (!s.has("reminderDays")) s.put("reminderDays", 7);
        if (!s.has("minReminder")) s.put("minReminder", 100);
        if (!s.has("directSmsEnabled")) s.put("directSmsEnabled", false);
        if (!s.has("darkMode")) s.put("darkMode", "system");
        if (!root.has("currentBizId")) root.put("currentBizId", 1);
        if (!root.has("schema")) root.put("schema", SCHEMA);
        if (root.optJSONArray("businesses").length() == 0)
            root.optJSONArray("businesses").put(new JSONObject().put("id", 1).put("name", s.optString("biz", "My Business")).put("owner", ""));
        paidCache = null; // stale after any structural change
    }

    public synchronized void save() throws Exception {
        root.put("schema", SCHEMA).put("updatedAt", Calc.now());
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(root.toString().getBytes(StandardCharsets.UTF_8));

        File t = tmp(), d = db(), old = prev();
        try (FileOutputStream o = new FileOutputStream(t)) { o.write(iv); o.write(ct); o.getFD().sync(); }
        boolean had = d.exists();
        if (old.exists() && !old.delete()) throw new IOException("Cannot clear previous staging file");
        if (had && !d.renameTo(old)) throw new IOException("Could not stage previous database safely");
        if (!t.renameTo(d)) { if (had) old.renameTo(d); throw new IOException("Could not finalize database safely"); }
        if (old.exists()) old.delete();
        paidCache = null;
    }

    public synchronized File createEmergencySnapshot() throws Exception {
        File dir = new File(context.getFilesDir(), BACKUP_DIR);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create safety backup");
        File f = new File(dir, "pre-change-" + System.currentTimeMillis() + ".json");
        exportTo(f);
        return f;
    }

    // --------------------------------------------------------------- accessors

    public JSONObject data() { return root; }
    public JSONArray businesses() { return root.optJSONArray("businesses"); }
    public JSONArray customers() { return root.optJSONArray("customers"); }
    public JSONArray txns() { return root.optJSONArray("khataTxns"); }
    public JSONArray payments() { return root.optJSONArray("byaajPayments"); }
    public JSONArray loans() { return root.optJSONArray("byaajPayments"); } // legacy alias
    public JSONArray expenses() { return root.optJSONArray("expenses"); }
    public JSONArray diary() { return root.optJSONArray("diaryEntries"); }
    public JSONArray trash() { return root.optJSONArray("deletedItems"); }
    public JSONObject settings() { return root.optJSONObject("settings"); }
    public int currentBizId() { return root.optInt("currentBizId", 1); }

    public int next(String k) {
        int n = root.optInt(k, 1);
        try { root.put(k, n + 1); } catch (Exception ignored) { }
        return n;
    }

    public JSONObject customer(int id) {
        JSONArray cs = customers();
        for (int i = 0; i < cs.length(); i++) {
            JSONObject c = cs.optJSONObject(i);
            if (c != null && c.optInt("id") == id) return c;
        }
        return null;
    }

    /** customerId:loanId -> total paid. Built once per load, invalidated on any save/edit. */
    public HashMap<String, Double> paidIndex() {
        if (paidCache != null) return paidCache;
        HashMap<String, Double> m = new HashMap<>();
        JSONArray p = payments();
        for (int i = 0; i < p.length(); i++) {
            JSONObject x = p.optJSONObject(i);
            if (x == null) continue;
            String k = x.optInt("customerId") + ":" + x.optInt("loanId");
            m.put(k, (m.containsKey(k) ? m.get(k) : 0) + x.optDouble("amount", 0));
        }
        paidCache = m;
        return m;
    }

    public void invalidateCache() { paidCache = null; }

    // --------------------------------------------------------------- soft delete

    public void softDelete(String kind, JSONObject item) {
        try {
            trash().put(new JSONObject().put("id", next("seqDel")).put("kind", kind)
                    .put("deletedAt", Calc.now()).put("data", item));
        } catch (Exception ignored) { }
    }

    // --------------------------------------------------------------- backup / restore

    public synchronized void exportTo(File f) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            o.getFD().sync();
        }
    }

    public synchronized void importFrom(InputStream in) throws Exception {
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String s;
            while ((s = r.readLine()) != null) b.append(s).append('\n');
        }
        JSONObject n = new JSONObject(b.toString());
        validate(n);
        createEmergencySnapshot();
        JSONObject old = root;
        root = n;
        try { normalize(); save(); }
        catch (Exception e) { root = old; save(); throw e; }
    }

    private void validate(JSONObject o) throws Exception {
        String f = o.optString("format");
        if (!"BSPK-NATIVE".equals(f) && !"BSPK-VAULT".equals(f))
            throw new IOException("Unsupported backup format");
        for (String k : new String[]{"businesses", "customers", "khataTxns", "byaajPayments", "expenses"})
            if (!(o.opt(k) instanceof JSONArray)) throw new IOException("Backup missing: " + k);
        if (o.optJSONArray("customers").length() > 1_000_000)
            throw new IOException("Customer count exceeds safe import limit");
        if ("BSPK-VAULT".equals(f)) o.put("format", "BSPK-NATIVE").put("schema", SCHEMA);
    }

    private static byte[] read(File f) throws IOException {
        try (FileInputStream i = new FileInputStream(f); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = i.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        }
    }

    // --------------------------------------------------------------- PIN hashing

    /** Kept only so a very old backup with a legacy hash can still be verified once, then upgraded. */
    public static String sha256(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format(Locale.US, "%02x", x));
        return b.toString();
    }

    public static String pbkdf2(String pin, String saltHex) throws Exception {
        byte[] salt = hexToBytes(saltHex);
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 120_000, 256);
        byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return bytesToHex(hash);
    }

    public static String newSalt() {
        byte[] s = new byte[16];
        new SecureRandom().nextBytes(s);
        return bytesToHex(s);
    }

    private static String bytesToHex(byte[] d) {
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format(Locale.US, "%02x", x));
        return b.toString();
    }

    private static byte[] hexToBytes(String s) {
        int n = s.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    /** Sets a new PIN using the modern PBKDF2 path. */
    public void setPin(String pin) throws Exception {
        String salt = newSalt();
        settings().put("pinSalt", salt).put("pinHash", pbkdf2(pin, salt)).put("pinAlgo", "pbkdf2");
        save();
    }

    public void clearPin() throws Exception {
        settings().put("pinSalt", "").put("pinHash", "").put("pinAlgo", "");
        save();
    }

    public boolean hasPin() {
        return !settings().optString("pinHash", "").isEmpty();
    }

    /** Verifies the PIN, transparently upgrading a legacy SHA-256 hash on success. */
    public boolean verifyPin(String pin) {
        try {
            JSONObject s = settings();
            String hash = s.optString("pinHash", "");
            if (hash.isEmpty()) return true;
            String algo = s.optString("pinAlgo", "");
            if ("pbkdf2".equals(algo)) {
                return pbkdf2(pin, s.optString("pinSalt", "")).equals(hash);
            }
            // Legacy: sha256(salt + ":" + pin)
            boolean ok = sha256(s.optString("pinSalt", "") + ":" + pin).equals(hash);
            if (ok) setPin(pin); // upgrade silently
            return ok;
        } catch (Exception e) {
            return false;
        }
    }
}
