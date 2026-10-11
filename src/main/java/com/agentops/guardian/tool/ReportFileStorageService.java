package com.agentops.guardian.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class ReportFileStorageService {

    private final Path reportDirectory;

    public ReportFileStorageService(
            @Value("${agentops.reports.directory:./data/reports}") String reportDirectory
    ) {
        this.reportDirectory = Path.of(reportDirectory).toAbsolutePath().normalize();
        Path sourceDirectory = Path.of("src").toAbsolutePath().normalize();
        if (this.reportDirectory.startsWith(sourceDirectory)) {
            throw new IllegalArgumentException("Report PDFs must be stored outside the source tree.");
        }
    }

    public StoredReport storePdf(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF content is required.");
        }

        Path reportPath = reportDirectory.resolve("customer-report-" + UUID.randomUUID() + ".pdf");
        try {
            Files.createDirectories(reportDirectory);
            Files.write(reportPath, pdfBytes);
            return new StoredReport(reportPath.toString(), Files.size(reportPath));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to store generated report PDF.", exception);
        }
    }

    public record StoredReport(String reference, long sizeBytes) {
    }
}
