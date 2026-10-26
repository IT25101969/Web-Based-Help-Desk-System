package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:account-transaction-review;MODE=MySQL;DB_CLOSE_DELAY=-1")
class AccountTransactionTests extends TestAccounts {
    @Autowired AdminAccountService access;
    @Autowired UserAccountRepository users;
    @Autowired UserRoleRepository memberships;
    @Autowired RoleRepository roles;
    @Autowired StudentRepository students;
    @MockitoSpyBean ActivityLogService logs;

    @Test void accountStatusRolesAndStudentRecordsRollBackWhenAuditWriteFails() {
        Long studentId=users.findByUniversityId("STU001").orElseThrow().getUserId();
        Long supportId=users.findByUniversityId("SUP001").orElseThrow().getUserId();
        Long studentRole=roles.findByRoleName("Student").orElseThrow().getRoleId();
        int roleCount=memberships.findByUserUserIdAndActiveTrue(studentId).size();
        doThrow(new IllegalStateException("Simulated audit database failure")).when(logs)
                .log(any(),anyString(),anyString(),anyLong(),any());
        try {
            assertThrows(IllegalStateException.class,()->access.status(studentId,AccountStatus.DISABLED,"ADM001",null));
            assertEquals(AccountStatus.ACTIVE,users.findById(studentId).orElseThrow().getAccountStatus());
            assertThrows(IllegalStateException.class,()->access.delete(studentId,"ADM001",null));
            assertEquals(AccountStatus.ACTIVE,users.findById(studentId).orElseThrow().getAccountStatus());
            assertEquals(roleCount,memberships.findByUserUserIdAndActiveTrue(studentId).size());
            assertThrows(IllegalStateException.class,()->access.addRole(supportId,studentRole,"ADM001",null));
            assertFalse(students.existsById(supportId));
            assertTrue(memberships.findByUserUserIdAndRoleRoleId(supportId,studentRole).isEmpty());
        } finally { reset(logs); }
    }
}
