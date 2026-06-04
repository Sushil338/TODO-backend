package com.manager.TODO.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.TODO.DTO.GroqMessage;
import com.manager.TODO.DTO.GroqRequest;
import com.manager.TODO.DTO.TaskDTO;
import com.manager.TODO.DTO.TaskMapper;
import com.manager.TODO.Repository.TaskRepository;
import com.manager.TODO.Repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class AiChatService {

    private static final String CHAT_MODEL = "llama-3.3-70b-versatile";

    private final RestClient groqRestClient;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository; // Replaced CurrentUserService
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiChatService(
            RestClient groqRestClient,
            TaskRepository taskRepository,
            UserRepository userRepository // Updated dependency inject via Constructor
    ) {
        this.groqRestClient = groqRestClient;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    // Direct look-up logic bypassing an external service class
    private Long getAuthenticatedUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username))
                .getId();
    }

    public String chatWithDatabaseContext(String userQuestion) {
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            throw new IllegalArgumentException("Question is required");
        }

        Long userId = getAuthenticatedUserId();
        LocalDateTime now = LocalDateTime.now();
        List<TaskDTO> tasks = taskRepository.findByOwnerIdOrderByIdAsc(userId).stream()
                .map(TaskMapper::toDTO)
                .sorted(TaskOrderHelper.taskDtoComparator(now))
                .toList();

        try {
            String tasksContext = objectMapper.writeValueAsString(tasks);
            String systemPrompt = """
                    You are an AI chat assistant inside a task manager.
                    Answer only from the database snapshot below.
                    Be direct, concise, and mention task titles and deadlines when useful.
                    If the user asks for something not present in the data, say so.
                    Current local date/time: %s.

                    DATABASE TASK SNAPSHOT:
                    %s
                    """.formatted(LocalDateTime.now(), tasksContext);

            GroqRequest request = new GroqRequest(
                    CHAT_MODEL,
                    List.of(new GroqMessage("system", systemPrompt), new GroqMessage("user", userQuestion)),
                    null
            );

            return callGroq(request);
        } catch (Exception ex) {
            return fallbackAnswer(userQuestion, tasks);
        }
    }

    private String callGroq(GroqRequest request) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("messages", request.messages());

        if (request.response_format() != null) {
            body.put("response_format", request.response_format());
        }

        String response = groqRestClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");

        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("AI response did not include content");
        }

        return content.asText();
    }

    private String fallbackAnswer(String question, List<TaskDTO> tasks) {
        String lowerQuestion = question.toLowerCase();

        if (lowerQuestion.contains("pending") && lowerQuestion.contains("week")) {
            LocalDate today = LocalDate.now();
            LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            return listTasks("Pending tasks this week", tasks, task ->
                    !task.isCompleted()
                            && task.getDeadline() != null
                            && !task.getDeadline().toLocalDate().isBefore(weekStart)
                            && !task.getDeadline().toLocalDate().isAfter(weekEnd));
        }

        if (lowerQuestion.contains("overdue")) {
            LocalDateTime now = LocalDateTime.now();
            return listTasks("Overdue tasks", tasks, task ->
                    !task.isCompleted()
                            && task.getDeadline() != null
                            && task.getDeadline().isBefore(now));
        }

        if (lowerQuestion.contains("today")) {
            LocalDate today = LocalDate.now();
            return listTasks("Tasks due today", tasks, task ->
                    task.getDeadline() != null && task.getDeadline().toLocalDate().isEqual(today));
        }

        if (lowerQuestion.contains("tomorrow")) {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            return listTasks("Tasks due tomorrow", tasks, task ->
                    task.getDeadline() != null && task.getDeadline().toLocalDate().isEqual(tomorrow));
        }

        if (lowerQuestion.contains("completed")) {
            return listTasks("Completed tasks", tasks, TaskDTO::isCompleted);
        }

        if (lowerQuestion.contains("pending")) {
            return listTasks("Pending tasks", tasks, task -> !task.isCompleted());
        }

        long pending = tasks.stream().filter(task -> !task.isCompleted()).count();
        long completed = tasks.stream().filter(TaskDTO::isCompleted).count();

        return "You have " + pending + " pending task(s) and " + completed
                + " completed task(s). Ask about pending, overdue, today, tomorrow, or this week for details.";
    }

    private String listTasks(String heading, List<TaskDTO> tasks, Predicate<TaskDTO> filter) {
        LocalDateTime now = LocalDateTime.now();
        List<TaskDTO> matchingTasks = tasks.stream()
                .filter(filter)
                .sorted(TaskOrderHelper.taskDtoComparator(now))
                .limit(6)
                .toList();

        if (matchingTasks.isEmpty()) {
            return heading + ": none found.";
        }

        return heading + ":\n" + matchingTasks.stream()
                .map(this::formatTaskLine)
                .collect(Collectors.joining("\n"));
    }

    private String formatTaskLine(TaskDTO task) {
        String deadline = task.getDeadline() == null
                ? "no deadline"
                : task.getDeadline().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));

        return "- " + task.getTitle() + " (" + deadline + ", " + task.getImportance() + ")";
    }
}
