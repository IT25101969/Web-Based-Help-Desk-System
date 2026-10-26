package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class SmtpDeliveryTests {
    @Test void realJavaMailTransportDeliversToLoopbackSmtp() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            Future<String> received = executor.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    var out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                    out.print("220 localhost test SMTP\r\n"); out.flush();
                    StringBuilder transcript = new StringBuilder();
                    boolean data = false;
                    for (String line; (line = in.readLine()) != null;) {
                        transcript.append(line).append('\n');
                        if (data && !line.equals(".")) continue;
                        if (data) { data = false; out.print("250 accepted\r\n"); }
                        else if (line.equals("DATA")) { data = true; out.print("354 send message\r\n"); }
                        else if (line.equals("QUIT")) { out.print("221 bye\r\n"); out.flush(); break; }
                        else out.print("250 OK\r\n");
                        out.flush();
                    }
                    return transcript.toString();
                }
            });
            var transport = new JavaMailSenderImpl();
            transport.setHost(server.getInetAddress().getHostAddress());
            transport.setPort(server.getLocalPort());
            transport.getJavaMailProperties().setProperty("mail.smtp.timeout", "5000");
            new DefaultEmailSenderService(true, "sender@example.invalid", transport)
                    .sendEmail("recipient@example.invalid", "Ticket submitted", "Ticket HD-123 received.");
            String wire = received.get(10, TimeUnit.SECONDS);
            assertTrue(wire.contains("MAIL FROM:<sender@example.invalid>"));
            assertTrue(wire.contains("RCPT TO:<recipient@example.invalid>"));
            assertTrue(wire.contains("Subject: Ticket submitted"));
            assertTrue(wire.contains("Ticket HD-123 received."));
        }
    }

    @Test void disabledProcessorDoesNotConsumeAttemptsOrReadQueue() {
        var queue = mock(EmailNotificationQueueRepository.class);
        var sender = mock(EmailSenderService.class);
        var processor = new EmailQueueProcessor(queue, sender, mock(ActivityLogRepository.class), true);
        var item = new EmailNotificationQueue();
        processor.scheduledProcess();
        assertEquals(0, processor.processPendingEmails());
        processor.processItem(item);
        assertEquals(0, item.getAttemptCount());
        verifyNoInteractions(queue);
    }

    @Test void authenticationFailureNeverPersistsOrLogsExceptionContents(CapturedOutput output) throws Exception {
        var sender = mock(EmailSenderService.class);
        when(sender.isMailEnabled()).thenReturn(true);
        doThrow(new MailAuthenticationException("secret-app-password reset-token private-address"))
                .when(sender).sendEmail(any(), any(), any());
        var item = new EmailNotificationQueue();
        item.setAttemptCount(2);
        new EmailQueueProcessor(mock(EmailNotificationQueueRepository.class), sender,
                mock(ActivityLogRepository.class), true).processItem(item);
        assertEquals(EmailQueueStatus.FAILED, item.getStatus());
        assertEquals("SMTP authentication failed. Check mail account configuration.", item.getLastError());
        assertFalse(output.getAll().contains("secret-app-password"));
        assertFalse(output.getAll().contains("reset-token"));
        assertFalse(output.getAll().contains("private-address"));
    }

    @Test void missingSenderIsRejectedBeforeTransport() {
        var transport = mock(org.springframework.mail.javamail.JavaMailSender.class);
        assertThrows(IllegalStateException.class, () -> new DefaultEmailSenderService(true, "", transport)
                .sendEmail("recipient@example.invalid", "Subject", "Body"));
        verifyNoInteractions(transport);
    }
}
