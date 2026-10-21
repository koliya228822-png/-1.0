package com.heroicpaths.rpg.ability;

import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Простой планировщик на тиках сервера: задержанные и повторяющиеся действия способностей. */
public final class DelayedTasks {
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    private static final class Task {
        int ticksUntilRun;
        final int interval;
        int runsLeft;
        final Runnable action;

        Task(int delay, int interval, int runs, Runnable action) {
            this.ticksUntilRun = delay;
            this.interval = interval;
            this.runsLeft = runs;
            this.action = action;
        }
    }

    private DelayedTasks() {}

    public static void schedule(int delayTicks, Runnable action) {
        repeat(delayTicks, 1, 1, action);
    }

    /** Выполняет action runs раз: первый через delayTicks, затем каждые intervalTicks. */
    public static void repeat(int delayTicks, int intervalTicks, int runs, Runnable action) {
        PENDING.add(new Task(Math.max(0, delayTicks), Math.max(1, intervalTicks), runs, action));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        TASKS.addAll(PENDING);
        PENDING.clear();
        Iterator<Task> iterator = TASKS.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (--task.ticksUntilRun > 0) continue;
            task.action.run();
            if (--task.runsLeft <= 0) iterator.remove();
            else task.ticksUntilRun = task.interval;
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
        PENDING.clear();
    }
}
