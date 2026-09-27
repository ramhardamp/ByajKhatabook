package com.babasitaram.pro;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

public final class LedgerStore {
    public static final int SCHEMA=21;
    private static final String FILE="byaj_khatabook_native.bkdb", KEY_ALIAS="ByajKhatabookNativeMasterKey", BACKUP_DIR="safety_backups";
    private final Context context; private JSONObject root;
    public LedgerStore(Context c)throws Exception{context=c.getApplicationContext();load();}
    private SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(KEY_ALIAS))return ((KeyStore.SecretKeyEntry)ks.getEntry(KEY_ALIAS,null)).getSecretKey();
        KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");
        kg.init(new android.security.keystore.KeyGenParameterSpec.Builder(KEY_ALIAS,3)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setUserAuthenticationRequired(false).build());
        return kg.generateKey();
    }
    private File db(){return new File(context.getFilesDir(),FILE);}
    private File tmp(){return new File(context.getFilesDir(),FILE+".tmp");}
    private void load()throws Exception{
        File f=db();if(!f.exists()){root=empty();save();return;}
        byte[] all=read(f);if(all.length<13)throw new IOException("Invalid native database");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOf(all,12)));
        root=new JSONObject(new String(c.doFinal(Arrays.copyOfRange(all,12,all.length)),StandardCharsets.UTF_8));normalize();
    }
    private JSONObject empty()throws Exception{
        JSONObject o=new JSONObject().put("app","ByajKhatabook").put("format","BSPK-NATIVE").put("schema",SCHEMA)
            .put("updatedAt",new Date().toString()).put("currentBizId",1).put("seqBiz",2).put("seqCust",1).put("seqTxn",1).put("seqExp",1).put("seqDel",1).put("seqAudit",1).put("seqRec",1).put("seqDiary",1);
        o.put("businesses",new JSONArray().put(new JSONObject().put("id",1).put("name","My Business").put("owner","Owner")));
        for(String k:new String[]{"customers","khataTxns","byaajPayments","expenses","deletedItems","auditLog","recurring","diaryEntries","reminders"})o.put(k,new JSONArray());
        o.put("ppEnabled",new JSONObject()).put("settings",new JSONObject().put("name","").put("biz","My Business").put("email","").put("phone","").put("pinHash","").put("pinSalt","").put("reminderEnabled",true).put("reminderDays",7).put("minReminder",100).put("directSmsEnabled",false));
        return o;
    }
    private void normalize()throws Exception{
        for(String k:new String[]{"businesses","customers","khataTxns","byaajPayments","expenses","deletedItems","auditLog","recurring","diaryEntries","reminders"})if(!(root.opt(k) instanceof JSONArray))root.put(k,new JSONArray());
        if(!(root.opt("settings") instanceof JSONObject))root.put("settings",new JSONObject());
        JSONObject s=root.optJSONObject("settings");if(!s.has("biz"))s.put("biz","My Business");if(!s.has("reminderEnabled"))s.put("reminderEnabled",true);if(!s.has("reminderDays"))s.put("reminderDays",7);if(!s.has("minReminder"))s.put("minReminder",100);if(!s.has("directSmsEnabled"))s.put("directSmsEnabled",false);
        if(!root.has("currentBizId"))root.put("currentBizId",1);if(!root.has("schema"))root.put("schema",SCHEMA);
    }
    public synchronized void save()throws Exception{
        root.put("schema",SCHEMA).put("updatedAt",new Date().toString());byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));byte[] ct=c.doFinal(root.toString().getBytes(StandardCharsets.UTF_8));
        File t=tmp(),d=db(),old=new File(context.getFilesDir(),FILE+".previous");
        try(FileOutputStream o=new FileOutputStream(t)){o.write(iv);o.write(ct);o.getFD().sync();}
        boolean had=d.exists();if(old.exists()&&!old.delete())throw new IOException("Cannot clear previous staging file");
        if(had&&!d.renameTo(old))throw new IOException("Could not stage previous database safely");
        if(!t.renameTo(d)){if(had)old.renameTo(d);throw new IOException("Could not finalize database safely");}
        if(old.exists())old.delete();
    }
    public synchronized File createEmergencySnapshot()throws Exception{File dir=new File(context.getFilesDir(),BACKUP_DIR);if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create safety backup");File f=new File(dir,"pre-change-"+System.currentTimeMillis()+".json");exportTo(f);return f;}
    public JSONObject data(){return root;} public JSONArray customers(){return root.optJSONArray("customers");} public JSONArray txns(){return root.optJSONArray("khataTxns");} public JSONArray loans(){return root.optJSONArray("byaajPayments");} public JSONObject settings(){return root.optJSONObject("settings");}
    public int next(String k){int n=root.optInt(k,1);try{root.put(k,n+1);}catch(Exception ignored){}return n;}
    public synchronized void exportTo(File f)throws Exception{try(FileOutputStream o=new FileOutputStream(f)){o.write(root.toString(2).getBytes(StandardCharsets.UTF_8));o.getFD().sync();}}
    public synchronized void importFrom(InputStream in)throws Exception{
        StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String s;while((s=r.readLine())!=null)b.append(s).append('\n');}
        JSONObject n=new JSONObject(b.toString());validate(n);createEmergencySnapshot();JSONObject old=root;root=n;normalize();try{save();}catch(Exception e){root=old;save();throw e;}
    }
    private void validate(JSONObject o)throws Exception{String f=o.optString("format");if(!"BSPK-NATIVE".equals(f)&&!"BSPK-VAULT".equals(f))throw new IOException("Unsupported backup format");for(String k:new String[]{"businesses","customers","khataTxns","byaajPayments","expenses","deletedItems","auditLog","recurring","diaryEntries"})if(!(o.opt(k) instanceof JSONArray))throw new IOException("Backup missing: "+k);if(o.optJSONArray("customers").length()>1000000)throw new IOException("Customer count exceeds safe import limit");if("BSPK-VAULT".equals(f))o.put("format","BSPK-NATIVE").put("schema",SCHEMA);}
    private static byte[] read(File f)throws IOException{try(FileInputStream i=new FileInputStream(f);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=i.read(b))>0)o.write(b,0,n);return o.toByteArray();}}
    public static String sha256(String s)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format(Locale.US,"%02x",x));return b.toString();}
}
