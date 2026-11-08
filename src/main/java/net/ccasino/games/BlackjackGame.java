package net.ccasino.games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import net.ccasino.Wallet;
import net.ccasino.ui.CasinoScreen;
import net.ccasino.ui.Sounds;
import net.ccasino.ui.Ui;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Blackjack gegen den Dealer: fliegende Karten, Umdreh-Animation, 6-Deck-Schuh. */
public class BlackjackGame extends Game {
	private static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
	private static final String[] SUITS = {"♠", "♥", "♦", "♣"};
	private static final float CW = 28, CH = 40;
	private static final float SHOE_X = 372, SHOE_Y = 44;
	private static final float FLY = 0.38f, FLIP = 0.22f;

	private enum State { IDLE, DEALING, PLAYER, DEALER, DONE }

	private static final class Card {
		final int rank, suit;
		double dealAt;
		double flipAt = -1;
		float tx, ty;

		Card(int rank, int suit) {
			this.rank = rank;
			this.suit = suit;
		}

		int value() { return rank == 0 ? 11 : Math.min(10, rank + 1); }
		boolean red() { return suit == 1 || suit == 2; }
	}

	private final List<Integer> shoe = new ArrayList<>();
	private final List<Card> player = new ArrayList<>();
	private final List<Card> dealer = new ArrayList<>();
	private State state = State.IDLE;
	private long stake;
	private double nextEvent = -1;
	private Runnable nextAction;
	private double lastDealTime;
	private long pendingWin;
	private String resultText;
	private int resultColor;
	private double resultT = -10;

	public BlackjackGame(CasinoScreen s) {
		super(s);
		reshuffle();
	}

	@Override public String name() { return "Blackjack"; }
	@Override public ItemStack icon() { return new ItemStack(Items.PAPER); }
	@Override public String[] description() { return new String[] {"Schlag den Dealer", "ohne über 21 zu gehen", "Blackjack zahlt 3:2"}; }
	@Override public int accent() { return 0xFF2E6FD8; }
	@Override public boolean busy() { return state != State.IDLE && state != State.DONE; }

