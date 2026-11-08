package net.ccasino.ui;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Immediate-Mode-Zeichenhelfer für den Casino-Screen.
 * Alle Koordinaten sind in "Canvas"-Pixeln (420x250), der Screen skaliert das Ganze.
 */
public final class Ui {
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GOLD = 0xFFF5C542;
	public static final int GOLD_DARK = 0xFFB07A1E;
	public static final int RED = 0xFFD7263D;
	public static final int BLACK = 0xFF16161A;
	public static final int GREEN = 0xFF1E9E52;
	public static final int MUTED = 0xFFA9BFB5;
	public static final int WIN = 0xFF5CF08A;
	public static final int LOSS = 0xFFFF5A5A;

	public GuiGraphicsExtractor g;
	public Font font;
	public float mx, my;
	public boolean clicked;
	public int clickButton;
	public float cx, cy;
	public double time;
	public float dt;

	private final Map<String, Float> anim = new HashMap<>();

	public void begin(GuiGraphicsExtractor g, Font font, float mx, float my, double time, float dt) {
		this.g = g;
		this.font = font;
		this.mx = mx;
		this.my = my;
		this.time = time;
		this.dt = dt;
	}

	public Matrix3x2fStack pose() { return g.pose(); }

	// ------------------------------------------------------------------ Mathe / Easing

	public static float clamp(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }
	public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

	public static float easeOutCubic(float t) {
		t = clamp(t, 0, 1);
		float u = 1 - t;
		return 1 - u * u * u;
	}

	public static float easeInOutCubic(float t) {
		t = clamp(t, 0, 1);
		return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
	}

	public static float easeOutBack(float t) {
		return easeOutBack(t, 1.70158f);
	}

	public static float easeOutBack(float t, float c1) {
		t = clamp(t, 0, 1);
		float c3 = c1 + 1;
		float u = t - 1;
		return 1 + c3 * u * u * u + c1 * u * u;
	}

	/** Weiche Annäherung pro Schlüssel – für Hover-Effekte etc. */
	public float approach(String key, float target, float speed) {
		float v = anim.getOrDefault(key, target);
		v += (target - v) * (1 - (float) Math.exp(-speed * dt));
		anim.put(key, v);
		return v;
	}

	public float pulse(float speed) { return 0.5f + 0.5f * (float) Math.sin(time * speed); }

	// ------------------------------------------------------------------ Farben

	public static int argb(int a, int r, int g, int b) {
		return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
	}

	public static int alpha(int c, float a) {
		int base = (c >>> 24) & 0xFF;
		int na = Math.round(base * clamp(a, 0, 1));
		return (na << 24) | (c & 0xFFFFFF);
	}

