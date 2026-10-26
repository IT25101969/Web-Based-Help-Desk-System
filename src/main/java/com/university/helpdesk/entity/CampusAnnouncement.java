package com.university.helpdesk.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
public class CampusAnnouncement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=140) private String title;
    @Column(nullable=false,length=2000) private String message;
    @Column(nullable=false,length=20) private String kind;
    private boolean published;
    @Column(nullable=false) private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public String getKind(){return kind;} public void setKind(String v){kind=v;}
    public boolean isPublished(){return published;} public void setPublished(boolean v){published=v;}
    public LocalDateTime getStartsAt(){return startsAt;} public void setStartsAt(LocalDateTime v){startsAt=v;}
    public LocalDateTime getEndsAt(){return endsAt;} public void setEndsAt(LocalDateTime v){endsAt=v;}
}
