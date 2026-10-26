package com.university.helpdesk.service;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
@Service
public class OverdueAlertService {
    private final TicketRepository tickets;private final TicketDeadlineService deadlines;private final TicketAssignmentRepository assignments;private final UserDepartmentRepository memberships;private final UserRoleRepository roles;private final NotificationRepository notifications;private final NotificationPreferenceService preferences;private final NotificationService delivery;private final EmailNotificationQueueRepository emails;
    public OverdueAlertService(TicketRepository tickets,TicketDeadlineService deadlines,TicketAssignmentRepository assignments,UserDepartmentRepository memberships,UserRoleRepository roles,NotificationRepository notifications,NotificationPreferenceService preferences,NotificationService delivery,EmailNotificationQueueRepository emails){this.tickets=tickets;this.deadlines=deadlines;this.assignments=assignments;this.memberships=memberships;this.roles=roles;this.notifications=notifications;this.preferences=preferences;this.delivery=delivery;this.emails=emails;}
    @Transactional public int alert(Long ticketId,LocalDateTime now){
        var ticket=tickets.findByIdWithLock(ticketId).orElse(null);
        if(ticket==null||!"overdue".equals(deadlines.stateAt(ticket,now)))return 0;
        Map<Long,UserAccount> recipients=new LinkedHashMap<>();
        for(var assignment:assignments.findByTicketInAndStatus(List.of(ticket),AssignmentStatus.ACTIVE))recipients.put(assignment.getAssignedToUser().getUserId(),assignment.getAssignedToUser());
        Long departmentId=ticket.getCategory()==null||ticket.getCategory().getDepartment()==null?null:ticket.getCategory().getDepartment().getDepartmentId();
        boolean managerFound=false;
        if(departmentId!=null)for(var m:memberships.findByDepartmentDepartmentIdAndActiveTrue(departmentId)){
            var account=m.getUser();
            if(account.getAccountStatus()==AccountStatus.ACTIVE&&roles.findByUserUserIdAndActiveTrue(account.getUserId()).stream().anyMatch(r->"Department Manager".equals(r.getRole().getRoleName()))){recipients.put(account.getUserId(),account);managerFound=true;}
        }
        if(!managerFound)for(var r:roles.findByRoleRoleNameAndActiveTrue("System Administrator"))recipients.put(r.getUser().getUserId(),r.getUser());
        String message="Resolution target overdue for "+ticket.getReferenceNo()+" (target "+deadlines.dueAt(ticket)+"). Review the ticket and coordinate the next action.";
        int count=0;
        for(var account:recipients.values()){
            if(account.getAccountStatus()!=AccountStatus.ACTIVE)continue;
            var activeRoles=roles.findByUserUserIdAndActiveTrue(account.getUserId());
            boolean admin=activeRoles.stream().anyMatch(r->"System Administrator".equals(r.getRole().getRoleName()));
            if(!admin&&activeRoles.stream().noneMatch(r->Set.of("Help Desk Support Staff","Department Support Team Member","Department Manager").contains(r.getRole().getRoleName())))continue;
            if(!admin&&(departmentId==null||!memberships.existsByUserUserIdAndDepartmentDepartmentIdAndActiveTrue(account.getUserId(),departmentId)))continue;
            var preference=preferences.getOrCreatePreference(account.getUserId());
            if(!Boolean.TRUE.equals(preference.getInAppEnabled())&&!Boolean.TRUE.equals(preference.getEmailEnabled()))continue;
            if(notifications.existsByUserUserIdAndTicketTicketIdAndNotificationTypeAndMessage(account.getUserId(),ticketId,NotificationType.OVERDUE,message))continue;
            if(emails.existsByUserUserIdAndTicketTicketIdAndMessageContaining(account.getUserId(),ticketId,message))continue;
            delivery.notifyUser(account,ticket,NotificationType.OVERDUE,message);count++;
        }
        return count;
    }
}
