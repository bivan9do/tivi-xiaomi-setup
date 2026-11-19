package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sorts the grid into topic rows: each topic starts a new row with a title tile (not focusable), then its
 * apps, then empty cells to the end of the row. Apps no topic names go to "Khác" at the end.
 */
final class Groups {

    private static final String[] TITLES = {"Xem phim", "Đọc báo, web", "Xem bóng đá", "Gaming", "Cài đặt"};
    private static final List<List<String>> PACKAGES = Arrays.asList(
            Arrays.asList("org.smarttube.stable", "com.netflix.mediaclient", "com.chophim.tv"),
            Arrays.asList("com.coccoc.trinhduyet_tv", "vn.tintuc.vnexpress", "vn.tintuc.tuoitre",
                    "vn.tintuc.dantri", "vn.tintuc.thanhnien"),
            Arrays.asList("vn.vtv.vtvgotv", "com.viettel.tv360.tv", "com.linor.vn.getout",
                    "vn.quan.quantv", "com.nghenhin24h.vietplaytv", "vn.vtvcab.vtvprime.atv"),
            Arrays.asList("vn.trochoi.ransan", "vn.trochoi.luotray", "vn.trochoi.sahinh"),
            Arrays.asList("dev.imranr.obtainium", "com.liskovsoft.leankeyboard", "cm.aptoide.pt",
                    "com.vxm.store", "moe.shizuku.privileged.api"));
    private static final String OTHER = "Khác";

    private Groups() { }

    static List<AppAdapter.Entry> arrange(Context ctx, List<AppAdapter.Entry> apps, int columns) {
        List<AppAdapter.Entry> left = new ArrayList<>(apps);
        List<AppAdapter.Entry> out = new ArrayList<>();
        int n = 0;
        for (int g = 0; g < TITLES.length; g++) {
            List<AppAdapter.Entry> members = new ArrayList<>();
            for (String pkg : PACKAGES.get(g)) {
                for (AppAdapter.Entry e : left) {
                    if (e.kind == AppAdapter.Entry.APP && e.pkg.equals(pkg)) {
                        members.add(e);
                        break;
                    }
                }
            }
            if (members.isEmpty()) continue;
            left.removeAll(members);
            n = addRow(ctx, out, TITLES[g], members, columns, n);
        }
        List<AppAdapter.Entry> rest = new ArrayList<>();
        for (AppAdapter.Entry e : left) if (e.kind != AppAdapter.Entry.EMPTY) rest.add(e);
        if (!rest.isEmpty()) addRow(ctx, out, OTHER, rest, columns, n);
        return out;
    }

    private static int addRow(Context ctx, List<AppAdapter.Entry> out, String title, List<AppAdapter.Entry> members,
                              int columns, int n) {
        out.add(new AppAdapter.Entry(AppAdapter.Entry.HEADER, title,
                new ComponentName(ctx, "group-" + n), new TitleDrawable(title), null, null, 0));
        // A row that wraps keeps the title column empty, so each app sits under the one above it.
        int col = 1, pad = 0;
        for (AppAdapter.Entry m : members) {
            if (col == columns) {
                out.add(new AppAdapter.Entry(AppAdapter.Entry.EMPTY, "",
                        new ComponentName(ctx, "group-pad-" + n + "-" + pad++), null, null, null, 0));
                col = 1;
            }
            out.add(m);
            col++;
        }
        for (; col < columns; col++) {
            out.add(new AppAdapter.Entry(AppAdapter.Entry.EMPTY, "",
                    new ComponentName(ctx, "group-pad-" + n + "-" + pad++), null, null, null, 0));
        }
        return n + 1;
    }

    /** The title tile: the topic's name in large white type on a dark translucent pill. */
    static final class TitleDrawable extends Drawable {
        private final String text;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);

        TitleDrawable(String text) {
            this.text = text;
            fill.setColor(0xB0101418);
            ink.setColor(0xFFFFFFFF);
            ink.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            ink.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float r = b.height() * 0.18f;
            canvas.drawRoundRect(new RectF(b), r, r, fill);
            float size = b.height() * 0.26f;
            ink.setTextSize(size);
            while (ink.measureText(text) > b.width() * 0.88f && size > 8) {
                size -= 1;
                ink.setTextSize(size);
            }
            float y = b.exactCenterY() - (ink.descent() + ink.ascent()) / 2;
            canvas.drawText(text, b.exactCenterX(), y, ink);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
