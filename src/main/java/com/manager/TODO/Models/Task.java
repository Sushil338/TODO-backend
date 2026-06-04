package com.manager.TODO.Models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name="tasks")
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;

    @Column(nullable = false)
    @NotBlank(message = "Title is required")
    private String title;

    @Size(max = 255)
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime deadline;
    private LocalDateTime completedAt; // Track exactly when tasks are checked off

    @Enumerated(EnumType.STRING)
    private Importance importance;

    private boolean completed;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (importance == null) {
            importance = Importance.MEDIUM;
        }

        if (!completed) {
            completedAt = null;
        }
    }

}
