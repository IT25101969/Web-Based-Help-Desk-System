package com.university.helpdesk.controller;
import com.university.helpdesk.repository.CampusAnnouncementRepository;
import com.university.helpdesk.entity.CampusAnnouncement;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
@ControllerAdvice
public class AnnouncementAdvice {
    private final CampusAnnouncementRepository announcements;
    public AnnouncementAdvice(CampusAnnouncementRepository announcements){this.announcements=announcements;}
    @ModelAttribute("campusAnnouncements") public List<CampusAnnouncement> notices(){return announcements.findVisible(LocalDateTime.now());}
}
