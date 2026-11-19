package vn.trochoi.ransan;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The board, the snake and the food, stepped by a timer that speeds up as the snake grows. */
final class SnakeView extends View {

    private static final int COLS = 32, ROWS = 17;
    private static final long START_MS = 180, MIN_MS = 60, FASTER_BY_MS = 3;

    private enum State { READY, PLAYING, PAUSED, OVER }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final SharedPreferences prefs;

    private final List<int[]> snake = new ArrayList<>();
    private final ArrayDeque<int[]> turns = new ArrayDeque<>();
    private int dx = 1, dy = 0;
    private int foodX, foodY;
    private int score, best;
    private long stepMs = START_MS;
    private State state = State.READY;
    private boolean newBest;

    private final Paint board = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boardAlt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint food = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leaf = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (state != State.PLAYING) return;
            advance();
            invalidate();
            if (state == State.PLAYING) handler.postDelayed(this, stepMs);
        }
    };

    SnakeView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(0xFF0E1A12);
        prefs = context.getSharedPreferences("ransan", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        board.setColor(0xFF1B3324);
        boardAlt.setColor(0xFF1F3A29);
        body.setColor(0xFF7BD67B);
        head.setColor(0xFFB8F28A);
        eye.setColor(0xFF10200F);
        food.setColor(0xFFF25C54);
        leaf.setColor(0xFF4CAF50);
        text.setColor(0xFFF2F5EE);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        dim.setColor(0xB0000000);
        reset();
    }

    private void reset() {
        snake.clear();
        int y = ROWS / 2;
        for (int i = 0; i < 4; i++) snake.add(new int[]{6 - i, y});
        dx = 1;
        dy = 0;
        turns.clear();
        score = 0;
        stepMs = START_MS;
        newBest = false;
        placeFood();
    }

    private void placeFood() {
        while (true) {
            int x = random.nextInt(COLS), y = random.nextInt(ROWS);
            boolean onSnake = false;
            for (int[] p : snake) if (p[0] == x && p[1] == y) { onSnake = true; break; }
            if (!onSnake) { foodX = x; foodY = y; return; }
        }
    }

    private void advance() {
        if (!turns.isEmpty()) {
            int[] t = turns.poll();
            dx = t[0];
            dy = t[1];
        }
        int[] h = snake.get(0);
        int nx = h[0] + dx, ny = h[1] + dy;
        boolean eats = nx == foodX && ny == foodY;
        if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS || hitsSelf(nx, ny, eats)) {
            gameOver();
            return;
        }
        snake.add(0, new int[]{nx, ny});
        if (eats) {
            score++;
            stepMs = Math.max(MIN_MS, stepMs - FASTER_BY_MS);
            if (snake.size() == COLS * ROWS) { gameOver(); return; }
            placeFood();
        } else {
            snake.remove(snake.size() - 1);
        }
    }

    /** The tail moves away this step unless the snake eats, so its cell is free to enter. */
    private boolean hitsSelf(int x, int y, boolean eats) {
        int last = eats ? snake.size() : snake.size() - 1;
        for (int i = 0; i < last; i++) {
            int[] p = snake.get(i);
            if (p[0] == x && p[1] == y) return true;
        }
        return false;
    }

    private void gameOver() {
        state = State.OVER;
        if (score > best) {
            best = score;
            newBest = true;
            prefs.edit().putInt("best", best).apply();
        }
    }

    private void start() {
        if (state == State.OVER || state == State.READY) reset();
        state = State.PLAYING;
        handler.removeCallbacks(step);
        handler.postDelayed(step, stepMs);
        invalidate();
    }

    void pauseGame() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            handler.removeCallbacks(step);
            invalidate();
        }
    }

    /** Back pauses a running game; on any other screen it leaves. */
    boolean backPressed() {
        if (state == State.PLAYING) {
            pauseGame();
            return true;
        }
        return false;
    }

    boolean key(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_W: return turn(0, -1);
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_S: return turn(0, 1);
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_A: return turn(-1, 0);
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_D: return turn(1, 0);
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE: case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (state == State.PLAYING) pauseGame(); else start();
                return true;
            default:
                return false;
        }
    }

    private boolean turn(int x, int y) {
        if (state == State.READY || state == State.OVER) return true;
        if (state == State.PAUSED) start();
        int[] lastDir = turns.isEmpty() ? new int[]{dx, dy} : turns.peekLast();
        if ((x == lastDir[0] && y == lastDir[1]) || (x == -lastDir[0] && y == -lastDir[1])) return true;
        if (turns.size() < 2) turns.add(new int[]{x, y});
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float top = h * 0.11f;
        float cell = Math.min((w * 0.94f) / COLS, (h - top - h * 0.04f) / ROWS);
        float left = (w - cell * COLS) / 2f;
        float boardTop = top + (h - top - h * 0.04f - cell * ROWS) / 2f;

        // Header: title, score, best.
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(h * 0.05f);
        c.drawText("Rắn săn mồi", left, top * 0.68f, text);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Điểm: " + score + "     Kỷ lục: " + best, left + cell * COLS, top * 0.68f, text);

        // Board.
        float rad = cell * 0.18f;
        r.set(left - cell * 0.15f, boardTop - cell * 0.15f, left + cell * COLS + cell * 0.15f, boardTop + cell * ROWS + cell * 0.15f);
        c.drawRoundRect(r, rad * 2, rad * 2, board);
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if ((x + y) % 2 == 1) c.drawRect(left + x * cell, boardTop + y * cell, left + (x + 1) * cell, boardTop + (y + 1) * cell, boardAlt);
            }
        }

        // Food: an apple with a leaf.
        float fx = left + (foodX + 0.5f) * cell, fy = boardTop + (foodY + 0.55f) * cell;
        c.drawCircle(fx, fy, cell * 0.36f, food);
        r.set(fx, fy - cell * 0.5f, fx + cell * 0.3f, fy - cell * 0.28f);
        c.drawOval(r, leaf);

        // Snake.
        float pad = cell * 0.08f;
        for (int i = snake.size() - 1; i >= 0; i--) {
            int[] p = snake.get(i);
            r.set(left + p[0] * cell + pad, boardTop + p[1] * cell + pad, left + (p[0] + 1) * cell - pad, boardTop + (p[1] + 1) * cell - pad);
            c.drawRoundRect(r, cell * 0.3f, cell * 0.3f, i == 0 ? head : body);
        }
        int[] hd = snake.get(0);
        float cx = left + (hd[0] + 0.5f) * cell, cy = boardTop + (hd[1] + 0.5f) * cell;
        float ex = dy != 0 ? cell * 0.18f : 0, ey = dx != 0 ? cell * 0.18f : 0;
        float fwdX = dx * cell * 0.15f, fwdY = dy * cell * 0.15f;
        c.drawCircle(cx + fwdX + ex, cy + fwdY + ey, cell * 0.08f, eye);
        c.drawCircle(cx + fwdX - ex, cy + fwdY - ey, cell * 0.08f, eye);

        // Overlays.
        if (state != State.PLAYING) {
            c.drawRect(0, 0, w, h, dim);
            text.setTextAlign(Paint.Align.CENTER);
            String title, hint;
            switch (state) {
                case PAUSED: title = "Tạm dừng"; hint = "Bấm OK để chơi tiếp  ·  Back để thoát"; break;
                case OVER:
                    title = newBest ? "Kỷ lục mới: " + score + " điểm!" : "Thua rồi! " + score + " điểm";
                    hint = "Bấm OK để chơi lại  ·  Back để thoát";
                    break;
                default: title = "Rắn săn mồi"; hint = "Bấm OK để bắt đầu  ·  Dùng phím mũi tên để lái rắn"; break;
            }
            text.setTextSize(h * 0.09f);
            c.drawText(title, w / 2f, h * 0.47f, text);
            text.setTextSize(h * 0.04f);
            c.drawText(hint, w / 2f, h * 0.58f, text);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(step);
        super.onDetachedFromWindow();
    }
}
