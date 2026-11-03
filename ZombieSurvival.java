import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Zombie Survival (2D) - Java OOP project  (polished version)
 * - Uses only JFrame / JPanel / Graphics2D (no image files, no libraries)
 * - Zombie (general zombie) is the base class
 *   GirlZombie and FatZombie inherit from Zombie and override behavior/drawing
 *
 * Compile : javac ZombieSurvival.java
 * Run     : java ZombieSurvival
 *
 * Controls: W A S D / arrow keys = move
 *           Mouse               = aim (survivor rotates toward the cursor)
 *           Shooting is automatic
 *           After each wave: click a card or press 1 / 2 / 3 to pick an upgrade
 *           ENTER               = start / retry
 */
public class ZombieSurvival extends JFrame {

    public ZombieSurvival() {
        setTitle("Zombie Survival");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        GamePanel panel = new GamePanel();
        add(panel);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);
        panel.requestFocusInWindow();
        SwingUtilities.invokeLater(panel::requestFocusInWindow);   // make sure the keyboard works right away
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ZombieSurvival::new);
    }
}

/* ===================================================================== */
/*                       GFX : small drawing helpers                      */
/* ===================================================================== */
final class Gfx {
    private Gfx() { }

    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    static void centerText(Graphics2D g, String s, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }

    /** Centered text with a soft drop shadow. */
    static void shadowCenter(Graphics2D g, String s, int cx, int y, Color c) {
        g.setColor(new Color(0, 0, 0, (int) (c.getAlpha() * 0.6)));
        centerText(g, s, cx + 3, y + 3);
        g.setColor(c);
        centerText(g, s, cx, y);
    }

    /** Left aligned text with a soft drop shadow. */
    static void shadowText(Graphics2D g, String s, int x, int y, Color c) {
        g.setColor(new Color(0, 0, 0, (int) (c.getAlpha() * 0.6)));
        g.drawString(s, x + 2, y + 2);
        g.setColor(c);
        g.drawString(s, x, y);
    }

    static void panel(Graphics2D g, int x, int y, int w, int h, int arc, Color fill, Color border) {
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, arc, arc);
        g.setColor(border);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, w, h, arc, arc);
    }

    static void heart(Graphics2D g, double cx, double cy, double s, Color c) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy + s * 0.9);
        p.curveTo(cx - s * 1.4, cy - s * 0.1, cx - s * 0.6, cy - s * 1.1, cx, cy - s * 0.35);
        p.curveTo(cx + s * 0.6, cy - s * 1.1, cx + s * 1.4, cy - s * 0.1, cx, cy + s * 0.9);
        p.closePath();
        g.setColor(c);
        g.fill(p);
        g.setColor(new Color(0, 0, 0, 120));
        g.setStroke(new BasicStroke(2f));
        g.draw(p);
        g.setColor(new Color(255, 255, 255, 120));
        g.fillOval((int) (cx - s * 0.75), (int) (cy - s * 0.55), (int) (s * 0.4), (int) (s * 0.3));
    }

    static void bolt(Graphics2D g, double cx, double cy, double s, Color c) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx + 0.30 * s, cy - 1.00 * s);
        p.lineTo(cx - 0.55 * s, cy + 0.15 * s);
        p.lineTo(cx - 0.05 * s, cy + 0.15 * s);
        p.lineTo(cx - 0.30 * s, cy + 1.00 * s);
        p.lineTo(cx + 0.55 * s, cy - 0.20 * s);
        p.lineTo(cx + 0.05 * s, cy - 0.20 * s);
        p.closePath();
        g.setColor(c);
        g.fill(p);
        g.setColor(new Color(0, 0, 0, 120));
        g.setStroke(new BasicStroke(2f));
        g.draw(p);
    }

    static void glowDot(Graphics2D g, double cx, double cy, double r, Color c) {
        g.setColor(alpha(c, 50));
        g.fill(new Ellipse2D.Double(cx - r * 1.8, cy - r * 1.8, r * 3.6, r * 3.6));
        g.setColor(alpha(c, 110));
        g.fill(new Ellipse2D.Double(cx - r * 1.3, cy - r * 1.3, r * 2.6, r * 2.6));
        g.setColor(c);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(cx - r * 0.45, cy - r * 0.45, r * 0.9, r * 0.9));
    }
}

/* ===================================================================== */
/*                         PARTICLE / FLOAT TEXT                         */
/* ===================================================================== */
class Particle {
    double x, y, vx, vy, life, maxLife, size;
    Color color;

    Particle(double x, double y, double vx, double vy, double life, double size, Color color) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.life = life;
        this.maxLife = life;
        this.size = size;
        this.color = color;
    }

    boolean update(double dt) {
        life -= dt;
        x += vx * dt;
        y += vy * dt;
        vx *= 0.93;
        vy *= 0.93;
        return life > 0;
    }

    void draw(Graphics2D g) {
        double t = Math.max(0, life / maxLife);
        double s = size * (0.4 + 0.6 * t);
        g.setColor(Gfx.alpha(color, (int) (255 * t)));
        g.fill(new Ellipse2D.Double(x - s / 2, y - s / 2, s, s));
    }
}

class FloatText {
    double x, y, life = 0.8;
    String text;
    Color color;

    FloatText(double x, double y, String text, Color color) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
    }

    boolean update(double dt) {
        life -= dt;
        y -= 42 * dt;
        return life > 0;
    }

    void draw(Graphics2D g) {
        int a = (int) (255 * Math.min(1.0, life / 0.4));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        Gfx.shadowCenter(g, text, (int) x, (int) y, Gfx.alpha(color, a));
    }
}

/* ===================================================================== */
/*                               SURVIVOR                                */
/* ===================================================================== */
class Survivor {
    static final int HALF = 16;          // half size of body
    double x, y;
    double angle = -Math.PI / 2;         // aim direction (radians), start aiming up
    int hp = 100, maxHp = 100;
    double moveSpeed = 200;              // pixels per second (upgradable)
    int bulletDamage = 1;                // damage per bullet (upgradable)
    double invul = 0;                    // invincible time after being hit
    double cooldown = 0;                 // time until next shot
    double muzzle = 0;                   // muzzle flash timer
    double walkPhase = 0;                // walking animation
    boolean moving = false;

