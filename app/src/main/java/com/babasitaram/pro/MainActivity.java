package com.babasitaram.pro;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * BEFORE (this whole app used to be 69 dense lines): plain android.widget.* views built by hand,
 * one flat colour scheme, no PIN enforcement, no edit/delete for anything you'd already saved,
 * a wrong compound-interest formula shared between here and ReminderReceiver, dates stored with
 * Date.toString() (locale-dependent), and a single business with no way to separate customers.
 *
 * AFTER: Material 3 cards/buttons/inputs with day+night colours, a PIN lock gate, edit/delete +
 * a recycle bin for customers/transactions/loans/diary/expenses, per-business filtering, an
 * overdue badge driven by loan duration, a proper multi-page PDF statement shared via
 * FileProvider, and all math routed through the single shared Calc class.
 */
public class MainActivity extends AppCompatActivity {
    private LedgerStore store;
    private LinearLayout content;
    private LinearLayout[] navButtons;
    private final ArrayDeque<String> stack = new ArrayDeque<>();
    private String screen = "home";
    private int customerId = -1;
    private String ledgerSearch = "";

    private static final int REQ_PIN = 8001, REQ_EXPORT = 9001, REQ_IMPORT = 9002, REQ_RECOVERY_IMPORT = 9003;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        try {
            store = new LedgerStore(this);
            if (store.hasPin()) {
                startActivityForResult(new Intent(this, PinLockActivity.class), REQ_PIN);
            } else {
                afterUnlock();
            }
        } catch (Exception e) {
            fatal(e);
        }
    }

    private void afterUnlock() {
        build();
        ReminderScheduler.schedule(this);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7002);
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_PIN) {
            if (result == RESULT_OK) afterUnlock(); else finish();
            return;
        }
        if (req == REQ_RECOVERY_IMPORT) {
            recoverFromPickedBackup(result, data);
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (req == REQ_EXPORT) {
                try (OutputStream o = getContentResolver().openOutputStream(data.getData())) {
                    o.write(store.data().toString(2).getBytes(StandardCharsets.UTF_8));
                }
                toast("Backup saved");
            } else if (req == REQ_IMPORT) {
                try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                    store.importFrom(in);
                }
                render();
                toast("Backup restored safely");
            }
        } catch (Exception e) {
            toast("Backup failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ fatal data error + recovery

    private void fatal(Exception e) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Data storage error")
                .setMessage("Local database could not be opened safely. Your data was NOT deleted.\n\n" + e.getMessage())
                .setPositiveButton("Retry", (d, w) -> recreate())
                .setNeutralButton("Restore from backup…", (d, w) -> startActivityForResult(
                        new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE),
                        REQ_RECOVERY_IMPORT))
                .setNegativeButton("Exit", (d, w) -> finish())
                .show();
    }

    private void recoverFromPickedBackup(int result, Intent data) {
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("This will erase the unreadable database")
                .setMessage("The current local database cannot be opened, so it will be replaced with the backup file you picked. This does not touch the backup file itself.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Replace & Restore", (d, w) -> {
                    try {
                        File dir = getFilesDir();
                        for (String suffix : new String[]{"", ".tmp", ".previous"}) {
                            File f = new File(dir, "byaj_khatabook_native.bkdb" + suffix);
                            if (f.exists()) f.delete();
                        }
                        store = new LedgerStore(this); // fresh empty store
                        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                            store.importFrom(in);
                        }
                        afterUnlock();
                        toast("Restored from backup");
                    } catch (Exception e) {
                        toast("Restore failed: " + e.getMessage());
                    }
                }).show();
    }

    // ------------------------------------------------------------ shell / navigation

    private int dp(int n) { return UiKit.dp(this, n); }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.color(this, R.color.bk_bg));

        // Header
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Theme.color(this, R.color.bk_surface));
        bar.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Theme.color(this, R.color.bk_text));
        bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton settingsBtn = UiKit.smallButton(this, "⚙ Settings");
        settingsBtn.setOnClickListener(v -> go("settings"));
        bar.addView(settingsBtn);
        root.addView(bar);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(24));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        // Bottom navigation: 5 tabs, with Diary promoted to a top-level tab since it is one of
        // the three things this app is actually for (khata, byaaj, diary).
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Theme.color(this, R.color.bk_surface));
        String[][] tabs = {{"⌂", "Home", "home"}, {"👥", "Ledger", "ledger"}, {"₹", "Byaaj", "byaaj"}, {"📓", "Diary", "diary"}, {"☰", "More", "more"}};
        navButtons = new LinearLayout[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            String id = tabs[i][2];
            LinearLayout item = UiKit.column(this);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(10), 0, dp(10));
            item.setClickable(true);
            item.setFocusable(true);
            UiKit.rippleForeground(this, item);
            TextView icon = new TextView(this);
            icon.setText(tabs[i][0]);
            icon.setTextSize(18);
            icon.setGravity(Gravity.CENTER);
            TextView lbl = new TextView(this);
            lbl.setText(tabs[i][1]);
            lbl.setTextSize(11);
            lbl.setGravity(Gravity.CENTER);
            item.addView(icon);
            item.addView(lbl);
            item.setOnClickListener(v -> go(id));
            navButtons[i] = item;
            nav.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        root.addView(nav);
        setContentView(root);
        render();
    }

    private void highlightNav() {
        String[] ids = {"home", "ledger", "byaaj", "diary", "more"};
        for (int i = 0; i < navButtons.length; i++) {
            boolean active = ids[i].equals(screen) || (ids[i].equals("ledger") && "customer".equals(screen));
            TextView lbl = (TextView) navButtons[i].getChildAt(1);
            TextView icon = (TextView) navButtons[i].getChildAt(0);
            int c = Theme.color(this, active ? R.color.bk_primary : R.color.bk_muted);
            lbl.setTextColor(c);
            icon.setTextColor(c);
            lbl.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void go(String s) {
        if (s.equals(screen)) return;
        stack.push(screen);
        screen = s;
        render();
    }

    private void back() {
        if (!stack.isEmpty()) { screen = stack.pop(); render(); }
        else if (!screen.equals("home")) { screen = "home"; render(); }
        else new MaterialAlertDialogBuilder(this).setTitle("Exit " + getString(R.string.app_name) + "?")
                .setNegativeButton("Stay", null).setPositiveButton("Exit", (d, w) -> finish()).show();
    }

    // ------------------------------------------------------------ data helpers

    private JSONArray allCustomers() { return store.customers(); }

    /** Customers belonging to the currently selected business — the old app ignored businessId entirely. */
    private java.util.List<JSONObject> cs() {
        java.util.List<JSONObject> out = new java.util.ArrayList<>();
        int biz = store.currentBizId();
        JSONArray all = allCustomers();
        for (int i = 0; i < all.length(); i++) {
            JSONObject c = all.optJSONObject(i);
            if (c != null && (!c.has("businessId") || c.optInt("businessId", biz) == biz)) out.add(c);
        }
        return out;
    }

    private JSONObject settings() { return store.settings(); }
    private String nm(JSONObject c) { return c.optString("name", "Customer"); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void render() {
        content.removeAllViews();
        highlightNav();
        try {
            switch (screen) {
                case "home": home(); break;
                case "ledger": ledger(); break;
                case "byaaj": byaaj(); break;
                case "customer": detail(); break;
                case "settings": settingsPage(); break;
                case "more": more(); break;
                case "recovery": recovery(); break;
                case "reports": reports(); break;
                case "expenses": expenses(); break;
                case "diary": diary(); break;
                case "business": business(); break;
                case "trash": trash(); break;
                default: home();
            }
        } catch (Exception e) {
            content.addView(UiKit.label(this, "Error: " + e.getMessage(), R.color.bk_due, 14));
        }
    }

    private void add(View v) { content.addView(v, new LinearLayout.LayoutParams(-1, -2)); }

    // ------------------------------------------------------------ HOME

    private void home() {
        add(UiKit.heading(this, "Dashboard"));
        long now = System.currentTimeMillis();
        double total = 0, advance = 0;
        int pending = 0, overdue = 0;
        for (JSONObject c : cs()) {
            total += Calc.totalDue(store, c, now);
            advance += Calc.advance(c);
            if (Calc.totalDue(store, c, now) > 0) pending++;
            for (JSONObject l : Calc.loans(c)) if (Calc.overdue(store, c, l, now)) overdue++;
        }

        MaterialCardView hero = UiKit.card(this);
        hero.setCardBackgroundColor(Theme.color(this, R.color.bk_primary));
        hero.setStrokeWidth(0);
        LinearLayout heroBody = UiKit.column(this);
        heroBody.addView(UiKit.label(this, "कुल बकाया (Total Outstanding)", R.color.bk_white, 13));
        heroBody.addView(UiKit.label(this, Calc.inr(total), R.color.bk_white, 30));
        heroBody.addView(UiKit.label(this, pending + " customers pending" + (overdue > 0 ? "  •  " + overdue + " loans overdue" : ""), R.color.bk_white, 13));
        hero.addView(heroBody);
        add(hero);

        if (advance > 0.5) add(statRow("Advance held (customers paid extra)", Calc.inr(advance), R.color.bk_credit));

        LinearLayout quick = UiKit.rowLayout(this);
        MaterialButton rec = UiKit.outlinedButton(this, "📌 Recovery");
        rec.setOnClickListener(v -> go("recovery"));
        MaterialButton rep = UiKit.outlinedButton(this, "📊 Reports");
        rep.setOnClickListener(v -> go("reports"));
        quick.addView(rec, new LinearLayout.LayoutParams(0, -2, 1));
        quick.addView(rep, new LinearLayout.LayoutParams(0, -2, 1));
        add(quick);

        add(UiKit.subheading(this, "Recent customers"));
        java.util.List<JSONObject> list = cs();
        if (list.isEmpty()) {
            add(UiKit.muted(this, "अभी कोई customer नहीं है। Ledger से पहला customer जोड़ें।"));
        } else {
            for (int i = Math.max(0, list.size() - 20); i < list.size(); i++) customerRow(list.get(i));
        }
    }

    private MaterialCardView statRow(String label, String value, int valueColor) {
        MaterialCardView c = UiKit.card(this);
        LinearLayout row = UiKit.rowLayout(this);
        TextView l = UiKit.body(this, label);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(UiKit.label(this, value, valueColor, 16));
        c.addView(row);
        return c;
    }

    private void customerRow(JSONObject c) {
        long now = System.currentTimeMillis();
        double due = Calc.totalDue(store, c, now);
        double adv = Calc.advance(c);
        boolean anyOverdue = false;
        for (JSONObject l : Calc.loans(c)) if (Calc.overdue(store, c, l, now)) anyOverdue = true;

        MaterialCardView card = UiKit.row(this, v -> { customerId = c.optInt("id"); go("customer"); });
        LinearLayout row = UiKit.rowLayout(this);
        row.addView(UiKit.avatar(this, nm(c)));
        LinearLayout col = UiKit.column(this);
        col.addView(UiKit.body(this, nm(c)));
        col.addView(UiKit.muted(this, c.optString("phone", "")));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout right = UiKit.column(this);
        right.setGravity(Gravity.END);
        if (due > 0.5) right.addView(UiKit.label(this, Calc.inr(due), R.color.bk_due, 15));
        else if (adv > 0.5) right.addView(UiKit.label(this, "+" + Calc.inr(adv), R.color.bk_credit, 15));
        else right.addView(UiKit.label(this, "✓ Clear", R.color.bk_credit, 13));
        if (anyOverdue) right.addView(UiKit.pill(this, "OVERDUE", R.color.bk_overdue_bg, R.color.bk_due));
        row.addView(right);
        card.addView(row);
        add(card);
    }

    // ------------------------------------------------------------ LEDGER (customers)

    private void ledger() {
        add(UiKit.heading(this, "Customers / Ledger"));
        TextInputLayout search = UiKit.inputBox(this, "Search name or mobile");
        UiKit.editOf(search).setText(ledgerSearch);
        add(search);
        MaterialButton addc = UiKit.primaryButton(this, "＋ Add Customer / Supplier");
        addc.setOnClickListener(v -> addCustomer());
        add(addc);
        LinearLayout list = UiKit.column(this);
        add(list);
        Runnable refresh = () -> {
            list.removeAllViews();
            String q = ledgerSearch.toLowerCase(Locale.US);
            for (JSONObject c : cs()) {
                if (q.isEmpty() || nm(c).toLowerCase(Locale.US).contains(q) || c.optString("phone").contains(q)) {
                    LinearLayout wrap = UiKit.column(this);
                    list.addView(wrap);
                    wrap.removeAllViews();
                    MaterialCardView card = UiKit.row(this, v -> { customerId = c.optInt("id"); go("customer"); });
                    LinearLayout row = UiKit.rowLayout(this);
                    row.addView(UiKit.avatar(this, nm(c)));
                    LinearLayout col = UiKit.column(this);
                    col.addView(UiKit.body(this, nm(c) + (Calc.isSupplier(c) ? "  (Supplier)" : "")));
                    col.addView(UiKit.muted(this, c.optString("phone", "")));
                    row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
                    double due = Calc.totalDue(store, c, System.currentTimeMillis());
                    row.addView(UiKit.label(this, due > 0.5 ? Calc.inr(due) : "✓", due > 0.5 ? R.color.bk_due : R.color.bk_credit, 15));
                    card.addView(row);
                    list.addView(card);
                }
            }
            if (list.getChildCount() == 0) list.addView(UiKit.muted(this, "कोई customer नहीं मिला।"));
        };
        UiKit.editOf(search).addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            public void onTextChanged(CharSequence s, int a, int b, int d) { ledgerSearch = s.toString(); refresh.run(); }
            public void afterTextChanged(Editable e) { }
        });
        refresh.run();
    }

    private void addCustomer() {
        LinearLayout f = UiKit.column(this);
        TextInputLayout n = UiKit.inputBox(this, "Name"), p = UiKit.inputBox(this, "Mobile");
        Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Customer (owes you)", "Supplier (you owe them)"}));
        f.addView(n); f.addView(p); f.addView(type);
        new MaterialAlertDialogBuilder(this).setTitle("Add Customer").setView(f)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        String name = UiKit.textOf(n);
                        if (name.isEmpty()) throw new IllegalArgumentException("Name required");
                        boolean supplier = type.getSelectedItemPosition() == 1;
                        JSONObject c = new JSONObject().put("id", store.next("seqCust")).put("businessId", store.currentBizId())
                                .put("name", name).put("phone", UiKit.textOf(p)).put("type", supplier ? "supplier" : "customer")
                                .put("khata", new JSONObject().put("balance", 0))
                                .put("byaaj", new JSONObject().put("loans", new JSONArray()));
                        allCustomers().put(c);
                        store.save();
                        render();
                    } catch (Exception e) { toast(e.getMessage() == null ? "Invalid customer" : e.getMessage()); }
                }).show();
    }

    private void editCustomer(JSONObject c) {
        LinearLayout f = UiKit.column(this);
        TextInputLayout n = UiKit.inputBox(this, "Name"), p = UiKit.inputBox(this, "Mobile");
        UiKit.editOf(n).setText(nm(c));
        UiKit.editOf(p).setText(c.optString("phone", ""));
        f.addView(n); f.addView(p);
        new MaterialAlertDialogBuilder(this).setTitle("Edit Customer").setView(f)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        String name = UiKit.textOf(n);
                        if (name.isEmpty()) throw new IllegalArgumentException("Name required");
                        c.put("name", name).put("phone", UiKit.textOf(p));
                        store.save();
                        render();
                    } catch (Exception e) { toast("Could not save"); }
                }).show();
    }

    private void deleteCustomer(JSONObject c) {
        new MaterialAlertDialogBuilder(this).setTitle("Delete " + nm(c) + "?")
                .setMessage("This moves the customer to Trash (More → Recycle Bin). You can restore it later.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    try {
                        store.softDelete("customer", c);
                        for (int i = 0; i < allCustomers().length(); i++) {
                            if (allCustomers().optJSONObject(i) == c) { allCustomers().remove(i); break; }
                        }
                        store.save();
                        screen = "ledger";
                        render();
                    } catch (Exception e) { toast("Could not delete"); }
                }).show();
    }

    // ------------------------------------------------------------ CUSTOMER DETAIL

    private void detail() {
        JSONObject c = store.customer(customerId);
        if (c == null) { screen = "ledger"; render(); return; }
        long now = System.currentTimeMillis();

        add(UiKit.heading(this, nm(c)));
        add(UiKit.muted(this, c.optString("phone", "") + (Calc.isSupplier(c) ? "  •  Supplier" : "")));

        double khataDue = Calc.khataDue(c), advance = Calc.advance(c), byaajDue = Calc.byaajDue(store, c, now);
        MaterialCardView summary = UiKit.card(this);
        LinearLayout sc = UiKit.column(this);
        sc.addView(UiKit.label(this, "Total Outstanding", R.color.bk_muted, 12));
        sc.addView(UiKit.label(this, Calc.inr(khataDue + byaajDue), R.color.bk_due, 26));
        if (advance > 0.5) sc.addView(UiKit.label(this, "Advance held: " + Calc.inr(advance), R.color.bk_credit, 13));
        summary.addView(sc);
        add(summary);

        LinearLayout actions = UiKit.rowLayout(this);
        MaterialButton u = UiKit.outlinedButton(this, "📤 Udhar");
        u.setOnClickListener(v -> txn(c, false));
        MaterialButton j = UiKit.outlinedButton(this, "📥 Jama");
        j.setOnClickListener(v -> txn(c, true));
        actions.addView(u, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(j, new LinearLayout.LayoutParams(0, -2, 1));
        add(actions);

        LinearLayout actions2 = UiKit.rowLayout(this);
        MaterialButton l = UiKit.outlinedButton(this, "📈 New Byaaj Loan");
        l.setOnClickListener(v -> loan(c));
        MaterialButton edit = UiKit.outlinedButton(this, "✏ Edit");
        edit.setOnClickListener(v -> editCustomer(c));
        actions2.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        actions2.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));
        add(actions2);

        LinearLayout actions3 = UiKit.rowLayout(this);
        MaterialButton w = UiKit.outlinedButton(this, "WhatsApp / Share");
        w.setOnClickListener(v -> share(c));
        MaterialButton sms = UiKit.outlinedButton(this, "SMS Reminder");
        sms.setOnClickListener(v -> sms(c));
        actions3.addView(w, new LinearLayout.LayoutParams(0, -2, 1));
        actions3.addView(sms, new LinearLayout.LayoutParams(0, -2, 1));
        add(actions3);

        MaterialButton del = UiKit.dangerButton(this, "🗑 Delete Customer");
        del.setOnClickListener(v -> deleteCustomer(c));
        add(del);

        // Loans
        java.util.List<JSONObject> loans = Calc.loans(c);
        if (!loans.isEmpty()) {
            add(UiKit.subheading(this, "Byaaj Loans"));
            for (JSONObject x : loans) loanRow(c, x, now);
        }

        // Recent khata transactions for this customer
        add(UiKit.subheading(this, "Recent Khata Entries"));
        JSONArray txns = store.txns();
        int shown = 0;
        for (int i = txns.length() - 1; i >= 0 && shown < 15; i--) {
            JSONObject t = txns.optJSONObject(i);
            if (t != null && t.optInt("customerId") == c.optInt("id")) { txnRow(c, t); shown++; }
        }
        if (shown == 0) add(UiKit.muted(this, "कोई entry नहीं है।"));
    }

    private void loanRow(JSONObject c, JSONObject l, long now) {
        double due = Calc.loanDue(store, c, l, now);
        boolean overdue = Calc.overdue(store, c, l, now);
        MaterialCardView card = UiKit.card(this);
        LinearLayout col = UiKit.column(this);
        LinearLayout head = UiKit.rowLayout(this);
        head.addView(UiKit.body(this, "Loan #" + l.optInt("id") + "  •  " + Calc.inr(l.optDouble("principal")) + " @ " + l.optDouble("rate") + "%/mo"), new LinearLayout.LayoutParams(0, -2, 1));
        if (overdue) head.addView(UiKit.pill(this, "OVERDUE", R.color.bk_overdue_bg, R.color.bk_due));
        col.addView(head);
        long dd = Calc.dueDate(l);
        col.addView(UiKit.muted(this, "Started " + Calc.display(Calc.loanStart(l)) + (dd > 0 ? "  •  Due " + Calc.display(dd) : "")));
        col.addView(UiKit.label(this, "Outstanding: " + Calc.inr(due), R.color.bk_due, 15));
        LinearLayout btns = UiKit.rowLayout(this);
        MaterialButton pay = UiKit.smallButton(this, "Payment");
        pay.setOnClickListener(v -> payment(c, l));
        MaterialButton edit = UiKit.smallButton(this, "Edit");
        edit.setOnClickListener(v -> editLoan(c, l));
        MaterialButton del = UiKit.smallButton(this, "Delete");
        del.setOnClickListener(v -> deleteLoan(c, l));
        btns.addView(pay); btns.addView(edit); btns.addView(del);
        col.addView(btns);
        card.addView(col);
        add(card);
    }

    private void txnRow(JSONObject c, JSONObject t) {
        boolean jama = "jama".equals(t.optString("type"));
        MaterialCardView card = UiKit.card(this);
        LinearLayout row = UiKit.rowLayout(this);
        LinearLayout col = UiKit.column(this);
        col.addView(UiKit.body(this, (jama ? "Jama (received)" : "Udhar (given)")));
        col.addView(UiKit.muted(this, Calc.display(Calc.txnTime(t)) + (t.optString("note", "").isEmpty() ? "" : " • " + t.optString("note"))));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(UiKit.label(this, Calc.inr(t.optDouble("amount")), jama ? R.color.bk_credit : R.color.bk_due, 15));
        MaterialButton del = UiKit.smallButton(this, "✕");
        del.setOnClickListener(v -> deleteTxn(c, t));
        row.addView(del);
        card.addView(row);
        add(card);
    }

    private void deleteTxn(JSONObject c, JSONObject t) {
        new MaterialAlertDialogBuilder(this).setTitle("Delete this entry?")
                .setMessage("The balance will be reversed and the entry moved to Trash.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    try {
                        boolean jama = "jama".equals(t.optString("type"));
                        double amt = t.optDouble("amount", 0);
                        JSONObject k = c.optJSONObject("khata");
                        if (k != null) k.put("balance", k.optDouble("balance", 0) + (jama ? -amt : amt));
                        store.softDelete("khataTxn", t);
                        JSONArray txns = store.txns();
                        for (int i = 0; i < txns.length(); i++) if (txns.optJSONObject(i) == t) { txns.remove(i); break; }
                        store.save();
                        render();
                    } catch (Exception e) { toast("Could not delete"); }
                }).show();
    }

    private void txn(JSONObject c, boolean jama) {
        LinearLayout f = UiKit.column(this);
        TextInputLayout a = UiKit.inputBox(this, "Amount ₹"), note = UiKit.inputBox(this, "Note (optional)");
        f.addView(a); f.addView(note);
        new MaterialAlertDialogBuilder(this).setTitle(jama ? "Jama (Received)" : "Udhar (Given)").setView(f)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        double x = Double.parseDouble(UiKit.textOf(a));
                        if (x <= 0) throw new NumberFormatException();
                        JSONObject k = c.optJSONObject("khata");
                        double old = k.optDouble("balance", 0);
                        k.put("balance", jama ? old + x : old - x);
                        String now = Calc.now();
                        store.txns().put(new JSONObject().put("id", store.next("seqTxn")).put("customerId", c.optInt("id"))
                                .put("businessId", store.currentBizId()).put("type", jama ? "jama" : "udhaar")
                                .put("amount", Calc.round2(x)).put("rawDate", now).put("date", Calc.display(System.currentTimeMillis()))
                                .put("note", UiKit.textOf(note)));
                        store.save();
                        render();
                    } catch (Exception e) { toast("Invalid amount"); }
                }).show();
    }

    private void loan(JSONObject c) {
        LinearLayout f = UiKit.column(this);
        TextInputLayout p = UiKit.inputBox(this, "Principal ₹"), r = UiKit.inputBox(this, "Rate % / month"), du = UiKit.inputBox(this, "Duration (months, optional)");
        Spinner t = new Spinner(this);
        t.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"simple", "compound"}));
        f.addView(p); f.addView(r); f.addView(du); f.addView(t);
        new MaterialAlertDialogBuilder(this).setTitle("New Byaaj Loan").setView(f)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (x, w) -> {
                    try {
                        double pr = Double.parseDouble(UiKit.textOf(p)), rt = Double.parseDouble(UiKit.textOf(r));
                        int dur = UiKit.textOf(du).isEmpty() ? 0 : Integer.parseInt(UiKit.textOf(du));
                        String now = Calc.now();
                        JSONObject l = new JSONObject().put("id", 100 + Calc.loans(c).size() + 1).put("principal", pr).put("rate", rt)
                                .put("duration", dur).put("interestType", t.getSelectedItem().toString())
                                .put("compFreq", 12).put("rawStartDate", now).put("startDate", Calc.display(System.currentTimeMillis()))
                                .put("partPaid", 0);
                        c.optJSONObject("byaaj").optJSONArray("loans").put(l);
                        store.save();
                        render();
                    } catch (Exception e) { toast("Invalid loan details"); }
                }).show();
    }

    private void editLoan(JSONObject c, JSONObject l) {
        LinearLayout f = UiKit.column(this);
        TextInputLayout r = UiKit.inputBox(this, "Rate % / month"), du = UiKit.inputBox(this, "Duration (months)");
        UiKit.editOf(r).setText(String.valueOf(l.optDouble("rate")));
        UiKit.editOf(du).setText(String.valueOf(l.optInt("duration", 0)));
        f.addView(r); f.addView(du);
        new MaterialAlertDialogBuilder(this).setTitle("Edit Loan #" + l.optInt("id")).setView(f)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        l.put("rate", Double.parseDouble(UiKit.textOf(r)));
                        l.put("duration", UiKit.textOf(du).isEmpty() ? 0 : Integer.parseInt(UiKit.textOf(du)));
                        store.save();
                        render();
                    } catch (Exception e) { toast("Invalid values"); }
                }).show();
    }

    private void deleteLoan(JSONObject c, JSONObject l) {
        new MaterialAlertDialogBuilder(this).setTitle("Delete this loan?")
                .setMessage("Its payment history stays in Byaaj Payments but the loan itself moves to Trash.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    try {
                        store.softDelete("loan", l);
                        JSONArray loans = c.optJSONObject("byaaj").optJSONArray("loans");
                        for (int i = 0; i < loans.length(); i++) if (loans.optJSONObject(i) == l) { loans.remove(i); break; }
                        store.save();
                        render();
                    } catch (Exception e) { toast("Could not delete"); }
                }).show();
    }

    private void payment(JSONObject c, JSONObject l) {
        TextInputLayout a = UiKit.inputBox(this, "Payment ₹");
        new MaterialAlertDialogBuilder(this).setTitle("Loan Payment").setView(a)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        double x = Double.parseDouble(UiKit.textOf(a));
                        double due = Calc.loanDue(store, c, l, System.currentTimeMillis());
                        if (x <= 0 || x > due + 0.01) throw new NumberFormatException();
                        String now = Calc.now();
                        store.payments().put(new JSONObject().put("id", store.next("seqTxn")).put("customerId", c.optInt("id"))
                                .put("loanId", l.optInt("id")).put("amount", Calc.round2(x)).put("rawDate", now)
                                .put("date", Calc.display(System.currentTimeMillis())).put("businessId", store.currentBizId()));
                        l.put("partPaid", l.optDouble("partPaid", 0) + x);
                        store.invalidateCache();
                        store.save();
                        render();
                    } catch (Exception e) { toast("Invalid payment (check the amount isn't more than what's due)"); }
                }).show();
    }

    // ------------------------------------------------------------ BYAAJ overview

    private void byaaj() {
        add(UiKit.heading(this, "Byaaj (Interest Loans)"));
        boolean any = false;
        for (JSONObject c : cs()) {
            if (!Calc.loans(c).isEmpty()) { customerRow(c); any = true; }
        }
        if (!any) add(UiKit.muted(this, "अभी कोई byaaj loan नहीं है। किसी customer में जाकर 'New Byaaj Loan' जोड़ें।"));
    }

    // ------------------------------------------------------------ MORE / tools

    private void more() {
        add(UiKit.heading(this, "Tools"));
        toolRow("📌 Recovery", "recovery");
        toolRow("📊 Reports & PDF", "reports");
        toolRow("💸 Expenses", "expenses");
        toolRow("🏪 Businesses", "business");
        toolRow("🗑 Recycle Bin", "trash");
        toolRow("⚙ Settings", "settings");
        MaterialButton b = UiKit.outlinedButton(this, "💾 Backup / Restore");
        b.setOnClickListener(v -> backup());
        add(b);
    }

    private void toolRow(String label, String dest) {
        MaterialCardView card = UiKit.row(this, v -> go(dest));
        card.addView(UiKit.body(this, label));
        add(card);
    }

    // ------------------------------------------------------------ RECOVERY

    private void recovery() {
        add(UiKit.heading(this, "Recovery"));
        long now = System.currentTimeMillis();
        double total = 0;
        int n = 0;
        java.util.List<JSONObject> pending = new java.util.ArrayList<>();
        for (JSONObject c : cs()) {
            double d = Calc.totalDue(store, c, now);
            if (d > 0.5) { pending.add(c); total += d; n++; }
        }
        // Overdue first, then largest due.
        pending.sort((x, y) -> {
            boolean ox = false, oy = false;
            for (JSONObject l : Calc.loans(x)) if (Calc.overdue(store, x, l, now)) ox = true;
            for (JSONObject l : Calc.loans(y)) if (Calc.overdue(store, y, l, now)) oy = true;
            if (ox != oy) return ox ? -1 : 1;
            return Double.compare(Calc.totalDue(store, y, now), Calc.totalDue(store, x, now));
        });
        add(statRow("Pending customers", String.valueOf(n), R.color.bk_text));
        add(statRow("Total outstanding", Calc.inr(total), R.color.bk_due));
        for (JSONObject c : pending) customerRow(c);
        if (pending.isEmpty()) add(UiKit.muted(this, "सभी customers का हिसाब clear है 🎉"));
    }

    // ------------------------------------------------------------ REPORTS + PDF

    private void reports() {
        add(UiKit.heading(this, "Reports"));
        long now = System.currentTimeMillis();
        double due = 0, collectedThisMonth;
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (JSONObject c : cs()) { due += Calc.totalDue(store, c, now); ids.add(c.optInt("id")); }
        collectedThisMonth = Calc.collectedThisMonth(store, ids, now);
        add(statRow("Customers", String.valueOf(cs().size()), R.color.bk_text));
        add(statRow("Outstanding", Calc.inr(due), R.color.bk_due));
        add(statRow("Collected this month", Calc.inr(collectedThisMonth), R.color.bk_credit));
        MaterialButton p = UiKit.primaryButton(this, "📄 Create & Share PDF Statement");
        p.setOnClickListener(v -> pdf());
        add(p);
    }

    private void pdf() {
        try {
            File f = new File(getExternalFilesDir(null), "Guru-Shree-statement.pdf");
            PdfDocument doc = new PdfDocument();
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            int pageW = 595, pageH = 842, margin = 40;
            Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.logo);

            PdfDocument.Page cover = doc.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, doc.getPages().size() + 1).create());
            Canvas cv = cover.getCanvas();
            if (logo != null) cv.drawBitmap(logo, null, new RectF(margin, 25, margin + 60, 85), paint);
            paint.setTextSize(24);
            cv.drawText(settings().optString("biz", getString(R.string.app_name)), margin + 75, 60, paint);
            paint.setTextSize(12);
            cv.drawText("Generated " + Calc.display(System.currentTimeMillis()), margin + 75, 80, paint);
            paint.setTextSize(14);
            long now = System.currentTimeMillis();
            double due = 0;
            for (JSONObject c : cs()) due += Calc.totalDue(store, c, now);
            cv.drawText("Total customers: " + cs().size(), margin, 130, paint);
            cv.drawText("Total outstanding: " + Calc.inr(due), margin, 155, paint);
            doc.finishPage(cover);

            // One page per customer that has any balance, so the recovery team can hand out slips.
            for (JSONObject c : cs()) {
                double d = Calc.totalDue(store, c, now);
                if (d < 0.5) continue;
                PdfDocument.Page pg = doc.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, doc.getPages().size() + 1).create());
                Canvas pc = pg.getCanvas();
                paint.setTextSize(18);
                pc.drawText(nm(c), margin, 50, paint);
                paint.setTextSize(12);
                pc.drawText(c.optString("phone", ""), margin, 70, paint);
                paint.setTextSize(16);
                pc.drawText("Outstanding: " + Calc.inr(d), margin, 100, paint);
                paint.setTextSize(11);
                int y = 130;
                JSONArray txns = store.txns();
                for (int i = 0; i < txns.length() && y < pageH - 40; i++) {
                    JSONObject t = txns.optJSONObject(i);
                    if (t != null && t.optInt("customerId") == c.optInt("id")) {
                        boolean jama = "jama".equals(t.optString("type"));
                        pc.drawText(Calc.display(Calc.txnTime(t)) + "  " + (jama ? "Jama +" : "Udhar -") + Calc.inr(t.optDouble("amount")), margin, y, paint);
                        y += 18;
                    }
                }
                doc.finishPage(pg);
            }

            try (FileOutputStream o = new FileOutputStream(f)) { doc.writeTo(o); }
            doc.close();

            Uri uri = FileProvider.getUriForFile(this, "com.babasitaram.pro.fileprovider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share PDF statement"));
        } catch (Exception e) {
            toast("PDF failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ EXPENSES

    private void expenses() {
        add(UiKit.heading(this, "Expenses"));
        MaterialButton addBtn = UiKit.primaryButton(this, "＋ Add Expense");
        addBtn.setOnClickListener(v -> {
            LinearLayout f = UiKit.column(this);
            TextInputLayout cat = UiKit.inputBox(this, "Category"), amt = UiKit.inputBox(this, "Amount ₹");
            f.addView(cat); f.addView(amt);
            new MaterialAlertDialogBuilder(this).setTitle("Expense").setView(f)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (d, w) -> {
                        try {
                            double x = Double.parseDouble(UiKit.textOf(amt));
                            store.expenses().put(new JSONObject().put("id", store.next("seqExp")).put("businessId", store.currentBizId())
                                    .put("category", UiKit.textOf(cat)).put("amount", Calc.round2(x))
                                    .put("rawDate", Calc.now()).put("date", Calc.display(System.currentTimeMillis())));
                            store.save();
                            render();
                        } catch (Exception e) { toast("Invalid expense"); }
                    }).show();
        });
        add(addBtn);
        double total = 0;
        JSONArray ex = store.expenses();
        for (int i = ex.length() - 1; i >= 0; i--) {
            JSONObject x = ex.optJSONObject(i);
            if (x == null) continue;
            total += x.optDouble("amount", 0);
            MaterialCardView card = UiKit.card(this);
            LinearLayout row = UiKit.rowLayout(this);
            LinearLayout col = UiKit.column(this);
            col.addView(UiKit.body(this, x.optString("category", "Other")));
            col.addView(UiKit.muted(this, Calc.display(Calc.txnTime(x))));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(UiKit.label(this, Calc.inr(x.optDouble("amount")), R.color.bk_due, 15));
            MaterialButton del = UiKit.smallButton(this, "✕");
            JSONObject fx = x;
            del.setOnClickListener(v -> {
                store.softDelete("expense", fx);
                for (int j = 0; j < ex.length(); j++) if (ex.optJSONObject(j) == fx) { ex.remove(j); break; }
                try { store.save(); } catch (Exception ignored) { }
                render();
            });
            row.addView(del);
            card.addView(row);
            add(card);
        }
        add(statRow("Total expenses", Calc.inr(total), R.color.bk_due));
    }

    // ------------------------------------------------------------ DIARY

    private void diary() {
        add(UiKit.heading(this, "Diary"));
        MaterialButton addBtn = UiKit.primaryButton(this, "＋ New Note");
        addBtn.setOnClickListener(v -> diaryEditor(null));
        add(addBtn);
        JSONArray d = store.diary();
        if (d.length() == 0) add(UiKit.muted(this, "अभी कोई note नहीं है।"));
        for (int i = d.length() - 1; i >= 0; i--) {
            JSONObject e = d.optJSONObject(i);
            if (e == null) continue;
            MaterialCardView card = UiKit.row(this, v -> diaryEditor(e));
            LinearLayout col = UiKit.column(this);
            col.addView(UiKit.body(this, e.optString("title", "Untitled")));
            col.addView(UiKit.muted(this, Calc.display(Calc.parse(e.optString("createdAt", "")))));
            String content = e.optString("content", "");
            col.addView(UiKit.muted(this, content.length() > 80 ? content.substring(0, 80) + "…" : content));
            card.addView(col);
            add(card);
        }
    }

    private void diaryEditor(JSONObject existing) {
        LinearLayout f = UiKit.column(this);
        TextInputLayout title = UiKit.inputBox(this, "Title"), body = UiKit.inputBox(this, "Note");
        if (existing != null) {
            UiKit.editOf(title).setText(existing.optString("title", ""));
            UiKit.editOf(body).setText(existing.optString("content", ""));
        }
        f.addView(title); f.addView(body);
        MaterialAlertDialogBuilder dlg = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "New Diary Note" : "Edit Note").setView(f)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        String t = UiKit.textOf(title), c = UiKit.textOf(body);
                        if (existing == null) {
                            store.diary().put(new JSONObject().put("id", store.next("seqDiary")).put("date", Calc.display(System.currentTimeMillis()))
                                    .put("title", t.isEmpty() ? "Untitled" : t).put("content", c).put("createdAt", Calc.now()));
                        } else {
                            existing.put("title", t.isEmpty() ? "Untitled" : t).put("content", c);
                        }
                        store.save();
                        render();
                    } catch (Exception e) { toast("Could not save note"); }
                });
        if (existing != null) {
            dlg.setNeutralButton("Delete", (d, w) -> {
                store.softDelete("diary", existing);
                JSONArray arr = store.diary();
                for (int i = 0; i < arr.length(); i++) if (arr.optJSONObject(i) == existing) { arr.remove(i); break; }
                try { store.save(); } catch (Exception ignored) { }
                render();
            });
        }
        dlg.show();
    }

    // ------------------------------------------------------------ BUSINESS

    private void business() {
        add(UiKit.heading(this, "Businesses"));
        JSONArray biz = store.businesses();
        int current = store.currentBizId();
        for (int i = 0; i < biz.length(); i++) {
            JSONObject b = biz.optJSONObject(i);
            if (b == null) continue;
            boolean active = b.optInt("id") == current;
            MaterialCardView card = UiKit.row(this, v -> {
                try { store.data().put("currentBizId", b.optInt("id")); store.settings().put("biz", b.optString("name")); store.save(); } catch (Exception ignored) { }
                render();
            });
            LinearLayout row = UiKit.rowLayout(this);
            row.addView(UiKit.body(this, b.optString("name", "Business")), new LinearLayout.LayoutParams(0, -2, 1));
            if (active) row.addView(UiKit.pill(this, "ACTIVE", R.color.bk_overdue_bg, R.color.bk_primary));
            card.addView(row);
            add(card);
        }
        MaterialButton addBiz = UiKit.primaryButton(this, "＋ Add Business");
        addBiz.setOnClickListener(v -> {
            TextInputLayout n = UiKit.inputBox(this, "Business name");
            new MaterialAlertDialogBuilder(this).setTitle("New Business").setView(n)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (d, w) -> {
                        try {
                            String s = UiKit.textOf(n);
                            if (s.isEmpty()) throw new IllegalArgumentException();
                            int id = store.next("seqBiz");
                            store.businesses().put(new JSONObject().put("id", id).put("name", s).put("owner", settings().optString("name", "")));
                            store.data().put("currentBizId", id);
                            settings().put("biz", s);
                            store.save();
                            render();
                        } catch (Exception e) { toast("Invalid business name"); }
                    }).show();
        });
        add(addBiz);
    }

    // ------------------------------------------------------------ TRASH / recycle bin

    private void trash() {
        add(UiKit.heading(this, "Recycle Bin"));
        JSONArray t = store.trash();
        if (t.length() == 0) add(UiKit.muted(this, "Trash खाली है।"));
        for (int i = t.length() - 1; i >= 0; i--) {
            JSONObject item = t.optJSONObject(i);
            if (item == null) continue;
            MaterialCardView card = UiKit.card(this);
            LinearLayout col = UiKit.column(this);
            col.addView(UiKit.body(this, item.optString("kind", "item") + " • deleted " + Calc.display(Calc.parse(item.optString("deletedAt", "")))));
            LinearLayout btns = UiKit.rowLayout(this);
            MaterialButton restore = UiKit.smallButton(this, "Restore");
            restore.setOnClickListener(v -> restoreTrashItem(item));
            MaterialButton purge = UiKit.dangerButton(this, "Delete forever");
            purge.setOnClickListener(v -> {
                for (int j = 0; j < t.length(); j++) if (t.optJSONObject(j) == item) { t.remove(j); break; }
                try { store.save(); } catch (Exception ignored) { }
                render();
            });
            btns.addView(restore); btns.addView(purge);
            col.addView(btns);
            card.addView(col);
            add(card);
        }
    }

    private void restoreTrashItem(JSONObject item) {
        try {
            String kind = item.optString("kind");
            JSONObject data = item.optJSONObject("data");
            if (data == null) return;
            switch (kind) {
                case "customer": allCustomers().put(data); break;
                case "khataTxn": store.txns().put(data); break;
                case "loan":
                    // A stored loan object doesn't carry its owning customer's id, so ask which
                    // customer to restore it into rather than guessing.
                    restoreLoanPickCustomer(data);
                    return;
                case "expense": store.expenses().put(data); break;
                case "diary": store.diary().put(data); break;
                default: return;
            }
            JSONArray t = store.trash();
            for (int j = 0; j < t.length(); j++) if (t.optJSONObject(j) == item) { t.remove(j); break; }
            store.save();
            render();
        } catch (Exception e) { toast("Could not restore"); }
    }

    private void restoreLoanPickCustomer(JSONObject loanData) {
        String[] names = new String[allCustomers().length()];
        for (int i = 0; i < names.length; i++) names[i] = nm(allCustomers().optJSONObject(i));
        new MaterialAlertDialogBuilder(this).setTitle("Restore loan to which customer?")
                .setItems(names, (d, which) -> {
                    try {
                        JSONObject c = allCustomers().optJSONObject(which);
                        c.optJSONObject("byaaj").optJSONArray("loans").put(loanData);
                        store.save();
                        render();
                    } catch (Exception e) { toast("Could not restore"); }
                }).show();
    }

    // ------------------------------------------------------------ SETTINGS

    private void settingsPage() {
        add(UiKit.heading(this, "Settings"));
        add(UiKit.body(this, settings().optString("biz", "My Business")));

        MaterialButton pin = UiKit.outlinedButton(this, store.hasPin() ? "🔐 Change App PIN" : "🔐 Set App PIN");
        pin.setOnClickListener(v -> setPinDialog());
        add(pin);
        if (store.hasPin()) {
            MaterialButton clearPin = UiKit.outlinedButton(this, "Remove PIN");
            clearPin.setOnClickListener(v -> {
                try { store.clearPin(); toast("PIN removed"); } catch (Exception e) { toast("Could not remove PIN"); }
            });
            add(clearPin);
        }

        MaterialButton rem = UiKit.outlinedButton(this, "🔔 Reminder Settings");
        rem.setOnClickListener(v -> reminderSettings());
        add(rem);

        MaterialButton bk = UiKit.outlinedButton(this, "💾 Backup / Restore");
        bk.setOnClickListener(v -> backup());
        add(bk);

        add(UiKit.subheading(this, "Appearance"));
        LinearLayout darkRow = UiKit.rowLayout(this);
        darkRow.addView(UiKit.body(this, "Dark mode"), new LinearLayout.LayoutParams(0, -2, 1));
        Switch darkSwitch = new Switch(this);
        darkSwitch.setChecked("dark".equals(settings().optString("darkMode", "system")));
        darkSwitch.setOnCheckedChangeListener((btn, checked) -> {
            try {
                settings().put("darkMode", checked ? "dark" : "system");
                store.save();
                AppCompatDelegate.setDefaultNightMode(checked
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                recreate();
            } catch (Exception ignored) { }
        });
        darkRow.addView(darkSwitch);
        add(darkRow);
    }

    private void setPinDialog() {
        TextInputLayout p = UiKit.inputBox(this, "4–6 digit PIN");
        new MaterialAlertDialogBuilder(this).setTitle("App PIN").setView(p)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        String x = UiKit.textOf(p);
                        if (!x.matches("\\d{4,6}")) throw new IllegalArgumentException();
                        store.setPin(x);
                        toast("PIN saved. It will be asked for next time you open the app.");
                    } catch (Exception e) { toast("PIN must be 4–6 digits"); }
                }).show();
    }

    private void reminderSettings() {
        LinearLayout f = UiKit.column(this);
        Switch e = new Switch(this);
        e.setText("Automatic reminders");
        e.setChecked(settings().optBoolean("reminderEnabled", true));
        TextInputLayout d = UiKit.inputBox(this, "Remind again after (days)");
        UiKit.editOf(d).setText(String.valueOf(settings().optInt("reminderDays", 7)));
        TextInputLayout min = UiKit.inputBox(this, "Minimum due amount to remind");
        UiKit.editOf(min).setText(String.valueOf(settings().optInt("minReminder", 100)));
        f.addView(e); f.addView(d); f.addView(min);
        new MaterialAlertDialogBuilder(this).setTitle("Reminder Settings").setView(f)
                .setPositiveButton("Save", (x, w) -> {
                    try {
                        settings().put("reminderEnabled", e.isChecked())
                                .put("reminderDays", Math.max(1, Integer.parseInt(UiKit.textOf(d))))
                                .put("minReminder", Math.max(0, Double.parseDouble(UiKit.textOf(min))));
                        store.save();
                        ReminderScheduler.schedule(this);
                    } catch (Exception z) { toast("Invalid settings"); }
                }).setNegativeButton("Cancel", null).show();
    }

    private void backup() {
        new MaterialAlertDialogBuilder(this).setTitle("Backup / Restore")
                .setMessage("Export saves a plain-JSON copy of everything. Restoring validates the file and creates a safety snapshot first, so a bad restore can always be undone.")
                .setPositiveButton("Export", (d, w) -> exportBackup())
                .setNeutralButton("Import", (d, w) -> importBackup())
                .setNegativeButton("Close", null).show();
    }

    private void exportBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "GuruShree-backup-" + Calc.today() + ".json");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_IMPORT);
    }

    // ------------------------------------------------------------ share / sms

    private void share(JSONObject c) {
        double due = Calc.totalDue(store, c, System.currentTimeMillis());
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, "नमस्ते " + nm(c) + " जी 🙏\nकुल बकाया: " + Calc.inr(due) + "\nधन्यवाद\n" + settings().optString("biz", getString(R.string.app_name)));
        try { startActivity(Intent.createChooser(i, "Share statement")); }
        catch (Exception e) { toast("No share app available"); }
    }

    private void sms(JSONObject c) {
        String p = c.optString("phone", "").replaceAll("\\D", "");
        if (p.isEmpty()) { toast("Mobile missing"); return; }
        double due = Calc.totalDue(store, c, System.currentTimeMillis());
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + p));
        i.putExtra("sms_body", "नमस्ते " + nm(c) + " जी 🙏\nबकाया: " + Calc.inr(due));
        startActivity(i);
    }
}
