package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** Shows the launcher in Vietnamese whatever language the TV's own menus use. */
final class VietnameseLocale {

    static final Locale VI = new Locale("vi", "VN");

    private VietnameseLocale() { }

    static Context wrap(Context base) {
        Locale.setDefault(VI);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(VI);
        return base.createConfigurationContext(config);
    }
}
