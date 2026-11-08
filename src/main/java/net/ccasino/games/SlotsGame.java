package net.ccasino.games;

import org.lwjgl.glfw.GLFW;

import net.ccasino.Wallet;
import net.ccasino.ui.CasinoScreen;
import net.ccasino.ui.Sounds;
import net.ccasino.ui.Ui;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Einarmiger Bandit mit 3 Walzen, Hebel, Lauflichtern und Gewinnlinie. */
public class SlotsGame extends Game {
	private static final ItemStack[] SYM = {
			new ItemStack(Items.SWEET_BERRIES), new ItemStack(Items.REDSTONE), new ItemStack(Items.IRON_INGOT),
			new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND),
			new ItemStack(Items.NETHER_STAR)};
	private static final String[] SYM_NAME = {"Beeren", "Redstone", "Eisen", "Gold", "Smaragd", "Diamant", "Netherstern"};
	private static final int[] WEIGHT = {30, 24, 18, 12, 8, 5, 2};
	private static final int[] PAY3 = {6, 10, 15, 25, 50, 100, 300};
	private static final double[] PAY2 = {0.5, 1, 1.5, 2, 2, 3, 5};

	private static final int REELS = 3, STRIP = 24;
	private static final float ROW = 26;

	private final int[][] strips = new int[REELS][STRIP];
	private final float[] pos = new float[REELS];
	private final float[] start = new float[REELS];
	private final float[] target = new float[REELS];
	private final float[] dur = new float[REELS];
	private final boolean[] stopped = {true, true, true};
	private final int[] result = new int[REELS];
	private final int[] lastTick = new int[REELS];

	private boolean spinning;
	private boolean hasSpun;
	private double spinT;
	private long stake;
	private long pendingWin;
	private boolean[] winReel = new boolean[REELS];
	private double winT = -10;

	public SlotsGame(CasinoScreen s) {
		super(s);
		for (int r = 0; r < REELS; r++) {
			for (int i = 0; i < STRIP; i++) strips[r][i] = i < SYM.length ? i : weighted();
			// mischen
			for (int i = STRIP - 1; i > 0; i--) {
				int j = rnd.nextInt(i + 1);
				int t = strips[r][i];
				strips[r][i] = strips[r][j];
				strips[r][j] = t;
			}
			pos[r] = rnd.nextInt(STRIP);
		}
	}

	private int weighted() {
		int sum = 0;
		for (int w : WEIGHT) sum += w;
		int x = rnd.nextInt(sum);
		for (int i = 0; i < WEIGHT.length; i++) {
			x -= WEIGHT[i];
			if (x < 0) return i;
		}
		return 0;
	}

	@Override public String name() { return "Slots"; }
	@Override public ItemStack icon() { return SYM[6]; }
	@Override public String[] description() { return new String[] {"3 Walzen, 1 Linie", "Drei Netherstars", "zahlen 300x!"}; }
	@Override public int accent() { return 0xFFB0306A; }
	@Override public boolean busy() { return spinning; }

	@Override
	public void onKey(int key) {
		if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) spin();
	}

	private void spin() {
		if (spinning) return;
		long b = bet();
		if (!Wallet.take(b)) {
			s.notEnoughChips();
			return;
		}
		stake = b;
		spinning = true;
		hasSpun = true;
		spinT = s.now();
		winReel = new boolean[REELS];
		Sounds.play(net.minecraft.sounds.SoundEvents.LEVER_CLICK, 0.8f, 0.7f);
		for (int r = 0; r < REELS; r++) {
			result[r] = weighted();
			// zufällige Position auf dem Streifen, die das Ergebnis zeigt
			int idx;
			do {
				idx = rnd.nextInt(STRIP);
			} while (strips[r][idx] != result[r]);
			start[r] = pos[r];
			int cur = Math.floorMod(Math.round(pos[r]), STRIP);
			int need = Math.floorMod((idx - 1) - cur, STRIP);
			target[r] = Math.round(pos[r]) + STRIP * (3 + r) + need;
			dur[r] = 1.3f + r * 0.45f;
			stopped[r] = false;
			lastTick[r] = (int) Math.floor(pos[r]);
		}
		// Auswertung vorab (Auszahlung erst nach der Animation)
		pendingWin = payout(result, stake);
	}

	static long payout(int[] r, long stake) {
		if (r[0] == r[1] && r[1] == r[2]) return stake * PAY3[r[0]];
		int pair = -1;
		if (r[0] == r[1] || r[0] == r[2]) pair = r[0];
		else if (r[1] == r[2]) pair = r[1];
		if (pair >= 0) return Math.round(stake * PAY2[pair]);
		return 0;
	}

	private void finish() {
		spinning = false;
		if (pendingWin > 0) {
			Wallet.give(pendingWin);
			boolean three = result[0] == result[1] && result[1] == result[2];
			int pairSym = (result[0] == result[1] || result[0] == result[2]) ? result[0] : result[1];
			for (int r = 0; r < REELS; r++) winReel[r] = result[r] == pairSym;
			winT = s.now();
			String head = three ? (result[0] == 6 ? "JACKPOT!" : "DREIER!") : "GEWINN!";
			s.banner(head, "+" + Wallet.fmt(pendingWin) + " Chips", Ui.GOLD, 176);
			s.celebrate(pendingWin, stake, 210, 100);
		} else {
			Sounds.lose();
		}
		Wallet.endRound(pendingWin - stake);
		pendingWin = 0;
	}

	@Override
	public void forceFinish() {
		if (!spinning) return;
		for (int r = 0; r < REELS; r++) {
			pos[r] = Math.floorMod((int) target[r], STRIP);
			stopped[r] = true;
		}
		spinning = false;
		Wallet.give(pendingWin);
		Wallet.endRound(pendingWin - stake);
		pendingWin = 0;
	}

	// ------------------------------------------------------------------ Update & Zeichnen

	private void update(Ui ui) {
		if (!spinning) return;
		float el = (float) (ui.time - spinT);
		boolean all = true;
		for (int r = 0; r < REELS; r++) {
			if (stopped[r]) continue;
			float u = el / dur[r];
			if (u >= 1) {
				pos[r] = Math.floorMod((int) target[r], STRIP);
				stopped[r] = true;
				Sounds.thunk();
				Sounds.tick(0.7f + r * 0.2f);
				continue;
			}
			all = false;
			pos[r] = start[r] + (target[r] - start[r]) * Ui.easeOutBack(u, 0.6f);
			int f = (int) Math.floor(pos[r]);
			if (f != lastTick[r]) {
				lastTick[r] = f;
				if (r == lastSpinningReel()) Sounds.tick(1.5f + u * 0.4f);
			}
		}
		if (all) finish();
	}

	private int lastSpinningReel() {
		for (int r = REELS - 1; r >= 0; r--) if (!stopped[r]) return r;
		return -1;
	}

	@Override
	public void render(Ui ui) {
		update(ui);

		float mx = 120, my = 34, mw = 180, mh = 172; // Automat
		boolean winning = ui.time - winT < 2.5;

		// --- Gewinntabelle links
		ui.panel(16, 40, 92, 160, 0xE0141A1C, 0xE00B0F10, 0xFF3A2A12);
		ui.textS("GEWINNTABELLE", 62, 46, 0.75f, Ui.GOLD, true, true);
		for (int i = SYM.length - 1, row = 0; i >= 0; i--, row++) {
			float y = 58 + row * 19;
			boolean hl = winning && result[0] == i && result[1] == i && result[2] == i;
			if (hl) ui.rect(19, y - 2, 86, 18, Ui.alpha(Ui.GOLD, 0.25f + 0.2f * ui.pulse(12)));
			for (int k = 0; k < 3; k++) ui.item(SYM[i], 21 + k * 10, y, 0.75f);
			ui.textR(PAY3[i] + "x", 102, y + 1, 0.75f, Ui.WHITE);
			ui.textR("2er " + fmtMul(PAY2[i]), 102, y + 8, 0.5f, Ui.MUTED);
		}

		// --- Automat
		ui.shadow(mx, my, mw, mh, 2);
		ui.panel(mx, my, mw, mh, 0xFF7A1E3E, 0xFF2A0A18, 0xFF120408);
		ui.outline(mx + 3, my + 3, mw - 6, mh - 6, Ui.alpha(Ui.GOLD, 0.8f));
		// Lauflichter auf dem Automaten
		int step = (int) (ui.time * (spinning ? 16 : 5));
		for (int i = 0; i < 14; i++) {
			float bx = mx + 10 + i * 12.3f;
			boolean on = winning ? ((step + i) % 2 == 0) : ((step + i) % 3 == 0);
			ui.bulb(bx, my + 9, on, winning ? Ui.hsv(i / 14f + (float) ui.time, 0.5f, 1) : 0xFFFFD27A);
		}
		// Schild
		ui.panel(mx + 30, my + 15, mw - 60, 16, 0xFF1A0A10, 0xFF0A0306, Ui.GOLD_DARK);
		ui.shimmerText("LUCKY CRAFT", mx + mw / 2, my + 19, 1, Ui.GOLD, 0xFFFFFFFF);

		// Walzenfenster
		float wx = mx + 18, wy = my + 38, ww = mw - 36, wh = ROW * 3 + 4;
		ui.round(wx - 3, wy - 3, ww + 6, wh + 6, Ui.GOLD_DARK);
		ui.round(wx - 2, wy - 2, ww + 4, wh + 4, 0xFF140508);
		float rw = (ww - 8) / 3f;
		for (int r = 0; r < REELS; r++) {
			float rx = wx + r * (rw + 4);
			ui.grad(rx, wy, rw, wh, 0xFFF4EEE2, 0xFFD9CFBE);
			ui.scissor(rx, wy, rw, wh);
			float p = pos[r];
			int first = (int) Math.floor(p) - 1;
			for (int k = first; k <= first + 4; k++) {
				int sym = strips[r][Math.floorMod(k, STRIP)];
				float y = wy + 2 + (k - p) * ROW + ROW / 2;
				boolean center = stopped[r] && !spinning && Math.abs(y - (wy + 2 + ROW * 1.5f)) < 1;
				float sc = 1.25f;
				if (center && winning && winReel[r]) sc = 1.25f + 0.25f * ui.pulse(14);
				if (center && winning && winReel[r]) ui.circle(rx + rw / 2, y, 12, Ui.alpha(Ui.GOLD, 0.5f));
				ui.itemC(SYM[sym], rx + rw / 2, y, sc, 0);
			}
			// Zylinder-Schattierung
			ui.grad(rx, wy, rw, 16, 0xA0000000, 0x00000000);
			ui.grad(rx, wy + wh - 16, rw, 16, 0x00000000, 0xA0000000);
			ui.endScissor();
			if (r < REELS - 1) ui.rect(rx + rw, wy, 4, wh, 0xFF140508);
		}
		// Gewinnlinie
		float ly = wy + 2 + ROW * 1.5f;
		int lc = winning ? Ui.mix(Ui.RED, Ui.GOLD, ui.pulse(14)) : Ui.alpha(Ui.RED, 0.7f);
		ui.rect(wx - 6, ly - 0.5f, 5, 1, lc);
		ui.rect(wx + ww + 1, ly - 0.5f, 5, 1, lc);
		ui.textS("▶", wx - 9, ly - 4, 1, lc, false, false);

		// Info + Button
		float iy = wy + wh + 10;
		ui.textS("Einsatz: " + Wallet.fmt(bet()), mx + 14, iy + 4, 0.75f, Ui.MUTED, false, true);
		if (ui.button("spin", spinning ? "..." : "DREHEN", mx + mw - 74, iy, 60, 18, 0xFFC8283C, !spinning)) spin();
		ui.textS("Leertaste = Drehen", mx + mw / 2, my + mh - 11, 0.5f, Ui.alpha(Ui.MUTED, 0.7f), true, false);

		// --- Hebel rechts
		drawLever(ui, mx + mw + 4, my + 40);

		// --- Info rechts
		ui.panel(326, 120, 80, 80, 0xE0141A1C, 0xE00B0F10, 0xFF3A2A12);
		ui.textS("LETZTER DREH", 366, 126, 0.75f, Ui.GOLD, true, true);
		if (!spinning && hasSpun) {
			for (int r = 0; r < REELS; r++) ui.item(SYM[result[r]], 336 + r * 20, 140, 1);
			ui.textS(SYM_NAME[result[1]], 366, 162, 0.75f, Ui.WHITE, true, true);
		} else {
			ui.textS(spinning ? "läuft…" : "–", 366, 150, 0.75f, Ui.MUTED, true, true);
		}
		ui.textS("RTP ca. 98%", 366, 184, 0.5f, Ui.alpha(Ui.MUTED, 0.7f), true, false);
	}

	private void drawLever(Ui ui, float x, float y) {
		float el = (float) (ui.time - spinT);
		float pull = spinning ? (el < 0.25f ? Ui.easeOutCubic(el / 0.25f) : 1 - Ui.easeOutBack((el - 0.25f) / 0.5f)) : 0;
		pull = Ui.clamp(pull, -0.15f, 1);
		boolean hov = !spinning && ui.hover(x - 6, y - 16, 24, 80);
		// Halterung
		ui.panel(x, y + 30, 12, 24, 0xFF8A8A92, 0xFF3A3A40, 0xFF1A1A1E);
		// Stange
		float knobY = y - 10 + pull * 52;
		float top = Math.min(knobY, y + 38);
		float bottom = Math.max(knobY, y + 38);
		ui.rect(x + 4.5f, top, 3, bottom - top, 0xFFB8B8C0);
		ui.rect(x + 4.5f, top, 1, bottom - top, 0xFFE8E8F0);
		// Kugel
		float r = 6 + (hov ? 0.8f : 0);
		ui.circle(x + 6, knobY, r, 0xFF7A0E14);
		ui.circle(x + 6, knobY - 0.5f, r - 1, 0xFFE0283A);
		ui.circle(x + 4.5f, knobY - 2.5f, 1.8f, 0xC0FFFFFF);
		if (hov) ui.textS("ziehen!", x + 6, y + 58, 0.5f, Ui.GOLD, true, true);
		if (!spinning && ui.click(x - 6, y - 16, 24, 80)) spin();
	}

	private static String fmtMul(double d) {
		return (d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d)) + "x";
	}
}
