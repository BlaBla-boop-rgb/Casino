package net.ccasino.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Kleine Sound-Helfer. Überladen für SoundEvent und Holder, damit beide Konstanten-Arten passen. */
public final class Sounds {
	private Sounds() {}

	public static void play(SoundEvent s, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
	}

	public static void play(Holder<SoundEvent> s, float pitch, float volume) {
		play(s.value(), pitch, volume);
	}

	public static void click() { play(SoundEvents.UI_BUTTON_CLICK, 1.0f, 0.6f); }
	public static void chip() { play(SoundEvents.NOTE_BLOCK_HAT, 1.8f, 0.5f); }
	public static void tick(float pitch) { play(SoundEvents.NOTE_BLOCK_HAT, pitch, 0.25f); }
	public static void thunk() { play(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.8f, 0.6f); }
	public static void card() { play(SoundEvents.BOOK_PAGE_TURN, 1.3f, 0.8f); }
	public static void win() { play(SoundEvents.PLAYER_LEVELUP, 1.2f, 0.6f); }
	public static void coin(float pitch) { play(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch, 0.5f); }
	public static void bigWin() { play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.7f); }
	public static void lose() { play(SoundEvents.NOTE_BLOCK_BASS, 0.6f, 0.6f); }
	public static void bell(float pitch) { play(SoundEvents.NOTE_BLOCK_BELL, pitch, 0.5f); }
	public static void explode() { play(SoundEvents.GENERIC_EXPLODE, 1.4f, 0.35f); }
	public static void launch() { play(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.6f); }
}
