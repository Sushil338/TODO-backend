package com.manager.TODO.Controllers;

import com.manager.TODO.DTO.AiChatRequest;
import com.manager.TODO.DTO.AiChatResponse;
import com.manager.TODO.DTO.AiTaskRequest;
import com.manager.TODO.DTO.PrioritizedTaskDTO;
import com.manager.TODO.DTO.TaskDTO;
import com.manager.TODO.Services.AiChatService;
import com.manager.TODO.Services.AiTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:5173")
public class AiController {

    private final AiTaskService aiTaskService;
    private final AiChatService aiChatService;

    public AiController(AiTaskService aiTaskService, AiChatService aiChatService) {
        this.aiTaskService = aiTaskService;
        this.aiChatService = aiChatService;
    }

    @PostMapping("/parse-task")
    public ResponseEntity<TaskDTO> parseTask(
            @RequestBody(required = false) AiTaskRequest request,
            @RequestParam(required = false) String text
    ) {
        String taskText = request != null && request.getText() != null ? request.getText() : text;
        if (taskText == null || taskText.isBlank()) {
            throw new IllegalArgumentException("Task text is required");
        }
        return ResponseEntity.ok(aiTaskService.parseTaskFromText(taskText));
    }

    @GetMapping("/prioritize")
    public ResponseEntity<List<PrioritizedTaskDTO>> prioritizeTasks() {
        return ResponseEntity.ok(aiTaskService.prioritizeTasks());
    }

    @GetMapping("/insights")
    public ResponseEntity<List<String>> productivityInsights() {
        return ResponseEntity.ok(aiTaskService.generateProductivityInsights());
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @RequestBody(required = false) AiChatRequest request,
            @RequestParam(required = false) String question
    ) {
        String userQuestion = request != null && request.getQuestion() != null ? request.getQuestion() : question;
        if (userQuestion == null || userQuestion.isBlank()) {
            throw new IllegalArgumentException("Question is required");
        }
        return ResponseEntity.ok(new AiChatResponse(aiChatService.chatWithDatabaseContext(userQuestion)));
    }
}
