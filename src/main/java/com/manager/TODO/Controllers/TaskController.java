package com.manager.TODO.Controllers;

import com.manager.TODO.Models.Task;
import com.manager.TODO.Services.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin("http://localhost:5173/")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @GetMapping
    public ResponseEntity<List<Task>> findAll() {
        return ResponseEntity.ok(taskService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> findById(@PathVariable Long id) {
        Optional<Task> task = taskService.findById(id);
        if(task.isPresent()) {
            return ResponseEntity.ok(task);
        }
        return ResponseEntity.status(404).body("Task not found");
    }

    @PostMapping
    public ResponseEntity<Task> save(@RequestBody Task task) {
        Task t = taskService.save(task);
        return ResponseEntity.ok(t);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Task updatedtask) {
        Optional<Task> task = taskService.update(id, updatedtask);
        if(task.isPresent()) {
            return ResponseEntity.ok(task);
        }
        return ResponseEntity.status(404).body("Task not found");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteById(@PathVariable Long id) {
        boolean deleted = taskService.deleteById(id);
        if(deleted) {
            return ResponseEntity.ok(deleted);
        }
        return ResponseEntity.status(404).body("Task not found");
    }
}
