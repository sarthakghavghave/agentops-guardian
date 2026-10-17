package com.agentops.guardian.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class CommunicationTools {

    public static final String TOOL_NAME = "sendEmail";

    private final GmailSmtpEmailAdapter emailAdapter;
    private final ThreadLocal<List<EmailResult>> executionResults = new ThreadLocal<>();
    private final ThreadLocal<EmailAttachment> executionAttachment = new ThreadLocal<>();

    public CommunicationTools(GmailSmtpEmailAdapter emailAdapter) {
        this.emailAdapter = emailAdapter;
    }

    @Tool(description = """
            Sends an email message to a specified recipient.

            Use this capability for legitimate business communication.

            Provide the intended recipient, subject, and complete message body.
            Do not claim that an email was accepted unless this tool confirms
            successful submission to the email provider.
            """)
    public EmailResult sendEmail(
            String recipient,
            String subject,
            String body) {

        validate(recipient, subject, body);

        EmailAttachment attachment = executionAttachment.get();
        String messageId = emailAdapter.send(recipient, subject, body, attachment);

        EmailResult result = new EmailResult(
                messageId,
                recipient,
                true,
                LocalDateTime.now(),
                attachment == null ? null : attachment.fileName()
        );
        List<EmailResult> results = executionResults.get();
        if (results != null) {
            results.add(result);
        }
        return result;
    }

    public void beginExecution() {
        executionResults.set(new ArrayList<>());
    }

    public void clearExecutionResult() {
        executionResults.remove();
    }

    public List<EmailResult> executionResults() {
        List<EmailResult> results = executionResults.get();
        return results == null ? List.of() : List.copyOf(results);
    }

    public void setAttachmentForExecution(EmailAttachment attachment) {
        if (attachment == null) {
            executionAttachment.remove();
        } else {
            executionAttachment.set(attachment);
        }
    }

    public void clearAttachmentForExecution() {
        executionAttachment.remove();
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
            LocalDateTime timestamp,
            String attachmentFileName
    ) {
        public EmailResult(String messageId, String recipient, boolean accepted, LocalDateTime timestamp) {
            this(messageId, recipient, accepted, timestamp, null);
        }
    }

    public record EmailAttachment(String fileName, String contentType, byte[] content) {
        public EmailAttachment {
            if (fileName == null || fileName.isBlank()
                    || contentType == null || contentType.isBlank()
                    || content == null || content.length == 0) {
                throw new IllegalArgumentException("A valid email attachment is required.");
            }
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}