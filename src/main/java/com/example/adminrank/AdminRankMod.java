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
    /** Second rank: anyone with the vanilla tag "hrmod" (/tag Name add hrmod). */
    private static final String TEAM_HR = "hrmod";
    private static final String HR_TAG = "hrmod";
    /** Indigo shades the HR MOD letters flow through. */
    private static final String[] INDIGO = {
        "#3730A3", "#4338CA", "#4F46E5", "#6366F1", "#818CF8", "#A5B4FC", "#818CF8", "#6366F1", "#4F46E5", "#4338CA"
    };
    private static final String HR_LETTERS = "HR MOD";
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
            run(server, "team add " + TEAM_HR);
            run(server, "team modify " + TEAM_HR + " color white");
            teamCreated = true;
        }
        if (ticks % ANIMATION_EVERY_TICKS == 0) {
            frame = (frame + 1) % FRAMES;
            run(server, "team modify " + TEAM + " prefix " + prefix(frame));
            run(server, "team modify " + TEAM_HR + " prefix " + hrPrefix(frame));
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

    /** "\u2726 [HR MOD] " with an indigo wave flowing through the letters. */
    private static String hrPrefix(int frame) {
        StringBuilder sb = new StringBuilder("[{text:\"\u2726 \",color:\"#A5B4FC\"},{text:\"[\",color:\"#312E81\"}");
        for (int i = 0; i < HR_LETTERS.length(); i++) {
            char c = HR_LETTERS.charAt(i);
            if (c == ' ') {
                sb.append(",{text:\" \"}");
                continue;
            }
            String color = INDIGO[Math.floorMod(i - frame, INDIGO.length)];
            sb.append(",{text:\"").append(c).append("\",color:\"").append(color).append("\",bold:true}");
        }
        sb.append(",{text:\"] \",color:\"#312E81\"}]");
        return sb.toString();
    }

    /** Puts operators into the team and takes de-opped players out again. */
    private void sync(MinecraftServer server) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String name = sp.getName().getString();
            PlayerTeam current = sp.getTeam() instanceof PlayerTeam pt ? pt : null;
            boolean inOurs = current != null && TEAM.equals(current.getName());
            boolean op = isOp(sp);
            boolean inHr = current != null && TEAM_HR.equals(current.getName());
            if (op && (current == null || inHr)) {
                run(server, "team join " + TEAM + " " + name);   // ADMIN wins over HR MOD
            } else if (!op && inOurs) {
                run(server, "team leave " + name);
            }
        }
        // HR MOD: players carrying the "hrmod" tag who are not in any team yet.
        run(server, "team join " + TEAM_HR + " @a[tag=" + HR_TAG + ",team=]");
        run(server, "team leave @a[team=" + TEAM_HR + ",tag=!" + HR_TAG + "]");
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
