package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteDto;
import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNote;
import org.springframework.data.domain.Page;

public interface DeliveryNoteService {
    DeliveryNoteDto createDeliveryNote(DeliveryNoteCreateDto dto);
    DeliveryNoteDto getDeliveryNoteById(Long id);
    Page<DeliveryNoteDto> getAllDeliveryNotes(int page, int size, String sortBy, String sortDir, Long salesOrderId);
    void deleteDeliveryNote(Long id);
}
