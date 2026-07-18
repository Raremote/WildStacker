package com.bgsoftware.wildstacker.utils.threads;

import com.bgsoftware.wildstacker.WildStackerPlugin;
import com.bgsoftware.wildstacker.api.objects.AsyncStackedObject;
import com.bgsoftware.wildstacker.api.objects.StackedEntity;
import com.bgsoftware.wildstacker.api.objects.StackedItem;
import com.bgsoftware.wildstacker.api.objects.StackedObject;
import com.bgsoftware.wildstacker.objects.WAsyncStackedObject;
import com.bgsoftware.wildstacker.utils.FoliaUtils;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@SuppressWarnings({"WeakerAccess", "BooleanMethodIsAlwaysInverted"})
public final class StackService {

    private static final Map<String, StackServiceWorld> stackServiceWorldMap = Maps.newConcurrentMap();
    private static final Pattern STACKING_THREAD_NAME_PATTERN = Pattern.compile("WildStacker (Items|Entities) Stacking Thread");

    public static void execute(StackedObject stackedObject, StackedObject otherObject, Runnable runnable) {
        Runnable finalRunnable;

        if (stackedObject instanceof AsyncStackedObject && otherObject instanceof AsyncStackedObject) {
            finalRunnable = () -> DoubleMutex.hold((WAsyncStackedObject<?>) stackedObject,
                    (WAsyncStackedObject<?>) otherObject, runnable);
        } else {
            finalRunnable = runnable;
        }

        if (FoliaUtils.hasSchedulerApi()) {
            if (!scheduleOnFolia(stackedObject, finalRunnable))
                execute(stackedObject.getWorld(), StackType.fromObject(stackedObject), finalRunnable);
            return;
        }

        execute(stackedObject.getWorld(), StackType.fromObject(stackedObject), finalRunnable);
    }

    private static boolean scheduleOnFolia(StackedObject<?> stackedObject, Runnable task) {
        if (stackedObject instanceof StackedEntity) {
            FoliaUtils.runEntityTask(WildStackerPlugin.getPlugin(),
                    ((StackedEntity) stackedObject).getLivingEntity(), task);
            return true;
        }
        if (stackedObject instanceof StackedItem) {
            FoliaUtils.runEntityTask(WildStackerPlugin.getPlugin(),
                    ((StackedItem) stackedObject).getItem(), task);
            return true;
        }
        Location loc = stackedObject.getLocation();
        if (loc != null && loc.getWorld() != null) {
            FoliaUtils.runRegionTask(WildStackerPlugin.getPlugin(), loc, task);
            return true;
        }
        return false;
    }

    private static void execute(World world, StackType stackType, Runnable runnable) {
        if (isStackThread()) {
            runnable.run();
            return;
        }

        stackServiceWorldMap.computeIfAbsent(world.getName(), s -> new StackServiceWorld(world.getName())).execute(stackType, runnable);
    }

    public static boolean isStackThread() {
        if (FoliaUtils.hasSchedulerApi())
            return FoliaUtils.isGlobalOrPrimaryThread();

        return STACKING_THREAD_NAME_PATTERN.matcher(Thread.currentThread().getName()).find();
    }

    public static boolean canStackFromThread() {
        if (FoliaUtils.hasSchedulerApi())
            return FoliaUtils.isGlobalOrPrimaryThread();

        return isStackThread() || Bukkit.isPrimaryThread();
    }

    public static void stop() {
        stackServiceWorldMap.values().forEach(StackServiceWorld::stop);
        stackServiceWorldMap.clear();
    }

    public enum StackType {

        ITEMS("Items"),
        ENTITIES("Entities");

        private final String name;

        StackType(String name) {
            this.name = name;
        }

        static StackType fromObject(StackedObject stackedObject) {
            return stackedObject instanceof StackedItem ? ITEMS : ENTITIES;
        }

        @Override
        public String toString() {
            return name;
        }

    }

    private static final class StackServiceWorld {

        private static final WildStackerPlugin plugin = WildStackerPlugin.getPlugin();

        private final Map<StackType, ExecutorService> executorServiceMap = new EnumMap<>(StackType.class);


        StackServiceWorld(String world) {
            for (StackType stackType : StackType.values())
                executorServiceMap.put(stackType, Executors.newFixedThreadPool(2,
                        new ThreadFactoryBuilder().setNameFormat(
                                "WildStacker " + stackType + " Stacking Thread (" + world + ")"
                        ).build()));
        }

        void execute(StackType type, Runnable runnable) {
            executorServiceMap.get(type).submit(runnable);
        }

        void stop() {
            executorServiceMap.values().forEach(ExecutorService::shutdownNow);
        }

    }

}
