package net.ccasino.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import net.ccasino.Wallet;
import net.ccasino.ui.CasinoScreen;
import net.ccasino.ui.Particles;
import net.ccasino.ui.Sounds;
import net.ccasino.ui.Ui;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Crash: eine Rakete steigt, der Multiplikator wächst – rechtzeitig auszahlen, bevor sie explodiert! */
public class CrashGame extends Game {
	private static final float K = 0.16f; // Wachstumsrate
	private static final float GX = 22, GY = 38, GW = 270, GH = 150;
	private static final ItemStack ROCKET = new ItemStack(Items.FIREWORK_ROCKET);

	private boolean running, crashed;
	private double startT, crashT = -10;
	private double crashPoint;
	private double cashedAt; // 0 = nicht ausgezahlt
	private long stake;
	private float lastHeadX, lastHeadY;
	private int lastWhole = 1;
	private final List<Double> history = new ArrayList<>();

	public CrashGame(CasinoScreen s) {
		super(s);
	}

	@Override public String name() { return "Crash"; }
	@Override public ItemStack icon() { return ROCKET; }
	@Override public String[] description() { return new String[] {"Die Rakete steigt…", "Zahl rechtzeitig aus,", "bevor sie explodiert!"}; }
	@Override public int accent() { return 0xFFE0861E; }
	@Override public boolean busy() { return running; }

