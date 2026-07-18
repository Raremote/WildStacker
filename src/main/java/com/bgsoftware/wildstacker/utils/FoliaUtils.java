package com.bgsoftware.wildstacker.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public final class FoliaUtils {

    private static final boolean IS_FOLIA;
    private static final boolean HAS_SCHEDULER_API;

    static {
        boolean isFolia = false;
        boolean hasSchedulerApi = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            hasSchedulerApi = true;
        } catch (Throwable ex) {
            try {
                Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                hasSchedulerApi = true;
            } catch (Throwable ignored) {
            }
        }
        IS_FOLIA = isFolia;
        HAS_SCHEDULER_API = hasSchedulerApi;
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static boolean hasSchedulerApi() {
        return HAS_SCHEDULER_API;
    }

    public static boolean isOwnedByCurrentRegion(Location location) {
        if (!HAS_SCHEDULER_API)
            return true;

        try {
            Object regionScheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            return (boolean) regionScheduler.getClass()
                    .getMethod("isOwnedByCurrentRegion", Location.class)
                    .invoke(regionScheduler, location);
        } catch (Throwable ex) {
            return true;
        }
    }

    public static boolean isGlobalOrPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }

    @SuppressWarnings("unchecked")
    public static void runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (HAS_SCHEDULER_API) {
            try {
                Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
                scheduler.getClass()
                        .getMethod("run", Plugin.class, Consumer.class, Runnable.class)
                        .invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), null);
                return;
            } catch (Throwable ex) {
                if (!plugin.isEnabled())
                    return;
                ex.printStackTrace();
            }
        }

        if (IS_FOLIA) {
            task.run();
            return;
        }

        if (!Bukkit.isPrimaryThread())
            Bukkit.getScheduler().runTask(plugin, task);
        else
            task.run();
    }

    @SuppressWarnings("unchecked")
    public static void runRegionTask(Plugin plugin, Location location, Runnable task) {
        if (HAS_SCHEDULER_API) {
            try {
                Object regionScheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
                regionScheduler.getClass()
                        .getMethod("run", Plugin.class, Location.class, Consumer.class)
                        .invoke(regionScheduler, plugin, location, (Consumer<Object>) t -> task.run());
                return;
            } catch (Throwable ex) {
                if (!plugin.isEnabled())
                    return;
                ex.printStackTrace();
            }
        }

        if (IS_FOLIA) {
            task.run();
            return;
        }

        if (!Bukkit.isPrimaryThread())
            Bukkit.getScheduler().runTask(plugin, task);
        else
            task.run();
    }

    @SuppressWarnings("unchecked")
    public static void runEntityTaskDelayed(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (HAS_SCHEDULER_API) {
            try {
                Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
                scheduler.getClass()
                        .getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class)
                        .invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), null, delayTicks);
                return;
            } catch (Throwable ex) {
                if (!plugin.isEnabled())
                    return;
                ex.printStackTrace();
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    @SuppressWarnings("unchecked")
    public static void runRegionTaskDelayed(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (HAS_SCHEDULER_API) {
            try {
                Object regionScheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
                regionScheduler.getClass()
                        .getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class)
                        .invoke(regionScheduler, plugin, location, (Consumer<Object>) t -> task.run(), delayTicks);
                return;
            } catch (Throwable ex) {
                if (!plugin.isEnabled())
                    return;
                ex.printStackTrace();
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }
}
