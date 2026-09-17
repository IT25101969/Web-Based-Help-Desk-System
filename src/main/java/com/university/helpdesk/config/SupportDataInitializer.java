package com.university.helpdesk.config;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.List;

@Configuration
@ConditionalOnProperty(name = "app.seed.demo", havingValue = "true")
public class SupportDataInitializer {

    @Bean
    @Order(2)
    CommandLineRunner initializeSupportData(
            UserAccountRepository userAccountRepository,
            DepartmentRepository departmentRepository,
            CategoryRepository categoryRepository,
            StudentRepository studentRepository,
            UserDepartmentRepository userDepartmentRepository,
            FaqRepository faqRepository
    ) {
        return args -> {

            UserAccount studentUser = userAccountRepository
                    .findByUniversityId("STU001")
                    .orElse(null);

            UserAccount supportUser = userAccountRepository
                    .findByUniversityId("SUP001")
                    .orElse(null);

            UserAccount departmentSupportUser = userAccountRepository
                    .findByUniversityId("DSU001")
                    .orElse(null);

            UserAccount managerUser = userAccountRepository
                    .findByUniversityId("MGR001")
                    .orElse(null);

            UserAccount student2User = userAccountRepository
                    .findByUniversityId("STU002")
                    .orElse(null);

            if (studentUser != null && !studentRepository.existsById(studentUser.getUserId())) {
                Student student = new Student();
                student.setUser(studentUser);
                student.setFaculty("Computing");
                student.setProgram("Information Technology");
                student.setAcademicYear(2);
                studentRepository.save(student);
            }

            if (student2User != null && !studentRepository.existsById(student2User.getUserId())) {
                Student student2 = new Student();
                student2.setUser(student2User);
                student2.setFaculty("Computing");
                student2.setProgram("Software Engineering");
                student2.setAcademicYear(3);
                studentRepository.save(student2);
            }

            Department studentServices = createDepartmentIfMissing(
                    departmentRepository,
                    "Student Services",
                    "General student support services",
                    "studentservices@university.edu",
                    "Main Building"
            );

            Department itSupport = createDepartmentIfMissing(
                    departmentRepository,
                    "IT Support",
                    "Technical and account support",
                    "itsupport@university.edu",
                    "IT Centre"
            );

            Department finance = createDepartmentIfMissing(
                    departmentRepository,
                    "Finance",
                    "Payments and finance support",
                    "finance@university.edu",
                    "Administration Building"
            );

            Department academicAffairs = createDepartmentIfMissing(
                    departmentRepository,
                    "Academic Affairs",
                    "Academic registration and programme support",
                    "academic@university.edu",
                    "Academic Building"
            );

            Department library = createDepartmentIfMissing(
                    departmentRepository,
                    "Library",
                    "Library account and resource support",
                    "library@university.edu",
                    "University Library"
            );

            Department examination = createDepartmentIfMissing(
                    departmentRepository,
                    "Examination Division",
                    "Examination and results support",
                    "exams@university.edu",
                    "Examination Centre"
            );

            Category passwordReset = createCategoryIfMissing(
                    categoryRepository,
                    itSupport,
                    "Password Reset",
                    "University account password and access issues"
            );

            Category wifiIssue = createCategoryIfMissing(
                    categoryRepository,
                    itSupport,
                    "Wi-Fi Issue",
                    "Campus Wi-Fi and connectivity issues"
            );

            createCategoryIfMissing(
                    categoryRepository,
                    finance,
                    "Payment Inquiry",
                    "Payment, fee and receipt inquiries"
            );

            createCategoryIfMissing(
                    categoryRepository,
                    academicAffairs,
                    "Academic Registration",
                    "Course and academic registration assistance"
            );

            Category libraryAccount = createCategoryIfMissing(
                    categoryRepository,
                    library,
                    "Library Account",
                    "Library account and access assistance"
            );

            createCategoryIfMissing(
                    categoryRepository,
                    examination,
                    "Exam Result",
                    "Examination results and result-related inquiries"
            );

            createCategoryIfMissing(
                    categoryRepository,
                    itSupport,
                    "Software Installation",
                    "University-supported software installation"
            );

            createCategoryIfMissing(
                    categoryRepository,
                    finance,
                    "Payment Receipt",
                    "Payment receipt requests and corrections"
            );

            createCategoryIfMissing(
                    categoryRepository,
                    null,
                    "General Inquiry",
                    "General university inquiries requiring administrative triage and manual routing"
            );

            if (supportUser != null) {
                addMembershipIfMissing(userDepartmentRepository, supportUser, itSupport, "SUPPORT");
                addMembershipIfMissing(userDepartmentRepository, supportUser, studentServices, "SUPPORT");
                addMembershipIfMissing(userDepartmentRepository, supportUser, library, "SUPPORT");
            }

            if (departmentSupportUser != null) {
                addMembershipIfMissing(userDepartmentRepository, departmentSupportUser, finance, "SUPPORT");
                addMembershipIfMissing(userDepartmentRepository, departmentSupportUser, academicAffairs, "SUPPORT");
                addMembershipIfMissing(userDepartmentRepository, departmentSupportUser, examination, "SUPPORT");
            }

            if (managerUser != null) {
                for (Department department : List.of(
                        studentServices,
                        itSupport,
                        finance,
                        academicAffairs,
                        library,
                        examination
                )) {
                    addMembershipIfMissing(userDepartmentRepository, managerUser, department, "MANAGER");
                }
            }

            createFaqIfMissing(
                    faqRepository,
                    passwordReset,
                    "How can I reset my university account password?",
                    "Use the Forgot Password option on the sign-in page. If access is still unavailable, submit a Password Reset support ticket."
            );

            createFaqIfMissing(
                    faqRepository,
                    wifiIssue,
                    "What should I do if campus Wi-Fi is not working?",
                    "Confirm Wi-Fi is enabled, reconnect to the university network, and verify your university credentials. Submit a ticket if the issue continues."
            );

            createFaqIfMissing(
                    faqRepository,
                    libraryAccount,
                    "How do I get help with my library account?",
                    "Search the FAQ first. If the issue remains unresolved, submit a Library Account ticket so the Library team can assist you."
            );
        };
    }

