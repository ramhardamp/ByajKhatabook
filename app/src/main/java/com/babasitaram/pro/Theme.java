package com.babasitaram.pro;

import android.content.Context;
import androidx.core.content.ContextCompat;

final class Theme {
    private Theme() {}
    static int color(Context c, int resId) { return ContextCompat.getColor(c, resId); }
}
