package com.manager.TODO.DTO;

import com.manager.TODO.Models.Importance;
import com.manager.TODO.Models.Task;

public class TaskMapper {

    // Entity to DTO
    public static TaskDTO toDTO(Task task) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setDeadline(task.getDeadline());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setCompletedAt(task.getCompletedAt());
        dto.setImportance(task.getImportance() == null ? Importance.MEDIUM : task.getImportance());
        dto.setCompleted(task.isCompleted());
        return dto;
    }

    // DTO to Entity
    public static Task toEntity(TaskDTO dto) {
        Task task = new Task();
        task.setId(dto.getId());
        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setDeadline(dto.getDeadline());
        task.setCreatedAt(dto.getCreatedAt());
        task.setCompletedAt(dto.getCompletedAt());
        task.setImportance(dto.getImportance() == null ? Importance.MEDIUM : dto.getImportance());
        task.setCompleted(dto.isCompleted());
        return task;
    }
}
