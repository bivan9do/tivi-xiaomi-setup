package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Today's buy and sell prices for 9999 gold (PNJ's price board), the US dollar (Vietcombank) and Bitcoin
 * (Binance BTC/USDT best bid and ask). Fetched off the main thread at most every 15 minutes; each part
 * that fails keeps its last value.
 */
final class Market {

    static final long FRESH_MS = 15 * 60_000;
    private static final int TIMEOUT_MS = 10_000;

    static volatile String gold = "";
    static volatile String usd = "";
    static volatile String btc = "";
    static volatile long fetchedAt;
    private static volatile boolean fetching;

    private Market() { }

    static boolean stale() {
        return System.currentTimeMillis() - fetchedAt > FRESH_MS;
    }

    /** Fetches on a background thread, then runs done on the caller's handler thread via post. */
    static void refresh(Runnable done, android.os.Handler main) {
        if (fetching || !stale()) return;
        fetching = true;
        new Thread(() -> {
            try {
                String g = goldPrice();
                if (g != null) gold = g;
            } catch (Exception ignored) { }
            try {
                String u = usdPrice();
                if (u != null) usd = u;
            } catch (Exception ignored) { }
            try {
                String b = btcPrice();
                if (b != null) btc = b;
            } catch (Exception ignored) { }
            fetchedAt = System.currentTimeMillis();
            fetching = false;
            main.post(done);
        }, "market").start();
    }

    /** 9999 gold (PNJ "Vàng nữ trang 999.9", in thousand dong per chỉ), the closest match to local shops' nhẫn 9999 price; shown in dong per chỉ. */
    private static String goldPrice() throws Exception {
        JSONObject root = new JSONObject(get("https://edge-api.pnj.io/ecom-frontend/v1/get-gold-price?zone=00"));
        JSONArray data = root.getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            JSONObject row = data.getJSONObject(i);
            if (!"24K".equals(row.optString("masp"))) continue;
            double buy = row.getDouble("giamua");
            double sell = row.getDouble("giaban");
            return whole(buy) + "k/" + whole(sell) + "k";
        }
        return null;
    }

    private static String usdPrice() throws Exception {
        String xml = get("https://portal.vietcombank.com.vn/Usercontrols/TVPortal.TyGia/pXML.aspx");
        Matcher m = Pattern.compile("CurrencyCode=\"USD\"[^>]*?Buy=\"([0-9.,]+)\"[^>]*?Sell=\"([0-9.,]+)\"").matcher(xml);
        if (!m.find()) return null;
        return whole(number(m.group(1))) + " / " + whole(number(m.group(2)));
    }

    private static String btcPrice() throws Exception {
        JSONObject t = new JSONObject(get("https://api.binance.com/api/v3/ticker/bookTicker?symbol=BTCUSDT"));
        return whole(t.getDouble("bidPrice")) + " / " + whole(t.getDouble("askPrice")) + " $";
    }

    private static double number(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }

    private static String whole(double v) {
        DecimalFormatSymbols vi = new DecimalFormatSymbols(VietnameseLocale.VI);
        return new DecimalFormat("#,##0", vi).format(Math.round(v));
    }

    private static String one(double v) {
        DecimalFormatSymbols vi = new DecimalFormatSymbols(VietnameseLocale.VI);
        return new DecimalFormat("#,##0.0", vi).format(v);
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11)");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }
}
