package com.babasitaram.pro;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * All money / interest / date logic lives here so the UI, the PDF export and the
 * background reminder use exactly the same numbers (before, the formula was copy-pasted
 * in two places and the compound-interest copy was wrong).
 */
final class Calc {
    private Calc() {}

    /** Same 30-day month the web version uses, so both apps show identical interest. */
    static final double MS_PER_MONTH = 30.0 * 24 * 3600 * 1000;
    static final long DAY = 24L * 3600 * 1000;

    // ------------------------------------------------------------------ money

    static double round2(double x) {
        return Math.round(x * 100.0) / 100.0;
    }

    /** Indian digit grouping: ₹12,34,567 (paise shown only when non-zero). */
    static String inr(double v) {
        long paise = Math.round(Math.abs(v) * 100);
        long rupees = paise / 100;
        int ps = (int) (paise % 100);
        String s = Long.toString(rupees);
        String grouped;
        if (s.length() <= 3) {
            grouped = s;
        } else {
            String last3 = s.substring(s.length() - 3);
            String rest = s.substring(0, s.length() - 3);
            StringBuilder b = new StringBuilder();
            int i = rest.length();
            while (i > 2) {
                b.insert(0, "," + rest.substring(i - 2, i));
                i -= 2;
            }
            b.insert(0, rest.substring(0, i));
            grouped = b + "," + last3;
        }
        return "₹" + grouped + (ps != 0 ? String.format(Locale.US, ".%02d", ps) : "");
    }

    // ------------------------------------------------------------------ dates

    private static SimpleDateFormat fmt(String p, boolean utc, Locale l) {
        SimpleDateFormat f = new SimpleDateFormat(p, l);
        if (utc) f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    }

    /** ISO-8601 UTC timestamp. Replaces Date.toString(), which depends on phone language. */
    static String now() {
        return fmt("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", true, Locale.US).format(new Date());
    }

    /** Local calendar day as yyyy-MM-dd. */
    static String today() {
        return fmt("yyyy-MM-dd", false, Locale.US).format(new Date());
    }

    static String display(long ms) {
        return ms <= 0 ? "—" : fmt("dd MMM yyyy", false, new Locale("en", "IN")).format(new Date(ms));
    }

