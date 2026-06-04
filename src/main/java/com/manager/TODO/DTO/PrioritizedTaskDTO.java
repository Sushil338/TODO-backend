package com.manager.TODO.DTO;

import com.manager.TODO.Models.Importance;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PrioritizedTaskDTO {
    private Long id;
    private String title;
    private String description;
    private LocalDateTime deadline;
    private boolean completed;
    private Importance importance;
    private int priorityScore;
    private String priorityReason;
}
