package dev.spiritstudios.cantilever.bridge;

import dev.spiritstudios.cantilever.Cantilever;
import dev.spiritstudios.cantilever.CantileverConfig;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;

public class BridgeEvents {
	private static Bridge bridge;

	public static void init(Bridge bridge) {
		BridgeEvents.bridge = bridge;

		registerMinecraftEvents();
		registerDiscordEvents();
	}

	private static void registerMinecraftEvents() {
		ServerLifecycleEvents.SERVER_STARTING.register(
			Identifier.of(Cantilever.MODID, "after_bridge"),
			server -> BridgeEvents.bridge.sendBasicMessageM2D(CantileverConfig.INSTANCE.gameEventFormat.get().formatted("Server starting..."))
		);

		ServerLifecycleEvents.SERVER_STARTED.register(server ->
			BridgeEvents.bridge.sendBasicMessageM2D(CantileverConfig.INSTANCE.gameEventFormat.get().formatted("Server started"))
		);

		ServerLifecycleEvents.SERVER_STOPPING.register(server ->
			BridgeEvents.bridge.sendBasicMessageM2D(CantileverConfig.INSTANCE.gameEventFormat.get().formatted("Server stopping..."))
		);

		ServerLifecycleEvents.SERVER_STOPPED.register(
			Identifier.of(Cantilever.MODID, "before_shutdown"),
			server -> BridgeEvents.bridge.sendBasicMessageM2D(CantileverConfig.INSTANCE.gameEventFormat.get().formatted("Server stopped"))
		);

		ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
			if (message.getContent() instanceof BridgeTextContent content && content.bot()) return;
			BridgeEvents.bridge.sendBasicMessageM2D(CantileverConfig.INSTANCE.gameEventFormat.get().formatted(message.getString()));
		});

		ServerMessageEvents.COMMAND_MESSAGE.register((message, source, parameters) -> {
			if (message.getContent().getContent() instanceof BridgeTextContent content && content.bot()) return;
			BridgeEvents.bridge.sendBasicMessageM2D(CantileverConfig.INSTANCE.gameEventFormat.get().formatted(message.getContent().getString()));
		});

		ServerMessageEvents.CHAT_MESSAGE.register((message, user, params) -> BridgeEvents.bridge.sendWebhookMessageM2D(message, user));
	}

	private static void registerDiscordEvents() {
		BridgeEvents.bridge.api().addEventListener(new ListenerAdapter() {
			@Override
			public void onMessageReceived(@NotNull MessageReceivedEvent event) {
				if (!BridgeEvents.bridge.channel().map(c -> c == event.getChannel()).orElse(false) ||
					event.getAuthor().getIdLong() == event.getJDA().getSelfUser().getIdLong() || event.getAuthor().getIdLong() == BridgeEvents.bridge.getWebhookId()) {
					return;
				}

				Util.getIoWorkerExecutor().execute(() -> {
					try {
						// Catch all to make sure that PluralKit, Tupperbox and other webhook utilizing bots'
						// original message does not get caught and sent.
						Thread.sleep(CantileverConfig.INSTANCE.discordMessageDelay.get());

						var messageHistory = event.getChannel().asTextChannel().getHistoryAround(event.getMessageIdLong(),4).complete();
						if (messageHistory.getMessageById(event.getMessageIdLong()) == null) {
							return;
						}

						BridgeEvents.bridge.sendBasicMessageD2M(new BridgeTextContent(
							Text.of(CantileverConfig.INSTANCE.gameChatFormat.get().formatted(
								event.getAuthor().getName(), event.getMessage().getContentDisplay()
							)),
							true
						));
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
				});
			}
		});
	}
}
