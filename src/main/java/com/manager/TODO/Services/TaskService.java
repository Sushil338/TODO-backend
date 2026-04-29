package com.manager.TODO.Services;

import com.manager.TODO.DTO.TaskDTO;
import com.manager.TODO.DTO.TaskMapper;
import com.manager.TODO.Exceptions.ResourceNotFoundException;
import com.manager.TODO.Models.Task;
import com.manager.TODO.Repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class TaskService {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "title", "description", "deadline", "completed");

    @Autowired
    private TaskRepository taskRepository;

    public Page<TaskDTO> getTasks(
            Boolean completed,
            String search,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be 0 or greater");
        }

        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than 0");
        }

        String normalizedSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "deadline";
        String normalizedDirection = "desc".equalsIgnoreCase(direction) ? "desc" : "asc";

        Sort sort = "desc".equals(normalizedDirection)
                ? Sort.by(normalizedSortBy).descending()
                : Sort.by(normalizedSortBy).ascending();

        Page<Task> taskPage = taskRepository.findTasks(
                completed,
                (search == null || search.trim().isEmpty()) ? null : search.trim(),
                PageRequest.of(page, size, sort)
        );

        return taskPage.map(TaskMapper::toDTO);
    }

    public TaskDTO findById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        return TaskMapper.toDTO(task);
    }

    public TaskDTO save(TaskDTO dto) {
        Task task = TaskMapper.toEntity(dto);
        return TaskMapper.toDTO(taskRepository.save(task));
    }

    public TaskDTO update(Long id, TaskDTO dto) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setDeadline(dto.getDeadline());
        task.setCompleted(dto.isCompleted());

        return TaskMapper.toDTO(taskRepository.save(task));
    }

    public void deleteById(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new ResourceNotFoundException("Task not found with id: " + id);
        }
        taskRepository.deleteById(id);
    }
}
