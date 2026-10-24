package com.agentops.guardian.tool;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class GmailSmtpEmailAdapter {

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("[^\\\\/:*?\"<>|\\r\\n]+");

    private final JavaMailSender mailSender;
    private final String sender;
    private final String username;
    private final String appPassword;
    private final Set<String> permittedRecipients;

    public GmailSmtpEmailAdapter(
            JavaMailSender mailSender,
            @Value("${agentops.email.sender:}") String sender,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String appPassword,
            @Value("${agentops.email.allowed-recipients:}") String allowedRecipients
    ) {
        this.mailSender = mailSender;
        this.sender = sender;
        this.username = username;
        this.appPassword = appPassword;
        this.permittedRecipients = Arrays.stream(allowedRecipients.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public String send(
            String recipient,
            String subject,
            String body,
            CommunicationTools.EmailAttachment attachment
    ) throws MailException {
        validateConfiguration();
        if (!permittedRecipients.contains(recipient.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("The recipient is not in the configured email allowlist.");
        }
        validateAttachment(attachment);

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    attachment != null,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(sender, "AgentOps");
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body, false);
            if (attachment != null) {
                helper.addAttachment(
                        attachment.fileName(),
                        new ByteArrayResource(attachment.content()),
                        attachment.contentType()
                );
            }
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new IllegalStateException("Unable to construct the email message.", exception);
        }

        String messageId = UUID.randomUUID().toString();
        mailSender.send(message);
        return messageId;
    }

    private void validateConfiguration() {
        if (sender == null || sender.isBlank()
                || username == null || username.isBlank()
                || appPassword == null || appPassword.isBlank()) {
            throw new IllegalStateException(
                    "Gmail SMTP sender, username, and App Password must be configured before sending email."
            );
        }
        if (permittedRecipients.isEmpty()) {
            throw new IllegalStateException(
                    "EMAIL_ALLOWED_RECIPIENTS must contain at least one permitted email address."
            );
        }
    }

    private void validateAttachment(CommunicationTools.EmailAttachment attachment) {
        if (attachment == null) {
            return;
        }
        if (!"application/pdf".equalsIgnoreCase(attachment.contentType())
                || !FILE_NAME_PATTERN.matcher(attachment.fileName()).matches()) {
            throw new IllegalArgumentException("Only a valid PDF attachment can be sent.");
        }

        byte[] content = attachment.content();
        if (content.length < 5 || content[0] != '%'
                || content[1] != 'P' || content[2] != 'D'
                || content[3] != 'F' || content[4] != '-') {
            throw new IllegalArgumentException("The email attachment is not a PDF.");
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.getNumberOfPages() < 1) {
                throw new IllegalArgumentException("The email attachment PDF has no pages.");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The email attachment is not a valid PDF.", exception);
        }
    }
}
