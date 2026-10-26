package com.university.helpdesk.service;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
@Service
public class SupportToolsService {
    private final CampusAnnouncementRepository announcements;
    private final SavedReplyRepository replies;
    private final UserAccountRepository accounts;
    private final ActivityLogService logs;
    private final UserRoleRepository roles;
    private final RolePermissionRepository permissions;
    public SupportToolsService(CampusAnnouncementRepository announcements,SavedReplyRepository replies,UserAccountRepository accounts,ActivityLogService logs, UserRoleRepository roles, RolePermissionRepository permissions){this.announcements=announcements;this.replies=replies;this.accounts=accounts;this.logs=logs;this.roles=roles;this.permissions=permissions;}
    @Transactional
    public CampusAnnouncement saveAnnouncement(Long id,String title,String message,String kind,boolean published,LocalDateTime startsAt,LocalDateTime endsAt,String actor,String ip){
        if(!java.util.Set.of("INFORMATION","MAINTENANCE","OUTAGE").contains(kind))throw new IllegalArgumentException("Choose a valid announcement type.");
        if(startsAt==null)startsAt=LocalDateTime.now();
        if(endsAt!=null&&!endsAt.isAfter(startsAt))throw new IllegalArgumentException("The end time must be later than the start time.");
        var a=id==null?new CampusAnnouncement():announcements.findById(id).orElseThrow(()->new IllegalArgumentException("Announcement was not found."));
        a.setTitle(text(title,140));a.setMessage(text(message,2000));a.setKind(kind);a.setPublished(published);a.setStartsAt(startsAt);a.setEndsAt(endsAt);announcements.saveAndFlush(a);
        logs.log(accounts.findByUniversityId(actor).orElseThrow(),"ANNOUNCEMENT_SAVED","CAMPUS_ANNOUNCEMENT",a.getId(),ip);return a;
    }
    @Transactional
    public SavedReply saveReply(Long id,String title,String message,boolean enabled,String actor,String ip){
        var r=id==null?new SavedReply():replies.findById(id).orElseThrow(()->new IllegalArgumentException("Saved reply was not found."));
        r.setTitle(text(title,120));r.setMessage(text(message,2000));r.setEnabled(enabled);replies.saveAndFlush(r);
        logs.log(accounts.findByUniversityId(actor).orElseThrow(),"SAVED_REPLY_UPDATED","SAVED_REPLY",r.getId(),ip);return r;
    }
    @Transactional
    public void deleteAnnouncement(Long id, String actor, String ip) {
        var admin = accounts.findByUniversityId(actor).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Account was not found."));
        boolean allowed = admin.getAccountStatus() == AccountStatus.ACTIVE && roles.findByUserUserIdAndActiveTrue(admin.getUserId()).stream()
                .filter(r -> "System Administrator".equals(r.getRole().getRoleName()))
                .anyMatch(r -> permissions.findByRoleRoleIdAndActiveTrue(r.getRole().getRoleId()).stream()
                        .anyMatch(p -> "MANAGE_SYSTEM".equals(p.getPermission().getPermissionName())));
        if (!allowed) throw new org.springframework.security.access.AccessDeniedException("Announcement deletion requires administrator access.");
        var notice = announcements.findById(id).orElseThrow(() -> new IllegalArgumentException("Announcement was not found. It may already have been deleted."));
        announcements.delete(notice); announcements.flush();
        logs.log(admin, "ANNOUNCEMENT_DELETED", "CAMPUS_ANNOUNCEMENT", id, ip);
    }
    private String text(String value,int max){if(value==null||value.isBlank()||value.trim().length()>max)throw new IllegalArgumentException("Title and message are required and must fit the displayed character limits.");return value.trim();}
}
