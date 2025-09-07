
package net.mcblueice.blueresextension.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TaskScheduler {

    private TaskScheduler() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    public static void runTask(Plugin plugin, Runnable task) {
        try {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    public static void runTaskLater(Plugin plugin, Runnable task, long delay) {
        try {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delay);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    public static void runTask(Player player, Plugin plugin, Runnable task) {
        try {
            player.getScheduler().run(plugin, scheduledTask -> task.run(), () -> {});
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    public static void runTaskLater(Player player, Plugin plugin, Runnable task, long delay) {
        try {
            player.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), () -> {}, delay);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    public static void dispatchCommand(Player player, Plugin plugin, String command) {
        runTask(player, plugin, () -> Bukkit.dispatchCommand(player, command));
    }

    @FunctionalInterface
    public static interface RepeatingTaskHandler {
        void cancel();
    }
    public static RepeatingTaskHandler runRepeatingTask(Plugin plugin, Runnable task, long delay, long period) {
        try {
            long d = Math.max(1L, delay);
            long p = Math.max(1L, period);
            java.util.concurrent.atomic.AtomicReference<Runnable> cancelRef = new java.util.concurrent.atomic.AtomicReference<>();
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
                if (cancelRef.get() == null) cancelRef.set(scheduledTask::cancel);
                task.run();
        }, d, p);
            return () -> {
                Runnable c = cancelRef.get();
                if (c != null) c.run();
            };
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            try {
                class FoliaRepeater implements Runnable, RepeatingTaskHandler {
                    private volatile boolean running = true;
                    @Override
                    public void run() {
                        if (!running) return;
                        task.run();
            TaskScheduler.runTaskLater(plugin, this, Math.max(1L, period));
                    }
                    @Override
                    public void cancel() { running = false; }
                }
                FoliaRepeater repeater = new FoliaRepeater();
        TaskScheduler.runTaskLater(plugin, repeater, Math.max(1L, delay));
                return repeater;
            } catch (NoSuchMethodError | NoClassDefFoundError e2) {
        int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, Math.max(1L, delay), Math.max(1L, period));
                return () -> Bukkit.getScheduler().cancelTask(id);
            }
        }
    }

    public static RepeatingTaskHandler runRepeatingTask(Player player, Plugin plugin, Runnable task, long delay, long period) {
        try {
        long d = Math.max(1L, delay);
        long p = Math.max(1L, period);
            java.util.concurrent.atomic.AtomicReference<Runnable> cancelRef = new java.util.concurrent.atomic.AtomicReference<>();
            player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
                if (cancelRef.get() == null) cancelRef.set(scheduledTask::cancel);
                task.run();
        }, () -> {}, d, p);
            return () -> {
                Runnable c = cancelRef.get();
                if (c != null) c.run();
            };
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            try {
                class FoliaRepeater implements Runnable, RepeatingTaskHandler {
                    private volatile boolean running = true;
                    @Override
                    public void run() {
                        if (!running) return;
                        task.run();
            TaskScheduler.runTaskLater(player, plugin, this, Math.max(1L, period));
                    }
                    @Override
                    public void cancel() { running = false; }
                }
                FoliaRepeater repeater = new FoliaRepeater();
        TaskScheduler.runTaskLater(player, plugin, repeater, Math.max(1L, delay));
                return repeater;
            } catch (NoSuchMethodError | NoClassDefFoundError e2) {
        int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, Math.max(1L, delay), Math.max(1L, period));
                return () -> Bukkit.getScheduler().cancelTask(id);
            }
        }
    }
}