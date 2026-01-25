package com.nextgenmanager.nextgenmanager.bom.service;

import java.io.ByteArrayInputStream;

public interface BomExportService {
    ByteArrayInputStream exportUnifiedBom(int bomId);
}
