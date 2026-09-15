package com.university.helpdesk.controller;

import com.university.helpdesk.entity.Student;
import com.university.helpdesk.entity.Ticket;
import com.university.helpdesk.entity.TicketStatus;
import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.repository.NotificationRepository;
import com.university.helpdesk.repository.StudentRepository;
import com.university.helpdesk.repository.TicketRepository;
import com.university.helpdesk.repository.UserAccountRepository;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class StudentDashboardController {

    private final UserAccountRepository userAccountRepository;
    private final StudentRepository studentRepository;
    private final TicketRepository ticketRepository;
    private final NotificationRepository notificationRepository;

    public StudentDashboardController(
            UserAccountRepository userAccountRepository,
            StudentRepository studentRepository,
            TicketRepository ticketRepository,
            NotificationRepository notificationRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.studentRepository = studentRepository;
        this.ticketRepository = ticketRepository;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/student/dashboard")
    public String studentDashboard(Authentication authentication, Model model) {
        UserAccount user = userAccountRepository.findByUniversityId(authentication.getName())
                .orElse(null);

        if (user != null) {
            Student student = studentRepository.findById(user.getUserId()).orElse(null);
            model.addAttribute("user", user);
            model.addAttribute("student", student);

            long totalTickets = ticketRepository.countByStudentUserId(user.getUserId());
            long openTickets = ticketRepository.countByStudentUserIdAndStatusIn(
                    user.getUserId(),
                    List.of(
                            TicketStatus.NEW,
                            TicketStatus.ASSIGNED,
                            TicketStatus.IN_PROGRESS,
                            TicketStatus.ESCALATED
                    )
            );
            long resolvedTickets = ticketRepository.countByStudentUserIdAndStatusIn(
                    user.getUserId(),
                    List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED)
            );
            long unreadNotifications = notificationRepository.countByUserUserIdAndReadStatusFalse(user.getUserId());

            List<Ticket> recentTickets = ticketRepository
                    .findByStudentUserIdOrderByCreatedDateDesc(user.getUserId())
                    .stream()
                    .limit(5)
                    .toList();

            model.addAttribute("totalTickets", totalTickets);
            model.addAttribute("openTickets", openTickets);
            model.addAttribute("resolvedTickets", resolvedTickets);
            model.addAttribute("unreadNotifications", unreadNotifications);
            model.addAttribute("recentTickets", recentTickets);
        }

        return "student-dashboard";
    }
}