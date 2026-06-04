package com.manager.TODO.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.TODO.DTO.GroqMessage;
import com.manager.TODO.DTO.GroqRequest;
import com.manager.TODO.DTO.GroqResponseFormat;
import com.manager.TODO.DTO.PrioritizedTaskDTO;
import com.manager.TODO.DTO.TaskDTO;
import com.manager.TODO.DTO.TaskMapper;
import com.manager.TODO.Models.Importance;
import com.manager.TODO.Models.Task;
import com.manager.TODO.Models.User;
import com.manager.TODO.Repository.TaskRepository;
import com.manager.TODO.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiTaskService {

    @Autowired
    private UserRepository userRepository;

    private User getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private static final String TASK_MODEL = "llama-3.3-70b-versatile";
    private static final Pattern TIME_PATTERN =
            Pattern.compile("(?i)\\b(?:at|by)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b");

    private final RestClient groqRestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskRepository taskRepository;

    public AiTaskService(
            RestClient groqRestClient,
            TaskRepository taskRepository
    ) {
        this.groqRestClient = groqRestClient;
        this.taskRepository = taskRepository;
    }

    // Natural Language Task Creation
    public TaskDTO parseTaskFromText(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            throw new IllegalArgumentException("Task text is required");
        }

        try {
            String systemPrompt = """
                    Extract one task from the user's text.
                    Return only a valid JSON object with this exact shape:
                    {
                      "title": "short task title",
                      "description": "optional short description or null",
                      "deadline": "ISO local datetime like 2026-06-04T17:00:00 or null",
                      "importance": "HIGH, MEDIUM, or LOW"
                    }
                    Use the current local date/time to understand relative dates.
                    Current local date/time: %s.
                    """.formatted(LocalDateTime.now());

            GroqRequest request = new GroqRequest(
                    TASK_MODEL,
                    List.of(new GroqMessage("system", systemPrompt), new GroqMessage("user", userInput)),
                    new GroqResponseFormat("json_object")
            );

            String content = callGroq(request);
            TaskDTO parsedTask = buildTaskFromJson(content, userInput);

            if (parsedTask.getTitle() == null || parsedTask.getTitle().trim().isEmpty()) {
                return fallbackTaskFromText(userInput);
            }

            return parsedTask;
        } catch (Exception ex) {
            return fallbackTaskFromText(userInput);
        }
    }

    // Smart Prioritization Matrix
    public List<PrioritizedTaskDTO> prioritizeTasks() {
        Long userId = getAuthenticatedUser().getId(); // Replaced currentUserService with security check
        List<Task> pendingTasks = taskRepository.findByOwnerIdAndCompletedFalse(userId);
        UserBehaviorProfile profile = buildUserBehaviorProfile(
                taskRepository.findByOwnerIdAndCompletedTrueAndCompletedAtIsNotNull(userId)
        );

        LocalDateTime now = LocalDateTime.now();

        return pendingTasks.stream()
                .map(task -> buildPrioritizedTask(task, profile))
                .sorted(Comparator
                        .comparingInt((PrioritizedTaskDTO task) ->
                                task.getDeadline() != null && task.getDeadline().isBefore(now) ? 1 : 0
                        )
                        .thenComparing(Comparator.comparingInt(PrioritizedTaskDTO::getPriorityScore).reversed())
                        .thenComparing(task ->
                                task.getDeadline() == null ? LocalDateTime.MAX : task.getDeadline()))
                .toList();
    }

    // Daily Productivity Insights
    public List<String> generateProductivityInsights() {
        Long userId = getAuthenticatedUser().getId(); // Replaced currentUserService with security check
        List<Task> completedTasks =
                taskRepository.findByOwnerIdAndCompletedTrueAndCompletedAtIsNotNull(userId);

        if (completedTasks.isEmpty()) {
            return List.of(
                    "Complete a few tasks to unlock productivity insights.",
                    "Add deadlines and importance levels so priority suggestions can become sharper."
            );
        }

        int total = completedTasks.size();
        int[] hourlyCounts = new int[24];
        Map<DayOfWeek, Integer> dayCounts = new EnumMap<>(DayOfWeek.class);

        for (Task task : completedTasks) {
            LocalDateTime completedAt = task.getCompletedAt();
            hourlyCounts[completedAt.getHour()]++;
            dayCounts.merge(completedAt.getDayOfWeek(), 1, Integer::sum);
        }

        int bestWindowStart = findBestThreeHourWindow(hourlyCounts);
        int bestWindowCount = hourlyCounts[bestWindowStart]
                + hourlyCounts[(bestWindowStart + 1) % 24]
                + hourlyCounts[(bestWindowStart + 2) % 24];
        int bestWindowPercent = Math.round((bestWindowCount * 100f) / total);

        DayOfWeek bestDay = dayCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(LocalDate.now().getDayOfWeek());

        long deadlineTasks = completedTasks.stream()
                .filter(task -> task.getDeadline() != null)
                .count();
        long onTimeTasks = completedTasks.stream()
                .filter(task -> task.getDeadline() != null)
                .filter(task -> !task.getCompletedAt().isAfter(task.getDeadline()))
                .count();

        List<String> insights = new ArrayList<>();
        insights.add("You complete " + bestWindowPercent + "% of finished tasks between "
                + formatHour(bestWindowStart) + " and " + formatHour((bestWindowStart + 3) % 24) + ".");
        insights.add(formatDay(bestDay) + " is your most productive day.");

        if (deadlineTasks > 0) {
            int onTimePercent = Math.round((onTimeTasks * 100f) / deadlineTasks);
            insights.add("You finish " + onTimePercent + "% of completed deadline tasks on time.");
        } else {
            insights.add("Add deadlines to more tasks to improve future schedule insights.");
        }

        return insights;
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

    private TaskDTO buildTaskFromJson(String content, String originalInput) throws Exception {
        JsonNode node = objectMapper.readTree(content);

        TaskDTO task = new TaskDTO();
        task.setTitle(readNullableText(node, "title"));
        task.setDescription(readNullableText(node, "description"));
        task.setDeadline(parseDeadline(readNullableText(node, "deadline")));
        task.setImportance(parseImportance(readNullableText(node, "importance"), originalInput));
        task.setCompleted(false);
        return task;
    }

    private String readNullableText(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }

        String value = node.get(field).asText();
        return "null".equalsIgnoreCase(value) ? null : value;
    }

    private TaskDTO fallbackTaskFromText(String userInput) {
        TaskDTO task = new TaskDTO();
        task.setTitle(cleanTitle(userInput));
        task.setDeadline(extractDeadline(userInput));
        task.setImportance(parseImportance(null, userInput));
        task.setCompleted(false);
        return task;
    }

    private String cleanTitle(String userInput) {
        String result = userInput.trim()
                .replaceFirst("(?i)^remind\\s+me\\s+(to\\s+)?", "")
                .replaceFirst("(?i)^add\\s+(a\\s+)?task\\s+(to\\s+)?", "")
                .replaceFirst("(?i)^create\\s+(a\\s+)?task\\s+(to\\s+)?", "");

        result = result.replaceAll("(?i)\\b(today|tomorrow|tonight|this evening|this morning|morning|evening|night)\\b", "");
        result = result.replaceAll("(?i)\\b(at|by)\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?\\b", "");
        result = result.replaceAll("(?i)\\b(urgent|very important|important|low priority|high priority)\\b", "");
        result = result.replaceAll("\\s+", " ").trim();

        return result.isEmpty() ? userInput.trim() : result;
    }

    private LocalDateTime extractDeadline(String userInput) {
        String lower = userInput.toLowerCase();
        LocalDate today = LocalDate.now();
        LocalDate date = null;

        if (lower.contains("tomorrow")) {
            date = today.plusDays(1);
        } else if (lower.contains("today") || lower.contains("tonight")) {
            date = today;
        }

        LocalTime time = extractTime(userInput);

        if (time == null) {
            if (lower.contains("morning")) {
                time = LocalTime.of(9, 0);
            } else if (lower.contains("evening") || lower.contains("night")) {
                time = LocalTime.of(18, 0);
            } else {
                time = LocalTime.of(9, 0);
            }
        }

        if (date == null && hasExplicitTime(userInput)) {
            date = today;
            if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now())) {
                date = date.plusDays(1);
            }
        }

        return date == null ? null : LocalDateTime.of(date, time);
    }

    private boolean hasExplicitTime(String userInput) {
        return TIME_PATTERN.matcher(userInput).find();
    }

    private LocalTime extractTime(String userInput) {
        Matcher matcher = TIME_PATTERN.matcher(userInput);

        if (!matcher.find()) {
            return null;
        }

        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        String meridiem = matcher.group(3);
        String lower = userInput.toLowerCase();

        if (meridiem != null) {
            if ("pm".equalsIgnoreCase(meridiem) && hour < 12) {
                hour += 12;
            }
            if ("am".equalsIgnoreCase(meridiem) && hour == 12) {
                hour = 0;
            }
        } else if ((lower.contains("evening") || lower.contains("night")) && hour < 12) {
            hour += 12;
        }

        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }

        return LocalTime.of(hour, minute);
    }

    private LocalDateTime parseDeadline(String deadlineText) {
        if (deadlineText == null || deadlineText.isBlank()) {
            return null;
        }

        String value = deadlineText.trim().replace("\"", "");

        if ("null".equalsIgnoreCase(value)) {
            return null;
        }

        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Importance parseImportance(String value, String fallbackText) {
        String text = ((value == null ? "" : value) + " " + (fallbackText == null ? "" : fallbackText)).toUpperCase();

        if (text.contains("HIGH") || text.contains("URGENT") || text.contains("IMPORTANT")) {
            return Importance.HIGH;
        }

        if (text.contains("LOW")) {
            return Importance.LOW;
        }

        return Importance.MEDIUM;
    }

    private PrioritizedTaskDTO buildPrioritizedTask(Task task, UserBehaviorProfile profile) {
        PrioritizedTaskDTO dto = new PrioritizedTaskDTO();
        TaskDTO taskDTO = TaskMapper.toDTO(task);

        dto.setId(taskDTO.getId());
        dto.setTitle(taskDTO.getTitle());
        dto.setDescription(taskDTO.getDescription());
        dto.setDeadline(taskDTO.getDeadline());
        dto.setCompleted(taskDTO.isCompleted());
        dto.setImportance(taskDTO.getImportance());

        PriorityResult priority = calculatePriority(task, profile);
        dto.setPriorityScore(priority.score());
        dto.setPriorityReason(priority.reason());
        return dto;
    }

    private PriorityResult calculatePriority(Task task, UserBehaviorProfile profile) {
        LocalDateTime now = LocalDateTime.now();
        List<String> reasons = new ArrayList<>();
        int score = 0;

        Importance importance = task.getImportance() == null ? Importance.MEDIUM : task.getImportance();

        switch (importance) {
            case HIGH -> {
                score += 40;
                reasons.add("high importance");
            }
            case MEDIUM -> {
                score += 25;
                reasons.add("medium importance");
            }
            case LOW -> {
                score += 10;
                reasons.add("low importance");
            }
        }

        LocalDateTime deadline = task.getDeadline();

        if (deadline == null) {
            score += 5;
            reasons.add("no deadline");
        } else {
            long hoursUntilDeadline = Duration.between(now, deadline).toHours();

            if (hoursUntilDeadline < 0) {
                score += 60;
                reasons.add("overdue");
            } else if (hoursUntilDeadline <= 24) {
                score += 50;
                reasons.add("due today");
            } else if (hoursUntilDeadline <= 48) {
                score += 40;
                reasons.add("due tomorrow");
            } else if (hoursUntilDeadline <= 72) {
                score += 30;
                reasons.add("due in 3 days");
            } else if (hoursUntilDeadline <= 168) {
                score += 20;
                reasons.add("due this week");
            } else {
                score += 10;
                reasons.add("deadline is later");
            }

            if (profile.bestDay() != null && deadline.getDayOfWeek() == profile.bestDay()) {
                score += 5;
                reasons.add("matches your productive day");
            }

            if (profile.bestHour() >= 0 && Math.abs(deadline.getHour() - profile.bestHour()) <= 1) {
                score += 5;
                reasons.add("near your productive hours");
            }
        }

        if (task.getCreatedAt() != null) {
            long ageInDays = Math.max(0, Duration.between(task.getCreatedAt(), now).toDays());
            int ageScore = (int) Math.min(10, ageInDays);
            score += ageScore;

            if (ageScore > 0) {
                reasons.add("waiting " + ageInDays + " day(s)");
            }
        }

        return new PriorityResult(score, String.join(", ", reasons));
    }

    private UserBehaviorProfile buildUserBehaviorProfile(List<Task> completedTasks) {
        if (completedTasks.isEmpty()) {
            return new UserBehaviorProfile(null, -1);
        }

        Map<DayOfWeek, Integer> dayCounts = new EnumMap<>(DayOfWeek.class);
        int[] hourlyCounts = new int[24];

        for (Task task : completedTasks) {
            LocalDateTime completedAt = task.getCompletedAt();
            dayCounts.merge(completedAt.getDayOfWeek(), 1, Integer::sum);
            hourlyCounts[completedAt.getHour()]++;
        }

        DayOfWeek bestDay = dayCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        int bestHour = 0;
        for (int hour = 1; hour < hourlyCounts.length; hour++) {
            if (hourlyCounts[hour] > hourlyCounts[bestHour]) {
                bestHour = hour;
            }
        }

        return new UserBehaviorProfile(bestDay, bestHour);
    }

    private int findBestThreeHourWindow(int[] hourlyCounts) {
        int bestStart = 0;
        int bestCount = -1;

        for (int hour = 0; hour < 24; hour++) {
            int count = hourlyCounts[hour] + hourlyCounts[(hour + 1) % 24] + hourlyCounts[(hour + 2) % 24];

            if (count > bestCount) {
                bestCount = count;
                bestStart = hour;
            }
        }

        return bestStart;
    }

    private String formatHour(int hour) {
        int normalizedHour = ((hour % 24) + 24) % 24;
        int displayHour = normalizedHour % 12;
        String suffix = normalizedHour < 12 ? "AM" : "PM";

        return (displayHour == 0 ? 12 : displayHour) + " " + suffix;
    }

    private String formatDay(DayOfWeek day) {
        String value = day.name().toLowerCase();
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private record UserBehaviorProfile(DayOfWeek bestDay, int bestHour) {
    }

    private record PriorityResult(int score, String reason) {
    }
}