    Survivor(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** Rotate the gun toward the mouse position. */
    void aimAt(double mx, double my) {
        angle = Math.atan2(my - y, mx - x);
    }

    /** Walk in direction (dx, dy); diagonal speed is normalized and kept inside the window. */
    void move(double dx, double dy, double dt, int w, int h) {
        double len = Math.hypot(dx, dy);
        moving = len > 0;
        if (moving) {
            x += dx / len * moveSpeed * dt;
            y += dy / len * moveSpeed * dt;
            walkPhase += dt * 14;
        }
        x = Math.max(HALF, Math.min(w - HALF, x));
        y = Math.max(24, Math.min(h - 24, y));
    }

    void update(double dt) {
        if (invul > 0) invul -= dt;
        if (cooldown > 0) cooldown -= dt;
        if (muzzle > 0) muzzle -= dt;
    }

    boolean canShoot() {
        return cooldown <= 0;
    }

    Bullet shoot() {
        cooldown = 0.22;
        muzzle = 0.06;
        double bx = x + Math.cos(angle) * 34;
        double by = y + Math.sin(angle) * 34;
        return new Bullet(bx, by, angle, bulletDamage);
    }

    void hurt(int damage) {
        if (invul > 0) return;
        hp -= damage;
        if (hp < 0) hp = 0;
        invul = 0.6;
    }

    void heal(int amount) {
        hp = Math.min(maxHp, hp + amount);
    }

    void draw(Graphics2D g) {
        // blink while invincible
        if (invul > 0 && ((int) (invul * 20)) % 2 == 0) return;

        int bob = moving ? (int) (-Math.abs(Math.sin(walkPhase)) * 3) : 0;
        int step = moving ? (int) (Math.sin(walkPhase) * 4) : 0;
        int bx = (int) x - HALF;
        int by = (int) y - 18 + bob;
        Color skin = new Color(0xD9A98F);
        Stroke outline = new BasicStroke(2f);

        // ground shadow
        g.setColor(new Color(0, 0, 0, 90));
        g.fillOval(bx - 1, (int) y + 15, 34, 11);

        // feet (swing while walking)
        g.setColor(new Color(0xE8821E));
        g.fillRoundRect(bx + 4, by + 33 + step / 2, 9, 6, 4, 4);
        g.setColor(new Color(0x8E44AD));
        g.fillRoundRect(bx + 19, by + 33 - step / 2, 9, 6, 4, 4);

        // pants
        g.setPaint(new GradientPaint(bx, by + 24, new Color(0x4A4AD0), bx, by + 34, new Color(0x2E2E9A)));
        g.fillRoundRect(bx, by + 24, 32, 10, 6, 6);
        g.setColor(new Color(0x1B1B55));
        g.fillRect(bx, by + 24, 32, 2);                      // belt

        // shirt
        g.setPaint(new GradientPaint(bx, by + 12, Color.WHITE, bx, by + 25, new Color(0xD8DCE6)));
        g.fillRect(bx, by + 12, 32, 13);
        g.setColor(new Color(0x2F7DE0));                     // student ID card
        g.fillRoundRect(bx + 21, by + 16, 7, 8, 2, 2);
        g.setColor(new Color(0x2F7DE0));
        g.drawLine(bx + 24, by + 12, bx + 24, by + 16);      // lanyard

        // head
        g.setPaint(new GradientPaint(bx, by, skin.brighter(), bx, by + 13, skin));
        g.fillRoundRect(bx, by, 32, 14, 8, 8);
        g.setColor(new Color(0x3B2A20));                     // hair
        g.fillRoundRect(bx - 1, by - 3, 34, 7, 8, 8);

        // eyes look toward the aim direction
        double ex = Math.cos(angle) * 1.6;
        double ey = Math.sin(angle) * 1.2;
        g.setColor(Color.WHITE);
        g.fillOval(bx + 6, by + 5, 8, 7);
        g.fillOval(bx + 18, by + 5, 8, 7);
        g.setColor(Color.BLACK);
        g.fillOval((int) (bx + 8 + ex), (int) (by + 6 + ey), 4, 4);
        g.fillOval((int) (bx + 20 + ex), (int) (by + 6 + ey), 4, 4);

        // outline
        g.setColor(Color.BLACK);
        g.setStroke(outline);
        g.drawRoundRect(bx, by, 32, 34, 8, 8);

        // gun (rotates with aim, flipped when aiming left so it is never upside down)
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x, y + bob + 3);
        g2.rotate(angle);
        if (Math.cos(angle) < 0) g2.scale(1, -1);
        g2.setColor(skin);                                   // arm
        g2.fillRoundRect(4, -3, 14, 6, 4, 4);
        g2.setColor(new Color(0x2A2A2A));                    // body of the gun
        g2.fillRoundRect(12, -4, 27, 8, 4, 4);
        g2.fillRoundRect(14, 2, 6, 9, 3, 3);                 // grip
        g2.setColor(new Color(0x666666));
        g2.fillRect(16, -3, 20, 2);                          // highlight
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(12, -4, 27, 8, 4, 4);
        if (muzzle > 0) {                                    // muzzle flash star
            Path2D star = new Path2D.Double();
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                double rr = (i % 2 == 0) ? 13 : 5;
                double px = 44 + Math.cos(a) * rr;
                double py = Math.sin(a) * rr;
                if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
            }
            star.closePath();
            g2.setColor(new Color(0xFFB000));
            g2.fill(star);
            g2.setColor(new Color(0xFFF3A0));
            g2.fillOval(40, -4, 8, 8);
        }
        g2.dispose();
    }
}

/* ===================================================================== */
/*                                BULLET                                 */
/* ===================================================================== */
class Bullet {
    double x, y, vx, vy;
    boolean alive = true;
    int damage;
    static final double SPEED = 650;

    Bullet(double x, double y, double angle, int damage) {
        this.damage = damage;
        this.x = x;
        this.y = y;
        this.vx = Math.cos(angle) * SPEED;
        this.vy = Math.sin(angle) * SPEED;
    }

    void update(double dt, int w, int h) {
        x += vx * dt;
        y += vy * dt;
        if (x < -10 || y < -10 || x > w + 10 || y > h + 10) alive = false;
    }

    void draw(Graphics2D g) {
        double sz = 4 + Math.min(damage, 5);
        Color core = damage >= 3 ? new Color(0xFF7043) : new Color(0xFFE000);
        // fading trail
        for (int i = 4; i >= 1; i--) {
            double tx = x - vx * 0.010 * i;
            double ty = y - vy * 0.010 * i;
            double s = sz * (1.2 - i * 0.18);
            g.setColor(Gfx.alpha(core, 130 - i * 28));
            g.fill(new Ellipse2D.Double(tx - s / 2, ty - s / 2, s, s));
        }
        Gfx.glowDot(g, x, y, sz * 0.55, core);
    }
}

/* ===================================================================== */
/*                  ZOMBIE  (base class = general zombie)                */
/* ===================================================================== */
class Zombie {
    protected double x, y, speed;
    protected int hp, maxHp, radius, damage, score;
    protected double flash = 0;       // white flash when hit
    protected double time;            // life time (used for animation / by subclasses)
    protected double dirX = 0, dirY = 1;   // direction the zombie is looking / moving
    protected double kbVx, kbVy;           // knockback velocity (slides and slows down)
    protected double stun;                 // dazed time after a knockback (cannot chase)

    /** General zombie. Gets stronger as the wave number increases. */
    public Zombie(double x, double y, int wave) {
        this(x, y, 18, 2 + wave / 3, Math.min(60 + wave * 4, 160), 10, 10);
    }

