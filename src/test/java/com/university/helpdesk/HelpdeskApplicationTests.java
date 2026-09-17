package com.university.helpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HelpdeskApplicationTests {
    @org.springframework.beans.factory.annotation.Autowired
    com.university.helpdesk.repository.UserAccountRepository users;
    @org.springframework.beans.factory.annotation.Autowired
    com.university.helpdesk.repository.RoleRepository roles;
    @org.springframework.beans.factory.annotation.Autowired
    com.university.helpdesk.repository.PermissionRepository permissions;

    @Test
    void contextLoads() {
        org.junit.jupiter.api.Assertions.assertEquals(6, roles.count());
        org.junit.jupiter.api.Assertions.assertEquals(9, permissions.count());
        org.junit.jupiter.api.Assertions.assertEquals(0, users.count(), "Normal startup must not seed demo users");
    }

}
