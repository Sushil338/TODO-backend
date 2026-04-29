package com.manager.TODO.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskDTO {

    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    @Size(max = 255)
    private String description;

    private LocalDateTime deadline;

    private boolean completed;
}