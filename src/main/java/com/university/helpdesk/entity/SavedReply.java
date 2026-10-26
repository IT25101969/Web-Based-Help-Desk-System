package com.university.helpdesk.entity;
import jakarta.persistence.*;
@Entity
public class SavedReply {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=120) private String title;
    @Column(nullable=false,length=2000) private String message;
    private boolean enabled=true;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
}
