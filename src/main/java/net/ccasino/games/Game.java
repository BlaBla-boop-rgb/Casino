package net.ccasino.games;

import java.util.Random;

import net.ccasino.ui.CasinoScreen;
import net.ccasino.ui.Ui;
import net.minecraft.world.item.ItemStack;

/** Basis für alle Tischspiele. Spielbereich: x 6..414, y 28..212 im Canvas. */
public abstract class Game {
	public static final float AX = 6, AY = 28, AW = 408, AH = 184;

	protected final CasinoScreen s;
	protected final Random rnd = new Random();

	protected Game(CasinoScreen s) {
		this.s = s;
	}

	public abstract String name();
	public abstract ItemStack icon();
	public abstract String[] description();
	public abstract int accent();
	public abstract void render(Ui ui);

	/** Läuft gerade eine Runde? (Sperrt Lobby-Button und Schließen) */
	public boolean busy() { return false; }

	/** Darf der Einsatz gerade verändert werden? */
	public boolean betLocked() { return busy(); }

	public void onKey(int key) {}

	/** Wird beim Schließen des Screens aufgerufen, um offene Runden fair abzurechnen. */
	public void forceFinish() {}

	protected long bet() { return s.bet(); }
}
