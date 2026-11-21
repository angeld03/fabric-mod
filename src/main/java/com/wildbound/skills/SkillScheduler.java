package com.wildbound.skills;

import com.wildbound.Wildbound;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

/**
 * Small server-tick scheduler for skill animations.
 *
 * <p>Skill effects must be spread across real game ticks: spawning every
 * particle inside one server tick looks like a single flash (or nothing), and
 * blocking the server thread with {@code Thread.sleep} freezes the game. This
 * scheduler replaces both patterns.</p>
 */
public final class SkillScheduler {
    private static final List<Task> TASKS = new CopyOnWriteArrayList<>();

    private SkillScheduler() {
    }

    /**
     * Runs {@code action} once per {@code interval} ticks, starting after
     * {@code startDelay} ticks, for {@code steps} total executions.
     */
    public static void run(ServerPlayer player, int startDelay, int interval,
                           int steps, IntConsumer action) {
        if (player == null || steps <= 0) return;
        TASKS.add(new Task(player.getUUID(), Math.max(0, startDelay),
                Math.max(1, interval), steps, action));
    }

    /** Runs {@code action} once after {@code delay} ticks. */
    public static void after(ServerPlayer player, int delay, Runnable action) {
        run(player, delay, 1, 1, step -> action.run());
    }

    /** Cancels all pending effects belonging to a player. */
    public static void cancel(UUID playerId) {
        if (playerId == null) return;
        TASKS.removeIf(task -> playerId.equals(task.owner));
    }

    /** Drops every pending effect, e.g. when the server shuts down. */
    public static void clear() {
        TASKS.clear();
    }

    /** Called once per server tick from the mod initializer. */
    public static void tick() {
        synchronized (TASKS) {
            Iterator<Task> iterator = TASKS.iterator();
            while (iterator.hasNext()) {
                Task task = iterator.next();
                if (--task.ticksUntilNext > 0) continue;
                task.ticksUntilNext = task.interval;
                try {
                    task.action.accept(task.step++);
                } catch (RuntimeException exception) {
                    Wildbound.LOGGER.error("Scheduled skill effect failed", exception);
                }
                if (task.step >= task.steps) TASKS.remove(task);
            }
        }
    }

    private static final class Task {
        private final UUID owner;
        private final int interval;
        private final int steps;
        private final IntConsumer action;
        private int ticksUntilNext;
        private int step;

        private Task(UUID owner, int startDelay, int interval, int steps, IntConsumer action) {
            this.owner = owner;
            this.ticksUntilNext = Math.max(1, startDelay);
            this.interval = Math.max(1, interval);
            this.steps = steps;
            this.action = action;
        }
    }
}
