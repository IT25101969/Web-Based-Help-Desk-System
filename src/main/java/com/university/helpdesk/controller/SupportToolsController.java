package com.university.helpdesk.controller;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.SupportToolsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDateTime;
@Controller
@RequestMapping("/admin/support-tools")
public class SupportToolsController {
    private final CampusAnnouncementRepository announcements;private final SavedReplyRepository replies;private final SupportToolsService tools;
    public SupportToolsController(CampusAnnouncementRepository announcements,SavedReplyRepository replies,SupportToolsService tools){this.announcements=announcements;this.replies=replies;this.tools=tools;}
    @GetMapping public String tools(Model model){model.addAttribute("announcements",announcements.findAllByOrderByStartsAtDesc());model.addAttribute("replies",replies.findAllByOrderByTitleAsc());return "admin-support-tools";}
    @PostMapping("/announcements") public String announcement(@RequestParam(required=false) Long id,@RequestParam String title,@RequestParam String message,@RequestParam String kind,@RequestParam(defaultValue="false") boolean published,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt,Authentication auth,HttpServletRequest request,RedirectAttributes flash){
        try{tools.saveAnnouncement(id,title,message,kind,published,startsAt,endsAt,auth.getName(),request.getRemoteAddr());flash.addFlashAttribute("success","Announcement saved.");}catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}return "redirect:/admin/support-tools#announcements";
    }
    @PostMapping("/announcements/{id}/delete")
    public String deleteAnnouncement(@PathVariable Long id, Authentication auth, HttpServletRequest request, RedirectAttributes flash) {
        try {
            tools.deleteAnnouncement(id, auth.getName(), request.getRemoteAddr());
            flash.addFlashAttribute("success", "Announcement deleted. It is no longer displayed on campus pages.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/support-tools#announcements";
    }
    @PostMapping("/replies") public String reply(@RequestParam(required=false) Long id,@RequestParam String title,@RequestParam String message,@RequestParam(defaultValue="false") boolean enabled,Authentication auth,HttpServletRequest request,RedirectAttributes flash){
        try{tools.saveReply(id,title,message,enabled,auth.getName(),request.getRemoteAddr());flash.addFlashAttribute("success","Saved reply updated.");}catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}return "redirect:/admin/support-tools#replies";
    }
}
