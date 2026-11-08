package net.ccasino.ui;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import net.ccasino.Wallet;
import net.ccasino.games.BlackjackGame;
import net.ccasino.games.CrashGame;
import net.ccasino.games.Game;
import net.ccasino.games.RouletteGame;
import net.ccasino.games.SlotsGame;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Der Casino-Tisch. Alles wird selbst gezeichnet (keine Vanilla-Widgets),
 * auf einem festen 420x250-Canvas, der passend zur Fenstergröße skaliert wird.
 */
public class CasinoScreen extends Screen {
	public static final int W = 420, H = 250;
	public static final long[] BETS = {10, 50, 100, 500, 1000};

	private static final int WOOD_TOP = 0xFF6E4020, WOOD_BOTTOM = 0xFF3A1F0E, WOOD_EDGE = 0xFF1A0D05;
	private static final int FELT_TOP = 0xFF15704D, FELT_BOTTOM = 0xFF093A29;

	private final Ui ui = new Ui();
	private final Particles fx = new Particles();
	private final Game[] games;
	private Game current;
	private Game pending;
	private double transStart = -10;
	private boolean transSwapped = true;

	private static int betIdx = 1; // bleibt zwischen Öffnungen erhalten
	private float scale = 1, ox, oy;
	private final long startNanos = System.nanoTime();
	private double lastTime;

	private float shownBalance;
	private long lastBalance;
	private final List<double[]> deltas = new ArrayList<>(); // {amount, startTime}

	private String bannerText, bannerSub;
	private int bannerColor;
	private float bannerY;
	private double bannerT = -10;
	private double partyUntil = -10;
	private float shake;

	public CasinoScreen() {
		super(Component.literal("Craft Casino"));
		games = new Game[] {new SlotsGame(this), new RouletteGame(this), new BlackjackGame(this), new CrashGame(this)};
		shownBalance = Wallet.balance();
		lastBalance = Wallet.balance();
	}

	// ------------------------------------------------------------------ API für die Spiele

	public long bet() { return BETS[betIdx]; }
	public Particles fx() { return fx; }
	public double now() { return ui.time; }

	public void banner(String text, String sub, int color) {
		banner(text, sub, color, Game.AY + Game.AH / 2f);
	}

	public void banner(String text, String sub, int color, float y) {
		bannerText = text;
		bannerSub = sub;
		bannerColor = color;
		bannerY = y;
		bannerT = ui.time;
	}

	public void shake(float amount) { shake = Math.max(shake, amount); }

	/** Gewinn-Effekte. win = Auszahlung inkl. Einsatz. */
	public void celebrate(long win, long stake, float x, float y) {
		int n = (int) Math.min(70, 14 + win / Math.max(1, stake) * 4);
		fx.coins(x, y, n);
		if (win >= stake * 10) {
			fx.confetti(10, W - 20, 10, 140);
			partyUntil = ui.time + 3;
			Sounds.bigWin();
			shake(3);
		} else {
			Sounds.win();
		}
		Sounds.coin(1.2f);
	}

	public void notEnoughChips() {
		banner("Nicht genug Chips", Wallet.canRefill() ? "Unten rechts nachfüllen" : "Wähle einen kleineren Einsatz", Ui.LOSS);
		Sounds.lose();
	}

	public boolean party() { return ui.time < partyUntil; }

	// ------------------------------------------------------------------ Rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		double t = (System.nanoTime() - startNanos) / 1e9;
		float dt = (float) Math.min(0.05, Math.max(0, t - lastTime));
		lastTime = t;

		scale = Math.min(1f, Math.min((this.width - 6) / (float) W, (this.height - 6) / (float) H));
		ox = (this.width - W * scale) / 2f;
		oy = (this.height - H * scale) / 2f;
		ui.begin(graphics, this.font, (mouseX - ox) / scale, (mouseY - oy) / scale, t, dt);

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(ox, oy);
		pose.scale(scale, scale);
		if (shake > 0.05f) {
			pose.translate((float) (Math.random() - 0.5) * shake * 2, (float) (Math.random() - 0.5) * shake * 2);
			shake *= (float) Math.exp(-8 * dt);
		}

