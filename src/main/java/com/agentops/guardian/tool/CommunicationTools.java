package com.agentops.guardian.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class CommunicationTools {

    @Tool(description = """
            Sends an email message to a specified recipient.

            Use this capability for legitimate business communication.

            Provide the intended recipient, subject, and complete message body.
            Do not claim that an email was sent unless this tool confirms
            successful execution.
            """)
    public EmailResult sendEmail(
            String recipient,
            String subject,
            String body) {

        validate(recipient, subject, body);

        String messageId = UUID.randomUUID().toString();

        System.out.println("""
                
                ================= OUTBOUND EMAIL =================
                Message ID : %s
                Recipient  : %s
                Subject    : %s
                
                %s
                ====================================================
                """.formatted(
                messageId,
                recipient,
                subject,
                body
        ));

        return new EmailResult(
                messageId,
                recipient,
                true,
                LocalDateTime.now()
        );
    }

    private void validate(String recipient, String subject, String body) {

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Recipient is required.");
        }

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required.");
        }

        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Email body is required.");
        }
    }

    public record EmailResult(
            String messageId,
            String recipient,
            boolean accepted,
            LocalDateTime timestamp
    ) {}
}