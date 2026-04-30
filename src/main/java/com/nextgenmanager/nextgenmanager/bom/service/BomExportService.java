package com.nextgenmanager.nextgenmanager.bom.service;

import java.io.ByteArrayInputStream;

import java.util.List;

public interface BomExportService {
    byte[] generateFlatBomExcel(List<Integer> bomIds) throws Exception;
    byte[] generateIndentedBomExcel(List<Integer> bomIds) throws Exception;
    byte[] generateManufacturingBomPdf(List<Integer> bomIds) throws Exception;
    byte[] generateBomJobSheet(List<Integer> bomIds) throws Exception;
}