    /** Constructor for subclasses (child types set their own stats). */
    protected Zombie(double x, double y, int radius, int hp, double speed, int damage, int score) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.hp = hp;
        this.maxHp = hp;
        this.speed = speed;
        this.damage = damage;
        this.score = score;
        this.time = Math.random() * 10;      // so zombies do not bob in sync
    }

    public void update(double dt, Survivor target) {
        time += dt;
        if (flash > 0) flash -= dt;

        // sliding after a knockback (friction slows it down smoothly)
        if (Math.abs(kbVx) > 1 || Math.abs(kbVy) > 1) {
            x += kbVx * dt;
            y += kbVy * dt;
            double f = Math.exp(-5 * dt);
            kbVx *= f;
            kbVy *= f;
        }

        double dx = target.x - x;
        double dy = target.y - y;
        double dist = Math.hypot(dx, dy);
        if (dist < 1e-6) return;
        dirX = dx / dist;
        dirY = dy / dist;
        if (stun > 0) {         // dazed: keeps looking at the survivor but cannot chase yet
            stun -= dt;
            return;
        }
        move(dirX, dirY, dt);   // polymorphic movement
    }

    /** Move toward the survivor. (ux, uy) is the unit direction. Subclasses can override. */
    protected void move(double ux, double uy, double dt) {
        x += ux * speed * dt;
        y += uy * speed * dt;
    }

    /** Make the zombie look toward a point (used for the menu characters). */
    public void lookAt(double tx, double ty) {
        double d = Math.hypot(tx - x, ty - y);
        if (d > 1e-6) {
            dirX = (tx - x) / d;
            dirY = (ty - y) / d;
        }
    }

    public void hit(int dmg) {
        hp -= dmg;
        flash = 0.08;
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public boolean touches(Survivor s) {
        return Math.hypot(s.x - x, s.y - y) < radius + Survivor.HALF - 2;
    }

    /** Throw the zombie away from the survivor. force = starting speed (px/s); it slides ~force/5 pixels. */
    public void knockBack(Survivor s, double force) {
        double dx = x - s.x;
        double dy = y - s.y;
        double d = Math.hypot(dx, dy);
        if (d < 1e-6) {          // exactly on top of the survivor: push backwards from where it was looking
            dx = -dirX;
            dy = -dirY;
            d = Math.hypot(dx, dy);
        }
        kbVx = dx / d * force;
        kbVy = dy / d * force;
        stun = 0.45;
        flash = 0.1;
    }

    public boolean isStunned() {
        return stun > 0;
    }

    /** Draw whole zombie: shadow + body (overridable) + hp bar. */
    public void draw(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 80));
        g.fillOval((int) x - radius, (int) y + radius - 4, radius * 2, radius / 2 + 5);
        drawBody(g);
        drawHpBar(g);
    }

    protected double bobY() {
        return Math.sin(time * 9) * 2;
    }

    protected Color skinColor() {
        return flash > 0 ? Color.WHITE : new Color(0x2BAA3F);
    }

    /** General zombie body: green square, black hair, big eyes, open mouth, torn shirt, blue shorts. */
    protected void drawBody(Graphics2D g) {
        int r = radius;
        int s = r * 2;
        int cx = (int) x;
        int cy = (int) (y + bobY());
        int bx = cx - r;
        int by = cy - r;
        Color skin = skinColor();
        Stroke thin = new BasicStroke(2f);
        double sway = Math.sin(time * 9 + 1.5);

        // arms (swing while shambling)
        int aw = Math.max(6, r / 2);
        int ah = Math.max(9, r * 2 / 3);
        int ayL = cy - r / 4 + (int) (sway * 3);
        int ayR = cy - r / 4 - (int) (sway * 3);
        g.setColor(skin);
        g.fillRoundRect(bx - aw + 3, ayL, aw, ah, 6, 6);
        g.fillRoundRect(bx + s - 3, ayR, aw, ah, 6, 6);
        g.setColor(Color.BLACK);
        g.setStroke(thin);
        g.drawRoundRect(bx - aw + 3, ayL, aw, ah, 6, 6);
        g.drawRoundRect(bx + s - 3, ayR, aw, ah, 6, 6);

        // legs (alternate steps)
        int lw = Math.max(5, s / 6);
        int lh = Math.max(6, r / 2);
        int step = (int) (Math.sin(time * 9) * 3);
        g.setColor(skin);
        g.fillRect(bx + s / 5, by + s - 2 + step, lw, lh);
        g.fillRect(bx + s - s / 5 - lw, by + s - 2 - step, lw, lh);
        g.setColor(Color.BLACK);
        g.drawRect(bx + s / 5, by + s - 2 + step, lw, lh);
        g.drawRect(bx + s - s / 5 - lw, by + s - 2 - step, lw, lh);

        // body
        g.setPaint(new GradientPaint(bx, by, skin.brighter(), bx, by + s, skin.darker()));
        g.fillRoundRect(bx, by, s, s, r / 2, r / 2);

        // blue shorts
        g.setColor(new Color(0x3F3FBF));
        g.fillRect(bx + 1, by + s * 2 / 3, s - 1, s / 3 - 1);

        // torn white shirt scrap
        int ty = by + s / 2 + 2;
        int th = Math.max(4, s / 6);
        Polygon tear = new Polygon();
        tear.addPoint(bx + 2, ty);
        tear.addPoint(bx + s - 2, ty);
        int teeth = 5;
        for (int i = teeth; i >= 0; i--) {
            int px = bx + 2 + (s - 4) * i / teeth;
            tear.addPoint(px, ty + th + ((i % 2 == 0) ? 0 : th / 2));
        }
        g.setColor(new Color(255, 255, 255, flash > 0 ? 255 : 225));
        g.fillPolygon(tear);

        // outline
        g.setColor(Color.BLACK);
        g.setStroke(thin);
        g.drawRoundRect(bx, by, s, s, r / 2, r / 2);

        // hair
        g.fillRoundRect(bx + 2, by - 4, s - 4, Math.max(7, r / 2), 8, 8);

        // scratches
        g.setColor(new Color(150, 0, 0, 170));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(bx + s * 7 / 10, by + s / 3, bx + s * 7 / 10 + 5, by + s / 3 + 8);
        g.drawLine(bx + s * 7 / 10 + 4, by + s / 3, bx + s * 7 / 10 + 9, by + s / 3 + 8);

        // eyes (pupils follow the survivor)
        int eyeR = Math.max(5, r * 2 / 5);
        double[] exs = {cx - r * 0.42, cx + r * 0.42};
        double ey = cy - r * 0.18;
        for (double exx : exs) {
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(exx - eyeR, ey - eyeR, eyeR * 2, eyeR * 2));
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(exx - eyeR, ey - eyeR, eyeR * 2, eyeR * 2));
            double px = exx + dirX * eyeR / 3.0;
            double py = ey + dirY * eyeR / 3.0;
            g.fill(new Ellipse2D.Double(px - eyeR / 2.2, py - eyeR / 2.2, eyeR / 1.1, eyeR / 1.1));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(px - 1, py - eyeR / 2.2, 2.5, 2.5));
        }

        // open mouth with teeth and tongue
        int mw = Math.max(8, r * 2 / 3);
        int mh = Math.max(7, r * 2 / 5);
        int mx = cx - mw / 2;
        int my = cy + r / 4;
        g.setColor(new Color(0x6E0B0B));
        g.fillOval(mx, my, mw, mh);
        g.setColor(new Color(0xE05A6A));
        g.fillOval(mx + mw / 4, my + mh / 2, mw / 2, mh / 2);
        g.setColor(Color.WHITE);
        g.fillRect(mx + mw / 4, my + 1, mw / 5, 3);
        g.fillRect(mx + mw / 2, my + 1, mw / 5, 3);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(mx, my, mw, mh);
    }

    protected void drawHpBar(Graphics2D g) {
        if (hp >= maxHp) return;
        int w = radius * 2;
        int bx = (int) x - radius;
        int by = (int) y - radius - 15;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(bx - 1, by - 1, w + 2, 8, 6, 6);
        int fw = (int) (w * Math.max(0, hp) / (double) maxHp);
        g.setPaint(new GradientPaint(bx, by, new Color(0xFF6B6B), bx, by + 6, new Color(0xC62828)));
        g.fillRoundRect(bx, by, fw, 6, 5, 5);
    }
}

