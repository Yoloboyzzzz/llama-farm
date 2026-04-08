package be.ucll.repository;

import be.ucll.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long> {

    @Query("SELECT j FROM Job j ORDER BY j.createdAt DESC")
    List<Job> findTop50ByOrderByCreatedAtDesc();

    @Query(value = "SELECT * FROM jobs ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<Job> findTopNByOrderByCreatedAtDesc(@Param("limit") int limit);

}
