package com.manager.TODO.Services;

import com.manager.TODO.DTO.TaskDTO;
import com.manager.TODO.DTO.TaskMapper;
import com.manager.TODO.Exceptions.ResourceNotFoundException;
import com.manager.TODO.Models.Importance;
import com.manager.TODO.Models.Task;
import com.manager.TODO.Models.User;
import com.manager.TODO.Repository.TaskRepository;
import com.manager.TODO.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class TaskService {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "title", "description", "deadline", "completed", "importance", "createdAt", "completedAt");

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    // Direct lookup context resolver bypassing old service layers
    private User getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found in context: " + username));
    }

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

        Long userId = getAuthenticatedUser().getId();

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = "deadline";
        }

        Sort.Direction sortDirection = Sort.Direction.ASC;
        if ("desc".equalsIgnoreCase(direction)) {
            sortDirection = Sort.Direction.DESC;
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        // FIXED: Replaced non-existent repository methods with your universal findTasks method
        return taskRepository.findTasks(userId, completed, search, pageRequest)
                .map(TaskMapper::toDTO);
    }

    public TaskDTO findById(Long id) {
        Task task = getOwnedTask(id);
        return TaskMapper.toDTO(task);
    }

    public TaskDTO save(TaskDTO dto) {
        User owner = getAuthenticatedUser();
        Task task = TaskMapper.toEntity(dto);
        task.setOwner(owner);

        if (task.isCompleted() && task.getCompletedAt() == null) {
            task.setCompletedAt(LocalDateTime.now());
        }

        return TaskMapper.toDTO(taskRepository.save(task));
    }

    public TaskDTO update(Long id, TaskDTO dto) {
        Task task = getOwnedTask(id);

        boolean wasCompleted = task.isCompleted();

        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setDeadline(dto.getDeadline());
        task.setImportance(dto.getImportance() == null ? Importance.MEDIUM : dto.getImportance());
        task.setCompleted(dto.isCompleted());

        if (!wasCompleted && dto.isCompleted()) {
            task.setCompletedAt(LocalDateTime.now());
        }

        if (wasCompleted && !dto.isCompleted()) {
            task.setCompletedAt(null);
        }

        return TaskMapper.toDTO(taskRepository.save(task));
    }

    public void deleteById(Long id) {
        Task task = getOwnedTask(id);
        taskRepository.delete(task);
    }

    private Task getOwnedTask(Long id) {
        Long userId = getAuthenticatedUser().getId();

        return taskRepository.findByIdAndOwnerId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }
}