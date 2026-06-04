package com.manager.TODO.Repository;

import com.manager.TODO.Models.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
    SELECT t FROM Task t
    WHERE t.owner.id = :userId
    AND (:completed IS NULL OR t.completed = :completed)
    AND (
        :search IS NULL OR :search = '' OR
        LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
        LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
    )
    ORDER BY
        CASE
            WHEN t.completed = true THEN 2
            WHEN t.deadline IS NOT NULL AND t.deadline < :now THEN 1
            ELSE 0
        END ASC,
        CASE
            WHEN t.completed = false
                AND (t.deadline IS NULL OR t.deadline >= :now)
                AND t.deadline IS NULL THEN 1
            ELSE 0
        END ASC,
        CASE
            WHEN t.completed = false
                AND (t.deadline IS NULL OR t.deadline >= :now) THEN t.deadline
        END ASC,
        CASE
            WHEN t.completed = false
                AND t.deadline IS NOT NULL
                AND t.deadline < :now THEN t.deadline
        END ASC,
        CASE
            WHEN t.completed = true THEN t.completedAt
        END DESC,
        t.id ASC
    """)
    Page<Task> findTasksByDisplayOrder(
            @Param("userId") Long userId,
            @Param("completed") Boolean completed,
            @Param("search") String search,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
    SELECT t FROM Task t
    WHERE t.owner.id = :userId
    AND (:completed IS NULL OR t.completed = :completed)
    AND (
        :search IS NULL OR :search = '' OR
        LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
        LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
    )
""")
    Page<Task> findTasks(
            @Param("userId") Long userId,
            @Param("completed") Boolean completed,
            @Param("search") String search,
            Pageable pageable
    );

    Optional<Task> findByIdAndOwnerId(Long id, Long ownerId);

    List<Task> findByOwnerIdAndCompletedFalse(Long ownerId);

    List<Task> findByOwnerIdAndCompletedTrueAndCompletedAtIsNotNull(Long ownerId);

    List<Task> findByOwnerIdOrderByIdAsc(Long ownerId);
}
