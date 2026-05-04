package com.nextgenmanager.nextgenmanager.production.service;

public interface JobWorkChallanExportService {
    byte[] generateChallanPdf(Long challanId) throws Exception;
}
