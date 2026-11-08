package net.ccasino.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.joml.Matrix3x2fStack;

/** Einfache 2D-Partikel: Münzregen, Konfetti, Funken, Explosionen. */
public final class Particles {
	private static final class P {
		float x, y, vx, vy, life, max, size, rot, vrot, gravity, drag;
		int color;
		boolean coin;
	}

	private final List<P> list = new ArrayList<>();
	private final Random rnd = new Random();

	public static final int[] GOLDS = {0xFFF5C542, 0xFFFFE17A, 0xFFE0A526, 0xFFFFF4C2};
	public static final int[] CONFETTI = {0xFFFF4F6D, 0xFF4FC3FF, 0xFFFFD84F, 0xFF6DFF8E, 0xFFC77DFF, 0xFFFFFFFF};
	public static final int[] FIRE = {0xFFFFD45C, 0xFFFF9A3C, 0xFFFF5A2E, 0xFFFFF1B0};

	private float rf(float a, float b) { return a + rnd.nextFloat() * (b - a); }

	/** Münz-Explosion aus einem Punkt. */
	public void coins(float x, float y, int n) {
		for (int i = 0; i < n; i++) {
			P p = new P();
			double a = rnd.nextDouble() * Math.PI * 2;
			float sp = rf(40, 150);
			p.x = x; p.y = y;
			p.vx = (float) Math.cos(a) * sp;
			p.vy = (float) Math.sin(a) * sp - 80;
			p.max = p.life = rf(0.9f, 1.6f);
			p.size = rf(2.5f, 4f);
			p.color = GOLDS[rnd.nextInt(GOLDS.length)];
			p.gravity = 260;
			p.drag = 0.6f;
			p.coin = true;
			p.vrot = rf(4, 12);
			list.add(p);
		}
	}

	/** Konfetti-Regen über die ganze Breite. */
	public void confetti(float x, float w, float y, int n) {
		for (int i = 0; i < n; i++) {
			P p = new P();
			p.x = x + rnd.nextFloat() * w;
			p.y = y - rnd.nextFloat() * 60;
			p.vx = rf(-25, 25);
			p.vy = rf(20, 70);
			p.max = p.life = rf(2.0f, 3.5f);
			p.size = rf(2, 3.5f);
			p.color = CONFETTI[rnd.nextInt(CONFETTI.length)];
			p.gravity = 40;
			p.drag = 0.3f;
			p.rot = rf(0, 6);
			p.vrot = rf(-8, 8);
			list.add(p);
		}
	}

	public void burst(float x, float y, int n, int[] colors, float speed, float life) {
		for (int i = 0; i < n; i++) {
			P p = new P();
			double a = rnd.nextDouble() * Math.PI * 2;
			float sp = rf(speed * 0.3f, speed);
			p.x = x; p.y = y;
			p.vx = (float) Math.cos(a) * sp;
			p.vy = (float) Math.sin(a) * sp;
			p.max = p.life = rf(life * 0.5f, life);
			p.size = rf(1.5f, 3.5f);
			p.color = colors[rnd.nextInt(colors.length)];
			p.gravity = 60;
			p.drag = 2.2f;
			list.add(p);
		}
	}

	/** Einzelner Funke (z. B. Raketenantrieb). */
	public void spark(float x, float y, float vx, float vy, int color, float life) {
		P p = new P();
		p.x = x; p.y = y;
		p.vx = vx + rf(-12, 12);
		p.vy = vy + rf(-12, 12);
		p.max = p.life = rf(life * 0.5f, life);
		p.size = rf(1.2f, 2.6f);
		p.color = color;
		p.drag = 1.5f;
		list.add(p);
	}

	public void clear() { list.clear(); }

	public void updateAndRender(Ui ui) {
		float dt = ui.dt;
		Matrix3x2fStack m = ui.pose();
		Iterator<P> it = list.iterator();
		while (it.hasNext()) {
			P p = it.next();
			p.life -= dt;
			if (p.life <= 0 || p.y > 300) {
				it.remove();
				continue;
			}
			float d = (float) Math.exp(-p.drag * dt);
			p.vx *= d;
			p.vy = p.vy * d + p.gravity * dt;
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			p.rot += p.vrot * dt;
			float a = Ui.clamp(p.life / (p.max * 0.4f), 0, 1);
			if (p.coin) {
				// "Drehende" Münze: Breite pendelt
				float w = Math.max(0.6f, Math.abs((float) Math.cos(p.rot)) * p.size * 2);
				ui.rect(p.x - w / 2, p.y - p.size, w, p.size * 2, Ui.alpha(Ui.shade(p.color, 0.7f), a));
				ui.rect(p.x - w / 2 + 0.5f, p.y - p.size + 0.5f, Math.max(0.5f, w - 1), p.size * 2 - 1, Ui.alpha(p.color, a));
			} else if (p.vrot != 0) {
				m.pushMatrix();
				m.translate(p.x, p.y);
				m.rotate(p.rot);
				ui.rect(-p.size / 2, -p.size / 4, p.size, p.size / 2 + 0.5f, Ui.alpha(p.color, a));
				m.popMatrix();
			} else {
				ui.rect(p.x - p.size / 2, p.y - p.size / 2, p.size, p.size, Ui.alpha(p.color, a));
			}
		}
	}
}
