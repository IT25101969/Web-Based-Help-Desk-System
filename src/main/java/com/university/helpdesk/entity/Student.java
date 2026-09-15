package com.university.helpdesk.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "STUDENT")
public class Student {

    @Id
    @Column(name = "User_ID")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "User_ID")
    private UserAccount user;

    @Column(name = "Faculty", length = 120)
    private String faculty;

    @Column(name = "Program", length = 150)
    private String program;

    @Column(name = "Academic_Year")
    private Integer academicYear;

    public Long getUserId() { return userId; }
    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }
    public String getFaculty() { return faculty; }
    public void setFaculty(String faculty) { this.faculty = faculty; }
    public String getProgram() { return program; }
    public void setProgram(String program) { this.program = program; }
    public Integer getAcademicYear() { return academicYear; }
    public void setAcademicYear(Integer academicYear) { this.academicYear = academicYear; }
}
