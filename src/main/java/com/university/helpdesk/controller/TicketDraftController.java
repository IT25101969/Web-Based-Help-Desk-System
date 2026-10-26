package com.university.helpdesk.controller;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
@RestController
@RequestMapping("/student/draft")
@Transactional
public class TicketDraftController {
    public record DraftData(Long categoryId,TicketType ticketType,@Size(max=200) String subject,@Size(max=20000) String description,@Size(max=150) String incidentDetail,@Size(max=150) String serviceDetail,@Size(max=20) String severity,Long assetId){}
    public record DraftView(DraftData data,LocalDateTime savedAt){}
    private final TicketDraftRepository drafts;private final UserAccountRepository accounts;
    public TicketDraftController(TicketDraftRepository drafts,UserAccountRepository accounts){this.drafts=drafts;this.accounts=accounts;}
    private Long owner(Authentication auth){var user=accounts.findByUniversityId(auth.getName()).orElseThrow();if(user.getAccountStatus()!=AccountStatus.ACTIVE)throw new org.springframework.security.access.AccessDeniedException("Account is inactive.");return user.getUserId();}
    private DraftView view(TicketDraft d){return new DraftView(new DraftData(d.getCategoryId(),d.getTicketType(),d.getSubject(),d.getDescription(),d.getIncidentDetail(),d.getServiceDetail(),d.getSeverity(),d.getAssetId()),d.getSavedAt());}
    @GetMapping public ResponseEntity<DraftView> load(Authentication auth){return drafts.findById(owner(auth)).map(d->ResponseEntity.ok(view(d))).orElseGet(()->ResponseEntity.noContent().build());}
    @PostMapping public DraftView save(@Valid @RequestBody DraftData data,Authentication auth){
        Long id=owner(auth);var d=drafts.findById(id).orElseGet(TicketDraft::new);d.setUserId(id);d.setCategoryId(data.categoryId());d.setAssetId(data.assetId());d.setTicketType(data.ticketType());d.setSubject(data.subject());d.setDescription(data.description());d.setIncidentDetail(data.incidentDetail());d.setServiceDetail(data.serviceDetail());d.setSeverity(data.severity());d.setSavedAt(LocalDateTime.now());return view(drafts.saveAndFlush(d));
    }
    @PostMapping("/clear") public ResponseEntity<Void> clear(Authentication auth){drafts.deleteById(owner(auth));return ResponseEntity.noContent().build();}
}
