package com.agentops.guardian.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

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

    public LoadedPdf loadPdf(String storageReference) {
        if (storageReference == null || storageReference.isBlank()) {
            throw new IllegalArgumentException("A stored PDF reference is required.");
        }
        Path requestedPath = Path.of(storageReference).toAbsolutePath().normalize();
        if (!requestedPath.startsWith(reportDirectory)) {
            throw new IllegalArgumentException("Report reference is outside the configured report directory.");
        }
        try {
            if (!Files.isRegularFile(requestedPath)) {
                throw new IllegalArgumentException("The registered report PDF is not available.");
            }
            byte[] bytes = Files.readAllBytes(requestedPath);
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P'
                    || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') {
                throw new IllegalArgumentException("The registered report file is not a PDF.");
            }
            try (PDDocument document = Loader.loadPDF(bytes)) {
                if (document.getNumberOfPages() < 1) {
                    throw new IllegalArgumentException("The registered report PDF has no pages.");
                }
            }
            return new LoadedPdf(requestedPath.getFileName().toString(), bytes);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load registered report PDF.", exception);
        }
    }

    public record StoredReport(String reference, long sizeBytes) {
    }

    public record LoadedPdf(String fileName, byte[] content) {
        public LoadedPdf {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
