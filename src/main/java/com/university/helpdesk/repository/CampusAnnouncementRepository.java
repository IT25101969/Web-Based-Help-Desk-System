package com.university.helpdesk.repository;
import com.university.helpdesk.entity.CampusAnnouncement;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
public interface CampusAnnouncementRepository extends JpaRepository<CampusAnnouncement,Long> {
    List<CampusAnnouncement> findAllByOrderByStartsAtDesc();
    @Query("SELECT a FROM CampusAnnouncement a WHERE a.published = true AND a.startsAt <= :now AND (a.endsAt IS NULL OR a.endsAt > :now) ORDER BY a.startsAt DESC")
    List<CampusAnnouncement> findVisible(@Param("now") LocalDateTime now);
}