/* ===================================================================== */
/*       GIRL ZOMBIE : inherits Zombie - fast, low hp, zig-zag path      */
/* ===================================================================== */
class GirlZombie extends Zombie {

    public GirlZombie(double x, double y, int wave) {
        super(x, y, 16, 1 + wave / 4, Math.min(110 + wave * 5, 230), 8, 20);
    }

    @Override
    protected void move(double ux, double uy, double dt) {
        // wobble left-right while running toward the survivor
        double w = Math.sin(time * 6) * 0.8;
        x += (ux - uy * w) * speed * dt;
        y += (uy + ux * w) * speed * dt;
    }

    @Override
    protected Color skinColor() {
        return flash > 0 ? Color.WHITE : new Color(0x55C773);   // lighter green
    }

    @Override
    protected void drawBody(Graphics2D g) {
        int r = radius;
        int cx = (int) x;
        int cy = (int) (y + bobY());
        int sway = (int) (Math.sin(time * 9) * 3);

        // pigtails swing behind the head
        g.setColor(new Color(0x1B1B1B));
        g.fillOval(cx - r - 10 - sway, cy - r - 4, 17, 22);
        g.fillOval(cx + r - 7 + sway, cy - r - 4, 17, 22);

        super.drawBody(g);

        // pink hair ties + bow
        g.setColor(new Color(0xFF69B4));
        g.fillOval(cx - r - 3 - sway / 2, cy - r - 1, 7, 7);
        g.fillOval(cx + r - 4 + sway / 2, cy - r - 1, 7, 7);
        g.fillOval(cx - 8, cy - r - 7, 8, 8);
        g.fillOval(cx, cy - r - 7, 8, 8);
        g.setColor(new Color(0xE0408F));
        g.fillOval(cx - 3, cy - r - 5, 6, 6);

        // eyelashes
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.5f));
        int ey = (int) (cy - r * 0.18);
        int eyeR = Math.max(5, r * 2 / 5);
        g.drawLine((int) (cx - r * 0.42) - eyeR, ey - eyeR + 1, (int) (cx - r * 0.42) - eyeR - 3, ey - eyeR - 2);
        g.drawLine((int) (cx + r * 0.42) + eyeR, ey - eyeR + 1, (int) (cx + r * 0.42) + eyeR + 3, ey - eyeR - 2);
    }
}

/* ===================================================================== */
/*     FAT ZOMBIE : inherits Zombie - slow, big, lots of hp, enrages     */
/* ===================================================================== */
class FatZombie extends Zombie {

    public FatZombie(double x, double y, int wave) {
        super(x, y, 30, 8 + wave, Math.min(35 + wave * 2, 80), 20, 50);
    }

    @Override
    protected void move(double ux, double uy, double dt) {
        // enrage: runs faster when hp is half or less
        double boost = isEnraged() ? 1.6 : 1.0;
        x += ux * speed * boost * dt;
        y += uy * speed * boost * dt;
    }

    private boolean isEnraged() {
        return hp <= maxHp / 2;
    }

    @Override
    public void knockBack(Survivor s, double force) {
        super.knockBack(s, force * 0.6);   // heavy: gets pushed back less
    }

    @Override
    protected Color skinColor() {
        return flash > 0 ? Color.WHITE : new Color(0x33963A);   // darker, heavier green
    }

    @Override
    protected double bobY() {
        return Math.sin(time * 6) * 3;   // heavier, slower waddle
    }

    @Override
    protected void drawBody(Graphics2D g) {
        super.drawBody(g);
        int r = radius;
        int s = r * 2;
        int cx = (int) x;
        int cy = (int) (y + bobY());

        // shirt buttons on the belly
        g.setColor(Color.BLACK);
        g.fillOval(cx - 3, cy + r / 6, 6, 6);
        g.fillOval(cx - 3, cy + r / 2 - 2, 6, 6);

        // drool
        g.setColor(new Color(255, 255, 255, 170));
        g.fillOval(cx + r / 4, cy + r / 2 + 2, 4, 8);

        if (isEnraged()) {
            // red tint + angry eyebrows + pulsing outline
            g.setColor(new Color(255, 0, 0, 70));
            g.fillRoundRect(cx - r, cy - r, s, s, r / 2, r / 2);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(3f));
            g.drawLine((int) (cx - r * 0.85), (int) (cy - r * 0.6), (int) (cx - r * 0.1), (int) (cy - r * 0.35));
            g.drawLine((int) (cx + r * 0.85), (int) (cy - r * 0.6), (int) (cx + r * 0.1), (int) (cy - r * 0.35));
            int pulse = (int) (90 + 80 * Math.sin(time * 14));
            g.setColor(new Color(255, 60, 60, Math.max(0, Math.min(255, pulse))));
            g.setStroke(new BasicStroke(3f));
            g.drawRoundRect(cx - r - 2, cy - r - 2, s + 4, s + 4, r / 2, r / 2);
        }
    }
}

/* ===================================================================== */
/*                              GAME PANEL                               */
/* ===================================================================== */
class GamePanel extends JPanel {
    static final int W = 800, H = 600;

    enum State { START, PLAYING, UPGRADE, GAME_OVER }

    private State state = State.START;
    private Survivor survivor;
    private final List<Zombie> zombies = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatText> floats = new ArrayList<>();
    private final Random rnd = new Random();

    private int wave, score, toSpawn;
    private double spawnTimer, bannerTimer, clearTimer;
    private double clock, hurtFlash, shake, overTime;
    private int highScore, highWave;       // best result, saved on disk
    private boolean newRecord;
    private static final Path SAVE_FILE =
            Paths.get(System.getProperty("user.home"), "zombie_survival_save.txt");
    private boolean up, down, left, right;
    private int mouseX = W / 2, mouseY = H / 2 - 100;

    private final Rectangle startBtn = new Rectangle(300, 500, 200, 56);
    private final Rectangle retryBtn = new Rectangle(300, 400, 200, 56);
    private final Rectangle[] cards = {
            new Rectangle(70, 200, 200, 260),
            new Rectangle(300, 200, 200, 260),
            new Rectangle(530, 200, 200, 260)
    };

    private final BufferedImage background;
    private Cursor blankCursor;

    // menu characters
    private final Survivor demoSurvivor = new Survivor(W / 2.0 - 210, 410);
    private final Zombie[] demoZombies = {
            new Zombie(W / 2.0 - 70, 408, 0),
            new GirlZombie(W / 2.0 + 70, 410, 0),
            new FatZombie(W / 2.0 + 215, 400, 0)
    };

    private long lastTime;

