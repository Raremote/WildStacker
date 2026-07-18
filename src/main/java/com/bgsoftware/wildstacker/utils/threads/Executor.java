package com.bgsoftware.wildstacker.utils.threads;

import com.bgsoftware.wildstacker.WildStackerPlugin;
import com.bgsoftware.wildstacker.utils.FoliaUtils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Executor {

    private static final ExecutorService dataService = Executors.newFixedThreadPool(3,
            new ThreadFactoryBuilder().setNameFormat("WildStacker Database Thread #%d").build());
    private static final WildStackerPlugin plugin = WildStackerPlugin.getPlugin();
    private static boolean shutdown = false, dataShutdown = false;

    private static Object globalRegionScheduler;
    private static Object asyncScheduler;
    private static Method globalRunNow;
    private static Method globalRunDelayed;
    private static Method globalRunAtFixedRate;
    private static Method globalCancelTasks;
    private static Method asyncRunNow;
    private static Method asyncRunDelayed;
    private static Method asyncCancelTasks;
    private static boolean schedulerMethodsLoaded;

    public static void sync(Runnable runnable) {
        if (shutdown)
            return;

        if (FoliaUtils.hasSchedulerApi()) {
            runGlobalRegionScheduler(runnable);
            return;
        }

        if (!Bukkit.isPrimaryThread())
            Bukkit.getScheduler().runTask(plugin, runnable);
        else
            runnable.run();
    }

    public static void sync(Runnable runnable, long delayedTime) {
        if (shutdown)
            return;

        if (FoliaUtils.hasSchedulerApi()) {
            runGlobalRegionSchedulerDelayed(runnable, delayedTime);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, runnable, delayedTime);
    }

    public static void sync(Runnable runnable, Entity entity) {
        if (shutdown)
            return;

        if (FoliaUtils.hasSchedulerApi()) {
            FoliaUtils.runEntityTask(plugin, entity, runnable);
            return;
        }

        if (!Bukkit.isPrimaryThread())
            Bukkit.getScheduler().runTask(plugin, runnable);
        else
            runnable.run();
    }

    public static void sync(Runnable runnable, Entity entity, long delay) {
        if (shutdown)
            return;

        if (FoliaUtils.hasSchedulerApi()) {
            FoliaUtils.runEntityTaskDelayed(plugin, entity, runnable, delay);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, runnable, delay);
    }

    public static void sync(Runnable runnable, Location location) {
        if (shutdown)
            return;

        if (FoliaUtils.hasSchedulerApi()) {
            FoliaUtils.runRegionTask(plugin, location, runnable);
            return;
        }

        if (!Bukkit.isPrimaryThread())
            Bukkit.getScheduler().runTask(plugin, runnable);
        else
            runnable.run();
    }

    public static void sync(Runnable runnable, Location location, long delay) {
        if (shutdown)
            return;

        if (FoliaUtils.hasSchedulerApi()) {
            FoliaUtils.runRegionTaskDelayed(plugin, location, runnable, delay);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, runnable, delay);
    }

    public static void runAtEndOfTick(Runnable code) {
        if (shutdown)
            return;

        if (FoliaUtils.hasSchedulerApi()) {
            runGlobalRegionSchedulerDelayed(code, 1L);
            return;
        }

        plugin.getNMSAdapter().runAtEndOfTick(code);
    }

    public static void async(Runnable runnable) {
        if (shutdown)
            return;

        if (FoliaUtils.hasSchedulerApi()) {
            runAsyncScheduler(runnable);
            return;
        }

        if (!Bukkit.isPrimaryThread())
            runnable.run();
        else
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    public static void async(Runnable runnable, long delay) {
        if (shutdown)
            return;

        if (FoliaUtils.hasSchedulerApi()) {
            runAsyncSchedulerDelayed(runnable, delay);
            return;
        }

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delay);
    }

    public static BukkitTask timer(Runnable runnable, long period) {
        return timer(runnable, 0L, period);
    }

    public static BukkitTask timer(Runnable runnable, long initialDelay, long period) {
        if (FoliaUtils.hasSchedulerApi())
            return new FoliaTask(runGlobalRegionSchedulerAtFixedRate(runnable, initialDelay, period));

        return Bukkit.getScheduler().runTaskTimer(plugin, runnable, initialDelay, period);
    }

    public static BukkitTask timerAsync(Runnable runnable, long period) {
        if (FoliaUtils.hasSchedulerApi())
            return new FoliaTask(runGlobalRegionSchedulerAtFixedRate(runnable, 0L, period));

        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, 0L, period);
    }

    public static void cancelTasks() {
        if (FoliaUtils.hasSchedulerApi()) {
            cancelGlobalRegionSchedulerTasks();
            cancelAsyncSchedulerTasks();
            return;
        }
        try {
            Bukkit.getScheduler().cancelAllTasks();
        } catch (Throwable ex) {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }

    public static boolean isDataThread() {
        return Thread.currentThread().getName().contains("WildStacker Database Thread");
    }

    public static void data(Runnable runnable) {
        if (dataShutdown)
            return;

        dataService.execute(runnable);
    }

    public static void stop() {
        shutdown = true;
    }

    public static void stopData() {
        try {
            dataShutdown = true;
            WildStackerPlugin.log("Shutting down database executor");
            shutdownAndAwaitTermination();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void shutdownAndAwaitTermination() {
        dataService.shutdown();
        try {
            if (!dataService.awaitTermination(60, TimeUnit.SECONDS)) {
                dataService.shutdownNow();
                if (!dataService.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            dataService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void loadSchedulerMethods() {
        if (schedulerMethodsLoaded)
            return;

        try {
            globalRegionScheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler")
                    .invoke(Bukkit.getServer());
            Class<?> globalClass = globalRegionScheduler.getClass();
            globalRunNow = globalClass.getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);
            globalRunDelayed = globalClass.getMethod("runDelayed", org.bukkit.plugin.Plugin.class,
                    Consumer.class, long.class);
            globalRunAtFixedRate = globalClass.getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class,
                    Consumer.class, long.class, long.class);
            globalCancelTasks = globalClass.getMethod("cancelTasks", org.bukkit.plugin.Plugin.class);

            asyncScheduler = Bukkit.getServer().getClass().getMethod("getAsyncScheduler")
                    .invoke(Bukkit.getServer());
            Class<?> asyncClass = asyncScheduler.getClass();
            asyncRunNow = asyncClass.getMethod("runNow", org.bukkit.plugin.Plugin.class, Consumer.class);
            asyncRunDelayed = asyncClass.getMethod("runDelayed", org.bukkit.plugin.Plugin.class,
                    Consumer.class, long.class, TimeUnit.class);
            asyncCancelTasks = asyncClass.getMethod("cancelTasks", org.bukkit.plugin.Plugin.class);

            schedulerMethodsLoaded = true;
        } catch (Throwable ex) {
            WildStackerPlugin.log("Failed to load Folia scheduler methods: " + ex.getMessage());
        }
    }

    private static boolean tryRunGlobalScheduler(Runnable runnable) {
        if (shutdown)
            return false;

        if (!schedulerMethodsLoaded)
            loadSchedulerMethods();

        if (!schedulerMethodsLoaded) {
            if (Bukkit.isPrimaryThread()) {
                runnable.run();
                return true;
            }
            return false;
        }

        try {
            Consumer<Object> consumer = task -> runnable.run();
            globalRunNow.invoke(globalRegionScheduler, plugin, consumer);
            return true;
        } catch (Throwable ex) {
            WildStackerPlugin.log("Failed to run global region task: " + ex.getMessage());
            if (Bukkit.isPrimaryThread()) {
                runnable.run();
                return true;
            }
            return false;
        }
    }

    private static void runGlobalRegionScheduler(Runnable runnable) {
        tryRunGlobalScheduler(runnable);
    }

    private static void runGlobalRegionSchedulerDelayed(Runnable runnable, long delay) {
        if (!schedulerMethodsLoaded)
            loadSchedulerMethods();

        if (!schedulerMethodsLoaded)
            return;

        try {
            Consumer<Object> consumer = task -> runnable.run();
            globalRunDelayed.invoke(globalRegionScheduler, plugin, consumer, delay);
        } catch (Throwable ex) {
            WildStackerPlugin.log("Failed to run delayed global region task: " + ex.getMessage());
        }
    }

    private static Object runGlobalRegionSchedulerAtFixedRate(Runnable runnable, long initialDelay, long period) {
        if (!schedulerMethodsLoaded)
            loadSchedulerMethods();

        if (!schedulerMethodsLoaded)
            return null;

        try {
            Consumer<Object> consumer = task -> runnable.run();
            return globalRunAtFixedRate.invoke(globalRegionScheduler, plugin, consumer, initialDelay, period);
        } catch (Throwable ex) {
            WildStackerPlugin.log("Failed to run repeating global region task: " + ex.getMessage());
            return null;
        }
    }

    private static void cancelGlobalRegionSchedulerTasks() {
        loadSchedulerMethods();
        try {
            globalCancelTasks.invoke(globalRegionScheduler, plugin);
        } catch (Throwable ex) {
            WildStackerPlugin.log("Failed to cancel global region tasks: " + ex.getMessage());
        }
    }

    private static void cancelAsyncSchedulerTasks() {
        loadSchedulerMethods();
        try {
            asyncCancelTasks.invoke(asyncScheduler, plugin);
        } catch (Throwable ex) {
            WildStackerPlugin.log("Failed to cancel async tasks: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void runAsyncScheduler(Runnable runnable) {
        loadSchedulerMethods();
        try {
            Consumer<Object> consumer = task -> runnable.run();
            asyncRunNow.invoke(asyncScheduler, plugin, consumer);
        } catch (Throwable ex) {
            WildStackerPlugin.log("Failed to run async task: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void runAsyncSchedulerDelayed(Runnable runnable, long delayTicks) {
        loadSchedulerMethods();
        try {
            Consumer<Object> consumer = task -> runnable.run();
            asyncRunDelayed.invoke(asyncScheduler, plugin, consumer, delayTicks * 50L, TimeUnit.MILLISECONDS);
        } catch (Throwable ex) {
            WildStackerPlugin.log("Failed to run delayed async task: " + ex.getMessage());
        }
    }

    private static final class FoliaTask implements BukkitTask {

        private final Object scheduledTask;

        FoliaTask(Object scheduledTask) {
            this.scheduledTask = scheduledTask;
        }

        @Override
        public int getTaskId() {
            return -1;
        }

        @Override
        public org.bukkit.plugin.Plugin getOwner() {
            return plugin;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        public boolean isCancelled() {
            if (scheduledTask == null)
                return true;
            try {
                Method isCancelled = scheduledTask.getClass().getMethod("isCancelled");
                return (boolean) isCancelled.invoke(scheduledTask);
            } catch (Throwable ex) {
                return false;
            }
        }

        @Override
        public void cancel() {
            if (scheduledTask == null)
                return;
            try {
                Method cancel = scheduledTask.getClass().getMethod("cancel");
                cancel.invoke(scheduledTask);
            } catch (Throwable ignored) {
            }
        }
    }
}
