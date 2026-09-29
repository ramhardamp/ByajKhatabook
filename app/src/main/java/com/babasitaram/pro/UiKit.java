package com.babasitaram.pro;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * NEW FILE. In the original app every screen hand-built plain TextView/Button/EditText with
 * inline colours, so every screen looked and behaved slightly differently and any visual change
 * meant editing dozens of call sites. These builders are the one place that decides what a
 * heading, a card, a button or an input field looks like across the whole app.
 */
final class UiKit {
    private UiKit() {}

    static int dp(Context c, int n) { return (int) (n * c.getResources().getDisplayMetrics().density + 0.5f); }
    static int sp(Context c, int n) { return n; }
    private static int col(Context c, int res) { return Theme.color(c, res); }

    static TextView heading(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        v.setTypeface(null, Typeface.BOLD);
        v.setTextColor(col(c, R.color.bk_text));
        v.setPadding(dp(c, 4), dp(c, 18), dp(c, 4), dp(c, 6));
        return v;
    }

    static TextView subheading(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        v.setTypeface(null, Typeface.BOLD);
        v.setTextColor(col(c, R.color.bk_text));
        v.setPadding(dp(c, 4), dp(c, 16), dp(c, 4), dp(c, 6));
        return v;
    }

    static TextView label(Context c, String text, int colorRes, float sizeSp) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        v.setTextColor(col(c, colorRes));
        return v;
    }

    static TextView muted(Context c, String text) { return label(c, text, R.color.bk_muted, 13); }
    static TextView body(Context c, String text) { return label(c, text, R.color.bk_text, 15); }

    /** A tappable, elevated Material card. Caller adds its own content and click listener. */
    static MaterialCardView card(Context c) {
        MaterialCardView card = new MaterialCardView(c);
        card.setRadius(dp(c, 16));
        card.setCardElevation(dp(c, 2));
        card.setCardBackgroundColor(col(c, R.color.bk_surface));
        card.setStrokeWidth(dp(c, 1));
        card.setStrokeColor(col(c, R.color.bk_outline));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 4), dp(c, 6), dp(c, 4), dp(c, 6));
        card.setLayoutParams(lp);
        int pad = dp(c, 14);
        card.setContentPadding(pad, pad, pad, pad);
        rippleForeground(c, card);
        return card;
    }

    /** A tappable list row built from a card, so ledger/diary/expense rows all feel identical. */
    static MaterialCardView row(Context c, View.OnClickListener onClick) {
        MaterialCardView card = card(c);
        if (onClick != null) card.setOnClickListener(onClick);
        card.setClickable(onClick != null);
        card.setFocusable(onClick != null);
        return card;
    }

    static void rippleForeground(Context c, View v) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        v.setForeground(c.getDrawable(tv.resourceId));
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout rowLayout(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static MaterialButton primaryButton(Context c, String text) {
        MaterialButton b = new MaterialButton(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setCornerRadius(dp(c, 14));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(col(c, R.color.bk_primary)));
        b.setMinHeight(dp(c, 48));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 4), dp(c, 6), dp(c, 4), dp(c, 6));
        b.setLayoutParams(lp);
        return b;
    }

    static MaterialButton outlinedButton(Context c, String text) {
        MaterialButton b = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setCornerRadius(dp(c, 14));
        b.setMinHeight(dp(c, 44));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        b.setLayoutParams(lp);
        return b;
    }

    static MaterialButton dangerButton(Context c, String text) {
        MaterialButton b = outlinedButton(c, text);
        b.setTextColor(col(c, R.color.bk_due));
        b.setStrokeColor(android.content.res.ColorStateList.valueOf(col(c, R.color.bk_due)));
        return b;
    }

    static MaterialButton smallButton(Context c, String text) {
        MaterialButton b = outlinedButton(c, text);
        b.setTextSize(12);
        b.setMinHeight(dp(c, 36));
        b.setPadding(dp(c, 10), 0, dp(c, 10), 0);
        return b;
    }

    /** Text field wrapped in a floating-label Material box, replacing a bare EditText+hint. */
    static TextInputLayout inputBox(Context c, String hint) {
        TextInputLayout box = new TextInputLayout(c);
        box.setHint(hint);
        box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        box.setBoxCornerRadii(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 2), dp(c, 6), dp(c, 2), dp(c, 6));
        box.setLayoutParams(lp);
        TextInputEditText edit = new TextInputEditText(box.getContext());
        box.addView(edit);
        return box;
    }

    static EditText editOf(TextInputLayout box) { return box.getEditText(); }
    static String textOf(TextInputLayout box) { return box.getEditText() == null ? "" : box.getEditText().getText().toString().trim(); }

    /** Small rounded status pill, e.g. "OVERDUE" / "ADVANCE". */
    static TextView pill(Context c, String text, int bgColorRes, int textColorRes) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(11);
        v.setTypeface(null, Typeface.BOLD);
        v.setTextColor(col(c, textColorRes));
        v.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(col(c, bgColorRes));
        bg.setCornerRadius(dp(c, 20));
        v.setBackground(bg);
        return v;
    }

    static View spacer(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, heightDp)));
        return v;
    }

    /** Circular initials avatar so a customer row reads at a glance instead of just plain text. */
    static TextView avatar(Context c, String name) {
        TextView v = new TextView(c);
        String initial = (name == null || name.trim().isEmpty()) ? "?" : name.trim().substring(0, 1).toUpperCase();
        v.setText(initial);
        v.setTextColor(col(c, R.color.bk_white));
        v.setTypeface(null, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(16);
        int size = dp(c, 40);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(dp(c, 12));
        v.setLayoutParams(lp);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(col(c, R.color.bk_primary));
        v.setBackground(bg);
        return v;
    }
}
