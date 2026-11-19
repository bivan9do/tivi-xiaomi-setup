package vn.trochoi.sahinh;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A top-down practice yard with the eleven class B exercises, a manual-gearbox car and test-style scoring:
 * 100 points, deductions per fault, failed below 80 or on a disqualifying fault.
 *
 * World units are metres; x grows east, y grows south, and the route mostly heads north (towards -y).
 * The car is tracked by the centre of its rear axle; heading 0 points north and grows clockwise.
 */
final class DrivingView extends View {

    private enum State { READY, PLAYING, PAUSED, OVER }

    // ---- the car ----
    private static final float WHEELBASE = 2.6f, REAR_OH = 0.85f, FRONT = 3.55f, HALF_W = 0.9f, TRACK = 0.78f;
    private static final float CENTRE = 1.35f; // body centre ahead of the rear axle
    private static final float MAX_STEER = (float) Math.toRadians(35), STEER_RATE = (float) Math.toRadians(55),
            CENTRE_RATE = (float) Math.toRadians(14);
    private static final int N = 0, REV = 4;
    private static final String[] GEAR_NAME = {"N", "1", "2", "3", "R"};
    private static final float[] VMAX = {0, 4.2f, 7.5f, 12f, 2.8f};
    private static final float[] IDLE = {0, 1.4f, 2.5f, 3.9f, 1.1f};
    private static final float[] GAS = {0, 1.6f, 1.3f, 1.0f, 1.0f};
    private static final float[] LUG = {0, 0, 1.4f, 3.0f, 0};
    private static final float GRADE_G = 0.98f; // 10% slope

    // ---- the yard ----
    private interface Area {
        boolean in(float x, float y);
        void fill(Canvas c, Paint p, float grow, Path scratch);
    }

