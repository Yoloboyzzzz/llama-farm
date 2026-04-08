package be.ucll.repository;

import be.ucll.model.GcodeFile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;



@Repository
public interface GcodeFileRepository extends JpaRepository<GcodeFile, Long> {
    List<GcodeFile> findByJobId(Long jobId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "UPDATE gcode_files SET queue_position = :pos WHERE id = :id", nativeQuery = true)
    void updateQueuePosition(@Param("id") Long id, @Param("pos") int pos);


    List<GcodeFile> findAllByOrderByQueuePositionAsc();

    List<GcodeFile> findAllByStatusOrderByQueuePositionAsc(String status);

    List<GcodeFile> findAllByStatusInOrderByQueuePositionAsc(java.util.Collection<String> statuses);

    @Query("SELECT COALESCE(MAX(g.queuePosition), 0) FROM GcodeFile g WHERE g.status = 'waiting'")
    int findMaxQueuePosition();

    @Query("""
           SELECT g 
           FROM GcodeFile g
           WHERE g.status = 'waiting'
             AND g.queuePosition > 0
           ORDER BY g.queuePosition ASC
           """)
    List<GcodeFile> findQueuedFiles();

  List<GcodeFile> findByStatus(String status);

    @Query("SELECT g FROM GcodeFile g LEFT JOIN FETCH g.stlFiles WHERE g.id = :id")
    Optional<GcodeFile> findByIdWithStlFiles(@Param("id") Long id);
}