	@Override
	public void onKey(int key) {
		if (state == State.PLAYER) {
			if (key == GLFW.GLFW_KEY_H) hit();
			else if (key == GLFW.GLFW_KEY_S) stand();
			else if (key == GLFW.GLFW_KEY_D) doubleDown();
		} else if (!busy() && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER)) {
			deal();
		}
	}

	// ------------------------------------------------------------------ Logik

	private void reshuffle() {
		shoe.clear();
		for (int d = 0; d < 6; d++) for (int c = 0; c < 52; c++) shoe.add(c);
		Collections.shuffle(shoe, rnd);
	}

	private Card draw() {
		if (shoe.isEmpty()) reshuffle();
		int c = shoe.remove(shoe.size() - 1);
		return new Card(c % 13, c / 13);
	}

	private static int total(List<Card> hand, boolean onlyVisible, double now) {
		int sum = 0, aces = 0;
		for (Card c : hand) {
			if (onlyVisible && (c.flipAt < 0 || now < c.flipAt + FLIP / 2)) continue;
			sum += c.value();
			if (c.rank == 0) aces++;
		}
		while (sum > 21 && aces > 0) {
			sum -= 10;
			aces--;
		}
		return sum;
	}

	private int total(List<Card> hand) { return total(hand, false, 0); }

	private boolean isBlackjack(List<Card> hand) { return hand.size() == 2 && total(hand) == 21; }

	/** Karte an eine Hand geben; Animation startet frühestens nach der vorherigen. */
	private Card give(List<Card> hand, boolean faceUp) {
		Card c = draw();
		double at = Math.max(s.now(), lastDealTime + 0.32);
		lastDealTime = at;
		c.dealAt = at;
		c.flipAt = faceUp ? at + FLY : -1;
		hand.add(c);
		layout();
		return c;
	}

	private void layout() {
		layoutHand(dealer, 62);
		layoutHand(player, 132);
	}

	private void layoutHand(List<Card> hand, float y) {
		float spacing = hand.size() > 5 ? 16 : 22;
		float w = (hand.size() - 1) * spacing + CW;
		float x0 = 200 - w / 2;
		for (int i = 0; i < hand.size(); i++) {
			hand.get(i).tx = x0 + i * spacing;
			hand.get(i).ty = y;
		}
	}

	private void schedule(double at, Runnable r) {
		nextEvent = at;
		nextAction = r;
	}

	private void deal() {
		if (busy()) return;
		long b = bet();
		if (!Wallet.take(b)) {
			s.notEnoughChips();
			return;
		}
		if (shoe.size() < 60) reshuffle();
		stake = b;
		player.clear();
		dealer.clear();
		resultText = null;
		state = State.DEALING;
		lastDealTime = s.now() - 0.32;
		give(player, true);
		give(dealer, true);
		give(player, true);
		give(dealer, false);
		schedule(lastDealTime + FLY + FLIP + 0.1, this::afterDeal);
	}

	private void afterDeal() {
		if (isBlackjack(player) || isBlackjack(dealer)) {
			revealHole();
			schedule(s.now() + 0.7, this::settle);
			state = State.DEALER;
		} else {
			state = State.PLAYER;
		}
	}

	private void hit() {
		if (state != State.PLAYER) return;
		give(player, true);
		state = State.DEALING;
		schedule(lastDealTime + FLY + FLIP, () -> {
			int t = total(player);
			if (t > 21) {
				revealHole();
				state = State.DEALER;
				schedule(s.now() + 0.7, this::settle);
			} else if (t == 21) {
				stand();
			} else {
				state = State.PLAYER;
			}
		});
	}

	private void doubleDown() {
		if (state != State.PLAYER || player.size() != 2) return;
		if (!Wallet.take(stake)) {
			s.notEnoughChips();
			return;
		}
		stake *= 2;
		give(player, true);
		state = State.DEALING;
		schedule(lastDealTime + FLY + FLIP, () -> {
			if (total(player) > 21) {
				revealHole();
				state = State.DEALER;
				schedule(s.now() + 0.7, this::settle);
			} else {
				stand();
			}
		});
	}

	private void stand() {
		state = State.DEALER;
		revealHole();
		schedule(s.now() + 0.6, this::dealerStep);
	}

	private void dealerStep() {
		if (total(dealer) < 17) {
			give(dealer, true);
			schedule(lastDealTime + FLY + FLIP + 0.25, this::dealerStep);
		} else {
			settle();
		}
	}

	private void revealHole() {
		for (Card c : dealer) {
			if (c.flipAt < 0) {
				c.flipAt = Math.max(s.now(), c.dealAt + FLY);
				Sounds.card();
			}
		}
	}

	private void settle() {
		int p = total(player), d = total(dealer);
		boolean pbj = isBlackjack(player), dbj = isBlackjack(dealer);
		long win;
		if (p > 21) {
			win = 0;
			result("ÜBERKAUFT", Ui.LOSS);
		} else if (pbj && !dbj) {
			win = stake + stake * 3 / 2;
			result("BLACKJACK!", Ui.GOLD);
		} else if (dbj && !pbj) {
			win = 0;
			result("DEALER BLACKJACK", Ui.LOSS);
		} else if (d > 21) {
			win = stake * 2;
			result("DEALER ÜBERKAUFT", Ui.WIN);
		} else if (p > d) {
			win = stake * 2;
			result("GEWONNEN", Ui.WIN);
		} else if (p == d) {
			win = stake;
			result("UNENTSCHIEDEN", Ui.WHITE);
		} else {
			win = 0;
			result("VERLOREN", Ui.LOSS);
		}
		state = State.DONE;
		nextAction = null;
		if (win > 0) Wallet.give(win);
		if (win > stake) {
			s.banner(resultText, "+" + Wallet.fmt(win) + " Chips", resultColor, 110);
			s.celebrate(win, stake, 200, 150);
		} else if (win == stake) {
			s.banner(resultText, "Einsatz zurück", resultColor, 110);
			Sounds.bell(1.0f);
		} else {
			s.banner(resultText, "-" + Wallet.fmt(stake) + " Chips", resultColor, 110);
			Sounds.lose();
		}
		Wallet.endRound(win - stake);
	}

	private void result(String text, int color) {
		resultText = text;
		resultColor = color;
		resultT = s.now();
	}

	@Override
	public void forceFinish() {
		if (!busy()) return;
		nextAction = null;
		if (total(player) <= 21 && !isBlackjack(player) && !isBlackjack(dealer)) {
			while (total(dealer) < 17) dealer.add(draw());
		}
		for (Card c : dealer) if (c.flipAt < 0) c.flipAt = 0;
		settle();
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void render(Ui ui) {
		if (nextAction != null && ui.time >= nextEvent) {
			Runnable r = nextAction;
			nextAction = null;
			r.run();
		}
		// Kartensound bei Ankunft
		for (List<Card> hand : List.of(dealer, player)) {
			for (Card c : hand) {
				double arrive = c.dealAt + FLY * 0.2;
				if (ui.time >= arrive && ui.time - ui.dt < arrive) Sounds.card();
			}
		}

		drawFelt(ui);
		drawShoe(ui);

		for (Card c : dealer) drawCard(ui, c);
		for (Card c : player) drawCard(ui, c);

		if (!dealer.isEmpty()) badge(ui, "DEALER", total(dealer, true, ui.time), 200, 54, false);
		if (!player.isEmpty()) badge(ui, "DU", total(player, true, ui.time), 200, 176, state == State.DONE && total(player) <= 21 && resultColor != Ui.LOSS);

		drawControls(ui);
	}

	private void drawFelt(Ui ui) {
		// Halbkreis-Tischmarkierung
		ui.scissor(AX, AY, AW, AH);
		ui.ring(200, 30, 150, 1, Ui.alpha(Ui.GOLD, 0.35f));
		ui.ring(200, 30, 147, 1, Ui.alpha(Ui.GOLD, 0.15f));
		ui.endScissor();
		ui.textS("BLACKJACK ZAHLT 3 ZU 2", 200, 112, 0.75f, Ui.alpha(Ui.GOLD, 0.55f), true, false);
		ui.textS("Dealer muss bis 16 ziehen und auf 17 stehen", 200, 121, 0.5f, Ui.alpha(Ui.GOLD, 0.45f), true, false);
		// Einsatzfeld
		ui.ring(200, 196, 11, 1, Ui.alpha(Ui.GOLD, 0.5f));
		if (state != State.IDLE) {
			int stack = stake > bet() ? 2 : 1;
			for (int i = 0; i < stack; i++) ui.chip(200 + i * 3, 196 - i * 3, 8, Ui.chipColor(bet()), i == stack - 1 ? Wallet.shortFmt(stake) : null);
		}
	}

	private void drawShoe(Ui ui) {
		ui.shadow(SHOE_X - 4, SHOE_Y - 4, CW + 12, CH + 10, 1.5f);
		ui.panel(SHOE_X - 4, SHOE_Y - 4, CW + 12, CH + 10, 0xFF3A2416, 0xFF1A0E06, 0xFF0A0402);
		for (int i = 3; i >= 0; i--) drawBack(ui, SHOE_X + i, SHOE_Y - i * 0.7f);
		ui.textS(shoe.size() + " Karten", SHOE_X + CW / 2 + 2, SHOE_Y + CH + 9, 0.5f, Ui.alpha(Ui.MUTED, 0.8f), true, false);
	}

	private void drawCard(Ui ui, Card c) {
		double now = ui.time;
		if (now < c.dealAt) return;
		float u = (float) ((now - c.dealAt) / FLY);
		float e = Ui.easeOutCubic(u);
		float x = Ui.lerp(SHOE_X, c.tx, e);
		float y = Ui.lerp(SHOE_Y, c.ty, e) - (float) Math.sin(Math.min(1, u) * Math.PI) * 10;
		float rot = (1 - e) * -0.5f;

		float flip = c.flipAt < 0 ? 0 : Ui.clamp((float) ((now - c.flipAt) / FLIP), 0, 1);
		float sx = Math.abs((float) Math.cos(flip * Math.PI));
		boolean face = flip >= 0.5f;
		float lift = (float) Math.sin(flip * Math.PI) * 3;

		Matrix3x2fStack p = ui.pose();
		p.pushMatrix();
		p.translate(x + CW / 2, y + CH / 2 - lift);
		p.rotate(rot);
		p.scale(Math.max(0.04f, sx), 1);
		ui.round(-CW / 2 + 1, -CH / 2 + 2 + lift, CW, CH, 0x60000000);
		if (face) drawFace(ui, c, -CW / 2, -CH / 2);
		else drawBack(ui, -CW / 2, -CH / 2);
		p.popMatrix();
	}

	private void drawFace(Ui ui, Card c, float x, float y) {
		int col = c.red() ? 0xFFD21E36 : 0xFF141418;
		ui.round(x, y, CW, CH, 0xFF8A8A8A);
		ui.roundGrad(x + 1, y + 1, CW - 2, CH - 2, 0xFFFFFFFF, 0xFFEDEAE2);
		String r = RANKS[c.rank], su = SUITS[c.suit];
		ui.textS(r, x + 2.5f, y + 2.5f, 0.75f, col, false, false);
		ui.textS(su, x + 2.5f, y + 9.5f, 0.75f, col, false, false);
		ui.textS(su, x + CW / 2, y + CH / 2 - 8, 2, col, true, false);
		ui.textR(r, x + CW - 2.5f, y + CH - 9, 0.75f, col);
		if (c.rank >= 10) ui.outline(x + 6, y + 6, CW - 12, CH - 12, Ui.alpha(col, 0.35f));
	}

	private void drawBack(Ui ui, float x, float y) {
		ui.round(x, y, CW, CH, 0xFF5A0C14);
		ui.roundGrad(x + 1, y + 1, CW - 2, CH - 2, 0xFFB01E30, 0xFF7A0E1C);
		ui.outline(x + 3, y + 3, CW - 6, CH - 6, Ui.alpha(Ui.GOLD, 0.8f));
		for (int yy = 0; yy < 8; yy++) {
			for (int xx = 0; xx < 5; xx++) {
				if ((xx + yy) % 2 == 0) ui.rect(x + 5 + xx * 4, y + 5 + yy * 4, 2, 2, 0x40FFFFFF);
			}
		}
		ui.circle(x + CW / 2, y + CH / 2, 5, Ui.GOLD);
		ui.textS("C", x + CW / 2, y + CH / 2 - 3.5f, 0.75f, 0xFF7A0E1C, true, false);
	}

	private void badge(Ui ui, String who, int value, float x, float y, boolean good) {
		String v = String.valueOf(value);
		boolean bust = value > 21;
		int col = bust ? Ui.LOSS : (value == 21 ? Ui.GOLD : (good ? Ui.WIN : Ui.WHITE));
		float w = ui.width(who + "  " + v) * 0.75f + 10;
		ui.round(x - w / 2, y - 5, w, 11, 0xC0000000);
		ui.textS(who, x - w / 2 + 5, y - 2.5f, 0.75f, Ui.alpha(Ui.GOLD, 0.9f), false, false);
		ui.textR(v, x + w / 2 - 5, y - 2.5f, 0.75f, col);
	}

	private void drawControls(Ui ui) {
		float y = 140;
		float x = 300;
		if (state == State.PLAYER) {
			ui.panel(x - 4, y - 18, 108, 76, 0xD0101418, 0xD0080A0C, 0xFF2A3036);
			ui.textS("DEIN ZUG", x + 50, y - 13, 0.75f, Ui.GOLD, true, true);
			if (ui.button("hit", "KARTE (H)", x, y - 3, 100, 16, 0xFF1F8A4C, true)) hit();
			if (ui.button("stand", "HALTEN (S)", x, y + 17, 100, 16, 0xFFC8283C, true)) stand();
			boolean canDouble = player.size() == 2 && Wallet.canAfford(stake);
			if (ui.button("double", "DOPPELN (D)", x, y + 37, 100, 16, 0xFF7B3FC4, canDouble)) doubleDown();
		} else if (!busy()) {
			if (ui.button("deal", "AUSTEILEN", x, y + 17, 100, 18, 0xFF2E6FD8, true)) deal();
			ui.textS("Leertaste = Austeilen", x + 50, y + 40, 0.5f, Ui.alpha(Ui.MUTED, 0.7f), true, false);
		} else {
			ui.textS(state == State.DEALER ? "Dealer zieht…" : "Karten werden gegeben…", x + 50, y + 22, 0.75f, Ui.alpha(Ui.MUTED, 0.6f + 0.4f * ui.pulse(6)), true, true);
		}
		// Ergebnis-Schild links
		if (resultText != null && state == State.DONE) {
			float t = Ui.easeOutBack((float) (ui.time - resultT) / 0.4f);
			ui.pose().pushMatrix();
			ui.pose().translate(64, 150);
			ui.pose().scale(t, t);
			ui.panel(-50, -14, 100, 28, 0xE0101418, 0xE0080A0C, resultColor);
			ui.textS(resultText, 0, -9, 0.75f, resultColor, true, true);
			ui.textS("Einsatz " + Wallet.fmt(stake), 0, 2, 0.5f, Ui.MUTED, true, false);
			ui.pose().popMatrix();
		}
	}
}