    private static final class Box implements Area {
        final float x0, y0, x1, y1;
        Box(float x0, float y0, float x1, float y1) { this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; }
        @Override public boolean in(float x, float y) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }
        @Override public void fill(Canvas c, Paint p, float g, Path s) { c.drawRect(x0 - g, y0 - g, x1 + g, y1 + g, p); }
    }

    private static final class Bend implements Area {
        final float cx, cy, rIn, rOut, a0, a1;
        final RectF oval = new RectF();
        Bend(float cx, float cy, float rIn, float rOut, float a0, float a1) {
            this.cx = cx; this.cy = cy; this.rIn = rIn; this.rOut = rOut; this.a0 = a0; this.a1 = a1;
        }
        @Override public boolean in(float x, float y) {
            float dx = x - cx, dy = y - cy, d = (float) Math.hypot(dx, dy);
            if (d < rIn || d > rOut) return false;
            float a = (float) Math.toDegrees(Math.atan2(dy, dx));
            return a >= a0 - 0.01f && a <= a1 + 0.01f;
        }
        @Override public void fill(Canvas c, Paint p, float g, Path s) {
            float da = (float) Math.toDegrees(g / ((rIn + rOut) / 2f));
            float start = a0 - da, sweep = a1 - a0 + 2 * da, ro = rOut + g, ri = Math.max(0.1f, rIn - g);
            s.reset();
            oval.set(cx - ro, cy - ro, cx + ro, cy + ro);
            s.arcTo(oval, start, sweep, true);
            oval.set(cx - ri, cy - ri, cx + ri, cy + ri);
            s.arcTo(oval, start + sweep, -sweep, false);
            s.close();
            c.drawPath(s, p);
        }
    }

    private static final Box GARAGE = new Box(4f, -46.3f, 9.6f, -43.7f);
    private static final Box BAY = new Box(4f, -163f, 6.6f, -156f);
    private static final Box STUB = new Box(6f, 22f, 25f, 27f);
    private final List<Area> road = Arrays.asList(
            new Box(0, 86, 5, 212), new Box(0, 86, 18, 91), new Box(13, -10, 18, 91), STUB,
            new Bend(8.5f, -10, 4.5f, 9.5f, -90, 0), new Bend(8.5f, -24, 4.5f, 9.5f, 90, 180),
            new Box(-1, -200, 4, -24), new Box(-3, -56, 4, -34), GARAGE, new Box(-2, -176, 4, -142), BAY);

    private static final float START_LINE = 192f, END_LINE = -188f;
    private static final float VET_Y0 = 118f, VET_Y1 = 124f, VET_X0 = 3.45f, VET_X1 = 4.05f;
    private static final float SHIFT_A = -88f, SHIFT_B = -113f, SHIFT_C = -138f;
    private static final float LIGHT_CYCLE = 43f, GREEN = 25f, YELLOW = 3f;

    private static final String[] EXAM = {
            "Xuất phát",
            "Dừng xe nhường đường cho người đi bộ",
            "Dừng và khởi hành xe ngang dốc",
            "Qua vệt bánh xe và đường vòng vuông góc",
            "Qua ngã tư có tín hiệu điều khiển giao thông",
            "Qua đường vòng quanh co",
            "Ghép xe dọc vào nơi đỗ",
            "Tạm dừng ở chỗ có đường sắt chạy qua",
            "Thay đổi số trên đường bằng",
            "Ghép xe ngang vào nơi đỗ",
            "Kết thúc",
    };
    private static final String[] HINT = {
            "Thắt dây an toàn, bật xi nhan trái, vào số 1, nhả phanh tay rồi đi. Qua vạch thì tắt xi nhan.",
            "Dừng xe sao cho đầu xe cách vạch dừng không quá 50 cm, rồi đi tiếp.",
            "Dừng sát vạch trên dốc (≤ 50 cm), khởi hành trong 30 giây, không để xe tụt quá 50 cm.",
            "Cho hai bánh bên phải (chấm vàng) đi qua vệt vàng, rồi qua hai khúc cua vuông không chạm vạch.",
            "Đèn đỏ thì dừng trước vạch; đèn xanh thì đi qua trong vòng 20 giây.",
            "Đi qua đường chữ S, không để bánh xe chạm vạch.",
            "Đi quá nơi đỗ bên phải rồi lùi xe vào, dừng hẳn trong ô. Sau đó tiến ra đi tiếp.",
            "Dừng trước vạch đường sắt, đầu xe cách vạch không quá 50 cm, rồi đi tiếp.",
            "25 m đầu: lên số 2 và vượt 20 km/h. 25 m sau: về số 1 và dưới 20 km/h.",
            "Đi quá ô đỗ bên phải rồi lùi ghép xe ngang vào ô, dừng hẳn trong ô, rồi đi ra.",
            "Bật xi nhan phải rồi đi qua vạch kết thúc.",
    };
    private static final int[] TIMED = {3, 5, 6, 9}; // exercises with a two-minute limit

    /** A stop line: where the front bumper must come to rest within 50 cm before it. */
    private static final class StopLine {
        final float y;
        final boolean hill, light;
        boolean ok, early, over, touched, crossed, late;
        float okRearY, okTime;
        StopLine(float y, boolean hill, boolean light) { this.y = y; this.hill = hill; this.light = light; }
    }

    private final StopLine[] stops = new StopLine[11];

    // ---- menu ----
    private static final int M_G1 = 0, M_G2 = 1, M_G3 = 2, M_N = 3, M_R = 4, M_LEFT = 5, M_RIGHT = 6, M_OFF = 7,
            M_HAND = 8, M_HAZ = 9, M_BELT = 10, M_ENGINE = 11, M_COUNT = 12;

    // ---- state ----
    private final SharedPreferences prefs;
    private State state = State.READY;
    private boolean menuOpen;
    private int menuSel = M_BELT;
    private boolean upHeld, downHeld, leftHeld, rightHeld;

    private float rx, ry, ang, v, steer, rpm;
    private int gear;
    private boolean engineOn, handbrake, belt, hazard;
    private int signal; // -1 left, 0 off, 1 right

    private int exam, score;
    private float time, examTime, lightT, stillT, lugT, overT, touchFree, blinkClock;
    private boolean stillHandled, touching, overPenalised, timePenalised, moved, startCrossed, lateStart;
    private boolean vetPenalised, vetFrontSeen, vetRearSeen, parked, shiftUpOk, shiftUpDone, shiftDownDone;
    private float prevFrontWheelY, prevRearWheelY;
    private boolean emergDone, emergActive, emergHandled, emergPenalised, hazardPenalised;
    private float emergT, alarmT;
    private boolean passed;
    private String failReason;
    private final List<String> log = new ArrayList<>();
    private String toast;
    private float toastT;
    private int best, attempts, passes;
    private long lastNanos;

    // ---- drawing ----
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slopeUp = new Paint(Paint.ANTI_ALIAS_FLAG), slopeDown = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final float[] trees;
    private float w, h, k;

    private ToneGenerator tone, tick;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (state != State.PLAYING || menuOpen) return;
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            update(dt);
            invalidate();
            if (state == State.PLAYING && !menuOpen) postOnAnimation(this);
        }
    };

    DrivingView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        prefs = context.getSharedPreferences("sahinh", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        attempts = prefs.getInt("attempts", 0);
        passes = prefs.getInt("passes", 0);
        text.setColor(0xFFFFFFFF);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        slopeUp.setShader(new LinearGradient(0, 161, 0, 151, 0xFF4A4E54, 0xFF7A7F87, Shader.TileMode.CLAMP));
        slopeDown.setShader(new LinearGradient(0, 146, 0, 139, 0xFF7A7F87, 0xFF4A4E54, Shader.TileMode.CLAMP));
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
            tick = new ToneGenerator(AudioManager.STREAM_MUSIC, 25);
        } catch (RuntimeException e) {
            tone = null;
            tick = null;
        }
        // Trees on the grass, kept clear of the road.
        Random rnd = new Random(7);
        List<Float> t = new ArrayList<>();
        for (int i = 0; i < 700; i++) {
            float x = -40 + rnd.nextFloat() * 85, y = -215 + rnd.nextFloat() * 440;
            boolean clear = true;
            for (int a = 0; a < 8 && clear; a++) {
                double an = a * Math.PI / 4;
                if (onRoad(x + 3.5f * (float) Math.cos(an), y + 3.5f * (float) Math.sin(an))) clear = false;
            }
            if (clear && !onRoad(x, y) && Math.abs(x - 2) > 0.5f) {
                t.add(x); t.add(y); t.add(1.1f + rnd.nextFloat() * 1.1f);
            }
        }
        trees = new float[t.size()];
        for (int i = 0; i < trees.length; i++) trees[i] = t.get(i);
        reset();
    }

    private void reset() {
        rx = 2.5f;
        ry = 198f;
        ang = 0;
        v = steer = 0;
        rpm = 800;
        gear = N;
        engineOn = true;
        handbrake = true;
        belt = false;
        hazard = false;
        signal = 0;
        exam = 0;
        score = 100;
        time = examTime = stillT = lugT = overT = touchFree = blinkClock = 0;
        lightT = new Random().nextFloat() * LIGHT_CYCLE;
        stillHandled = touching = overPenalised = timePenalised = moved = startCrossed = lateStart = false;
        vetPenalised = vetFrontSeen = vetRearSeen = parked = shiftUpOk = shiftUpDone = shiftDownDone = false;
        emergDone = emergActive = emergHandled = emergPenalised = hazardPenalised = false;
        emergT = alarmT = 0;
        passed = false;
        failReason = null;
        log.clear();
        toast = null;
        menuSel = M_BELT;
        Arrays.fill(stops, null);
        stops[1] = new StopLine(176f, false, false);
        stops[2] = new StopLine(152f, true, false);
        stops[4] = new StopLine(28.5f, false, true);
        stops[7] = new StopLine(-70f, false, false);
        prevFrontWheelY = prevRearWheelY = 999f;
        upHeld = downHeld = leftHeld = rightHeld = false;
    }

    // ---- geometry helpers ----

    private boolean onRoad(float x, float y) {
        for (Area a : road) if (a.in(x, y)) return true;
        return false;
    }

    private float ptX(float f, float l) { return rx + (float) Math.sin(ang) * f + (float) Math.cos(ang) * l; }
    private float ptY(float f, float l) { return ry - (float) Math.cos(ang) * f + (float) Math.sin(ang) * l; }

    private static float toward(float v, float t, float step) {
        return v < t ? Math.min(t, v + step) : Math.max(t, v - step);
    }

    private boolean wheelOnLine(float x, float y) {
        float e = 0.12f;
        return !onRoad(x - e, y) || !onRoad(x + e, y) || !onRoad(x, y - e) || !onRoad(x, y + e);
    }

    private boolean carInside(Box b) {
        float[] fs = {-REAR_OH, FRONT}, ls = {-HALF_W, HALF_W};
        for (float f : fs) for (float l : ls) if (!b.in(ptX(f, l), ptY(f, l))) return false;
        return true;
    }

    private static float angleDiff(float a, float b) {
        float d = (float) ((a - b) % (2 * Math.PI));
        if (d > Math.PI) d -= 2 * Math.PI;
        if (d < -Math.PI) d += 2 * Math.PI;
        return Math.abs(d);
    }

    private float lightPhase() { return lightT % LIGHT_CYCLE; }
    private int lightColour() {
        float ph = lightPhase();
        return ph < GREEN ? 0 : ph < GREEN + YELLOW ? 1 : 2;
    }

    // ---- simulation ----

    private void update(float dt) {
        time += dt;
        examTime += dt;
        lightT += dt;
        if (toastT > 0) toastT -= dt;

        // Steering wheel: held keys turn it, and it eases back to centre when rolling forward.
        if (leftHeld && !rightHeld) steer -= STEER_RATE * dt;
        else if (rightHeld && !leftHeld) steer += STEER_RATE * dt;
        else if (v > 0.3f) steer = toward(steer, 0, CENTRE_RATE * Math.min(1f, v / 2f) * dt);
        steer = Math.max(-MAX_STEER, Math.min(MAX_STEER, steer));

        // Longitudinal: slope, brakes (the brake also stands for the clutch), engine, rolling drag.
        float cx = ptX(CENTRE, 0), cy = ptY(CENTRE, 0);
        float cosA = (float) Math.cos(ang);
        if (cx >= 0 && cx <= 5) {
            if (cy >= 151 && cy <= 161) v -= GRADE_G * cosA * dt;
            else if (cy >= 139 && cy <= 146) v += GRADE_G * cosA * dt;
        }
        boolean braking = downHeld;
        if (handbrake || braking) {
            v = toward(v, 0, (handbrake ? 9f : 5f) * dt);
        } else if (engineOn && gear != N) {
            float dir = gear == REV ? -1 : 1;
            float target = dir * (upHeld ? VMAX[gear] : IDLE[gear]);
            boolean below = dir * v < dir * target;
            float rate = below ? (upHeld ? GAS[gear] : 0.6f) : 1.0f;
            v = toward(v, target, rate * dt);
        } else {
            v = toward(v, 0, 0.4f * dt);
        }

        // Stalls: rolling against the gear, or a high gear at too low a speed, with the clutch out.
        if (engineOn && gear != N && !braking && !handbrake) {
            float dir = gear == REV ? -1 : 1;
            if (dir * v < -0.6f) stall();
            else if (LUG[gear] > 0 && Math.abs(v) < LUG[gear]) {
                lugT += dt;
                if (lugT > 0.8f) stall();
            } else lugT = 0;
        } else lugT = 0;

        // Move with a bicycle model about the rear axle.
        ang += v / WHEELBASE * (float) Math.tan(steer) * dt;
        rx += (float) Math.sin(ang) * v * dt;
        ry -= (float) Math.cos(ang) * v * dt;

        // Engine speed.
        float target;
        if (!engineOn) target = 0;
        else if (gear == N || braking) target = 800 + (upHeld ? 3600 : 0);
        else target = 800 + Math.abs(v) / VMAX[gear] * 3300 + (upHeld ? 400 : 0);
        rpm = toward(rpm, target, 5000 * dt);
        if (rpm > 4000) {
            overT += dt;
            if (overT > 0.4f && !overPenalised) {
                overPenalised = true;
                penalise(5, "Tốc độ động cơ quá 4.000 vòng/phút");
            }
        } else overT = 0;

        // Blinker tick.
        boolean blinking = signal != 0 || hazard;
        float before = blinkClock;
        blinkClock += dt;
        if (blinking && (int) (before * 3) != (int) (blinkClock * 3) && tick != null) {
            tick.startTone(ToneGenerator.TONE_PROP_BEEP, 15);
        }

        boolean stopEvent = false;
        if (Math.abs(v) < 0.05f) {
            stillT += dt;
            if (stillT >= 0.3f && !stillHandled) { stillHandled = true; stopEvent = true; }
        } else if (Math.abs(v) > 0.15f) {
            stillT = 0;
            stillHandled = false;
        }

        if (state != State.PLAYING) return;
        checkBoundaries();
        if (state != State.PLAYING) return;
        runExam(dt, stopEvent);
        if (state != State.PLAYING) return;
        runEmergency(dt);
    }

    private void stall() {
        engineOn = false;
        lugT = 0;
        rpm = 0;
        penalise(5, "Xe bị chết máy");
        say("Chết máy! Về số N rồi chọn \"Nổ máy\"");
    }

    private void checkBoundaries() {
        float cx = ptX(CENTRE, 0), cy = ptY(CENTRE, 0);
        if (!onRoad(cx, cy)) { fail("Xe đi ra ngoài hình thi"); return; }
        if (STUB.in(cx, cy) && (cx < 10.5f || cx > 20.5f)) { fail("Đi sai lộ trình tại ngã tư"); return; }
        boolean now = wheelOnLine(ptX(0, -TRACK), ptY(0, -TRACK)) || wheelOnLine(ptX(0, TRACK), ptY(0, TRACK))
                || wheelOnLine(ptX(WHEELBASE, -TRACK), ptY(WHEELBASE, -TRACK))
                || wheelOnLine(ptX(WHEELBASE, TRACK), ptY(WHEELBASE, TRACK));
        if (now) {
            touchFree = 0;
            if (!touching) {
                touching = true;
                penalise(5, "Bánh xe đè vạch giới hạn");
            }
        } else if (touching) {
            touchFree += 1f / 60f;
            if (touchFree > 0.4f) touching = false;
        }
    }

    private void runExam(float dt, boolean stopEvent) {
        float fy = ptY(FRONT, 0), cx = ptX(CENTRE, 0), cy = ptY(CENTRE, 0);
        StopLine s = stops[exam];
        if (s != null) runStop(s, fy, dt, stopEvent);
        if (state != State.PLAYING) return;

        for (int t : TIMED) {
            if (t == exam && examTime > 120 && !timePenalised) {
                timePenalised = true;
                penalise(5, "Quá 2 phút chưa xong bài");
            }
        }

        switch (exam) {
            case 0:
                if (!moved && Math.abs(v) > 0.3f) {
                    moved = true;
                    if (!belt) penalise(5, "Không thắt dây an toàn");
                    if (signal != -1) penalise(5, "Không bật xi nhan trái khi xuất phát");
                }
                if (!startCrossed && fy < START_LINE) startCrossed = true;
                if (!startCrossed && examTime > 20 && !lateStart) {
                    lateStart = true;
                    penalise(5, "Quá 20 giây chưa qua vạch xuất phát");
                }
                if (fy < START_LINE - 5) {
                    if (signal == -1) penalise(5, "Không tắt xi nhan trái sau khi xuất phát");
                    advance();
                }
                break;
            case 1: if (cy < 165) advance(); break;
            case 2: if (cy < 137) advance(); break;
            case 3: {
                float fwy = ptY(WHEELBASE, TRACK), fwx = ptX(WHEELBASE, TRACK);
                float rwy = ptY(0, TRACK), rwx = ptX(0, TRACK);
                float mid = (VET_Y0 + VET_Y1) / 2f;
                if (!vetFrontSeen && prevFrontWheelY > mid && fwy <= mid) {
                    vetFrontSeen = true;
                    if (fwx < VET_X0 || fwx > VET_X1) vetMiss();
                }
                if (!vetRearSeen && prevRearWheelY > mid && rwy <= mid) {
                    vetRearSeen = true;
                    if (rwx < VET_X0 || rwx > VET_X1) vetMiss();
                    else if (!vetPenalised) say("Qua vệt bánh xe ✓");
                }
                prevFrontWheelY = fwy;
                prevRearWheelY = rwy;
                if (cy < 60 && cx > 12) {
                    if (!vetFrontSeen || !vetRearSeen) vetMiss();
                    advance();
                }
                break;
            }
            case 4: if (cy < 15) advance(); break;
            case 5: if (cx < 5 && cy < -28) advance(); break;
            case 6:
            case 9: {
                boolean garage = exam == 6;
                if (!parked && stopEvent && carInside(garage ? GARAGE : BAY)) {
                    float want = garage ? (float) (-Math.PI / 2) : 0f;
                    if (angleDiff(ang, want) < 0.35f) {
                        parked = true;
                        say("Đã ghép xe vào nơi đỗ ✓");
                        beep(ToneGenerator.TONE_PROP_ACK);
                    }
                }
                if (garage ? cy < -60 : cy < -180) {
                    if (!parked) { fail("Không ghép được xe vào nơi đỗ"); return; }
                    advance();
                }
                break;
            }
            case 7: if (cy < -84) advance(); break;
            case 8: {
                float kmh = Math.abs(v) * 3.6f;
                if (fy < SHIFT_A && fy >= SHIFT_B && gear >= 2 && gear <= 3 && kmh > 20) shiftUpOk = true;
                if (fy < SHIFT_B && !shiftUpDone) {
                    shiftUpDone = true;
                    if (!shiftUpOk) penalise(5, "Không lên số 2 và vượt 20 km/h trong 25 m");
                    else say("Tăng số đạt ✓");
                }
                if (fy < SHIFT_C && !shiftDownDone) {
                    shiftDownDone = true;
                    if (!(gear == 1 && kmh < 20)) penalise(5, "Không về số 1 và dưới 20 km/h trong 25 m");
                    else say("Giảm số đạt ✓");
                }
                if (cy < -140) advance();
                break;
            }
            case 10:
                if (fy < END_LINE) {
                    if (signal != 1) penalise(5, "Không bật xi nhan phải khi qua vạch kết thúc");
                    if (state == State.PLAYING) finish();
                }
                break;
            default: break;
        }
    }

    private void vetMiss() {
        if (vetPenalised) return;
        vetPenalised = true;
        penalise(5, "Bánh xe không đi qua vệt bánh xe");
    }

    private void runStop(StopLine s, float fy, float dt, boolean stopEvent) {
        float dist = fy - s.y;
        if (s.crossed) return;
        if (s.light) {
            float ph = lightPhase();
            if (!s.touched && dist < 0) {
                s.touched = true;
                if (lightColour() == 2) penalise(10, "Vi phạm đèn đỏ");
            }
            if (!s.touched && !s.late && lightColour() == 0 && ph > 20 && dist < 12) {
                s.late = true;
                penalise(5, "Quá 20 giây khi đèn xanh chưa qua vạch dừng");
            }
        }
        if (stopEvent) {
            if (!s.light && dist >= 0 && dist <= 0.5f && !s.ok) {
                s.ok = true;
                s.okRearY = ry;
                s.okTime = 0;
                say("Dừng đúng vạch ✓");
                beep(ToneGenerator.TONE_PROP_ACK);
            } else if (!s.light && dist > 0.5f && dist < 5f && !s.ok) {
                s.early = true;
            } else if (dist < 0 && dist > -2.5f && !s.over && !s.ok) {
                s.over = true;
                penalise(5, "Dừng xe quá vạch dừng");
            }
        }
        if (state != State.PLAYING) return;
        if (s.hill && s.ok) {
            s.okTime += dt;
            if (s.okTime > 30) { fail("Quá 30 giây không khởi hành qua vạch dừng"); return; }
            if (ry - s.okRearY > 0.5f) { fail("Xe bị tụt dốc quá 50 cm"); return; }
        }
        if (dist < -2.5f) {
            s.crossed = true;
            if (!s.light && !s.ok && !s.over) {
                if (s.early) penalise(5, "Dừng xe chưa đến vạch dừng (quá 50 cm)");
                else if (s.hill) fail("Không dừng xe ở vạch dừng trên dốc");
                else penalise(5, "Không dừng xe ở vạch dừng");
            }
        }
    }

    private void runEmergency(float dt) {
        if (emergDone) return;
        if (!emergActive) {
            if (exam == 7 && ry < -80f) {
                emergActive = true;
                emergT = 0;
                alarmT = 0;
            }
            return;
        }
        emergT += dt;
        if (!emergHandled) {
            alarmT -= dt;
            if (alarmT <= 0) { alarmT = 0.5f; beep(ToneGenerator.TONE_SUP_ERROR); }
            if (Math.abs(v) < 0.05f && hazard) {
                emergHandled = true;
                say("Xử lý tình huống đạt ✓ Tắt đèn khẩn cấp rồi đi tiếp");
            } else if (emergT > 3 && !emergPenalised) {
                emergPenalised = true;
                emergHandled = true;
                penalise(10, "Không dừng xe và bật đèn khẩn cấp trong 3 giây");
            }
        } else {
            if (hazard && Math.abs(v) > 0.5f && !hazardPenalised) {
                hazardPenalised = true;
                penalise(10, "Không tắt đèn khẩn cấp khi đi tiếp");
            }
            if (!hazard) { emergActive = false; emergDone = true; }
        }
    }

    private void advance() {
        exam++;
        examTime = 0;
        overPenalised = false;
        timePenalised = false;
        parked = false;
        if (exam < EXAM.length) say("Bài " + (exam + 1) + ": " + EXAM[exam]);
    }

    private void penalise(int points, String reason) {
        if (state != State.PLAYING) return;
        score -= points;
        String where = emergActive && reason.contains("khẩn cấp") ? "Tình huống nguy hiểm" : "Bài " + (exam + 1);
        log.add("−" + points + "   " + where + ": " + reason);
        say("−" + points + " điểm: " + reason);
        beep(ToneGenerator.TONE_PROP_NACK);
        if (score < 80) fail("Điểm dưới 80");
    }

    private void fail(String reason) {
        if (state != State.PLAYING) return;
        failReason = reason;
        passed = false;
        log.add("Truất quyền thi · Bài " + (exam + 1) + ": " + reason);
        beep(ToneGenerator.TONE_SUP_ERROR);
        end();
    }

    private void finish() {
        passed = score >= 80;
        if (passed) beep(ToneGenerator.TONE_PROP_ACK);
        end();
    }

    private void end() {
        state = State.OVER;
        menuOpen = false;
        removeCallbacks(frame);
        attempts++;
        if (passed) {
            passes++;
            if (score > best) best = score;
        }
        prefs.edit().putInt("best", best).putInt("attempts", attempts).putInt("passes", passes).apply();
        invalidate();
    }

    private void say(String msg) {
        toast = msg;
        toastT = 2.6f;
    }

    private void beep(int type) {
        if (tone != null) tone.startTone(type, 180);
    }

    // ---- input ----

    private void start() {
        reset();
        state = State.PLAYING;
        say("Có lệnh xuất phát! Bấm OK để mở bảng điều khiển");
        resume();
    }

    private void resume() {
        state = State.PLAYING;
        menuOpen = false;
        lastNanos = System.nanoTime();
        removeCallbacks(frame);
        postOnAnimation(frame);
        invalidate();
    }

    void pauseGame() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            menuOpen = false;
            upHeld = downHeld = leftHeld = rightHeld = false;
            removeCallbacks(frame);
            invalidate();
        }
    }

    private void openMenu() {
        menuOpen = true;
        upHeld = downHeld = leftHeld = rightHeld = false;
        removeCallbacks(frame);
        invalidate();
    }

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_SPACE
                || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    private static boolean isDpad(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    boolean keyDown(int code, int repeat) {
        if (code == KeyEvent.KEYCODE_BACK) {
            if (state == State.PLAYING) {
                if (menuOpen) resume(); else pauseGame();
                return true;
            }
            return false;
        }
        boolean ok = isOk(code);
        switch (state) {
            case READY:
            case OVER:
                if (ok && repeat == 0) start();
                return ok || isDpad(code);
            case PAUSED:
                if (ok && repeat == 0) resume();
                return ok || isDpad(code);
            default:
                break;
        }
        if (menuOpen) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) { menuSel = (menuSel + M_COUNT - 1) % M_COUNT; invalidate(); }
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { menuSel = (menuSel + 1) % M_COUNT; invalidate(); }
            else if (ok && repeat == 0) { apply(menuSel); if (state == State.PLAYING) resume(); }
            else if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_MENU) resume();
            return true;
        }
        if ((ok || code == KeyEvent.KEYCODE_MENU) && repeat == 0) { openMenu(); return true; }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: upHeld = true; downHeld = false; return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: downHeld = true; upHeld = false; return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: leftHeld = true; rightHeld = false; return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: rightHeld = true; leftHeld = false; return true;
            default: return ok;
        }
    }

    boolean keyUp(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: upHeld = false; return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: downHeld = false; return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: leftHeld = false; return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: rightHeld = false; return true;
            default: return false;
        }
    }

    private void apply(int item) {
        switch (item) {
            case M_G1: case M_G2: case M_G3:
                if (v < -0.5f) say("Dừng hẳn xe rồi mới vào số tiến");
                else gear = item - M_G1 + 1;
                break;
            case M_N: gear = N; break;
            case M_R:
                if (v > 0.5f) say("Dừng hẳn xe rồi mới vào số lùi");
                else gear = REV;
                break;
            case M_LEFT: signal = -1; break;
            case M_RIGHT: signal = 1; break;
            case M_OFF: signal = 0; break;
            case M_HAND: handbrake = !handbrake; break;
            case M_HAZ: hazard = !hazard; break;
            case M_BELT: belt = !belt; break;
            case M_ENGINE:
                if (engineOn) say("Máy đang nổ");
                else if (gear != N) say("Về số N rồi mới nổ máy");
                else { engineOn = true; rpm = 800; say("Đã nổ máy"); }
                break;
            default: break;
        }
    }

    private String menuLabel(int item) {
        switch (item) {
            case M_G1: return "Số 1";
            case M_G2: return "Số 2";
            case M_G3: return "Số 3";
            case M_N: return "Số N";
            case M_R: return "Số lùi R";
            case M_LEFT: return "Xi nhan trái";
            case M_RIGHT: return "Xi nhan phải";
            case M_OFF: return "Tắt xi nhan";
            case M_HAND: return handbrake ? "Nhả phanh tay" : "Kéo phanh tay";
            case M_HAZ: return hazard ? "Tắt đèn khẩn cấp" : "Bật đèn khẩn cấp";
            case M_BELT: return belt ? "Tháo dây an toàn" : "Thắt dây an toàn";
            default: return "Nổ máy";
        }
    }

    private boolean menuActive(int item) {
        switch (item) {
            case M_G1: case M_G2: case M_G3: return gear == item - M_G1 + 1;
            case M_N: return gear == N;
            case M_R: return gear == REV;
            case M_LEFT: return signal == -1;
            case M_RIGHT: return signal == 1;
            default: return false;
        }
    }

    // ---- drawing ----

    @Override
    protected void onDraw(Canvas c) {
        w = getWidth();
        h = getHeight();
        k = h / 38f;
        float camX = ptX(CENTRE, 0), camY = ptY(CENTRE, 0);

        c.drawColor(0xFF6E9E57);
        c.save();
        c.translate(w / 2f - camX * k, h / 2f - camY * k);
        c.scale(k, k);
        float vx0 = camX - w / 2f / k - 3, vx1 = camX + w / 2f / k + 3, vy0 = camY - h / 2f / k - 3, vy1 = camY + h / 2f / k + 3;

        p.setColor(0xFF4F8040);
        for (int i = 0; i < trees.length; i += 3) {
            float x = trees[i], y = trees[i + 1];
            if (x < vx0 || x > vx1 || y < vy0 || y > vy1) continue;
            c.drawCircle(x, y, trees[i + 2], p);
        }
        p.setColor(0xFF5E9450);
        for (int i = 0; i < trees.length; i += 3) {
            float x = trees[i], y = trees[i + 1];
            if (x < vx0 || x > vx1 || y < vy0 || y > vy1) continue;
            c.drawCircle(x - trees[i + 2] * 0.25f, y - trees[i + 2] * 0.25f, trees[i + 2] * 0.55f, p);
        }

        // Road: white edge lines come from drawing every piece slightly larger in white first.
        p.setColor(0xFFF4F4F0);
        for (Area a : road) a.fill(c, p, 0.15f, path);
        p.setColor(0xFF5A5E64);
        for (Area a : road) a.fill(c, p, 0f, path);
        p.setColor(0xFF63676E);
        GARAGE.fill(c, p, 0f, path);
        BAY.fill(c, p, 0f, path);

        drawMarkings(c);
        drawCar(c);
        c.restore();

        drawLabels(c);
        drawLight(c);
        drawHud(c);
        if (menuOpen) drawMenu(c);
        if (state != State.PLAYING) drawOverlay(c);
    }

    private void drawMarkings(Canvas c) {
        // Slope.
        c.drawRect(0, 151, 5, 161, slopeUp);
        p.setColor(0xFF7A7F87);
        c.drawRect(0, 146, 5, 151, p);
        c.drawRect(0, 139, 5, 146, slopeDown);
        p.setColor(0x55FFFFFF);
        for (float y = 160; y > 152; y -= 2.5f) {
            path.reset();
            path.moveTo(2.5f, y - 1.2f);
            path.lineTo(3.4f, y);
            path.lineTo(1.6f, y);
            path.close();
            c.drawPath(path, p);
        }
        // Zebra crossing for exercise 2.
        p.setColor(0xFFF4F4F0);
        for (float x = 0.3f; x < 5; x += 1f) c.drawRect(x, 170.5f, x + 0.5f, 175f, p);
        // Stop lines.
        for (StopLine s : stops) {
            if (s == null) continue;
            float x0 = s.light ? 13 : s.y < 0 ? -1 : 0, x1 = s.light ? 18 : s.y < 0 ? 4 : 5;
            p.setColor(0xFFFFFFFF);
            c.drawRect(x0, s.y - 0.3f, x1, s.y, p);
        }
        // Start and finish.
        p.setColor(0xFFFFFFFF);
        c.drawRect(0, START_LINE - 0.3f, 5, START_LINE, p);
        for (int i = 0; i < 10; i++) {
            p.setColor(i % 2 == 0 ? 0xFFFFFFFF : 0xFF222222);
            c.drawRect(-1 + i * 0.5f, END_LINE - 0.5f, -0.5f + i * 0.5f, END_LINE - 0.25f, p);
            p.setColor(i % 2 == 1 ? 0xFFFFFFFF : 0xFF222222);
            c.drawRect(-1 + i * 0.5f, END_LINE - 0.25f, -0.5f + i * 0.5f, END_LINE, p);
        }
        // Wheel track strip.
        p.setColor(0xFF3A3A30);
        c.drawRect(VET_X0 - 0.08f, VET_Y0 - 0.08f, VET_X1 + 0.08f, VET_Y1 + 0.08f, p);
        p.setColor(0xFFF2C230);
        c.drawRect(VET_X0, VET_Y0, VET_X1, VET_Y1, p);
        // Gear-change markers.
        p.setColor(0xFFF2C230);
        for (float y : new float[]{SHIFT_A, SHIFT_B, SHIFT_C}) {
            for (float x = -1; x < 4; x += 0.8f) c.drawRect(x, y - 0.12f, x + 0.45f, y + 0.12f, p);
        }
        // Railway.
        p.setColor(0xFF6B4F3A);
        for (float x = -9; x < 13; x += 0.7f) c.drawRect(x, -75.4f, x + 0.25f, -72.6f, p);
        p.setColor(0xFFB8BEC4);
        c.drawRect(-9, -74.6f, 13, -74.4f, p);
        c.drawRect(-9, -73.6f, 13, -73.4f, p);
        p.setColor(0xFFE53935);
        c.drawRect(-2.2f, -71.2f, -1.8f, -70.4f, p);
        c.drawRect(4.8f, -71.2f, 5.2f, -70.4f, p);
        // Traffic light pole.
        p.setColor(0xFF2B2B2B);
        c.drawRect(18.6f, 26.6f, 19.6f, 29.4f, p);
        int col = lightColour();
        int[] on = {0xFF2ECC40, 0xFFFFC107, 0xFFE53935};
        for (int i = 0; i < 3; i++) {
            p.setColor(i == 2 - col ? on[col] : 0xFF444444);
            c.drawCircle(19.1f, 27.1f + i * 0.9f, 0.32f, p);
        }
        // Parking frames.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.1f);
        p.setColor(0x88FFFFFF);
        c.drawRect(GARAGE.x0 + 0.25f, GARAGE.y0 + 0.25f, GARAGE.x1 - 0.25f, GARAGE.y1 - 0.25f, p);
        c.drawRect(BAY.x0 + 0.25f, BAY.y0 + 0.25f, BAY.x1 - 0.25f, BAY.y1 - 0.25f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawCar(Canvas c) {
        c.save();
        c.translate(rx, ry);
        c.rotate((float) Math.toDegrees(ang));
        // Wheels (the front ones turn with the steering).
        p.setColor(0xFF111111);
        float ww = 0.13f, wl = 0.33f;
        c.drawRect(-TRACK - ww, -wl, -TRACK + ww, wl, p);
        c.drawRect(TRACK - ww, -wl, TRACK + ww, wl, p);
        for (float side : new float[]{-TRACK, TRACK}) {
            c.save();
            c.translate(side, -WHEELBASE);
            c.rotate((float) Math.toDegrees(steer));
            c.drawRect(-ww, -wl, ww, wl, p);
            c.restore();
        }
        // Body.
        p.setColor(0xFF2F80ED);
        r.set(-HALF_W, -FRONT, HALF_W, REAR_OH);
        c.drawRoundRect(r, 0.35f, 0.35f, p);
        p.setColor(0xFF1F5FAF);
        r.set(-0.74f, -2.35f, 0.74f, -0.1f);
        c.drawRoundRect(r, 0.2f, 0.2f, p);
        p.setColor(0xFFA9D6F5);
        r.set(-0.7f, -2.35f, 0.7f, -1.8f);
        c.drawRoundRect(r, 0.12f, 0.12f, p);
        r.set(-0.66f, -0.45f, 0.66f, -0.12f);
        c.drawRoundRect(r, 0.1f, 0.1f, p);
        p.setColor(0xFFF2F2F2);
        c.drawRect(-0.06f, -FRONT + 0.05f, 0.06f, -2.4f, p);
        // Lights.
        p.setColor(0xFFFFF3B0);
        c.drawCircle(-0.6f, -FRONT + 0.12f, 0.12f, p);
        c.drawCircle(0.6f, -FRONT + 0.12f, 0.12f, p);
        p.setColor(downHeld || handbrake ? 0xFFFF2A2A : 0xFF7A1C1C);
        c.drawRect(-0.8f, REAR_OH - 0.12f, -0.45f, REAR_OH, p);
        c.drawRect(0.45f, REAR_OH - 0.12f, 0.8f, REAR_OH, p);
        if (gear == REV) {
            p.setColor(0xFFFFFFFF);
            c.drawRect(-0.3f, REAR_OH - 0.12f, -0.1f, REAR_OH, p);
        }
        boolean blinkOn = (blinkClock * 1.5f) % 1f < 0.5f;
        if (blinkOn) {
            p.setColor(0xFFFFA000);
            if (signal == -1 || hazard) { c.drawCircle(-0.8f, -FRONT + 0.15f, 0.15f, p); c.drawCircle(-0.8f, REAR_OH - 0.15f, 0.15f, p); }
            if (signal == 1 || hazard) { c.drawCircle(0.8f, -FRONT + 0.15f, 0.15f, p); c.drawCircle(0.8f, REAR_OH - 0.15f, 0.15f, p); }
        }
        // Help for the wheel track: the right-hand wheels are marked.
        if (exam == 3) {
            p.setColor(0xFFFFE04A);
            c.drawCircle(TRACK, 0, 0.12f, p);
            c.drawCircle(TRACK, -WHEELBASE, 0.12f, p);
        }
        c.restore();
    }

    private float sx(float x) { return w / 2f + (x - ptX(CENTRE, 0)) * k; }
    private float sy(float y) { return h / 2f + (y - ptY(CENTRE, 0)) * k; }

    private void label(Canvas c, float x, float y, String s, int bg) {
        float px = sx(x), py = sy(y);
        if (px < -400 || px > w + 400 || py < -60 || py > h + 60) return;
        text.setTextSize(h * 0.026f);
        text.setTextAlign(Paint.Align.CENTER);
        float tw = text.measureText(s);
        p.setColor(bg);
        r.set(px - tw / 2 - 12, py - h * 0.03f, px + tw / 2 + 12, py + h * 0.012f);
        c.drawRoundRect(r, 10, 10, p);
        text.setColor(0xFFFFFFFF);
        c.drawText(s, px, py, text);
    }

    private void drawLabels(Canvas c) {
        int bg = 0xCC1E3A2A;
        label(c, -7, 196, "Bài 1 · Xuất phát", bg);
        label(c, -7, 174, "Bài 2 · Nhường người đi bộ", bg);
        label(c, -7, 154, "Bài 3 · Dừng, khởi hành ngang dốc", bg);
        label(c, -7, 121, "Bài 4 · Vệt bánh xe", bg);
        label(c, 9, 99, "Đường vòng vuông góc", bg);
        label(c, 8, 33, "Bài 5 · Ngã tư có đèn", bg);
        label(c, 20, -16, "Bài 6 · Đường quanh co", bg);
        label(c, -10, -45, "Bài 7 · Ghép xe dọc", bg);
        label(c, -9, -71, "Bài 8 · Đường sắt", bg);
        label(c, -8, -100, "Bài 9 · Tăng số", bg);
        label(c, -8, -125, "Giảm số", bg);
        label(c, -10, -160, "Bài 10 · Ghép xe ngang", bg);
        label(c, -8, -190, "Bài 11 · Kết thúc", bg);
    }

    private void drawLight(Canvas c) {
        float px = sx(20.6f), py = sy(28f);
        if (px < -100 || px > w + 100 || py < -100 || py > h + 100) return;
        int col = lightColour();
        float ph = lightPhase();
        float left = col == 0 ? GREEN - ph : col == 1 ? GREEN + YELLOW - ph : LIGHT_CYCLE - ph;
        text.setTextSize(h * 0.04f);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(col == 0 ? 0xFF2ECC40 : col == 1 ? 0xFFFFC107 : 0xFFFF5252);
        p.setColor(0xDD111111);
        r.set(px - 8, py - h * 0.04f, px + h * 0.06f, py + h * 0.012f);
        c.drawRoundRect(r, 8, 8, p);
        c.drawText(String.valueOf((int) Math.ceil(left)), px, py, text);
        text.setColor(0xFFFFFFFF);
    }

    private void panel(Canvas c, float l, float t, float rr, float b) {
        p.setColor(0xC8101418);
        r.set(l, t, rr, b);
        c.drawRoundRect(r, 18, 18, p);
    }

    private void drawHud(Canvas c) {
        float u = h / 100f;
        // Top: exercise and score.
        panel(c, 2 * u, 1.5f * u, w - 2 * u, 12.5f * u);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(4.2f * u);
        int e = Math.min(exam, EXAM.length - 1);
        c.drawText("Bài " + (e + 1) + "/11 · " + EXAM[e], 4 * u, 6.3f * u, text);
        text.setTextSize(2.8f * u);
        text.setColor(0xFFCFD8DC);
        c.drawText(HINT[e], 4 * u, 10.6f * u, text);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(5f * u);
        text.setColor(score >= 90 ? 0xFF69F0AE : score >= 80 ? 0xFFFFD54F : 0xFFFF5252);
        c.drawText(score + " điểm", w - 4 * u, 6.6f * u, text);
        text.setTextSize(2.8f * u);
        text.setColor(0xFFCFD8DC);
        c.drawText(String.format(Locale.ROOT, "%d:%02d", (int) examTime / 60, (int) examTime % 60), w - 4 * u, 10.6f * u, text);

        // Dashboard.
        float dl = 2 * u, dt = h - 27 * u, dr = 2 * u + 44 * u, db = h - 2 * u;
        panel(c, dl, dt, dr, db);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(9f * u);
        c.drawText(GEAR_NAME[gear], dl + 3 * u, dt + 11 * u, text);
        text.setTextSize(5f * u);
        c.drawText(String.format(Locale.ROOT, "%d km/h", Math.round(Math.abs(v) * 3.6f)), dl + 13 * u, dt + 7.5f * u, text);
        // Rev counter.
        float bx = dl + 13 * u, by = dt + 10 * u, bw = 28 * u, bh = 2.2f * u;
        p.setColor(0xFF33393F);
        c.drawRect(bx, by, bx + bw, by + bh, p);
        float frac = Math.min(1f, rpm / 6000f);
        p.setColor(rpm > 4000 ? 0xFFFF5252 : rpm > 3000 ? 0xFFFFC107 : 0xFF4CAF50);
        c.drawRect(bx, by, bx + bw * frac, by + bh, p);
        p.setColor(0xFFFFFFFF);
        c.drawRect(bx + bw * 4000f / 6000f - 2, by - 4, bx + bw * 4000f / 6000f + 2, by + bh + 4, p);
        text.setTextSize(2.4f * u);
        text.setColor(0xFFB0BEC5);
        c.drawText(engineOn ? Math.round(rpm / 100f) * 100 + " vòng/phút" : "MÁY TẮT", bx, by + bh + 3.2f * u, text);
        // Indicators.
        float iy = db - 4 * u;
        float ix = dl + 3 * u;
        ix = indicator(c, ix, iy, "Dây", belt, 0xFF4CAF50, 0xFFFF5252);
        ix = indicator(c, ix, iy, "P", handbrake, 0xFFFF5252, 0xFF546E7A);
        boolean blinkOn = (blinkClock * 1.5f) % 1f < 0.5f;
        ix = indicator(c, ix, iy, "◀", (signal == -1 || hazard) && blinkOn, 0xFF4CAF50, 0xFF546E7A);
        ix = indicator(c, ix, iy, "▶", (signal == 1 || hazard) && blinkOn, 0xFF4CAF50, 0xFF546E7A);
        ix = indicator(c, ix, iy, "⚠", hazard, 0xFFFF5252, 0xFF546E7A);
        indicator(c, ix, iy, "Máy", engineOn, 0xFF4CAF50, 0xFFFF5252);
        // Steering wheel and pedals.
        float swx = dr + 9 * u, swy = h - 12 * u, swr = 7 * u;
        p.setColor(0xC8101418);
        c.drawCircle(swx, swy, swr + 2 * u, p);
        c.save();
        c.rotate((float) Math.toDegrees(steer) * 12f, swx, swy);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f * u);
        p.setColor(0xFFECEFF1);
        c.drawCircle(swx, swy, swr, p);
        c.drawLine(swx - swr, swy, swx + swr, swy, p);
        c.drawLine(swx, swy, swx, swy + swr, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFF7043);
        c.drawCircle(swx, swy - swr, 1.1f * u, p);
        c.restore();
        float px = swx + swr + 4 * u;
        p.setColor(downHeld ? 0xFFFF5252 : 0xFF37474F);
        c.drawRect(px, h - 15 * u, px + 4 * u, h - 4 * u, p);
        p.setColor(upHeld ? 0xFF69F0AE : 0xFF37474F);
        c.drawRect(px + 5 * u, h - 17 * u, px + 8 * u, h - 4 * u, p);
        text.setTextSize(2.2f * u);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText("Phanh", px + 2 * u, h - 1.2f * u, text);
        c.drawText("Ga", px + 6.5f * u, h - 1.2f * u, text);

        // Deductions so far.
        int shown = Math.min(5, log.size());
        if (shown > 0) {
            text.setTextSize(2.6f * u);
            text.setTextAlign(Paint.Align.LEFT);
            float lw = 56 * u;
            panel(c, w - lw - 2 * u, 14 * u, w - 2 * u, 14 * u + (shown + 1) * 3.6f * u);
            text.setColor(0xFFFFD54F);
            c.drawText("Lỗi bị trừ điểm", w - lw, 18 * u, text);
            text.setColor(0xFFFFFFFF);
            for (int i = 0; i < shown; i++) {
                String s = log.get(log.size() - shown + i);
                c.drawText(ellipsize(s, lw - 2 * u), w - lw, 18 * u + (i + 1) * 3.6f * u, text);
            }
        }

        // Emergency banner.
        if (emergActive && !emergHandled) {
            boolean on = (emergT * 3) % 1f < 0.6f;
            p.setColor(on ? 0xEED32F2F : 0xEE8E1B1B);
            r.set(w * 0.22f, h * 0.18f, w * 0.78f, h * 0.29f);
            c.drawRoundRect(r, 16, 16, p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(4.4f * u);
            text.setColor(0xFFFFFFFF);
            c.drawText("TÌNH HUỐNG NGUY HIỂM! Dừng xe, bật đèn khẩn cấp (" + Math.max(0, (int) Math.ceil(3 - emergT)) + ")",
                    w / 2f, h * 0.25f, text);
        }

        // Toast.
        if (toast != null && toastT > 0) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(3.6f * u);
            float tw = text.measureText(toast);
            p.setColor(0xDD000000);
            r.set(w / 2f - tw / 2 - 2 * u, h * 0.66f - 4.6f * u, w / 2f + tw / 2 + 2 * u, h * 0.66f + 1.6f * u);
            c.drawRoundRect(r, 14, 14, p);
            text.setColor(0xFFFFFFFF);
            c.drawText(toast, w / 2f, h * 0.66f, text);
        }
        if (state == State.PLAYING && !menuOpen) {
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTextSize(2.4f * u);
            text.setColor(0xCCFFFFFF);
            c.drawText("↑ ga · ↓ phanh · ← → đánh lái · OK: số, xi nhan, phanh tay… · Back: tạm dừng", w - 3 * u, h - 2.5f * u, text);
        }
    }

    private String ellipsize(String s, float max) {
        if (text.measureText(s) <= max) return s;
        while (s.length() > 4 && text.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private float indicator(Canvas c, float x, float y, String label, boolean on, int onColour, int offColour) {
        float u = h / 100f;
        text.setTextSize(2.6f * u);
        text.setTextAlign(Paint.Align.CENTER);
        float tw = Math.max(text.measureText(label) + 2 * u, 5 * u);
        p.setColor(on ? onColour : offColour);
        r.set(x, y - 3.2f * u, x + tw, y + 1.2f * u);
        c.drawRoundRect(r, 8, 8, p);
        text.setColor(0xFFFFFFFF);
        c.drawText(label, x + tw / 2, y, text);
        return x + tw + 1.2f * u;
    }

    private void drawMenu(Canvas c) {
        float u = h / 100f;
        text.setTextSize(3f * u);
        text.setTextAlign(Paint.Align.CENTER);
        float gap = 1.2f * u, total = -gap;
        float[] widths = new float[M_COUNT];
        for (int i = 0; i < M_COUNT; i++) { widths[i] = text.measureText(menuLabel(i)) + 3 * u; total += widths[i] + gap; }
        float scale = Math.min(1f, (w - 6 * u) / total);
        text.setTextSize(3f * u * scale);
        float x = (w - total * scale) / 2f, y = h * 0.8f;
        panel(c, x - 2 * u, y - 9 * u, x + total * scale + 2 * u, y + 7 * u);
        text.setColor(0xFFCFD8DC);
        text.setTextSize(2.6f * u);
        c.drawText("Bảng điều khiển · ← → chọn · OK thực hiện · ↑ ↓ hoặc Back để đóng", w / 2f, y - 5 * u, text);
        text.setTextSize(3f * u * scale);
        for (int i = 0; i < M_COUNT; i++) {
            float bw = widths[i] * scale;
            boolean sel = i == menuSel, act = menuActive(i);
            p.setColor(sel ? 0xFFFFC107 : act ? 0xFF2E7D32 : 0xFF37474F);
            r.set(x, y - 3 * u, x + bw, y + 3.2f * u);
            c.drawRoundRect(r, 10, 10, p);
            text.setColor(sel ? 0xFF111111 : 0xFFFFFFFF);
            c.drawText(menuLabel(i), x + bw / 2, y + 1.2f * u, text);
            x += bw + gap * scale;
        }
    }

    private void drawOverlay(Canvas c) {
        float u = h / 100f;
        p.setColor(0xD0000000);
        c.drawRect(0, 0, w, h, p);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFFFFFFF);
        if (state == State.PAUSED) {
            text.setTextSize(8 * u);
            c.drawText("Tạm dừng", w / 2f, h * 0.45f, text);
            text.setTextSize(3.6f * u);
            c.drawText("Bấm OK để thi tiếp  ·  Back để thoát", w / 2f, h * 0.55f, text);
            return;
        }
        if (state == State.READY) {
            text.setTextSize(7 * u);
            c.drawText("Thi sa hình hạng B · số sàn", w / 2f, 16 * u, text);
            text.setTextSize(3 * u);
            text.setColor(0xFFCFD8DC);
            c.drawText("11 bài thi, 100 điểm, dưới 80 điểm hoặc bị truất quyền là trượt", w / 2f, 22 * u, text);
            String[] lines = {
                    "↑ giữ: đạp ga   ·   ↓ giữ: phanh (kèm côn)   ·   ← → giữ: đánh lái",
                    "OK: bảng điều khiển — vào số 1/2/3/N/R, xi nhan, phanh tay, đèn khẩn cấp, dây an toàn, nổ máy",
                    "Xe số sàn: thả phanh ở số 1 xe tự bò ~5 km/h. Số 2, 3 đi quá chậm hoặc xe trôi ngược số là chết máy.",
                    "Ga lâu ở số thấp sẽ rồ máy quá 4.000 vòng/phút (−5). Lên dốc phải đạp ga, không thì xe tụt.",
                    "Bánh xe chạm vạch: −5 mỗi lần. Đi ra ngoài hình hoặc sai lộ trình: truất quyền.",
                    "Có một tình huống nguy hiểm bất ngờ: dừng xe và bật đèn khẩn cấp trong 3 giây.",
            };
            text.setColor(0xFFFFFFFF);
            text.setTextSize(3.1f * u);
            for (int i = 0; i < lines.length; i++) c.drawText(lines[i], w / 2f, 33 * u + i * 6.5f * u, text);
            text.setTextSize(4.4f * u);
            text.setColor(0xFFFFD54F);
            c.drawText("Bấm OK để nhận lệnh xuất phát", w / 2f, 80 * u, text);
            text.setTextSize(2.8f * u);
            text.setColor(0xFFB0BEC5);
            c.drawText("Đã thi " + attempts + " lần · đậu " + passes + " lần" + (best > 0 ? " · điểm cao nhất " + best : ""),
                    w / 2f, 88 * u, text);
            return;
        }
        // Results.
        text.setTextSize(10 * u);
        text.setColor(passed ? 0xFF69F0AE : 0xFFFF5252);
        c.drawText(passed ? "ĐẠT" : "TRƯỢT", w / 2f, 16 * u, text);
        text.setTextSize(4.4f * u);
        text.setColor(0xFFFFFFFF);
        String sub = failReason != null ? "Truất quyền / dừng thi: " + failReason : score + " / 100 điểm";
        if (failReason != null && failReason.startsWith("Điểm")) sub = score + " / 100 điểm — dưới 80 điểm";
        c.drawText(sub, w / 2f, 24 * u, text);
        text.setTextSize(3 * u);
        text.setTextAlign(Paint.Align.LEFT);
        int from = Math.max(0, log.size() - 10);
        if (log.isEmpty()) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(0xFF69F0AE);
            c.drawText("Không bị trừ điểm nào. Xuất sắc!", w / 2f, 40 * u, text);
        } else {
            text.setColor(0xFFFFD54F);
            c.drawText("Các lỗi:", w * 0.2f, 33 * u, text);
            text.setColor(0xFFFFFFFF);
            for (int i = from; i < log.size(); i++) c.drawText(log.get(i), w * 0.2f, 38 * u + (i - from) * 4.4f * u, text);
        }
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(4 * u);
        text.setColor(0xFFFFD54F);
        c.drawText("Bấm OK để thi lại  ·  Back để thoát", w / 2f, 92 * u, text);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        if (tone != null) tone.release();
        if (tick != null) tick.release();
        super.onDetachedFromWindow();
    }
}
