package net.ccasino;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** Virtuelle Chips – werden in config/ccasino.properties gespeichert. Kein echtes Geld, keine Items. */
public final class Wallet {
	public static final long START = 1000;
	public static final long REFILL = 500;

	private static long balance = START;
	private static long biggestWin = 0;
	private static long rounds = 0;

	private Wallet() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ccasino.properties");
	}

	public static void load() {
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			Properties p = new Properties();
			try (Reader r = Files.newBufferedReader(f)) {
				p.load(r);
			}
			balance = Long.parseLong(p.getProperty("balance", String.valueOf(START)));
			biggestWin = Long.parseLong(p.getProperty("biggestWin", "0"));
			rounds = Long.parseLong(p.getProperty("rounds", "0"));
		} catch (Exception e) {
			balance = START;
		}
	}

	public static void save() {
		try {
			Properties p = new Properties();
			p.setProperty("balance", String.valueOf(balance));
			p.setProperty("biggestWin", String.valueOf(biggestWin));
			p.setProperty("rounds", String.valueOf(rounds));
			Files.createDirectories(file().getParent());
			try (Writer w = Files.newBufferedWriter(file())) {
				p.store(w, "Craft Casino - virtuelle Chips");
			}
		} catch (Exception ignored) {
		}
	}

	public static long balance() { return balance; }
	public static long biggestWin() { return biggestWin; }
	public static long rounds() { return rounds; }

	public static boolean canAfford(long amount) { return amount > 0 && balance >= amount; }

	/** Zieht einen Einsatz ab. */
	public static boolean take(long amount) {
		if (!canAfford(amount)) return false;
		balance -= amount;
		save();
		return true;
	}

	/** Zahlt aus (inkl. Einsatz). */
	public static void give(long amount) {
		if (amount <= 0) return;
		balance += amount;
		save();
	}

	/** Am Ende jeder Runde aufrufen; netWin = Gewinn minus Einsatz. */
	public static void endRound(long netWin) {
		rounds++;
		if (netWin > biggestWin) biggestWin = netWin;
		save();
	}

	public static boolean canRefill() { return balance < 10; }

	public static void refill() {
		if (canRefill()) {
			balance += REFILL;
			save();
		}
	}

	public static void reset() {
		balance = START;
		biggestWin = 0;
		rounds = 0;
		save();
	}

	/** 12345 -> "12.345" */
	public static String fmt(long v) {
		String s = Long.toString(Math.abs(v));
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			if (i > 0 && (s.length() - i) % 3 == 0) b.append('.');
			b.append(s.charAt(i));
		}
		return (v < 0 ? "-" : "") + b;
	}

	/** 1500 -> "1.5K" für kleine Chip-Beschriftungen */
	public static String shortFmt(long v) {
		if (v >= 1_000_000) return trim(v / 1_000_000.0) + "M";
		if (v >= 1000) return trim(v / 1000.0) + "K";
		return Long.toString(v);
	}

	private static String trim(double d) {
		String s = String.format(java.util.Locale.ROOT, "%.1f", d);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}
}