    private Department createDepartmentIfMissing(
            DepartmentRepository repository,
            String name,
            String description,
            String contactEmail,
            String location
    ) {
        return repository.findByDepartmentName(name)
                .orElseGet(() -> {
                    Department department = new Department();
                    department.setDepartmentName(name);
                    department.setDescription(description);
                    department.setContactEmail(contactEmail);
                    department.setLocation(location);
                    return repository.save(department);
                });
    }

    private Category createCategoryIfMissing(
            CategoryRepository repository,
            Department department,
            String name,
            String description
    ) {
        return repository.findByCategoryName(name)
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setDepartment(department);
                    category.setCategoryName(name);
                    category.setDescription(description);
                    category.setStatus("ACTIVE");
                    return repository.save(category);
                });
    }

    private void addMembershipIfMissing(
            UserDepartmentRepository repository,
            UserAccount user,
            Department department,
            String membershipType
    ) {
        if (repository.existsByUserUserIdAndDepartmentDepartmentIdAndActiveTrue(
                user.getUserId(),
                department.getDepartmentId()
        )) {
            return;
        }

        UserDepartment membership = new UserDepartment();
        membership.setUser(user);
        membership.setDepartment(department);
        membership.setMembershipType(membershipType);
        membership.setActive(true);
        repository.save(membership);
    }

    private void createFaqIfMissing(
            FaqRepository repository,
            Category category,
            String question,
            String answer
    ) {
        if (repository.existsByQuestion(question)) {
            return;
        }

        Faq faq = new Faq();
        faq.setCategory(category);
        faq.setQuestion(question);
        faq.setAnswer(answer);
        faq.setStatus(FaqStatus.PUBLISHED);
        repository.save(faq);
    }
}
