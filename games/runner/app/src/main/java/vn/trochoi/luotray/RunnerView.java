package vn.trochoi.luotray;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * An endless runner on three rail lanes, drawn in simple perspective. Trains block a lane, low barriers are
 * jumped, overhead bars are rolled under, and coins add to the score. The track speeds up over time.
 */
final class RunnerView extends View {

    private enum State { READY, PLAYING, PAUSED, OVER }

    private static final int BARRIER = 0, BAR = 1, TRAIN = 2, COIN = 3;
    private static final float FAR = 70f, CAM = 6f, TRAIN_LEN = 8f;
    private static final float START_SPEED = 13f, MAX_SPEED = 30f, SPEED_UP = 0.28f;
    private static final float JUMP_V = 8.8f, GRAVITY = 26f, ROLL_S = 0.7f;
    private static final float HEIGHT_PX = 0.62f; // one unit of height relative to one lane's width

    private static final class Thing {
        final int type, lane;
        final float at; // distance along the track
        boolean gone;
        Thing(int type, int lane, float at) { this.type = type; this.lane = lane; this.at = at; }
    }

    private final Random random = new Random();
    private final SharedPreferences prefs;
    private final List<Thing> things = new ArrayList<>();

    private State state = State.READY;
    private int lane = 1;
    private float laneX = 0f; // drawn position, eases towards the lane
    private float distance, speed, nextRow, jumpY, jumpV, rollLeft, runPhase, crashFlash, playTime;
    private int coins, best;
    private boolean newBest;
    private long lastNanos;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sky = new Paint();
    private final Path path = new Path();
    private final RectF r = new RectF();
    private int skyH = -1;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (state != State.PLAYING) return;
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            update(dt);
            invalidate();
            if (state == State.PLAYING) postOnAnimation(this);
        }
    };

    RunnerView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        prefs = context.getSharedPreferences("luotray", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        text.setColor(0xFFFFFFFF);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setShadowLayer(6f, 0f, 2f, 0xAA000000);
        reset();
    }

    private void reset() {
        things.clear();
        lane = 1;
        laneX = 0f;
        distance = 0f;
        speed = START_SPEED;
        nextRow = 60f;
        jumpY = jumpV = rollLeft = runPhase = crashFlash = playTime = 0f;
        coins = 0;
        newBest = false;
        spawn();
    }

    private int score() { return (int) (distance / 2f) + coins * 5; }

    // ---- game logic ----

    private void spawn() {
        while (nextRow < distance + FAR) {
            int[] lanes = {0, 1, 2};
            for (int i = 2; i > 0; i--) { int j = random.nextInt(i + 1); int t = lanes[i]; lanes[i] = lanes[j]; lanes[j] = t; }
            int blocked = 1 + (random.nextFloat() < 0.45f ? 1 : 0);
            for (int i = 0; i < 3; i++) {
                if (i < blocked) {
                    float f = random.nextFloat();
                    int type = f < 0.4f ? TRAIN : f < 0.7f ? BARRIER : BAR;
                    things.add(new Thing(type, lanes[i], nextRow));
                } else if (random.nextFloat() < 0.55f) {
                    for (int k = 0; k < 5; k++) things.add(new Thing(COIN, lanes[i], nextRow + k * 1.6f));
                }
            }
            nextRow += 13f + random.nextFloat() * 7f + speed * 0.15f;
        }
    }

    private void update(float dt) {
        playTime += dt;
        speed = Math.min(MAX_SPEED, START_SPEED + playTime * SPEED_UP);
        distance += speed * dt;
        runPhase += dt * speed * 0.9f;
        laneX += ((lane - 1) - laneX) * Math.min(1f, dt * 16f);

        if (jumpY > 0f || jumpV > 0f) {
            jumpV -= GRAVITY * dt;
            jumpY += jumpV * dt;
            if (jumpY <= 0f) { jumpY = 0f; jumpV = 0f; }
        }
        if (rollLeft > 0f) rollLeft -= dt;

        for (Iterator<Thing> it = things.iterator(); it.hasNext(); ) {
            Thing t = it.next();
            float z = t.at - distance;
            float len = t.type == TRAIN ? TRAIN_LEN : 0.3f;
            if (z + len < -4f) { it.remove(); continue; }
            if (t.gone || t.lane != lane) continue;
            boolean overlaps = z < 0.35f && z + len > -0.35f;
            if (!overlaps) continue;
            switch (t.type) {
                case COIN: t.gone = true; coins++; break;
                case BARRIER: if (jumpY < 0.5f) crash(); break;
                case BAR: if (rollLeft <= 0f) crash(); break;
                default: crash(); break;
            }
            if (state != State.PLAYING) return;
        }
        spawn();
    }

    private void crash() {
        state = State.OVER;
        crashFlash = 1f;
        int s = score();
        if (s > best) {
            best = s;
            newBest = true;
            prefs.edit().putInt("best", best).apply();
        }
    }

    private void start() {
        if (state == State.OVER || state == State.READY) reset();
        state = State.PLAYING;
        lastNanos = System.nanoTime();
        removeCallbacks(frame);
        postOnAnimation(frame);
        invalidate();
    }

    void pauseGame() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            removeCallbacks(frame);
            invalidate();
        }
    }

    boolean backPressed() {
        if (state == State.PLAYING) { pauseGame(); return true; }
        return false;
    }

    boolean key(int code) {
        boolean action = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_SPACE
                || code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        if (action) {
            if (state == State.PLAYING) pauseGame(); else start();
            return true;
        }
        boolean move = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        if (!move) return false;
        if (state == State.PAUSED) start();
        if (state != State.PLAYING) return true;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: if (lane > 0) lane--; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (lane < 2) lane++; break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (jumpY == 0f) { jumpV = JUMP_V; rollLeft = 0f; }
                break;
            default:
                if (jumpY > 0f) jumpV = -JUMP_V * 1.4f; // drop fast, then roll
                rollLeft = ROLL_S;
                break;
        }
        return true;
    }

    // ---- drawing ----

    private float w, h, horizon, baseY, laneW;

    private float s(float z) { return CAM / (Math.max(z, -CAM + 0.6f) + CAM); }
    private float gy(float z) { return horizon + (baseY - horizon) * s(z); }
    private float lx(float x, float z) { return w / 2f + x * laneW * s(z); }
    private float up(float units, float z) { return units * laneW * HEIGHT_PX * s(z); }

    @Override
    protected void onDraw(Canvas c) {
        w = getWidth();
        h = getHeight();
        horizon = h * 0.30f;
        baseY = h * 0.92f;
        laneW = w * 0.2f;

        drawWorld(c);

        // Things far to near; the player goes in once everything in front of it is drawn behind.
        things.sort((a, b) -> Float.compare(farEdge(b), farEdge(a)));
        boolean playerDrawn = false;
        for (Thing t : things) {
            if (t.gone) continue;
            float z = t.at - distance;
            if (z > FAR) continue;
            if (!playerDrawn && farEdge(t) - distance < 0.2f && t.type != TRAIN) {
                drawPlayer(c);
                playerDrawn = true;
            }
            drawThing(c, t, z);
        }
        if (!playerDrawn) drawPlayer(c);

        drawHud(c);
        if (state != State.PLAYING) drawOverlay(c);
    }

    private float farEdge(Thing t) { return t.at + (t.type == TRAIN ? TRAIN_LEN : 0.3f); }

    private void drawWorld(Canvas c) {
        if (skyH != (int) h) {
            skyH = (int) h;
            sky.setShader(new LinearGradient(0, 0, 0, horizon, 0xFF5BB8F0, 0xFFCFEAF7, Shader.TileMode.CLAMP));
        }
        c.drawRect(0, 0, w, horizon + 1, sky);
        // Distant city blocks.
        p.setColor(0xFF9FB7C6);
        float[] bx = {0.02f, 0.09f, 0.15f, 0.24f, 0.31f, 0.62f, 0.7f, 0.77f, 0.85f, 0.92f};
        float[] bh = {0.10f, 0.16f, 0.08f, 0.13f, 0.06f, 0.07f, 0.15f, 0.11f, 0.17f, 0.09f};
        for (int i = 0; i < bx.length; i++) c.drawRect(w * bx[i], horizon - h * bh[i], w * (bx[i] + 0.065f), horizon, p);
        // Ground and gravel bed.
        p.setColor(0xFF7A9A5A);
        c.drawRect(0, horizon, w, h, p);
        path.reset();
        path.moveTo(lx(-1.75f, FAR), gy(FAR));
        path.lineTo(lx(1.75f, FAR), gy(FAR));
        path.lineTo(lx(1.75f, -2.5f), gy(-2.5f));
        path.lineTo(lx(-1.75f, -2.5f), gy(-2.5f));
        path.close();
        p.setColor(0xFF8C8073);
        c.drawPath(path, p);
        // Sleepers, scrolling with the distance.
        p.setColor(0xFF5E4B3C);
        float off = distance % 2f;
        for (float z = -2f - off; z < FAR; z += 2f) {
            float y1 = gy(z), y2 = gy(z + 0.45f);
            if (y1 - y2 < 0.6f) { y2 = y1 - 0.6f; }
            for (int l = -1; l <= 1; l++) {
                path.reset();
                path.moveTo(lx(l - 0.42f, z), y1);
                path.lineTo(lx(l + 0.42f, z), y1);
                path.lineTo(lx(l + 0.42f, z + 0.45f), y2);
                path.lineTo(lx(l - 0.42f, z + 0.45f), y2);
                path.close();
                c.drawPath(path, p);
            }
        }
        // Rails: two per lane.
        p.setColor(0xFFC9CED3);
        for (int l = -1; l <= 1; l++) {
            for (float side : new float[]{-0.27f, 0.27f}) {
                path.reset();
                path.moveTo(lx(l + side - 0.025f, FAR), gy(FAR));
                path.lineTo(lx(l + side + 0.025f, FAR), gy(FAR));
                path.lineTo(lx(l + side + 0.025f, -2.5f), gy(-2.5f));
                path.lineTo(lx(l + side - 0.025f, -2.5f), gy(-2.5f));
                path.close();
                c.drawPath(path, p);
            }
        }
    }

    private void drawThing(Canvas c, Thing t, float z) {
        float x = t.lane - 1;
        switch (t.type) {
            case COIN: {
                float cx = lx(x, z), cy = gy(z) - up(0.55f, z), rad = laneW * 0.11f * s(z);
                float squash = Math.abs((float) Math.cos(runPhase * 0.35f + t.at));
                p.setColor(0xFFE0A100);
                r.set(cx - rad * Math.max(0.25f, squash), cy - rad, cx + rad * Math.max(0.25f, squash), cy + rad);
                c.drawOval(r, p);
                p.setColor(0xFFFFD84A);
                r.inset(rad * 0.18f * Math.max(0.25f, squash), rad * 0.18f);
                c.drawOval(r, p);
                break;
            }
            case BARRIER: {
                float y = gy(z), half = 0.42f * laneW * s(z), top = y - up(0.55f, z);
                float cx = lx(x, z);
                p.setColor(0xFF6B6B6B);
                c.drawRect(cx - half * 0.85f, top, cx - half * 0.7f, y, p);
                c.drawRect(cx + half * 0.7f, top, cx + half * 0.85f, y, p);
                float bt = top, bb = top + (y - top) * 0.45f;
                p.setColor(0xFFFFFFFF);
                c.drawRect(cx - half, bt, cx + half, bb, p);
                p.setColor(0xFFE53935);
                float stripe = half / 3f;
                for (int i = 0; i < 6; i += 2) c.drawRect(cx - half + i * stripe, bt, cx - half + (i + 1) * stripe, bb, p);
                break;
            }
            case BAR: {
                float y = gy(z), half = 0.46f * laneW * s(z), cx = lx(x, z);
                float barBot = y - up(0.85f, z), barTop = y - up(1.2f, z);
                p.setColor(0xFF3D4A57);
                c.drawRect(cx - half, barTop, cx - half * 0.88f, y, p);
                c.drawRect(cx + half * 0.88f, barTop, cx + half, y, p);
                p.setColor(0xFFFFC107);
                c.drawRect(cx - half, barTop, cx + half, barBot, p);
                p.setColor(0xFF212121);
                float stripe = half / 4f;
                for (int i = 1; i < 8; i += 2) c.drawRect(cx - half + i * stripe, barTop, cx - half + (i + 0.5f) * stripe, barBot, p);
                break;
            }
            default: drawTrain(c, x, z, t.lane); break;
        }
    }

    private void drawTrain(Canvas c, float x, float z, int laneIndex) {
        float zn = Math.max(z, -CAM + 1.2f), zf = z + TRAIN_LEN;
        if (zf < zn) return;
        float hgt = 1.9f, half = 0.44f;
        int body = laneIndex == 1 ? 0xFF2E7D9A : laneIndex == 0 ? 0xFF8E3B46 : 0xFF4E6E3A;
        // Side wall facing the centre so the length reads.
        float sideX = x < 0 ? x + half : x - half;
        if (x != 0) {
            path.reset();
            path.moveTo(lx(sideX, zn), gy(zn));
            path.lineTo(lx(sideX, zf), gy(zf));
            path.lineTo(lx(sideX, zf), gy(zf) - up(hgt, zf));
            path.lineTo(lx(sideX, zn), gy(zn) - up(hgt, zn));
            path.close();
            p.setColor(darker(body, 0.75f));
            c.drawPath(path, p);
        }
        // Roof.
        path.reset();
        path.moveTo(lx(x - half, zn), gy(zn) - up(hgt, zn));
        path.lineTo(lx(x + half, zn), gy(zn) - up(hgt, zn));
        path.lineTo(lx(x + half, zf), gy(zf) - up(hgt, zf));
        path.lineTo(lx(x - half, zf), gy(zf) - up(hgt, zf));
        path.close();
        p.setColor(0xFFB0BEC5);
        c.drawPath(path, p);
        // Front face.
        float l = lx(x - half, zn), rr = lx(x + half, zn), b = gy(zn), t = b - up(hgt, zn);
        p.setColor(body);
        r.set(l, t, rr, b);
        float round = (rr - l) * 0.08f;
        c.drawRoundRect(r, round, round, p);
        p.setColor(0xFF1C2833);
        r.set(l + (rr - l) * 0.14f, t + (b - t) * 0.12f, rr - (rr - l) * 0.14f, t + (b - t) * 0.45f);
        c.drawRoundRect(r, round, round, p);
        p.setColor(0xFFFFF59D);
        float lampR = (rr - l) * 0.06f;
        c.drawCircle(l + (rr - l) * 0.22f, t + (b - t) * 0.72f, lampR, p);
        c.drawCircle(rr - (rr - l) * 0.22f, t + (b - t) * 0.72f, lampR, p);
        p.setColor(0xFFF5F5F5);
        c.drawRect(l, t + (b - t) * 0.55f, rr, t + (b - t) * 0.6f, p);
    }

    private static int darker(int color, float f) {
        int a = color >>> 24, rr = (int) (((color >> 16) & 0xFF) * f), g = (int) (((color >> 8) & 0xFF) * f), b = (int) ((color & 0xFF) * f);
        return (a << 24) | (rr << 16) | (g << 8) | b;
    }

    /** An original runner: green cap, orange jacket, dark trousers. */
    private void drawPlayer(Canvas c) {
        float z = 0f, unit = up(1f, z);
        float cx = lx(laneX, z), ground = gy(z), feet = ground - up(jumpY, z);
        // Shadow on the track.
        p.setColor(0x55000000);
        float sh = laneW * 0.22f * (1f - Math.min(0.6f, jumpY * 0.3f));
        r.set(cx - sh, ground - unit * 0.06f, cx + sh, ground + unit * 0.06f);
        c.drawOval(r, p);
        if (state == State.OVER && crashFlash > 0f) p.setAlpha(255);

        if (rollLeft > 0f) {
            float rad = unit * 0.38f, cy = feet - rad;
            p.setColor(0xFFF57C00);
            c.drawCircle(cx, cy, rad, p);
            p.setColor(0xFF2E7D32);
            c.drawArc(cx - rad, cy - rad, cx + rad, cy + rad, runPhase * 60f, 120f, true, p);
            p.setColor(0xFFFFCC99);
            c.drawCircle(cx + rad * 0.35f * (float) Math.cos(runPhase), cy + rad * 0.35f * (float) Math.sin(runPhase), rad * 0.3f, p);
            return;
        }

        float swing = jumpY > 0f ? 0.5f : (float) Math.sin(runPhase);
        float hip = feet - unit * 0.48f, shoulder = feet - unit * 0.9f;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);
        // Legs.
        p.setStrokeWidth(unit * 0.14f);
        p.setColor(0xFF263238);
        c.drawLine(cx - unit * 0.08f, hip, cx - unit * 0.08f + swing * unit * 0.12f, feet - unit * 0.04f - Math.max(0, swing) * unit * 0.12f, p);
        c.drawLine(cx + unit * 0.08f, hip, cx + unit * 0.08f - swing * unit * 0.12f, feet - unit * 0.04f - Math.max(0, -swing) * unit * 0.12f, p);
        // Arms.
        p.setStrokeWidth(unit * 0.1f);
        p.setColor(0xFFE65100);
        c.drawLine(cx - unit * 0.2f, shoulder + unit * 0.05f, cx - unit * 0.3f, shoulder + unit * 0.3f - swing * unit * 0.12f, p);
        c.drawLine(cx + unit * 0.2f, shoulder + unit * 0.05f, cx + unit * 0.3f, shoulder + unit * 0.3f + swing * unit * 0.12f, p);
        p.setStyle(Paint.Style.FILL);
        // Body.
        p.setColor(0xFFF57C00);
        r.set(cx - unit * 0.22f, shoulder, cx + unit * 0.22f, hip + unit * 0.05f);
        c.drawRoundRect(r, unit * 0.1f, unit * 0.1f, p);
        p.setColor(0xFF2E7D32);
        c.drawRect(cx - unit * 0.22f, hip - unit * 0.06f, cx + unit * 0.22f, hip + unit * 0.02f, p);
        // Head (seen from behind) and cap.
        float headR = unit * 0.15f, headY = shoulder - headR * 0.95f;
        p.setColor(0xFF4E342E);
        c.drawCircle(cx, headY, headR, p);
        p.setColor(0xFF2E7D32);
        c.drawArc(cx - headR * 1.05f, headY - headR * 1.15f, cx + headR * 1.05f, headY + headR * 0.6f, 180f, 180f, true, p);
        // Backpack.
        p.setColor(0xFF1565C0);
        r.set(cx - unit * 0.15f, shoulder + unit * 0.06f, cx + unit * 0.15f, shoulder + unit * 0.3f);
        c.drawRoundRect(r, unit * 0.06f, unit * 0.06f, p);
    }

    private void drawHud(Canvas c) {
        text.setTextSize(h * 0.045f);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText("Điểm: " + score(), w * 0.03f, h * 0.075f, text);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Xu: " + coins + "     Kỷ lục: " + best, w * 0.97f, h * 0.075f, text);
    }

    private void drawOverlay(Canvas c) {
        p.setColor(state == State.OVER ? 0xB0400000 : 0xB0000000);
        c.drawRect(0, 0, w, h, p);
        text.setTextAlign(Paint.Align.CENTER);
        String title, hint, help = "←  →  đổi làn   ·   ↑  nhảy   ·   ↓  lăn qua thanh chắn";
        switch (state) {
            case PAUSED: title = "Tạm dừng"; hint = "Bấm OK để chạy tiếp  ·  Back để thoát"; break;
            case OVER:
                title = newBest ? "Kỷ lục mới: " + score() + " điểm!" : "Đâm rồi! " + score() + " điểm";
                hint = "Bấm OK để chơi lại  ·  Back để thoát";
                break;
            default: title = "Lướt đường ray"; hint = "Bấm OK để bắt đầu"; break;
        }
        text.setTextSize(h * 0.09f);
        c.drawText(title, w / 2f, h * 0.42f, text);
        text.setTextSize(h * 0.045f);
        c.drawText(hint, w / 2f, h * 0.53f, text);
        text.setTextSize(h * 0.036f);
        c.drawText(help, w / 2f, h * 0.62f, text);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }
}
