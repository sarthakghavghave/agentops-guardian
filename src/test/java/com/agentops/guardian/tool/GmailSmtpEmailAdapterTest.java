package com.agentops.guardian.tool;

import jakarta.mail.Session;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.Multipart;
import jakarta.mail.BodyPart;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GmailSmtpEmailAdapterTest {

    private static final String RECIPIENT = "operator@example.test";
    private static final String SUBJECT = "Résumé – análisis";
    private static final String BODY = "The requested report is attached — análisis.";

    @Test
    void submitsUtf8PlainTextMimeMessage() throws Exception {
        JavaMailSender mailSender = mockMailSender();
        MimeMessage message = preparedMessage(mailSender);
        GmailSmtpEmailAdapter adapter = configuredAdapter(mailSender);

        String messageId = adapter.send(RECIPIENT, SUBJECT, BODY, null);
        message.saveChanges();

        assertFalse(messageId.isBlank());
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertEquals("guardian@example.test", from.getAddress());
        assertEquals("AgentOps", from.getPersonal());
        assertEquals(RECIPIENT, message.getAllRecipients()[0].toString());
        assertEquals(SUBJECT, message.getSubject());
        assertEquals(BODY, message.getContent());
        assertTrue(message.getContentType().toLowerCase().contains("charset=utf-8"));
        verify(mailSender).send(message);
    }

    @Test
    void submitsRegisteredPdfAsMimeAttachmentWithOriginalBytes() throws Exception {
        JavaMailSender mailSender = mockMailSender();
        MimeMessage message = preparedMessage(mailSender);
        GmailSmtpEmailAdapter adapter = configuredAdapter(mailSender);
        byte[] pdf = validPdf();
        CommunicationTools.EmailAttachment attachment =
                new CommunicationTools.EmailAttachment("customer-report.pdf", "application/pdf", pdf);

        adapter.send(RECIPIENT, SUBJECT, BODY, attachment);
        message.saveChanges();

        Multipart multipart = (Multipart) message.getContent();
        assertEquals(2, multipart.getCount());
        BodyPart body = multipart.getBodyPart(0);
        assertEquals(BODY, findText(body));

        BodyPart pdfPart = multipart.getBodyPart(1);
        assertEquals("application/pdf", pdfPart.getContentType().split(";")[0]);
        assertEquals("customer-report.pdf", pdfPart.getFileName());
        assertArrayEquals(pdf, pdfPart.getInputStream().readAllBytes());
        verify(mailSender).send(message);
    }

    @Test
    void missingCredentialsFailBeforeCreatingOrSendingMessage() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        GmailSmtpEmailAdapter adapter = new GmailSmtpEmailAdapter(
                mailSender, "guardian@example.test", "", "", RECIPIENT
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> adapter.send(RECIPIENT, SUBJECT, BODY, null)
        );

        assertTrue(exception.getMessage().contains("must be configured"));
        verifyNoMailCalls(mailSender);
    }

    @Test
    void emptyAllowlistFailsClosedBeforeCreatingOrSendingMessage() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        GmailSmtpEmailAdapter adapter = new GmailSmtpEmailAdapter(
                mailSender, "guardian@example.test", "guardian@example.test", "app-password", " "
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> adapter.send(RECIPIENT, SUBJECT, BODY, null)
        );

        assertTrue(exception.getMessage().contains("EMAIL_ALLOWED_RECIPIENTS"));
        verifyNoMailCalls(mailSender);
    }

    @Test
    void disallowedRecipientDoesNotInvokeProvider() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        GmailSmtpEmailAdapter adapter = configuredAdapter(mailSender);

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.send("outside@example.test", SUBJECT, BODY, null)
        );

        verifyNoMailCalls(mailSender);
    }

    @Test
    void invalidAttachmentDoesNotInvokeProvider() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        GmailSmtpEmailAdapter adapter = configuredAdapter(mailSender);
        CommunicationTools.EmailAttachment attachment =
                new CommunicationTools.EmailAttachment("customer-report.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.send(RECIPIENT, SUBJECT, BODY, attachment)
        );

        verifyNoMailCalls(mailSender);
    }

    @Test
    void providerExceptionEscapesWithoutAnAcceptedResult() throws Exception {
        JavaMailSender mailSender = mockMailSender();
        preparedMessage(mailSender);
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(MimeMessage.class));
        GmailSmtpEmailAdapter adapter = configuredAdapter(mailSender);

        assertThrows(
                MailSendException.class,
                () -> adapter.send(RECIPIENT, SUBJECT, BODY, null)
        );

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void communicationToolRecordsOnlyAfterAdapterSubmissionReturns() {
        GmailSmtpEmailAdapter adapter = mock(GmailSmtpEmailAdapter.class);
        CommunicationTools tools = new CommunicationTools(adapter);
        tools.beginExecution();
        when(adapter.send(RECIPIENT, SUBJECT, BODY, null)).thenReturn("submission-1");

        CommunicationTools.EmailResult result = tools.sendEmail(RECIPIENT, SUBJECT, BODY);

        assertTrue(result.accepted());
        assertEquals("submission-1", result.messageId());
        assertEquals(java.util.List.of(result), tools.executionResults());

        tools.clearExecutionResult();
        tools.beginExecution();
        when(adapter.send(RECIPIENT, SUBJECT, BODY, null)).thenThrow(new MailSendException("SMTP unavailable"));
        assertThrows(MailSendException.class, () -> tools.sendEmail(RECIPIENT, SUBJECT, BODY));
        assertTrue(tools.executionResults().isEmpty());
        verify(adapter, org.mockito.Mockito.times(2)).send(RECIPIENT, SUBJECT, BODY, null);
    }

    private JavaMailSender mockMailSender() {
        return mock(JavaMailSender.class);
    }

    private MimeMessage preparedMessage(JavaMailSender mailSender) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        return message;
    }

    private GmailSmtpEmailAdapter configuredAdapter(JavaMailSender mailSender) {
        return new GmailSmtpEmailAdapter(
                mailSender,
                "guardian@example.test",
                "guardian@example.test",
                "app-password",
                "operator@example.test"
        );
    }

    private void verifyNoMailCalls(JavaMailSender mailSender) {
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private String findText(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                String text = findText(multipart.getBodyPart(index));
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private byte[] validPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
