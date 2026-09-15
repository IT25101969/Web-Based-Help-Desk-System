package com.university.helpdesk.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserDepartmentId implements Serializable {
    private Long user;
    private Long department;

    public UserDepartmentId() {
    }

    public UserDepartmentId(Long user, Long department) {
        this.user = user;
        this.department = department;
    }

    public Long getUser() { return user; }
    public void setUser(Long user) { this.user = user; }
    public Long getDepartment() { return department; }
    public void setDepartment(Long department) { this.department = department; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserDepartmentId that)) return false;
        return Objects.equals(user, that.user) &&
                Objects.equals(department, that.department);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, department);
    }
}