	@Override
	public void onKey(int key) {
		if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
			if (!running) launch();
			else cashOut();
		}
	}

	private static double mult(double t) { return Math.exp(K * t); }

	private void launch() {
		if (running) return;
		long b = bet();
		if (!Wallet.take(b)) {
			s.notEnoughChips();
			return;
		}
		stake = b;
		double u = rnd.nextDouble();
		crashPoint = Math.max(1.0, Math.floor(0.97 / (1 - u) * 100) / 100.0);
		crashPoint = Math.min(crashPoint, 50.0);
		running = true;
		crashed = false;
		cashedAt = 0;
		lastWhole = 1;
		lastHeadX = GX;
		lastHeadY = GY + GH;
		startT = s.now();
		Sounds.launch();
	}

	private void cashOut() {
		if (!running || cashedAt > 0) return;
		double m = Math.min(mult(s.now() - startT), crashPoint);
		cashedAt = m;
		long win = (long) Math.floor(stake * m);
		Wallet.give(win);
		Wallet.endRound(win - stake);
		s.banner(fmt(m) + "x", "Ausgezahlt: +" + Wallet.fmt(win), Ui.WIN, 112);
		s.celebrate(win, stake, lastHeadX, lastHeadY);
	}

	private void doCrash() {
		running = false;
		crashed = true;
		crashT = s.now();
		history.add(0, crashPoint);
		if (history.size() > 9) history.remove(history.size() - 1);
		s.fx().burst(lastHeadX, lastHeadY, 70, Particles.FIRE, 140, 1.2f);
		s.fx().burst(lastHeadX, lastHeadY, 25, new int[] {0xFF404040, 0xFF707070}, 60, 1.6f);
		s.shake(4);
		Sounds.explode();
		if (cashedAt <= 0) {
			Wallet.endRound(-stake);
			Sounds.lose();
		}
	}

	@Override
	public void forceFinish() {
		if (running) {
			running = false;
			if (cashedAt <= 0) Wallet.endRound(-stake);
		}
	}

	private static String fmt(double m) { return String.format(Locale.ROOT, "%.2f", m); }

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(Ui ui) {
		double el = running ? ui.time - startT : (crashed ? Math.log(crashPoint) / K : 0);
		double m = mult(el);
		if (running && m >= crashPoint) {
			el = Math.log(crashPoint) / K;
			m = crashPoint;
			doCrash();
		}
		if (running && (int) m > lastWhole) {
			lastWhole = (int) m;
			Sounds.bell(0.8f + Math.min(1.2f, lastWhole * 0.1f));
		}

		drawGraph(ui, el, m);
		drawPanel(ui, m);
	}

	private void drawGraph(Ui ui, double el, double m) {
		ui.panel(GX - 6, GY - 6, GW + 12, GH + 22, 0xE00C1214, 0xE0060A0B, 0xFF2A3036);
		double tMax = Math.max(8, el * 1.15);
		double mMax = Math.max(2, m * 1.2);

		// Raster
		double[] steps = {0.25, 0.5, 1, 2, 5, 10, 20};
		double step = steps[steps.length - 1];
		for (double st : steps) if ((mMax - 1) / st <= 5) { step = st; break; }
		for (double v = 1 + step; v < mMax; v += step) {
			float y = (float) (GY + GH - (v - 1) / (mMax - 1) * GH);
			ui.rect(GX, y, GW, 1, 0x18FFFFFF);
			ui.textS(fmt(v).replaceAll("0+$", "").replaceAll("\\.$", "") + "x", GX + 2, y - 6, 0.5f, 0x80FFFFFF, false, false);
		}
		int tStep = tMax > 30 ? 10 : (tMax > 14 ? 5 : 2);
		for (int t = tStep; t < tMax; t += tStep) {
			float x = (float) (GX + t / tMax * GW);
			ui.rect(x, GY, 1, GH, 0x10FFFFFF);
			ui.textS(t + "s", x, GY + GH + 3, 0.5f, 0x80FFFFFF, true, false);
		}
		ui.rect(GX, GY + GH, GW, 1, 0x60FFFFFF);
		ui.rect(GX, GY, 1, GH, 0x60FFFFFF);

		if (!running && !crashed) {
			// Startbildschirm
			ui.itemC(ROCKET, GX + 20, GY + GH - 12, 2, (float) (Math.PI / 4) + (float) Math.sin(ui.time * 3) * 0.05f);
			ui.textS("BEREIT ZUM START", GX + GW / 2, GY + GH / 2 - 14, 1.5f, Ui.alpha(Ui.WHITE, 0.6f + 0.4f * ui.pulse(4)), true, true);
			ui.textS("Einsatz wählen und Leertaste drücken", GX + GW / 2, GY + GH / 2 + 4, 0.75f, Ui.MUTED, true, false);
			return;
		}

		// Kurve mit Fläche
		int lineCol = crashed ? Ui.LOSS : (cashedAt > 0 ? Ui.WIN : 0xFFFFB238);
		int fillCol = Ui.alpha(lineCol, 0.16f);
		float headX = GX, headY = GY + GH;
		float py = -1;
		int cols = (int) Math.max(1, Math.floor(el / tMax * GW));
		for (int i = 0; i <= cols; i++) {
			double tt = tMax * i / GW;
			double mm = mult(tt);
			float x = GX + i;
			float y = (float) (GY + GH - (mm - 1) / (mMax - 1) * GH);
			ui.rect(x, y, 1, GY + GH - y, fillCol);
			float top = py < 0 ? y : Math.min(py, y);
			float bot = py < 0 ? y : Math.max(py, y);
			ui.rect(x, top - 1, 1, bot - top + 2, lineCol);
			py = y;
			headX = x;
			headY = y;
		}
		lastHeadX = headX;
		lastHeadY = headY;

		// Auszahl-Markierung
		if (cashedAt > 0) {
			float cy = (float) (GY + GH - (cashedAt - 1) / (mMax - 1) * GH);
			ui.rect(GX, cy, GW, 1, Ui.alpha(Ui.WIN, 0.6f));
			ui.textS("Du: " + fmt(cashedAt) + "x", GX + GW - 2, cy - 7, 0.5f, Ui.WIN, false, false);
		}

		// Rakete + Flammen
		if (running) {
			double dm = mult(el + 0.05) - m;
			float dx = (float) (0.05 / tMax * GW), dy = (float) (-dm / (mMax - 1) * GH);
			float ang = (float) Math.atan2(dy, dx);
			for (int i = 0; i < 2; i++) {
				s.fx().spark(headX - (float) Math.cos(ang) * 8, headY - (float) Math.sin(ang) * 8,
						-(float) Math.cos(ang) * 40, -(float) Math.sin(ang) * 40,
						Particles.FIRE[rnd.nextInt(Particles.FIRE.length)], 0.5f);
			}
			// Rakete zeigt im Item nach oben-rechts (45°)
			ui.itemC(ROCKET, headX, headY, 1.5f, ang + (float) (Math.PI / 4));
		}

		// Großer Multiplikator
		String big = fmt(m) + "x";
		int bigCol = crashed ? Ui.LOSS : (cashedAt > 0 ? Ui.WIN : Ui.WHITE);
		float pop = running ? 1 + 0.06f * (float) Math.sin(ui.time * 10) * (float) Math.min(1, m / 5) : 1;
		ui.textS(big, GX + 72, GY + 14, 3 * pop, bigCol, true, true);
		if (crashed) {
			float t = Ui.easeOutBack((float) (ui.time - crashT) / 0.3f);
			ui.textS("CRASH!", GX + 72, GY + 44, 1.5f * t, Ui.LOSS, true, true);
		} else if (cashedAt > 0) {
			ui.textS("Ausgezahlt bei " + fmt(cashedAt) + "x", GX + 72, GY + 44, 0.75f, Ui.WIN, true, true);
		}
	}

	private void drawPanel(Ui ui, double m) {
		float x = 306, y = 32, w = 102;
		ui.panel(x, y, w, 120, 0xE0141A1C, 0xE00B0F10, 0xFF3A2A12);
		ui.textS("EINSATZ", x + 8, y + 7, 0.5f, Ui.alpha(Ui.GOLD, 0.9f), false, false);
		ui.textS(Wallet.fmt(running ? stake : bet()), x + 8, y + 14, 1, Ui.WHITE, false, true);
		ui.textS("MÖGLICHER GEWINN", x + 8, y + 29, 0.5f, Ui.alpha(Ui.GOLD, 0.9f), false, false);
		long pot = running && cashedAt <= 0 ? (long) Math.floor(stake * m) : 0;
		ui.textS(running && cashedAt <= 0 ? Wallet.fmt(pot) : "–", x + 8, y + 36, 1, running ? Ui.WIN : Ui.MUTED, false, true);

		if (!running) {
			if (ui.button("launch", "STARTEN", x + 6, y + 56, w - 12, 20, 0xFFE0861E, true)) launch();
		} else if (cashedAt <= 0) {
			int c = Ui.mix(0xFF1F8A4C, 0xFF35C46E, ui.pulse(8));
			if (ui.button("cash", "AUSZAHLEN", x + 6, y + 56, w - 12, 20, c, true)) cashOut();
		} else {
			ui.button("wait", "warte…", x + 6, y + 56, w - 12, 20, 0xFF3C4440, false);
		}
		ui.textS("Leertaste: Start / Auszahlen", x + w / 2, y + 82, 0.5f, Ui.alpha(Ui.MUTED, 0.7f), true, false);
		ui.textS("Hausvorteil 3%, max. 50x", x + w / 2, y + 92, 0.5f, Ui.alpha(Ui.MUTED, 0.5f), true, false);

		// Verlauf
		ui.panel(x, y + 126, w, 54, 0xE0141A1C, 0xE00B0F10, 0xFF3A2A12);
		ui.textS("LETZTE CRASHES", x + w / 2, y + 131, 0.5f, Ui.alpha(Ui.GOLD, 0.9f), true, false);
		for (int i = 0; i < history.size(); i++) {
			double v = history.get(i);
			int col = v >= 10 ? Ui.GOLD : (v >= 2 ? Ui.WIN : Ui.LOSS);
			float hx = x + 6 + (i % 3) * 31, hy = y + 140 + (i / 3) * 12;
			ui.round(hx, hy, 28, 10, Ui.alpha(col, 0.2f));
			ui.textS(fmt(v) + "x", hx + 14, hy + 2.5f, 0.5f, col, true, false);
		}
	}
}
