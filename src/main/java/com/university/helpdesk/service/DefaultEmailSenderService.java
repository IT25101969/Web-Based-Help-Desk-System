package com.university.helpdesk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class DefaultEmailSenderService implements EmailSenderService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmailSenderService.class);

    private final boolean mailEnabled;
    private final String fromAddress;
    private final JavaMailSender mailSender;

    public DefaultEmailSenderService(
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.mail.from:helpdesk@university.edu}") String fromAddress,
            @Autowired(required = false) JavaMailSender mailSender
    ) {
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
        this.mailSender = mailSender;
    }

    @Override
    public void sendEmail(String to, String subject, String body) throws Exception {
        if (!mailEnabled) {
            log.info("Email delivery skipped for {} (app.mail.enabled=false)", to);
            throw new IllegalStateException("Email delivery is disabled (app.mail.enabled=false).");
        }

        if (mailSender == null) {
            log.warn("JavaMailSender bean is not available. Check spring.mail configuration.");
            throw new IllegalStateException("JavaMailSender is not configured.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
        log.info("Email successfully sent to {} with subject '{}'", to, subject);
    }

    @Override
    public boolean isMailEnabled() {
        return mailEnabled;
    }
}
