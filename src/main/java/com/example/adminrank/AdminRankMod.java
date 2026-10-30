package com.example.adminrank;

import java.lang.reflect.Method;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Admin Rank: gives every operator an animated [ADMIN] tag.
 *
 * It uses a scoreboard team prefix, so the tag shows in the tab list and above the
 * player's head, and vanilla clients can see it. The red letters have a white shimmer
 * that sweeps across them.
 */
public class AdminRankMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("adminrank");
    private static final String TEAM = "adminrank";
    private static final String LETTERS = "ADMIN";
    /** Frames of the shimmer: it runs across the letters, then pauses. */
    private static final int FRAMES = LETTERS.length() + 6;
    private static final int ANIMATION_EVERY_TICKS = 3;
    private static final int SYNC_EVERY_TICKS = 20;

    private int ticks;
    private int frame;
    private boolean teamCreated;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        LOGGER.info("Admin Rank loaded.");
    }

    private void tick(MinecraftServer server) {
        ticks++;
        if (!teamCreated) {
            run(server, "team add " + TEAM);          // harmless if it already exists
            run(server, "team modify " + TEAM + " color white");
            teamCreated = true;
        }
        if (ticks % ANIMATION_EVERY_TICKS == 0) {
            frame = (frame + 1) % FRAMES;
            run(server, "team modify " + TEAM + " prefix " + prefix(frame));
        }
        if (ticks % SYNC_EVERY_TICKS == 0) sync(server);
    }

    /** "[ADMIN] " with a white shimmer on the letter at {@code frame}. */
    private static String prefix(int frame) {
        StringBuilder sb = new StringBuilder("[{text:\"[\",color:\"dark_gray\"}");
        for (int i = 0; i < LETTERS.length(); i++) {
            int d = Math.abs(i - frame);
            String color = d == 0 ? "white" : d == 1 ? "yellow" : "red";
            sb.append(",{text:\"").append(LETTERS.charAt(i)).append("\",color:\"")
                    .append(color).append("\",bold:true}");
        }
        sb.append(",{text:\"] \",color:\"dark_gray\"}]");
        return sb.toString();
    }

    /** Puts operators into the team and takes de-opped players out again. */
    private void sync(MinecraftServer server) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String name = sp.getName().getString();
            PlayerTeam current = sp.getTeam() instanceof PlayerTeam pt ? pt : null;
            boolean inOurs = current != null && TEAM.equals(current.getName());
            boolean op = isOp(sp);
            if (op && current == null) {
                run(server, "team join " + TEAM + " " + name);
            } else if (!op && inOurs) {
                run(server, "team leave " + name);
            }
        }
    }

    private static void run(MinecraftServer server, String command) {
        try {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), command);
        } catch (Exception e) {
            LOGGER.debug("Command failed: {}", command, e);
        }
    }

    // ------------------------------------------------------------ operator check
    // Minecraft changed how permissions are queried between versions, so we look the
    // method up by name instead of depending on one exact signature.

    private static boolean permissionLookedUp;
    private static Method hasPermissionsInt;

    private static boolean isOp(ServerPlayer sp) {
        if (!permissionLookedUp) {
            permissionLookedUp = true;
            try {
                hasPermissionsInt = sp.getClass().getMethod("hasPermissions", int.class);
            } catch (Throwable t) {
                hasPermissionsInt = null;
            }
        }
        try {
            if (hasPermissionsInt != null) {
                return (boolean) hasPermissionsInt.invoke(sp, 2);
            }
            return isOpViaPlayerList(sp);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isOpViaPlayerList(ServerPlayer sp) throws Exception {
        Object list = sp.level().getServer().getPlayerList();
        for (Method m : list.getClass().getMethods()) {
            if (!m.getName().equals("isOp") || m.getParameterCount() != 1) continue;
            Class<?> want = m.getParameterTypes()[0];
            for (String getter : new String[] {"nameAndId", "getGameProfile"}) {
                try {
                    Object arg = sp.getClass().getMethod(getter).invoke(sp);
                    if (want.isInstance(arg)) return (boolean) m.invoke(list, arg);
                } catch (NoSuchMethodException ignored) {
                    // try the next getter
                }
            }
        }
        return false;
    }
}
