package com.nextgenmanager.nextgenmanager.production.service.jobwork;

public interface JobWorkChallanExportService {
    byte[] generateChallanPdf(Long challanId) throws Exception;
}
