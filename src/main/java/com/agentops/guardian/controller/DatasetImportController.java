package com.agentops.guardian.controller;

import com.agentops.guardian.ingestion.DatasetImportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/admin")
public class DatasetImportController {

    private final DatasetImportService datasetImportService;

    public DatasetImportController(DatasetImportService datasetImportService) {
        this.datasetImportService = datasetImportService;
    }

    @GetMapping("/import")
    public String importDataset() throws Exception {
        datasetImportService.importDataset(Path.of("D:/Sarthak/Projects/agentops-guardian/data"));
        return "Dataset import completed.";
    }
}