    /** Parses every format this app or the web version ever wrote. Returns 0 if unknown. */
    static long parse(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        String[][] formats = {
                {"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "u"},
                {"yyyy-MM-dd'T'HH:mm:ss'Z'", "u"},
                {"yyyy-MM-dd", "l"},
                {"EEE MMM dd HH:mm:ss zzz yyyy", "l"},
                {"d MMM yyyy", "l"},
                {"dd MMM yyyy", "l"}};
        for (String[] f : formats) {
            try {
                Date d = fmt(f[0], "u".equals(f[1]), Locale.US).parse(s.trim());
                if (d != null) return d.getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    static String daysAgo(long ms, boolean hi) {
        if (ms <= 0) return hi ? "कभी नहीं" : "Never";
        long d = Math.max(0, (System.currentTimeMillis() - ms) / DAY);
        if (d == 0) return hi ? "आज" : "Today";
        if (d == 1) return hi ? "कल" : "Yesterday";
        return hi ? d + " दिन पहले" : d + " days ago";
    }

    static long txnTime(JSONObject t) {
        long x = parse(t.optString("rawDate", ""));
        if (x <= 0) x = parse(t.optString("createdAt", ""));
        if (x <= 0) x = parse(t.optString("date", ""));
        return x;
    }

    static boolean sameMonth(long a, long b) {
        Calendar x = Calendar.getInstance();
        Calendar y = Calendar.getInstance();
        x.setTimeInMillis(a);
        y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.MONTH) == y.get(Calendar.MONTH);
    }

    // ------------------------------------------------------------------ loans

    static long loanStart(JSONObject l) {
        long s = parse(l.optString("rawStartDate", ""));
        return s > 0 ? s : parse(l.optString("startDate", ""));
    }

    static double elapsedMonths(JSONObject l, long now) {
        long st = loanStart(l);
        return (st <= 0 || now <= st) ? 0 : (now - st) / MS_PER_MONTH;
    }

    /**
     * Interest accrued so far. The rate is a MONTHLY percentage.
     * Compound: compFreq = periods per year (12, 4, 2, 1); the periodic rate is
     * monthly-rate * (12 / compFreq). The old native code divided the monthly rate by
     * compFreq, which under-charged 2%/month compounding as ~0.17%/month.
     */
    static double interest(JSONObject l, long now) {
        double p = l.optDouble("principal", 0), r = l.optDouble("rate", 0);
        double m = elapsedMonths(l, now);
        if (p <= 0 || r <= 0 || m <= 0) return 0;
        if ("compound".equalsIgnoreCase(l.optString("interestType"))) {
            int f = l.optInt("compFreq", 12);
            if (f != 1 && f != 2 && f != 4 && f != 12) f = 12;
            double periodic = (r / 100.0) * (12.0 / f);
            double periods = (m / 12.0) * f;
            return round2(p * (Math.pow(1 + periodic, periods) - 1));
        }
        return round2(p * r * m / 100.0);
    }

    static double paid(LedgerStore st, JSONObject c, JSONObject l) {
        Double v = st.paidIndex().get(c.optInt("id") + ":" + l.optInt("id"));
        return v != null ? v : l.optDouble("partPaid", 0);
    }

    static double loanTotal(JSONObject l, long now) {
        return round2(l.optDouble("principal", 0) + interest(l, now));
    }

    static double loanDue(LedgerStore st, JSONObject c, JSONObject l, long now) {
        return round2(Math.max(0, loanTotal(l, now) - paid(st, c, l)));
    }

    /** Agreed end date, or 0 when no duration was set. */
    static long dueDate(JSONObject l) {
        long st = loanStart(l);
        int months = l.optInt("duration", 0);
        if (st <= 0 || months <= 0) return 0;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(st);
        cal.add(Calendar.MONTH, months);
        return cal.getTimeInMillis();
    }

    static boolean overdue(LedgerStore st, JSONObject c, JSONObject l, long now) {
        long d = dueDate(l);
        return d > 0 && now > d && loanDue(st, c, l, now) > 0.5;
    }

    static List<JSONObject> loans(JSONObject c) {
        List<JSONObject> out = new ArrayList<>();
        JSONObject b = c.optJSONObject("byaaj");
        JSONArray a = b == null ? null : b.optJSONArray("loans");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject l = a.optJSONObject(i);
            if (l != null) out.add(l);
        }
        return out;
    }

    // -------------------------------------------------------------- customers

    static boolean isSupplier(JSONObject c) {
        return "supplier".equalsIgnoreCase(c.optString("type", "customer"));
    }

    /** Negative = customer owes us (udhaar); positive = customer paid in advance. */
    static double balance(JSONObject c) {
        JSONObject k = c.optJSONObject("khata");
        return k == null ? 0 : k.optDouble("balance", 0);
    }

    static double khataDue(JSONObject c) {
        return isSupplier(c) ? 0 : Math.max(0, -balance(c));
    }

    static double advance(JSONObject c) {
        return isSupplier(c) ? 0 : Math.max(0, balance(c));
    }

    static double byaajDue(LedgerStore st, JSONObject c, long now) {
        if (isSupplier(c)) return 0;
        double x = 0;
        for (JSONObject l : loans(c)) x += loanDue(st, c, l, now);
        return round2(x);
    }

    static double totalDue(LedgerStore st, JSONObject c, long now) {
        return round2(khataDue(c) + byaajDue(st, c, now));
    }

    static HashMap<Integer, Double> dueMap(LedgerStore st, List<JSONObject> cs, long now) {
        HashMap<Integer, Double> m = new HashMap<>();
        for (JSONObject c : cs) m.put(c.optInt("id"), totalDue(st, c, now));
        return m;
    }

    /** Khata credits + loan payments received in the calendar month of {@code now}. */
    static double collectedThisMonth(LedgerStore st, java.util.Set<Integer> ids, long now) {
        double x = 0;
        JSONArray t = st.txns();
        for (int i = 0; i < t.length(); i++) {
            JSONObject o = t.optJSONObject(i);
            if (o != null && "jama".equals(o.optString("type")) && ids.contains(o.optInt("customerId"))
                    && sameMonth(txnTime(o), now)) x += o.optDouble("amount", 0);
        }
        JSONArray p = st.payments();
        for (int i = 0; i < p.length(); i++) {
            JSONObject o = p.optJSONObject(i);
            if (o != null && ids.contains(o.optInt("customerId")) && sameMonth(txnTime(o), now))
                x += o.optDouble("amount", 0);
        }
        return round2(x);
    }
}
