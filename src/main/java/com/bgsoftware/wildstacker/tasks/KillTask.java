package com.bgsoftware.wildstacker.tasks;

import com.bgsoftware.wildstacker.Locale;
import com.bgsoftware.wildstacker.WildStackerPlugin;
import com.bgsoftware.wildstacker.handlers.SystemHandler;
import com.bgsoftware.wildstacker.utils.FoliaUtils;
import com.bgsoftware.wildstacker.utils.threads.Executor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class KillTask extends BukkitRunnable {

    private static final WildStackerPlugin plugin = WildStackerPlugin.getPlugin();

    private static BukkitTask task = null;
    private static long timeLeft;

    private KillTask() {
        timeLeft = plugin.getSettings().killTaskInterval;
        task = Executor.timer(this, 20L, 20L);
    }

    public static void start() {
        if (task != null)
            task.cancel();

        new KillTask();
    }

    public static long getTimeLeft() {
        return timeLeft;
    }

    @Override
    public void run() {
        if (plugin.getSettings().killTaskInterval > 0) {
            if (timeLeft == 0) {
                if (Bukkit.getOnlinePlayers().size() > 0) {
                    if (FoliaUtils.isFolia()) {
                        for (World world : Bukkit.getWorlds()) {
                            for (Chunk chunk : world.getLoadedChunks()) {
                                Location loc = new Location(world, chunk.getX() << 4, 0, chunk.getZ() << 4);
                                FoliaUtils.runRegionTask(plugin, loc,
                                        () -> ((SystemHandler) plugin.getSystemManager()).performKillAllForChunk(chunk));
                            }
                        }
                    } else {
                        plugin.getSystemManager().performKillAll(true);
                    }
                    for (Player player : Bukkit.getOnlinePlayers())
                        Locale.KILL_ALL_ANNOUNCEMENT.send(player);
                }

                timeLeft = plugin.getSettings().killTaskInterval;
                return;
            }

            if (timeLeft == 10 || timeLeft == 30 || timeLeft == 60) {
                for (Player player : Bukkit.getOnlinePlayers())
                    Locale.KILL_ALL_REMAINING_TIME.send(player, timeLeft);
            }

            timeLeft -= 1;
        }
    }

}