	public static int mix(int c1, int c2, float t) {
		t = clamp(t, 0, 1);
		int a = Math.round(lerp((c1 >>> 24) & 0xFF, (c2 >>> 24) & 0xFF, t));
		int r = Math.round(lerp((c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF, t));
		int gg = Math.round(lerp((c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF, t));
		int b = Math.round(lerp(c1 & 0xFF, c2 & 0xFF, t));
		return argb(a, r, gg, b);
	}

	/** f < 1 abdunkeln, f > 1 aufhellen. */
	public static int shade(int c, float f) {
		if (f > 1) return mix(c, (c & 0xFF000000) | 0xFFFFFF, f - 1);
		int r = Math.round(((c >> 16) & 0xFF) * f);
		int gg = Math.round(((c >> 8) & 0xFF) * f);
		int b = Math.round((c & 0xFF) * f);
		return (c & 0xFF000000) | r << 16 | gg << 8 | b;
	}

	public static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		float r, gg, b;
		int i = (int) (h * 6);
		float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		switch (i % 6) {
			case 0 -> { r = v; gg = t; b = p; }
			case 1 -> { r = q; gg = v; b = p; }
			case 2 -> { r = p; gg = v; b = t; }
			case 3 -> { r = p; gg = q; b = v; }
			case 4 -> { r = t; gg = p; b = v; }
			default -> { r = v; gg = p; b = q; }
		}
		return argb(255, Math.round(r * 255), Math.round(gg * 255), Math.round(b * 255));
	}

	// ------------------------------------------------------------------ Primitive

	public void rect(float x, float y, float w, float h, int c) {
		if (w <= 0 || h <= 0 || (c >>> 24) == 0) return;
		g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), c);
	}

	public void grad(float x, float y, float w, float h, int top, int bottom) {
		if (w <= 0 || h <= 0) return;
		g.fillGradient(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), top, bottom);
	}

	public void outline(float x, float y, float w, float h, int c) {
		rect(x, y, w, 1, c);
		rect(x, y + h - 1, w, 1, c);
		rect(x, y + 1, 1, h - 2, c);
		rect(x + w - 1, y + 1, 1, h - 2, c);
	}

	/** Abgerundetes Rechteck (Radius 2, Pixel-Look). */
	public void round(float x, float y, float w, float h, int c) {
		rect(x + 2, y, w - 4, h, c);
		rect(x + 1, y + 1, 1, h - 2, c);
		rect(x + w - 2, y + 1, 1, h - 2, c);
		rect(x, y + 2, 1, h - 4, c);
		rect(x + w - 1, y + 2, 1, h - 4, c);
	}

	public void roundGrad(float x, float y, float w, float h, int top, int bottom) {
		grad(x + 2, y, w - 4, h, top, bottom);
		int t1 = mix(top, bottom, 1 / h), b1 = mix(top, bottom, 1 - 1 / h);
		int t2 = mix(top, bottom, 2 / h), b2 = mix(top, bottom, 1 - 2 / h);
		grad(x + 1, y + 1, 1, h - 2, t1, b1);
		grad(x + w - 2, y + 1, 1, h - 2, t1, b1);
		grad(x, y + 2, 1, h - 4, t2, b2);
		grad(x + w - 1, y + 2, 1, h - 4, t2, b2);
	}

	/** Panel mit Rand, Verlauf und Glanzkante. */
	public void panel(float x, float y, float w, float h, int top, int bottom, int border) {
		round(x, y, w, h, border);
		roundGrad(x + 1, y + 1, w - 2, h - 2, top, bottom);
		rect(x + 3, y + 1, w - 6, 1, alpha(WHITE, 0.12f));
	}

	public void shadow(float x, float y, float w, float h, float strength) {
		round(x + 1, y + 2, w, h, alpha(0xFF000000, 0.35f * strength));
		round(x + 2, y + 3, w, h, alpha(0xFF000000, 0.2f * strength));
	}

	/** Gefüllter Kreis aus horizontalen Spans (gleiche Zeilen werden zusammengefasst). */
	public void circle(float cx, float cy, float r, int c) {
		if (r <= 0.3f) return;
		Matrix3x2fStack p = pose();
		p.pushMatrix();
		p.translate(cx, cy);
		int rr = (int) Math.ceil(r);
		int startRow = Integer.MIN_VALUE, curHw = -1;
		for (int dy = -rr; dy <= rr; dy++) {
			int hw = -1;
			if (dy < rr) {
				float yy = dy + 0.5f;
				float d = r * r - yy * yy;
				hw = d > 0 ? Math.round((float) Math.sqrt(d)) : -1;
				if (hw == 0) hw = -1;
			}
			if (hw != curHw) {
				if (curHw > 0) g.fill(-curHw, startRow, curHw, dy, c);
				curHw = hw;
				startRow = dy;
			}
		}
		p.popMatrix();
	}

	/** Ring zwischen r-thick und r. */
	public void ring(float cx, float cy, float r, float thick, int c) {
		Matrix3x2fStack p = pose();
		p.pushMatrix();
		p.translate(cx, cy);
		int rr = (int) Math.ceil(r);
		float ri = r - thick;
		for (int dy = -rr; dy < rr; dy++) {
			float yy = dy + 0.5f;
			float d = r * r - yy * yy;
			if (d <= 0) continue;
			int ho = Math.round((float) Math.sqrt(d));
			float di = ri * ri - yy * yy;
			int hi = di > 0 ? Math.round((float) Math.sqrt(di)) : 0;
			if (ho <= hi) continue;
			g.fill(-ho, dy, -hi, dy + 1, c);
			g.fill(hi, dy, ho, dy + 1, c);
		}
		p.popMatrix();
	}

	/** Linie aus kleinen Quadraten. */
	public void line(float x1, float y1, float x2, float y2, float thick, int c) {
		float dx = x2 - x1, dy = y2 - y1;
		int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			rect(x1 + dx * t - thick / 2, y1 + dy * t - thick / 2, thick, thick, c);
		}
	}

	public void scissor(float x, float y, float w, float h) {
		g.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
	}

	public void endScissor() { g.disableScissor(); }

	// ------------------------------------------------------------------ Text & Items

	public int width(String s) { return font.width(s); }

	public void text(String s, float x, float y, int c) { textS(s, x, y, 1, c, false, true); }
	public void textC(String s, float x, float y, int c) { textS(s, x, y, 1, c, true, true); }

	public void textS(String s, float x, float y, float scale, int c, boolean centered, boolean shadow) {
		if ((c >>> 24) < 8) return;
		Matrix3x2fStack p = pose();
		p.pushMatrix();
		p.translate(x, y);
		if (scale != 1) p.scale(scale, scale);
		int ox = centered ? -font.width(s) / 2 : 0;
		g.text(font, s, ox, 0, c, shadow);
		p.popMatrix();
	}

	/** Rechtsbündig */
	public void textR(String s, float x, float y, float scale, int c) {
		textS(s, x - font.width(s) * scale, y, scale, c, false, true);
	}

	/** Goldener Titel mit wanderndem Glanz pro Buchstabe. */
	public void shimmerText(String s, float cx, float y, float scale, int base, int shine) {
		float total = font.width(s) * scale;
		float x = cx - total / 2;
		float sweep = (float) ((time * 0.6) % 1.6) - 0.3f;
		for (int i = 0; i < s.length(); i++) {
			String ch = String.valueOf(s.charAt(i));
			float rel = (x - (cx - total / 2)) / Math.max(1, total);
			float d = Math.abs(rel - sweep);
			int c = mix(base, shine, clamp(1 - d * 6, 0, 1));
			textS(ch, x, y, scale, c, false, true);
			x += font.width(ch) * scale;
		}
	}

	public void item(ItemStack st, float x, float y, float scale) {
		Matrix3x2fStack p = pose();
		p.pushMatrix();
		p.translate(x, y);
		if (scale != 1) p.scale(scale, scale);
		g.fakeItem(st, 0, 0);
		p.popMatrix();
	}

	/** Item um seinen Mittelpunkt gedreht/skaliert. */
	public void itemC(ItemStack st, float cx, float cy, float scale, float rot) {
		Matrix3x2fStack p = pose();
		p.pushMatrix();
		p.translate(cx, cy);
		if (rot != 0) p.rotate(rot);
		p.scale(scale, scale);
		g.fakeItem(st, -8, -8);
		p.popMatrix();
	}

	// ------------------------------------------------------------------ Eingabe

	public boolean hover(float x, float y, float w, float h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	public boolean click(float x, float y, float w, float h) { return clickBtn(x, y, w, h, 0); }

	public boolean rclick(float x, float y, float w, float h) { return clickBtn(x, y, w, h, 1); }

	private boolean clickBtn(float x, float y, float w, float h, int button) {
		if (clicked && clickButton == button && cx >= x && cy >= y && cx < x + w && cy < y + h) {
			clicked = false;
			return true;
		}
		return false;
	}

	/** Gezeichneter Casino-Button. Liefert true beim Klick. */
	public boolean button(String id, String label, float x, float y, float w, float h, int color, boolean enabled) {
		boolean hov = enabled && hover(x, y, w, h);
		float t = approach("btn:" + id, hov ? 1 : 0, 16);
		int base = enabled ? color : 0xFF3C4440;
		float by = y - t * 1.5f;
		round(x, y + 2, w, h, alpha(0xFF000000, 0.45f));
		round(x, by, w, h, shade(base, 0.5f));
		roundGrad(x + 1, by + 1, w - 2, h - 2, shade(base, 1.18f + 0.15f * t), shade(base, 0.82f + 0.12f * t));
		rect(x + 3, by + 1, w - 6, 1, alpha(WHITE, 0.3f + 0.25f * t));
		if (t > 0.01f) outline(x - 1, by - 1, w + 2, h + 2, alpha(GOLD, 0.6f * t));
		textS(label, x + w / 2, by + (h - 8) / 2f + 0.5f, 1, enabled ? WHITE : 0xFF8A938F, true, true);
		if (enabled && click(x, y - 2, w, h + 4)) {
			Sounds.click();
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ Casino-Elemente

	public static int chipColor(long value) {
		if (value >= 1000) return 0xFF222226;
		if (value >= 500) return 0xFF7B3FC4;
		if (value >= 100) return 0xFF1F8A4C;
		if (value >= 50) return 0xFFC8283C;
		return 0xFF2E6FD8;
	}

	/** Pokerchip mit Randstreifen und Beschriftung. */
	public void chip(float cx, float cy, float r, int color, String label) {
		circle(cx + 0.5f, cy + 1.5f, r, alpha(0xFF000000, 0.45f));
		circle(cx, cy, r, shade(color, 0.55f));
		circle(cx, cy, r - 1, color);
		Matrix3x2fStack p = pose();
		int stripe = color == 0xFF222226 ? GOLD : 0xFFF2F2F2;
		float sl = Math.max(1.5f, r * 0.32f);
		for (int i = 0; i < 6; i++) {
			p.pushMatrix();
			p.translate(cx, cy);
			p.rotate((float) (i * Math.PI / 3));
			rect(-1.2f, -(r - 1), 2.4f, sl, stripe);
			p.popMatrix();
		}
		float ir = r * 0.6f;
		circle(cx, cy, ir, shade(color, 0.7f));
		circle(cx, cy, ir - 1, shade(color, 1.1f));
		if (label != null && r >= 5) {
			float sc = r >= 10 ? 0.75f : 0.5f;
			textS(label, cx, cy - 4 * sc + 0.5f, sc, WHITE, true, true);
		}
	}

	/** Kleine Glühbirne für die Lauflichter. */
	public void bulb(float x, float y, boolean on, int color) {
		if (on) {
			circle(x, y, 3.5f, alpha(color, 0.25f));
			circle(x, y, 2f, color);
			rect(x - 1, y - 1, 1, 1, alpha(WHITE, 0.9f));
		} else {
			circle(x, y, 2f, shade(color, 0.3f));
		}
	}
}