    GamePanel() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(0x1E252B));
        setFocusable(true);
        background = makeBackground();
        loadHighScore();
        setupInput();
        addFocusListener(new FocusAdapter() {   // release keys if the window loses focus
            @Override
            public void focusLost(FocusEvent e) {
                up = down = left = right = false;
            }
        });

        lastTime = System.nanoTime();
        new javax.swing.Timer(16, e -> {
            long now = System.nanoTime();
            double dt = Math.min(0.05, (now - lastTime) / 1e9);
            lastTime = now;
            update(dt);
            repaint();
        }).start();
    }

    /* ---------------------------- background ---------------------------- */
    /** Draws the static floor once (tiles, stains, cracks, scattered papers, vignette). */
    private BufferedImage makeBackground() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Gfx.aa(g);
        Random r = new Random(11);

        g.setPaint(new GradientPaint(0, 0, new Color(0x2E3E49), 0, H, new Color(0x1A242B)));
        g.fillRect(0, 0, W, H);

        // floor tiles
        int t = 50;
        for (int ty = 0; ty < H; ty += t) {
            for (int tx = 0; tx < W; tx += t) {
                int v = r.nextInt(26);
                boolean light = ((tx / t) + (ty / t)) % 2 == 0;
                g.setColor(light ? new Color(255, 255, 255, 6 + v / 3) : new Color(0, 0, 0, 10 + v / 2));
                g.fillRect(tx, ty, t, t);
            }
        }
        g.setStroke(new BasicStroke(1f));
        for (int x = 0; x <= W; x += t) {
            g.setColor(new Color(0, 0, 0, 70));
            g.drawLine(x, 0, x, H);
            g.setColor(new Color(255, 255, 255, 10));
            g.drawLine(x + 1, 0, x + 1, H);
        }
        for (int y = 0; y <= H; y += t) {
            g.setColor(new Color(0, 0, 0, 70));
            g.drawLine(0, y, W, y);
            g.setColor(new Color(255, 255, 255, 10));
            g.drawLine(0, y + 1, W, y + 1);
        }

        // green virus stains
        for (int i = 0; i < 10; i++) {
            int sx = r.nextInt(W);
            int sy = r.nextInt(H);
            int sw = 24 + r.nextInt(40);
            g.setColor(new Color(60, 130, 40, 30));
            g.fillOval(sx, sy, sw, sw * 2 / 3);
            g.setColor(new Color(100, 180, 60, 32));
            g.fillOval(sx + 5, sy + 3, sw / 2, sw / 3);
        }

        // cracks
        g.setColor(new Color(0, 0, 0, 80));
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < 12; i++) {
            int cx = r.nextInt(W);
            int cy = r.nextInt(H);
            for (int k = 0; k < 4; k++) {
                int nx = cx + r.nextInt(31) - 15;
                int ny = cy + r.nextInt(31) - 15;
                g.drawLine(cx, cy, nx, ny);
                cx = nx;
                cy = ny;
            }
        }

        // scattered exam papers
        for (int i = 0; i < 14; i++) {
            Graphics2D pg = (Graphics2D) g.create();
            pg.translate(r.nextInt(W), r.nextInt(H));
            pg.rotate(r.nextDouble() * Math.PI * 2);
            pg.setColor(new Color(0, 0, 0, 40));
            pg.fillRect(-8, -10, 18, 24);
            pg.setColor(new Color(235, 235, 225, 150));
            pg.fillRect(-9, -12, 18, 24);
            pg.setColor(new Color(0, 0, 0, 55));
            for (int l = 0; l < 4; l++) pg.drawLine(-6, -8 + l * 5, 6, -8 + l * 5);
            pg.dispose();
        }

        // vignette
        g.setPaint(new RadialGradientPaint(new Point2D.Float(W / 2f, H / 2f), 520f,
                new float[]{0.55f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 190)}));
        g.fillRect(0, 0, W, H);

        g.dispose();
        return img;
    }

    /* ------------------------------ input ------------------------------ */
    private void setupInput() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W: case KeyEvent.VK_UP:    up = true;    break;
                    case KeyEvent.VK_S: case KeyEvent.VK_DOWN:  down = true;  break;
                    case KeyEvent.VK_A: case KeyEvent.VK_LEFT:  left = true;  break;
                    case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: right = true; break;
                    case KeyEvent.VK_1: if (state == State.UPGRADE) chooseUpgrade(0); break;
                    case KeyEvent.VK_2: if (state == State.UPGRADE) chooseUpgrade(1); break;
                    case KeyEvent.VK_3: if (state == State.UPGRADE) chooseUpgrade(2); break;
                    case KeyEvent.VK_ENTER:
                        if (state == State.START || state == State.GAME_OVER) startGame();
                        break;
                    case KeyEvent.VK_R:
                        if (state == State.GAME_OVER) startGame();
                        break;
                    default: break;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W: case KeyEvent.VK_UP:    up = false;    break;
                    case KeyEvent.VK_S: case KeyEvent.VK_DOWN:  down = false;  break;
                    case KeyEvent.VK_A: case KeyEvent.VK_LEFT:  left = false;  break;
                    case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: right = false; break;
                    default: break;
                }
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (state == State.START && startBtn.contains(e.getPoint())) {
                    startGame();
                } else if (state == State.GAME_OVER && retryBtn.contains(e.getPoint())) {
                    startGame();
                } else if (state == State.UPGRADE) {
                    for (int i = 0; i < cards.length; i++) {
                        if (cards[i].contains(e.getPoint())) {
                            chooseUpgrade(i);
                            break;
                        }
                    }
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Apply the chosen upgrade (0 = damage, 1 = health, 2 = move speed) and start the next wave. */
    private void chooseUpgrade(int index) {
        switch (index) {
            case 0:  survivor.bulletDamage += 1;                     break;
            case 1:  survivor.maxHp += 25; survivor.heal(25);        break;
            default: survivor.moveSpeed += 30;                       break;
        }
        startWave();
        state = State.PLAYING;
        setCursorHidden(true);
    }

    /** Hide the system cursor while playing (a crosshair is drawn instead). */
    private void setCursorHidden(boolean hide) {
        if (GraphicsEnvironment.isHeadless()) return;
        try {
            if (hide) {
                if (blankCursor == null) {
                    blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                            new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank");
                }
                setCursor(blankCursor);
            } else {
                setCursor(Cursor.getDefaultCursor());
            }
        } catch (Exception ignored) {
            // cursor is just cosmetic
        }
    }

    /* ----------------------------- high score ----------------------------- */
    /** The best score and best wave are stored in a small text file in the user's home folder. */
    private void loadHighScore() {
        try {
            List<String> lines = Files.readAllLines(SAVE_FILE);
            highScore = Integer.parseInt(lines.get(0).trim());
            highWave = Integer.parseInt(lines.get(1).trim());
        } catch (Exception ignored) {
            highScore = 0;   // no save file yet (or unreadable) -> start from zero
            highWave = 0;
        }
    }

    private void saveHighScore() {
        try {
            Files.write(SAVE_FILE, Arrays.asList(String.valueOf(highScore), String.valueOf(highWave)));
        } catch (IOException ignored) {
            // saving is optional: the game still works if the file cannot be written
        }
    }

    /* ------------------------------ logic ------------------------------ */
    private void startGame() {
        survivor = new Survivor(W / 2.0, H / 2.0);
        zombies.clear();
        bullets.clear();
        particles.clear();
        floats.clear();
        wave = 0;
        score = 0;
        hurtFlash = 0;
        shake = 0;
        newRecord = false;
        up = down = left = right = false;
        startWave();
        state = State.PLAYING;
        setCursorHidden(true);
    }

    private void startWave() {
        wave++;
        toSpawn = 3 + wave * 2;       // more zombies each wave
        spawnTimer = 0.5;
        clearTimer = 0;
        bannerTimer = 2.0;
    }

    private void spawnZombie() {
        // random position just outside one of the 4 edges
        double x, y;
        switch (rnd.nextInt(4)) {
            case 0:  x = rnd.nextDouble() * W; y = -40;     break;
            case 1:  x = rnd.nextDouble() * W; y = H + 40;  break;
            case 2:  x = -40;                  y = rnd.nextDouble() * H; break;
            default: x = W + 40;               y = rnd.nextDouble() * H; break;
        }

        double fatChance  = wave >= 3 ? Math.min(0.35, 0.08 + (wave - 3) * 0.03) : 0;
        double girlChance = wave >= 2 ? Math.min(0.40, 0.15 + (wave - 2) * 0.04) : 0;
        double r = rnd.nextDouble();

        Zombie z;   // polymorphism: the list holds Zombie, objects are different types
        if (r < fatChance)                   z = new FatZombie(x, y, wave);
        else if (r < fatChance + girlChance) z = new GirlZombie(x, y, wave);
        else                                 z = new Zombie(x, y, wave);
        zombies.add(z);
    }

    private void burst(double x, double y, int n, Color c, double speed) {
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double v = speed * (0.35 + rnd.nextDouble() * 0.65);
            Color col = rnd.nextBoolean() ? c : c.darker();
            particles.add(new Particle(x, y, Math.cos(a) * v, Math.sin(a) * v,
                    0.3 + rnd.nextDouble() * 0.35, 3 + rnd.nextDouble() * 4, col));
        }
    }

    private void update(double dt) {
        clock += dt;
        if (state == State.GAME_OVER) overTime += dt;
        if (state == State.START) {
            demoSurvivor.aimAt(mouseX, mouseY);
            return;
        }
        if (state != State.PLAYING) return;

        // visual effects timers
        if (hurtFlash > 0) hurtFlash -= dt;
        if (shake > 0) shake -= dt;
        particles.removeIf(p -> !p.update(dt));
        floats.removeIf(f -> !f.update(dt));

        // aim toward the mouse
        survivor.aimAt(mouseX, mouseY);

        // walk
        double mx = (right ? 1 : 0) - (left ? 1 : 0);
        double my = (down ? 1 : 0) - (up ? 1 : 0);
        survivor.move(mx, my, dt, W, H);
        survivor.update(dt);

        // auto fire
        if (survivor.canShoot()) bullets.add(survivor.shoot());

        // spawn zombies of the current wave
        if (toSpawn > 0) {
            spawnTimer -= dt;
            if (spawnTimer <= 0) {
                spawnZombie();
                toSpawn--;
                spawnTimer = Math.max(0.3, 1.2 - wave * 0.05);
            }
        }

        // bullets
        for (Bullet b : bullets) b.update(dt, W, H);

        // zombies
        for (Zombie z : zombies) z.update(dt, survivor);

        // bullet vs zombie
        for (Bullet b : bullets) {
            if (!b.alive) continue;
            for (Zombie z : zombies) {
                if (z.isDead()) continue;
                if (Math.hypot(b.x - z.x, b.y - z.y) < z.radius + 4) {
                    z.hit(b.damage);
                    b.alive = false;
                    burst(b.x, b.y, 5, new Color(0x7BE05A), 150);
                    if (z.isDead()) {
                        score += z.score;
                        burst(z.x, z.y, 14 + z.radius / 2, new Color(0x6CD04C), 230);
                        floats.add(new FloatText(z.x, z.y - z.radius, "+" + z.score, new Color(0xFFD54F)));
                    }
                    break;
                }
            }
        }
        bullets.removeIf(b -> !b.alive);
        zombies.removeIf(Zombie::isDead);

        // zombie vs survivor
        int hpBefore = survivor.hp;
        for (Zombie z : zombies) {
            if (z.touches(survivor) && !z.isStunned()) {
                survivor.hurt(z.damage);
                z.knockBack(survivor, 650);    // throw the zombie ~130 px away
                burst(z.x, z.y, 6, new Color(0xCFCFCF), 120);
            }
        }
        if (survivor.hp < hpBefore) {
            hurtFlash = 0.35;
            shake = 0.25;
            burst(survivor.x, survivor.y, 10, new Color(0xE53935), 190);
        }
        if (survivor.hp <= 0) {
            state = State.GAME_OVER;
            overTime = 0;
            setCursorHidden(false);

            // record the result
            newRecord = score > highScore;
            boolean changed = newRecord;
            if (newRecord) highScore = score;
            if (wave > highWave) {
                highWave = wave;
                changed = true;
            }
            if (changed) saveHighScore();
            return;
        }

        // wave cleared -> next wave
        if (bannerTimer > 0) bannerTimer -= dt;
        if (toSpawn == 0 && zombies.isEmpty()) {
            clearTimer += dt;
            if (clearTimer > 1.0) {
                survivor.heal(10);     // small reward for surviving a wave
                bullets.clear();
                state = State.UPGRADE; // let the player pick 1 of 3 upgrades
                setCursorHidden(false);
            }
        }
    }

    /* ----------------------------- drawing ----------------------------- */
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        Gfx.aa(g);

        g.drawImage(background, 0, 0, null);

        switch (state) {
            case START:     drawStart(g);    break;
            case PLAYING:   drawPlaying(g);  break;
            case UPGRADE:   drawUpgrade(g);  break;
            case GAME_OVER: drawGameOver(g); break;
        }
    }

    /** Everything that lives in the game world (so it can shake together). */
    private void drawWorld(Graphics2D w, boolean showAim) {
        for (Particle p : particles) p.draw(w);

        if (showAim) {
            double sx = survivor.x + Math.cos(survivor.angle) * 40;
            double sy = survivor.y + Math.sin(survivor.angle) * 40;
            double ex = survivor.x + Math.cos(survivor.angle) * 380;
            double ey = survivor.y + Math.sin(survivor.angle) * 380;
            w.setPaint(new GradientPaint((float) sx, (float) sy, new Color(255, 80, 80, 150),
                    (float) ex, (float) ey, new Color(255, 80, 80, 0)));
            w.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{8f, 8f}, 0f));
            w.draw(new Line2D.Double(sx, sy, ex, ey));
        }

        // depth sorting: things lower on the screen are drawn in front
        zombies.sort(Comparator.comparingDouble(z -> z.y));
        boolean drewSurvivor = false;
        for (Zombie z : zombies) {
            if (!drewSurvivor && z.y > survivor.y) {
                survivor.draw(w);
                drewSurvivor = true;
            }
            z.draw(w);
        }
        if (!drewSurvivor) survivor.draw(w);

        for (Bullet b : bullets) b.draw(w);
        for (FloatText f : floats) f.draw(w);
    }

    private void drawPlaying(Graphics2D g) {
        Graphics2D w = (Graphics2D) g.create();
        if (shake > 0) {
            double k = 5 * shake / 0.25;
            w.translate((rnd.nextDouble() - 0.5) * 2 * k, (rnd.nextDouble() - 0.5) * 2 * k);
        }
        drawWorld(w, true);
        w.dispose();

        // damage feedback
        if (hurtFlash > 0) {
            g.setColor(new Color(255, 0, 0, (int) (110 * hurtFlash / 0.35)));
            g.fillRect(0, 0, W, H);
        }
        float hpFrac = survivor.hp / (float) survivor.maxHp;
        if (hpFrac < 0.3f) {                         // low health warning
            int a = (int) (70 + 50 * Math.sin(clock * 6));
            g.setPaint(new RadialGradientPaint(new Point2D.Float(W / 2f, H / 2f), 470f,
                    new float[]{0.5f, 1f},
                    new Color[]{new Color(255, 0, 0, 0), new Color(255, 0, 0, Math.max(0, Math.min(255, a)))}));
            g.fillRect(0, 0, W, H);
        }

        drawHud(g);

        // crosshair at the mouse
        g.setColor(new Color(255, 90, 90, 220));
        g.setStroke(new BasicStroke(2));
        g.drawOval(mouseX - 9, mouseY - 9, 18, 18);
        g.drawLine(mouseX - 14, mouseY, mouseX - 5, mouseY);
        g.drawLine(mouseX + 5, mouseY, mouseX + 14, mouseY);
        g.drawLine(mouseX, mouseY - 14, mouseX, mouseY - 5);
        g.drawLine(mouseX, mouseY + 5, mouseX, mouseY + 14);
        g.fillOval(mouseX - 1, mouseY - 1, 3, 3);

        // wave banner
        if (bannerTimer > 0) {
            int alpha = (int) (255 * Math.min(1.0, bannerTimer));
            g.setFont(new Font("SansSerif", Font.BOLD, 72));
            Gfx.shadowCenter(g, "WAVE " + wave, W / 2, H / 2 - 110, new Color(255, 255, 255, alpha));
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            Gfx.shadowCenter(g, (3 + wave * 2) + " zombies incoming", W / 2, H / 2 - 75,
                    Gfx.alpha(new Color(0xFFD54F), alpha));
        }
    }

    private void drawHud(Graphics2D g) {
        // ---- left: wave / score / stats
        Gfx.panel(g, 14, 12, 176, 84, 16, new Color(0, 0, 0, 125), new Color(255, 255, 255, 60));
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        Gfx.shadowText(g, "WAVE " + wave, 28, 40, new Color(0xFFD54F));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        Gfx.shadowText(g, "SCORE " + score, 28, 64, Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        Gfx.shadowText(g, "DMG " + survivor.bulletDamage + "    SPD " + (int) survivor.moveSpeed,
                28, 85, new Color(255, 255, 255, 205));

        // ---- center: health bar
        int bw = 240, bh = 26, bx = W / 2 - bw / 2 + 10, by = 17;
        Gfx.panel(g, bx - 40, by - 7, bw + 50, bh + 14, 20, new Color(0, 0, 0, 130), new Color(255, 255, 255, 60));
        Gfx.heart(g, bx - 17, by + bh / 2.0 + 1, 9, new Color(0xE53935));

        double f = survivor.hp / (double) survivor.maxHp;
        g.setColor(new Color(70, 10, 10));
        g.fillRoundRect(bx, by, bw, bh, 14, 14);
        Color top, bottom;
        if (f > 0.6)      { top = new Color(0x7BE07F); bottom = new Color(0x2E9E3A); }
        else if (f > 0.3) { top = new Color(0xFFD54F); bottom = new Color(0xE08A00); }
        else              { top = new Color(0xFF6B6B); bottom = new Color(0xC62828); }
        int fw = (int) (bw * f);
        if (fw > 0) {
            Shape old = g.getClip();
            g.clip(new RoundRectangle2D.Double(bx, by, bw, bh, 14, 14));
            g.setPaint(new GradientPaint(0, by, top, 0, by + bh, bottom));
            g.fillRect(bx, by, fw, bh);
            g.setColor(new Color(255, 255, 255, 70));
            g.fillRect(bx, by, fw, bh / 2);
            g.setClip(old);
        }
        g.setColor(new Color(0, 0, 0, 200));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(bx, by, bw, bh, 14, 14);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        Gfx.shadowCenter(g, survivor.hp + " / " + survivor.maxHp, bx + bw / 2, by + 18, Color.WHITE);

        // ---- right: zombies left
        Gfx.panel(g, W - 176, 12, 162, 46, 16, new Color(0, 0, 0, 125), new Color(255, 255, 255, 60));
        g.setColor(new Color(0x2BAA3F));
        g.fillRoundRect(W - 164, 22, 24, 26, 8, 8);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(W - 164, 22, 24, 26, 8, 8);
        g.setColor(Color.WHITE);
        g.fillOval(W - 160, 28, 7, 7);
        g.fillOval(W - 151, 28, 7, 7);
        g.setColor(Color.BLACK);
        g.fillOval(W - 158, 30, 3, 3);
        g.fillOval(W - 149, 30, 3, 3);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        Gfx.shadowText(g, (zombies.size() + toSpawn) + " LEFT", W - 130, 42, Color.WHITE);

        // best score
        Gfx.panel(g, W - 176, 66, 162, 34, 14, new Color(0, 0, 0, 125), new Color(255, 255, 255, 60));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        Gfx.shadowText(g, "BEST " + Math.max(highScore, score), W - 164, 89,
                score > highScore ? new Color(0xFFD54F) : new Color(255, 255, 255, 220));
    }

    private void drawStart(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 115));
        g.fillRect(0, 0, W, H);

        // title with glow
        Color green = new Color(0x56D364);
        g.setFont(new Font("SansSerif", Font.BOLD, 84));
        for (int i = 3; i >= 1; i--) {
            g.setColor(Gfx.alpha(green, 20));
            for (int dx = -i; dx <= i; dx += i) {
                for (int dy = -i; dy <= i; dy += i) {
                    Gfx.centerText(g, "ZOMBIE", W / 2 + dx * 2, 135 + dy * 2);
                }
            }
        }
        Gfx.shadowCenter(g, "ZOMBIE", W / 2, 135, green);
        g.setFont(new Font("SansSerif", Font.BOLD, 40));
        Gfx.shadowCenter(g, "SURVIVAL", W / 2, 183, new Color(0xF2F2F2));

        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        Gfx.shadowCenter(g, "Survive the virus until you graduate in 4 years!", W / 2, 218,
                new Color(255, 255, 255, 215));

        // controls panel
        Gfx.panel(g, W / 2 - 240, 238, 480, 76, 18, new Color(0, 0, 0, 130), new Color(255, 255, 255, 60));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        Gfx.shadowCenter(g, "W A S D / ARROWS = move      MOUSE = aim      AUTO-FIRE", W / 2, 268, Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        Gfx.shadowCenter(g, "Clear a wave, then pick 1 of 3 upgrades", W / 2, 294, new Color(0xFFD54F));

        if (highScore > 0) {
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            Gfx.shadowCenter(g, "BEST SCORE " + highScore + "   (Wave " + highWave + ")", W / 2, 346,
                    new Color(0xFFD54F));
        }

        // animated menu characters
        demoSurvivor.draw(g);
        for (Zombie z : demoZombies) {
            z.time = clock;
            z.lookAt(mouseX, mouseY);
            z.draw(g);
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        Gfx.shadowCenter(g, "Survivor", (int) demoSurvivor.x, 472, Color.WHITE);
        Gfx.shadowCenter(g, "Zombie", (int) demoZombies[0].x, 472, Color.WHITE);
        Gfx.shadowCenter(g, "Girl Zombie", (int) demoZombies[1].x, 472, Color.WHITE);
        Gfx.shadowCenter(g, "Fat Zombie", (int) demoZombies[2].x, 472, Color.WHITE);

        drawButton(g, startBtn, "START", new Color(0xD86A9A));
    }

    private void drawUpgrade(Graphics2D g) {
        survivor.draw(g);

        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, W, H);

        g.setFont(new Font("SansSerif", Font.BOLD, 48));
        Gfx.shadowCenter(g, "WAVE " + wave + " CLEARED!", W / 2, 115, new Color(0x56D364));
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        Gfx.shadowCenter(g, "Choose 1 upgrade  (click a card or press 1 / 2 / 3)", W / 2, 158,
                new Color(255, 255, 255, 215));

        String[] names = {"DAMAGE", "HEALTH", "SPEED"};
        String[] gain  = {"+1 bullet damage", "+25 max HP", "+30 move speed"};
        String[] extra = {"stronger shots", "and heal 25 HP", "outrun the horde"};
        String[] now   = {"Now: " + survivor.bulletDamage, "Now: " + survivor.maxHp,
                "Now: " + (int) survivor.moveSpeed};
        Color[] cols   = {new Color(0xE8821E), new Color(0xD03030), new Color(0x2E8FD0)};

        for (int i = 0; i < cards.length; i++) {
            Rectangle r = cards[i];
            boolean hover = r.contains(mouseX, mouseY);
            int oy = hover ? -8 : 0;
            int x = r.x, y = r.y + oy, w = r.width, h = r.height;

            // shadow
            g.setColor(new Color(0, 0, 0, 110));
            g.fillRoundRect(x + 4, y + 8, w, h, 22, 22);

            // card body
            Color c = cols[i];
            g.setPaint(new GradientPaint(0, y, hover ? c.brighter() : c, 0, y + h, c.darker().darker()));
            g.fillRoundRect(x, y, w, h, 22, 22);
            g.setColor(new Color(255, 255, 255, 40));
            g.fillRoundRect(x + 6, y + 6, w - 12, 90, 18, 18);       // gloss
            if (hover) {
                g.setColor(new Color(255, 255, 255, 60));
                g.setStroke(new BasicStroke(8f));
                g.drawRoundRect(x, y, w, h, 22, 22);
            }
            g.setColor(hover ? Color.WHITE : new Color(0, 0, 0, 200));
            g.setStroke(new BasicStroke(hover ? 4f : 3f));
            g.drawRoundRect(x, y, w, h, 22, 22);

            // key badge
            g.setColor(new Color(0, 0, 0, 130));
            g.fillOval(x + 10, y + 10, 30, 30);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            Gfx.centerText(g, String.valueOf(i + 1), x + 25, y + 32);

            // icon
            int cx = x + w / 2;
            int cy = y + 88;
            if (i == 0) {
                Gfx.glowDot(g, cx - 26, cy + 6, 9, new Color(0xFFE000));
                Gfx.glowDot(g, cx, cy - 6, 11, new Color(0xFF7043));
                Gfx.glowDot(g, cx + 26, cy + 6, 9, new Color(0xFFE000));
            } else if (i == 1) {
                Gfx.heart(g, cx, cy, 26, new Color(0xFF5A5A));
            } else {
                Gfx.bolt(g, cx, cy, 30, new Color(0xFFE94A));
            }

            // text
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 30));
            Gfx.shadowCenter(g, names[i], cx, y + 160, Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            Gfx.shadowCenter(g, gain[i], cx, y + 192, Color.WHITE);
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            Gfx.shadowCenter(g, extra[i], cx, y + 214, new Color(255, 255, 255, 215));
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            Gfx.shadowCenter(g, now[i], cx, y + 244, new Color(0xFFF3A0));
        }
    }

    private void drawGameOver(Graphics2D g) {
        drawWorld(g, false);

        g.setColor(new Color(0, 0, 0, 175));
        g.fillRect(0, 0, W, H);

        // ghost floating up from where the survivor fell
        drawGhost(g, survivor.x, Math.max(258, survivor.y - 42 - Math.min(70, overTime * 55)));

        g.setFont(new Font("SansSerif", Font.BOLD, 76));
        Gfx.shadowCenter(g, "GAME OVER", W / 2, 120, new Color(0xE53935));

        g.setFont(new Font("SansSerif", Font.PLAIN, 24));
        Gfx.shadowCenter(g, "Reached Wave " + wave + "   |   Score " + score, W / 2, 170, Color.WHITE);
        if (newRecord) {
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            Gfx.shadowCenter(g, "NEW HIGH SCORE!", W / 2, 208, new Color(0xFFD54F));
        } else {
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            Gfx.shadowCenter(g, "BEST " + highScore + "   (Wave " + highWave + ")", W / 2, 204,
                    new Color(0xFFD54F));
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            Gfx.shadowCenter(g, "You didn't make it to graduation...", W / 2, 232, new Color(255, 255, 255, 170));
        }

        drawButton(g, retryBtn, "Retry", new Color(0xB5714F));
    }

    private void drawGhost(Graphics2D g, double gx, double gy) {
        Graphics2D q = (Graphics2D) g.create();
        q.setColor(new Color(255, 255, 255, 215));
        q.fill(new Ellipse2D.Double(gx - 18, gy - 18, 36, 36));
        q.fillRect((int) gx - 18, (int) gy, 36, 24);
        Polygon skirt = new Polygon();
        skirt.addPoint((int) gx - 18, (int) gy + 22);
        skirt.addPoint((int) gx - 12, (int) gy + 32);
        skirt.addPoint((int) gx - 6, (int) gy + 22);
        skirt.addPoint((int) gx, (int) gy + 32);
        skirt.addPoint((int) gx + 6, (int) gy + 22);
        skirt.addPoint((int) gx + 12, (int) gy + 32);
        skirt.addPoint((int) gx + 18, (int) gy + 22);
        q.fillPolygon(skirt);
        q.setColor(Color.BLACK);
        q.fillOval((int) gx - 10, (int) gy - 6, 7, 9);
        q.fillOval((int) gx + 3, (int) gy - 6, 7, 9);
        q.setStroke(new BasicStroke(2f));
        q.drawArc((int) gx - 6, (int) gy + 6, 12, 8, 200, 140);
        q.dispose();
    }

    private void drawButton(Graphics2D g, Rectangle r, String text, Color c) {
        boolean hover = r.contains(mouseX, mouseY);

        g.setColor(new Color(0, 0, 0, 110));
        g.fillRoundRect(r.x + 3, r.y + 6, r.width, r.height, 22, 22);

        g.setPaint(new GradientPaint(0, r.y, hover ? c.brighter().brighter() : c.brighter(),
                0, r.y + r.height, c.darker()));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 22, 22);
        g.setColor(new Color(255, 255, 255, 55));
        g.fillRoundRect(r.x + 5, r.y + 4, r.width - 10, r.height / 2 - 2, 16, 16);

        g.setColor(hover ? Color.WHITE : new Color(0, 0, 0, 200));
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 22, 22);

        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        Gfx.shadowCenter(g, text, r.x + r.width / 2, r.y + r.height / 2 + 10, Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        Gfx.shadowCenter(g, "(or press ENTER)", r.x + r.width / 2, r.y + r.height + 22,
                new Color(255, 255, 255, 160));
    }
}