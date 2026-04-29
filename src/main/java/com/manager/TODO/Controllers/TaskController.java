package com.manager.TODO.Controllers;

import com.manager.TODO.DTO.TaskDTO;
import com.manager.TODO.Services.TaskService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @GetMapping({"", "/"})
    public ResponseEntity<Page<TaskDTO>> getTasks(
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "deadline") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return ResponseEntity.ok(
                taskService.getTasks(completed, search, page, size, sortBy, direction)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.findById(id));
    }

    @PostMapping({"", "/"})
    public ResponseEntity<TaskDTO> save(@Valid @RequestBody TaskDTO task) {
        return ResponseEntity.ok(taskService.save(task));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody TaskDTO task
    ) {
        return ResponseEntity.ok(taskService.update(id, task));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteById(@PathVariable Long id) {
        taskService.deleteById(id);
        return ResponseEntity.ok("Task deleted successfully");
    }
}
