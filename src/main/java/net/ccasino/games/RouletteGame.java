package net.ccasino.games;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import net.ccasino.Wallet;
import net.ccasino.ui.CasinoScreen;
import net.ccasino.ui.Sounds;
import net.ccasino.ui.Ui;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Europäisches Roulette mit drehendem Kessel, rollender Kugel und Setzfeld. */
public class RouletteGame extends Game {
	private static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5,
			24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
	private static final boolean[] RED = new boolean[37];
	static {
		for (int n : new int[] {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}) RED[n] = true;
	}
	private static final float SEG = (float) (Math.PI * 2 / 37);
	private static final float CX = 84, CY = 118, R = 60, POCKET_R = 46;
	private static final float SPIN_T = 6.2f;

	private record Cell(String key, float x, float y, float w, float h, String label, int color) {}

	private final List<Cell> cells = new ArrayList<>();
	private final Map<String, Long> bets = new LinkedHashMap<>();
	private Map<String, Long> lastBets = new LinkedHashMap<>();
	private final List<Integer> history = new ArrayList<>();

	private float wheelRot, wheelSpeed = 0.35f;
	private boolean spinning, ballVisible;
	private double spinT;
	private int resultNum, resultIdx, lastFret;
	private float relEnd, ballRel;
	private double resultT = -10;
	private long stakeTotal;

	public RouletteGame(CasinoScreen s) {
		super(s);
		float bx = 178, by = 52, cw = 19, ch = 16;
		cells.add(new Cell("n:0", 160, by, 18, ch * 3, "0", Ui.GREEN));
		for (int col = 0; col < 12; col++) {
			for (int row = 0; row < 3; row++) {
				int n = col * 3 + (3 - row);
				cells.add(new Cell("n:" + n, bx + col * cw, by + row * ch, cw, ch, String.valueOf(n), RED[n] ? Ui.RED : Ui.BLACK));
			}
		}
		String[] dz = {"1. Dutzend", "2. Dutzend", "3. Dutzend"};
		for (int i = 0; i < 3; i++) cells.add(new Cell("dz:" + i, bx + i * 76, by + 48, 76, 16, dz[i], 0));
		String[] ok = {"low", "even", "red", "black", "odd", "high"};
		String[] ol = {"1-18", "Gerade", "◆", "◆", "Ungerade", "19-36"};
		for (int i = 0; i < 6; i++) {
			int c = ok[i].equals("red") ? Ui.RED : ok[i].equals("black") ? Ui.BLACK : 0;
			cells.add(new Cell(ok[i], bx + i * 38, by + 64, 38, 16, ol[i], c));
		}
	}

	@Override public String name() { return "Roulette"; }
	@Override public ItemStack icon() { return new ItemStack(Items.CLOCK); }
	@Override public String[] description() { return new String[] {"Europäischer Kessel", "Setze beliebig viele", "Chips aufs Feld"}; }
	@Override public int accent() { return 0xFFC8283C; }
	@Override public boolean busy() { return spinning; }

