package net.ccasino;

import net.ccasino.ui.CasinoScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

/**
 * Client-Einstiegspunkt: registriert /ccasino und öffnet den Casino-Screen.
 * Der Screen wird erst im nächsten Client-Tick geöffnet, weil sich der Chat
 * nach einem Befehl sonst wieder über unseren Screen legt.
 */
public class CasinoMod implements ClientModInitializer {
	public static final String MOD_ID = "ccasino";
	private static boolean openRequested;

	@Override
	public void onInitializeClient() {
		Wallet.load();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("ccasino")
						.executes(ctx -> {
							openRequested = true;
							return 1;
						})
						.then(ClientCommands.literal("reset").executes(ctx -> {
							Wallet.reset();
							ctx.getSource().sendFeedback(Component.literal("§6[Casino] §7Chips auf §e1.000 §7zurückgesetzt."));
							return 1;
						}))
						.then(ClientCommands.literal("stats").executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal(
									"§6[Casino] §7Chips: §e" + Wallet.fmt(Wallet.balance())
											+ " §7| Runden: §e" + Wallet.rounds()
											+ " §7| Größter Gewinn: §a" + Wallet.fmt(Wallet.biggestWin())));
							return 1;
						}))));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openRequested) {
				openRequested = false;
				client.setScreen(new CasinoScreen());
			}
		});
	}
}
