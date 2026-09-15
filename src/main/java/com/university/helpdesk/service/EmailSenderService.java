package com.university.helpdesk.service;

public interface EmailSenderService {

    void sendEmail(String to, String subject, String body) throws Exception;

    boolean isMailEnabled();
}
