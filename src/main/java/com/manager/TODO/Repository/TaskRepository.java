package com.manager.TODO.Repository;

import com.manager.TODO.Models.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
    SELECT t FROM Task t
    WHERE (:completed IS NULL OR t.completed = :completed)
    AND (
        :search IS NULL OR :search = '' OR 
        LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
        LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
    )
""")
    Page<Task> findTasks(
            @Param("completed") Boolean completed,
            @Param("search") String search,
            Pageable pageable
    );
}