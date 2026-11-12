package pl.anatracker;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

public class AnaTrackerClient implements ClientModInitializer {
    static boolean openGui = false;

    @Override
    public void onInitializeClient() {
        Cfg.load();

        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!overlay) Tracker.onMessage(msg.getString());
        });
        ClientReceiveMessageEvents.CHAT.register((msg, signed, sender, params, ts) ->
                Tracker.onMessage(msg.getString()));

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            Tracker.tick();
            if (openGui) {
                openGui = false;
                c.setScreen(new AnaScreen());
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("anatracker")
                        .executes(c -> { openGui = true; return 1; })
                        .then(ClientCommandManager.literal("gui").executes(c -> { openGui = true; return 1; }))
                        .then(ClientCommandManager.literal("on").executes(c -> {
                            Cfg.I.enabled = true; Cfg.save();
                            c.getSource().sendFeedback(Text.literal("§e[AnaTracker] §aWŁĄCZONY"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("off").executes(c -> {
                            Cfg.I.enabled = false; Cfg.save();
                            c.getSource().sendFeedback(Text.literal("§e[AnaTracker] §cWYŁĄCZONY"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("ban")
                                .then(ClientCommandManager.argument("nick", StringArgumentType.word()).executes(c -> {
                                    String n = StringArgumentType.getString(c, "nick");
                                    Tracker.ban(n);
                                    c.getSource().sendFeedback(Text.literal("§e[AnaTracker] §fZbanowano " + n));
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("unban")
                                .then(ClientCommandManager.argument("nick", StringArgumentType.word()).executes(c -> {
                                    String n = StringArgumentType.getString(c, "nick");
                                    Cfg.I.banned.remove(n.toLowerCase()); Cfg.save();
                                    c.getSource().sendFeedback(Text.literal("§e[AnaTracker] §fOdbanowano " + n));
                                    return 1;
                                })))
                ));
    }
}