	@Override
	public void onKey(int key) {
		if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) spin();
	}

	// ------------------------------------------------------------------ Logik

	private static boolean covers(String key, int n) {
		if (key.startsWith("n:")) return Integer.parseInt(key.substring(2)) == n;
		if (key.startsWith("dz:")) return n >= 1 && (n - 1) / 12 == Integer.parseInt(key.substring(3));
		if (n == 0) return false;
		return switch (key) {
			case "low" -> n <= 18;
			case "high" -> n >= 19;
			case "even" -> n % 2 == 0;
			case "odd" -> n % 2 == 1;
			case "red" -> RED[n];
			case "black" -> !RED[n];
			default -> false;
		};
	}

	private static int multiplier(String key) {
		if (key.startsWith("n:")) return 36;
		if (key.startsWith("dz:")) return 3;
		return 2;
	}

	private long totalBet() {
		long t = 0;
		for (long v : bets.values()) t += v;
		return t;
	}

	private void place(String key) {
		long b = bet();
		if (!Wallet.take(b)) {
			s.notEnoughChips();
			return;
		}
		bets.merge(key, b, Long::sum);
		Sounds.chip();
	}

	private void remove(String key) {
		Long v = bets.remove(key);
		if (v != null) {
			Wallet.give(v);
			Sounds.tick(1.2f);
		}
	}

	private void clearBets() {
		for (long v : bets.values()) Wallet.give(v);
		bets.clear();
	}

	private void repeatBets() {
		long sum = 0;
		for (long v : lastBets.values()) sum += v;
		if (!Wallet.canAfford(sum)) {
			s.notEnoughChips();
			return;
		}
		Wallet.take(sum);
		bets.putAll(lastBets);
		Sounds.chip();
	}

	private void spin() {
		if (spinning || bets.isEmpty()) return;
		spinning = true;
		ballVisible = true;
		spinT = s.now();
		stakeTotal = totalBet();
		resultNum = rnd.nextInt(37);
		for (int i = 0; i < WHEEL.length; i++) if (WHEEL[i] == resultNum) resultIdx = i;
		relEnd = resultIdx * SEG;
		lastFret = Integer.MIN_VALUE;
		wheelSpeed = 2.6f;
		Sounds.play(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_SNARE, 0.7f, 0.6f);
	}

	private void settle() {
		spinning = false;
		resultT = s.now();
		history.add(0, resultNum);
		if (history.size() > 12) history.remove(history.size() - 1);
		long win = 0;
		for (Map.Entry<String, Long> e : bets.entrySet()) {
			if (covers(e.getKey(), resultNum)) {
				long w = e.getValue() * multiplier(e.getKey());
				win += w;
				Cell c = cell(e.getKey());
				if (c != null) s.fx().coins(c.x + c.w / 2, c.y + c.h / 2, 12);
			}
		}
		lastBets = new LinkedHashMap<>(bets);
		bets.clear();
		String colorName = resultNum == 0 ? "GRÜN" : RED[resultNum] ? "ROT" : "SCHWARZ";
		int col = resultNum == 0 ? 0xFF4FE08A : RED[resultNum] ? 0xFFFF5A6A : Ui.WHITE;
		if (win > 0) {
			Wallet.give(win);
			s.banner(resultNum + " " + colorName, "Gewinn +" + Wallet.fmt(win), col, 186);
			s.celebrate(win, stakeTotal, CX, CY);
		} else {
			s.banner(resultNum + " " + colorName, "Leider verloren", col, 186);
			Sounds.lose();
		}
		Wallet.endRound(win - stakeTotal);
	}

	@Override
	public void forceFinish() {
		if (spinning) settle();
	}

	private Cell cell(String key) {
		for (Cell c : cells) if (c.key.equals(key)) return c;
		return null;
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(Ui ui) {
		float el = (float) (ui.time - spinT);
		if (spinning) wheelSpeed = 0.35f + (2.6f - 0.35f) * (float) Math.exp(-0.45 * el);
		else wheelSpeed += (0.35f - wheelSpeed) * (1 - (float) Math.exp(-2 * ui.dt));
		wheelRot = (float) ((wheelRot + wheelSpeed * ui.dt) % (Math.PI * 2));

		float ballR = POCKET_R;
		if (spinning) {
			float u = el / SPIN_T;
			ballRel = relEnd + (float) (Math.PI * 2 * 7) * (1 - Ui.easeOutCubic(u));
			if (u < 0.55f) {
				ballR = R + 2.5f;
			} else if (u < 0.82f) {
				float v = (u - 0.55f) / 0.27f;
				ballR = Ui.lerp(R + 2.5f, POCKET_R, Ui.easeInOutCubic(v)) - Math.abs((float) Math.sin(v * Math.PI * 4)) * (1 - v) * 4;
			}
			if (u > 0.5f && u < 0.97f) {
				int fret = (int) Math.floor(ballRel / SEG);
				if (fret != lastFret) {
					if (lastFret != Integer.MIN_VALUE) Sounds.tick(1.9f - u);
					lastFret = fret;
				}
			}
			if (u >= 1) {
				ballRel = relEnd;
				Sounds.thunk();
				settle();
			}
		}

		drawWheel(ui, ballR);
		drawBoard(ui);
	}

	private void drawWheel(Ui ui, float ballR) {
		Matrix3x2fStack p = ui.pose();
		ui.circle(CX + 1, CY + 4, R + 11, 0x70000000);
		ui.circle(CX, CY, R + 11, 0xFF1E0E06);
		ui.circle(CX, CY, R + 9, 0xFF6E4020);
		ui.ring(CX, CY, R + 9, 1.2f, 0xFF9A6438);
		ui.circle(CX, CY, R + 6, 0xFF2C170A);
		ui.ring(CX, CY, R + 6, 1, Ui.GOLD_DARK);

		// Fächer
		float[][] slices = {{56, 60}, {52, 56}, {48, 52}, {44, 48}, {40, 44}};
		for (int i = 0; i < 37; i++) {
			int n = WHEEL[i];
			int c = n == 0 ? Ui.GREEN : RED[n] ? 0xFFC41E32 : 0xFF1A1A1E;
			p.pushMatrix();
			p.translate(CX, CY);
			p.rotate(wheelRot + i * SEG);
			for (int k = 0; k < slices.length; k++) {
				float r0 = slices[k][0], r1 = slices[k][1];
				float w = (float) (Math.PI * 2 * r1 / 37) + 0.6f;
				int col = k < 2 ? c : Ui.shade(c, 0.62f);
				ui.rect(-w / 2, -r1, w, r1 - r0, col);
			}
			ui.textS(String.valueOf(n), 0, -R + 1.5f, 0.5f, Ui.WHITE, true, false);
			p.popMatrix();
		}
		// Stege
		for (int i = 0; i < 37; i++) {
			p.pushMatrix();
			p.translate(CX, CY);
			p.rotate(wheelRot + (i + 0.5f) * SEG);
			ui.rect(-0.5f, -R, 1, R - 40, Ui.alpha(Ui.GOLD, 0.75f));
			p.popMatrix();
		}
		ui.ring(CX, CY, 52.5f, 1, Ui.alpha(Ui.GOLD_DARK, 0.9f));

		// Konus + Drehkreuz
		ui.circle(CX, CY, 40, 0xFF3A1E0C);
		ui.circle(CX, CY, 37, 0xFF6E4020);
		ui.circle(CX, CY, 30, 0xFF845028);
		ui.circle(CX - 6, CY - 8, 14, 0x14FFFFFF);
		for (int a = 0; a < 4; a++) {
			float ang = wheelRot + a * (float) Math.PI / 2;
			p.pushMatrix();
			p.translate(CX, CY);
			p.rotate(ang);
			ui.rect(-1.5f, -24, 3, 24, Ui.GOLD);
			ui.rect(-0.5f, -24, 1, 24, 0xFFFFF0B0);
			p.popMatrix();
			float ex = CX + 24 * (float) Math.sin(ang), ey = CY - 24 * (float) Math.cos(ang);
			ui.circle(ex, ey, 2.6f, Ui.GOLD_DARK);
			ui.circle(ex, ey, 1.8f, Ui.GOLD);
		}
		ui.circle(CX, CY, 7, Ui.GOLD_DARK);
		ui.circle(CX, CY, 5.5f, Ui.GOLD);
		ui.circle(CX - 1.5f, CY - 1.5f, 1.8f, 0xC0FFFFFF);

		// Kugel
		if (ballVisible) {
			float a = wheelRot + ballRel;
			float bx = CX + ballR * (float) Math.sin(a), by = CY - ballR * (float) Math.cos(a);
			ui.circle(bx + 0.6f, by + 1.2f, 3.2f, 0x80000000);
			ui.circle(bx, by, 3.1f, 0xFFBFBFC8);
			ui.circle(bx - 0.3f, by - 0.3f, 2.5f, 0xFFF8F8FF);
			ui.rect(bx - 1.5f, by - 1.5f, 1, 1, Ui.WHITE);
		}

		// Ergebnis-Medaillon
		if (!spinning && ui.time - resultT < 6 && !history.isEmpty()) {
			int n = history.get(0);
			float t = Ui.easeOutBack((float) (ui.time - resultT) / 0.4f);
			int c = n == 0 ? Ui.GREEN : RED[n] ? Ui.RED : Ui.BLACK;
			ui.circle(CX, CY, 13 * t, Ui.GOLD);
			ui.circle(CX, CY, 11.5f * t, c);
			if (t > 0.5f) ui.textS(String.valueOf(n), CX, CY - 4.5f * t, t * 1.2f, Ui.WHITE, true, true);
		}
	}

	private void drawBoard(Ui ui) {
		// Verlauf
		ui.textS("LETZTE ZAHLEN", 160, 37, 0.5f, Ui.alpha(Ui.GOLD, 0.8f), false, true);
		for (int i = 0; i < history.size(); i++) {
			int n = history.get(i);
			float x = 211 + i * 16, y = 39.5f;
			int c = n == 0 ? Ui.GREEN : RED[n] ? Ui.RED : Ui.BLACK;
			ui.circle(x, y, 6, i == 0 ? Ui.GOLD : 0xFF0A0A0A);
			ui.circle(x, y, 5, c);
			ui.textS(String.valueOf(n), x, y - 2, 0.5f, Ui.WHITE, true, false);
		}

		// Brett
		ui.panel(156, 48, 254, 88, 0xFF0E5038, 0xFF0A3A28, Ui.GOLD_DARK);
		String hoverKey = null;
		for (Cell c : cells) if (ui.hover(c.x, c.y, c.w, c.h)) hoverKey = c.key;
		boolean flash = !spinning && ui.time - resultT < 3.5;
		int win = history.isEmpty() ? -1 : history.get(0);

		for (Cell c : cells) {
			boolean hov = c.key.equals(hoverKey) && !spinning;
			boolean covered = hoverKey != null && !spinning && c.key.startsWith("n:") && covers(hoverKey, Integer.parseInt(c.key.substring(2)));
			int base = c.color == 0 ? 0x00000000 : c.color;
			if (c.color != 0) ui.rect(c.x + 1, c.y + 1, c.w - 2, c.h - 2, base);
			if (hov || covered) ui.rect(c.x + 1, c.y + 1, c.w - 2, c.h - 2, 0x40FFFFFF);
			if (flash && c.key.equals("n:" + win)) {
				ui.rect(c.x + 1, c.y + 1, c.w - 2, c.h - 2, Ui.alpha(Ui.GOLD, 0.4f + 0.4f * ui.pulse(12)));
			}
			ui.outline(c.x, c.y, c.w, c.h, Ui.alpha(Ui.GOLD, 0.55f));
			boolean diamond = c.label.equals("◆");
			float sc = c.key.startsWith("n:") ? 0.75f : (diamond ? 1 : 0.5f);
			int lc = diamond ? (c.key.equals("red") ? 0xFFFF7A86 : 0xFF6A6A78) : Ui.WHITE;
			if (diamond) ui.rect(c.x + 1, c.y + 1, c.w - 2, c.h - 2, 0x30000000);
			ui.textS(c.label, c.x + c.w / 2, c.y + c.h / 2 - 4 * sc, sc, lc, true, !diamond);

			if (!spinning) {
				if (ui.click(c.x, c.y, c.w, c.h)) place(c.key);
				else if (ui.rclick(c.x, c.y, c.w, c.h)) remove(c.key);
			}
		}
		// Chips auf dem Feld
		for (Map.Entry<String, Long> e : bets.entrySet()) {
			Cell c = cell(e.getKey());
			if (c == null) continue;
			long v = e.getValue();
			float cx = c.x + c.w / 2, cy = c.y + c.h / 2;
			int stack = (int) Math.min(4, 1 + v / (bet() * 3 + 1));
			for (int k = 0; k < stack - 1; k++) ui.chip(cx, cy + 1 - k, 6.5f, Ui.chipColor(v), null);
			ui.chip(cx, cy - stack + 1, 6.5f, Ui.chipColor(v), Wallet.shortFmt(v));
		}

		// Bedienung
		float y = 142;
		long total = totalBet();
		ui.textS("Gesetzt: " + Wallet.fmt(total), 160, y + 1, 0.75f, Ui.WHITE, false, true);
		ui.textS("Links: setzen · Rechts: entfernen", 160, y + 10, 0.5f, Ui.alpha(Ui.MUTED, 0.8f), false, false);
		if (!lastBets.isEmpty() && bets.isEmpty() && !spinning) {
			if (ui.button("again", "NOCHMAL", 250, y, 50, 16, 0xFF2E6FD8, true)) repeatBets();
		} else if (ui.button("clear", "LÖSCHEN", 250, y, 50, 16, 0xFF5A5A62, !spinning && !bets.isEmpty())) {
			clearBets();
		}
		if (ui.button("spin", spinning ? "..." : "DREHEN", 306, y, 100, 16, 0xFFC8283C, !spinning && !bets.isEmpty())) spin();

		// Auszahlungen
		ui.textS("Zahl 36x  ·  Dutzend 3x  ·  Rot/Schwarz, Gerade/Ungerade, 1-18/19-36: 2x", 283, y + 22, 0.5f, Ui.alpha(Ui.MUTED, 0.8f), true, false);
	}
}
