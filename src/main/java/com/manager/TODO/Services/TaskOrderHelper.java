package com.manager.TODO.Services;

import com.manager.TODO.DTO.TaskDTO;
import com.manager.TODO.Models.Task;

import java.time.LocalDateTime;
import java.util.Comparator;

public final class TaskOrderHelper {

    private TaskOrderHelper() {
    }

    /**
     * 0 = active pending (upcoming or no deadline),
     * 1 = overdue pending,
     * 2 = completed
     */
    public static int displayTier(Task task, LocalDateTime now) {
        if (task.isCompleted()) {
            return 2;
        }

        if (task.getDeadline() != null && task.getDeadline().isBefore(now)) {
            return 1;
        }

        return 0;
    }

    public static int displayTier(TaskDTO task, LocalDateTime now) {
        if (task.isCompleted()) {
            return 2;
        }

        if (task.getDeadline() != null && task.getDeadline().isBefore(now)) {
            return 1;
        }

        return 0;
    }

    public static boolean isOverduePending(Task task, LocalDateTime now) {
        return displayTier(task, now) == 1;
    }

    public static boolean isOverduePending(TaskDTO task, LocalDateTime now) {
        return displayTier(task, now) == 1;
    }

    public static Comparator<Task> taskComparator(LocalDateTime now) {
        return Comparator
                .comparingInt((Task task) -> displayTier(task, now))
                .thenComparingInt(task -> activePendingWithoutDeadline(task, now) ? 1 : 0)
                .thenComparing(task -> tierDeadlineSortKey(task, now), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Task::getCompletedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Task::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    public static Comparator<TaskDTO> taskDtoComparator(LocalDateTime now) {
        return Comparator
                .comparingInt((TaskDTO task) -> displayTier(task, now))
                .thenComparingInt(task -> activePendingWithoutDeadline(task, now) ? 1 : 0)
                .thenComparing(task -> tierDeadlineSortKey(task, now), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TaskDTO::getCompletedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(TaskDTO::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static LocalDateTime tierDeadlineSortKey(Task task, LocalDateTime now) {
        int tier = displayTier(task, now);
        if (tier == 2 && task.isCompleted()) {
            return null;
        }
        return task.getDeadline();
    }

    private static LocalDateTime tierDeadlineSortKey(TaskDTO task, LocalDateTime now) {
        int tier = displayTier(task, now);
        if (tier == 2 && task.isCompleted()) {
            return null;
        }
        return task.getDeadline();
    }

    private static boolean activePendingWithoutDeadline(Task task, LocalDateTime now) {
        return displayTier(task, now) == 0 && task.getDeadline() == null;
    }

    private static boolean activePendingWithoutDeadline(TaskDTO task, LocalDateTime now) {
        return displayTier(task, now) == 0 && task.getDeadline() == null;
    }
}
