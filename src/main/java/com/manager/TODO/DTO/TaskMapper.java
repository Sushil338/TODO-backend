package com.manager.TODO.DTO;

import com.manager.TODO.DTO.TaskDTO;
import com.manager.TODO.Models.Task;

public class TaskMapper {

    // Entity → DTO
    public static TaskDTO toDTO(Task task) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setDeadline(task.getDeadline());
        dto.setCompleted(task.isCompleted());
        return dto;
    }

    // DTO → Entity
    public static Task toEntity(TaskDTO dto) {
        Task task = new Task();
        task.setId(dto.getId());
        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setDeadline(dto.getDeadline());
        task.setCompleted(dto.isCompleted());
        return task;
    }
}