		trackBalance();
		drawTable();
		drawTopBar();
		if (current == null) drawLobby();
		else current.render(ui);
		drawBottomBar();
		fx.updateAndRender(ui);
		drawBanner();
		drawTransition();

		pose.popMatrix();
		ui.clicked = false;
	}

	private void trackBalance() {
		long bal = Wallet.balance();
		if (bal != lastBalance) {
			deltas.add(new double[] {bal - lastBalance, ui.time});
			lastBalance = bal;
		}
		shownBalance += (bal - shownBalance) * (1 - (float) Math.exp(-7 * ui.dt));
		if (Math.abs(bal - shownBalance) < 0.5f) shownBalance = bal;
	}

	private void drawTable() {
		// Holzrahmen
		ui.round(-1, 1, W + 2, H + 1, Ui.alpha(0xFF000000, 0.5f));
		ui.panel(0, 0, W, H, WOOD_TOP, WOOD_BOTTOM, WOOD_EDGE);
		// Holzmaserung
		for (int i = 0; i < 6; i++) {
			float y = 2 + i * 1.1f;
			ui.rect(6, y, W - 12, 0.6f, Ui.alpha(0xFF000000, 0.08f));
		}
		ui.outline(6, 6, W - 12, H - 12, Ui.GOLD_DARK);
		ui.outline(7, 7, W - 14, H - 14, Ui.alpha(0xFF000000, 0.6f));
		// Filz
		ui.grad(8, 8, W - 16, H - 16, FELT_TOP, FELT_BOTTOM);
		for (int y = 14, row = 0; y < H - 10; y += 14, row++) {
			for (int x = 14 + (row % 2) * 9; x < W - 10; x += 18) {
				ui.rect(x, y, 1, 1, 0x10FFFFFF);
			}
		}
		// Spotlight in der Mitte + Vignette
		ui.circle(W / 2f, H / 2f + 10, 120, 0x0CFFFFFF);
		ui.circle(W / 2f, H / 2f + 10, 80, 0x08FFFFFF);
		ui.grad(8, 8, W - 16, 24, 0x60000000, 0x00000000);
		ui.grad(8, H - 40, W - 16, 32, 0x00000000, 0x70000000);

		// Lauflichter oben und unten
		boolean party = party();
		int step = (int) (ui.time * (party ? 14 : 6));
		int n = 0;
		for (float x = 12; x < W - 8; x += 13, n++) {
			boolean onTop = party ? ((step + n) % 2 == 0) : ((n + step) % 4 == 0);
			boolean onBot = party ? ((step + n) % 2 == 1) : ((n - step) % 4 == 0 || (n - step) % 4 == -4);
			ui.bulb(x, 3.5f, onTop, party ? Ui.hsv(n / 12f + (float) ui.time, 0.6f, 1) : 0xFFFFE08A);
			ui.bulb(x, H - 3.5f, onBot, party ? Ui.hsv(n / 12f - (float) ui.time, 0.6f, 1) : 0xFFFFE08A);
		}
	}

	private void drawTopBar() {
		ui.grad(8, 8, W - 16, 19, 0xB0000000, 0x60000000);
		ui.rect(8, 27, W - 16, 1, Ui.alpha(Ui.GOLD_DARK, 0.9f));
		ui.shimmerText("♦ CRAFT CASINO ♦", W / 2f, 13, 1.0f, Ui.GOLD, 0xFFFFFFFF);

		String left = current == null ? "LOBBY" : current.name().toUpperCase();
		ui.textS(left, 14, 13, 1, Ui.MUTED, false, true);

		// Kontostand mit Chip
		String bal = Wallet.fmt(Math.round(shownBalance));
		float bx = W - 14 - ui.width(bal);
		ui.chip(bx - 9, 17, 5.5f, 0xFFE0A526, null);
		boolean rising = Math.round(shownBalance) != Wallet.balance();
		ui.textS(bal, bx, 13, 1, rising ? Ui.mix(Ui.GOLD, Ui.WHITE, ui.pulse(20)) : Ui.WHITE, false, true);

		// fliegende +/- Anzeigen
		for (int i = deltas.size() - 1; i >= 0; i--) {
			double[] d = deltas.get(i);
			float u = (float) ((ui.time - d[1]) / 1.4);
			if (u >= 1) {
				deltas.remove(i);
				continue;
			}
			long amt = (long) d[0];
			String s = (amt > 0 ? "+" : "-") + Wallet.fmt(Math.abs(amt));
			int c = Ui.alpha(amt > 0 ? Ui.WIN : Ui.LOSS, 1 - u * u);
			ui.textR(s, W - 14, 31 + u * 12, 1, c);
		}
	}

	private void drawLobby() {
		ui.textS("WÄHLE DEINEN TISCH", W / 2f, 34, 0.75f, Ui.alpha(Ui.GOLD, 0.9f), true, true);
		float tw = 92, th = 150, gap = 8;
		float x0 = (W - (games.length * tw + (games.length - 1) * gap)) / 2f;
		float y0 = 46;
		for (int i = 0; i < games.length; i++) {
			Game g = games[i];
			float x = x0 + i * (tw + gap);
			boolean hov = ui.hover(x, y0 - 4, tw, th + 4);
			float t = ui.approach("tile" + i, hov ? 1 : 0, 12);
			float y = y0 - 5 * t;
			int acc = g.accent();
			ui.shadow(x, y, tw, th, 1 + t);
			ui.panel(x, y, tw, th, Ui.shade(acc, 0.32f + 0.08f * t), 0xFF0C1110, Ui.mix(Ui.shade(acc, 0.55f), Ui.GOLD, t));
			if (t > 0.02f) ui.outline(x - 1, y - 1, tw + 2, th + 2, Ui.alpha(Ui.GOLD, 0.5f * t));

			float cx = x + tw / 2;
			float iy = y + 40;
			ui.circle(cx, iy, 24 + 2 * ui.pulse(3 + i), Ui.alpha(acc, 0.16f + 0.18f * t));
			ui.circle(cx, iy, 15, Ui.alpha(acc, 0.12f + 0.1f * t));
			float bob = (float) Math.sin(ui.time * 2.2 + i) * 2;
			float wob = t * (float) Math.sin(ui.time * 6) * 0.12f;
			ui.itemC(g.icon(), cx, iy + bob, 2.4f + 0.4f * t, wob);

			ui.textS(g.name(), cx, y + 70, 1, Ui.WHITE, true, true);
			ui.rect(cx - 16, y + 81, 32, 1, Ui.alpha(acc, 0.8f));
			String[] desc = g.description();
			for (int l = 0; l < desc.length; l++) {
				ui.textS(desc[l], cx, y + 88 + l * 9, 0.75f, Ui.MUTED, true, false);
			}
			float py = y + th - 22;
			ui.round(x + 12, py, tw - 24, 14, Ui.shade(acc, 0.5f));
			ui.roundGrad(x + 13, py + 1, tw - 26, 12, Ui.shade(acc, 1.1f + 0.2f * t), Ui.shade(acc, 0.85f));
			ui.textS("SPIELEN ▶", cx, py + 3.5f, 0.75f, Ui.WHITE, true, true);

			if (ui.click(x, y0 - 4, tw, th + 4)) {
				Sounds.click();
				Sounds.bell(1.4f);
				openGame(g);
			}
		}
		String stats = "Runden: " + Wallet.fmt(Wallet.rounds()) + "   ·   Größter Gewinn: " + Wallet.fmt(Wallet.biggestWin());
		ui.textS(stats, W / 2f, 203, 0.75f, Ui.alpha(Ui.MUTED, 0.8f), true, false);
	}

	private void drawBottomBar() {
		ui.grad(8, 213, W - 16, 29, 0x50000000, 0xA0000000);
		ui.rect(8, 213, W - 16, 1, Ui.alpha(Ui.GOLD_DARK, 0.9f));

		if (current != null) {
			if (ui.button("back", "◀ Lobby", 14, 220, 58, 16, 0xFF6E4020, !current.busy())) {
				openGame(null);
			}
		}

		boolean locked = current != null && current.betLocked();
		float cx0 = W / 2f - 52;
		ui.textR("EINSATZ", cx0 - 16, 225, 0.75f, Ui.alpha(Ui.GOLD, locked ? 0.5f : 1f));
		for (int i = 0; i < BETS.length; i++) {
			float cx = cx0 + i * 26, cy = 228;
			boolean sel = i == betIdx;
			boolean hov = !locked && ui.hover(cx - 10, cy - 10, 20, 20);
			float lift = ui.approach("chipLift" + i, sel ? -4 : (hov ? -2 : 0), 14);
			if (sel) ui.ring(cx, cy + lift, 12.5f + ui.pulse(5), 1.5f, Ui.alpha(Ui.GOLD, locked ? 0.4f : 0.9f));
			ui.chip(cx, cy + lift, 10, Ui.chipColor(BETS[i]), Wallet.shortFmt(BETS[i]));
			if (!Wallet.canAfford(BETS[i])) ui.circle(cx, cy + lift, 10, 0x90000000);
			else if (locked) ui.circle(cx, cy + lift, 10, 0x60000000);
			if (!locked && ui.click(cx - 10, cy - 10, 20, 20)) {
				betIdx = i;
				Sounds.chip();
			}
		}

		if (Wallet.canRefill()) {
			if (ui.button("refill", "+" + Wallet.REFILL + " Chips", W - 92, 220, 78, 16, Ui.GREEN, true)) {
				Wallet.refill();
				Sounds.coin(1.0f);
				fx.coins(W - 52, 220, 25);
			}
		} else {
			ui.textR("ESC = zurück", W - 14, 226, 0.75f, Ui.alpha(Ui.MUTED, 0.6f));
		}
	}

	private void drawBanner() {
		if (bannerText == null) return;
		float u = (float) (ui.time - bannerT);
		if (u > 2.4f) return;
		float pop = Ui.easeOutBack(u / 0.35f, 2.2f);
		float a = u > 1.9f ? 1 - (u - 1.9f) / 0.5f : 1;
		float wid = (Game.AW - 40) * Ui.easeOutCubic(u / 0.25f);
		float cy = bannerY;
		float hgt = bannerSub == null ? 26 : 36;
		ui.rect(W / 2f - wid / 2, cy - hgt / 2, wid, hgt, Ui.alpha(0xD0000000, a));
		ui.rect(W / 2f - wid / 2, cy - hgt / 2, wid, 1, Ui.alpha(bannerColor, a));
		ui.rect(W / 2f - wid / 2, cy + hgt / 2 - 1, wid, 1, Ui.alpha(bannerColor, a));
		float ty = bannerSub == null ? cy - 8 : cy - 13;
		ui.textS(bannerText, W / 2f, ty, 2 * Math.max(0.01f, pop), Ui.alpha(bannerColor, a), true, true);
		if (bannerSub != null) ui.textS(bannerSub, W / 2f, cy + 6, 1, Ui.alpha(Ui.WHITE, a), true, true);
	}

	private void drawTransition() {
		float u = (float) ((ui.time - transStart) / 0.42);
		if (u >= 1) return;
		if (!transSwapped && u >= 0.5f) {
			current = pending;
			transSwapped = true;
			fx.clear();
			bannerText = null;
		}
		float a = 1 - Math.abs(2 * u - 1);
		ui.rect(0, 0, W, H, Ui.alpha(0xFF050807, Ui.easeInOutCubic(a)));
		// rotierender Chip als Lade-Logo
		ui.pose().pushMatrix();
		ui.pose().translate(W / 2f, H / 2f);
		ui.pose().scale(1 - Math.abs((float) Math.cos(ui.time * 8)) * 0.8f + 0.2f, 1);
		ui.chip(0, 0, 14 * a, 0xFFC8283C, null);
		ui.pose().popMatrix();
	}

	private void openGame(Game g) {
		if (ui.time - transStart < 0.42) return;
		pending = g;
		transStart = ui.time;
		transSwapped = false;
	}

	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		ui.clicked = true;
		ui.clickButton = event.button();
		ui.cx = (float) ((event.x() - ox) / scale);
		ui.cy = (float) ((event.y() - oy) / scale);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (current != null && current.busy()) return true;
			if (current != null) {
				openGame(null);
				return true;
			}
			return super.keyPressed(event);
		}
		if (current != null) {
			current.onKey(key);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		for (Game g : games) g.forceFinish();
		Wallet.save();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
