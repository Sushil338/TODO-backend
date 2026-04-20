package com.manager.TODO.Services;

import com.manager.TODO.Models.Task;
import com.manager.TODO.Repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;

    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    public Optional<Task> findById(Long id) {
        return taskRepository.findById(id);
    }

    public Task save(Task task) {
        return taskRepository.save(task);
    }

    public Optional<Task> update(Long id, Task updatedtask) {
        Optional<Task> existing = taskRepository.findById(id);
        if(existing.isPresent()) {
            Task task = existing.get();

            task.setTitle(updatedtask.getTitle());
            task.setDescription(updatedtask.getDescription());
            task.setDeadline(updatedtask.getDeadline());
            task.setCompleted(updatedtask.isCompleted());

            return Optional.of(taskRepository.save(task));
        }
        else {
            return Optional.empty();
        }
    }

    public boolean deleteById(Long id) {
        if(!taskRepository.existsById(id)) {
            return false;
        }
        taskRepository.deleteById(id);
        return true;
    }
}
