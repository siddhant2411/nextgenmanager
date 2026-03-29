package com.nextgenmanager.nextgenmanager.contact.service;

import com.nextgenmanager.nextgenmanager.contact.dto.ContactDTO;
import com.nextgenmanager.nextgenmanager.contact.dto.ContactRequestDTO;
import com.nextgenmanager.nextgenmanager.contact.dto.ContactSummaryDTO;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactType;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ContactService {

    ContactDTO create(ContactRequestDTO request);

    ContactDTO getById(int id);

    ContactDTO update(int id, ContactRequestDTO request);

    void delete(int id);

    Page<ContactDTO> search(String query, ContactType type, int page, int size, String sortBy, String sortDir);

    List<ContactSummaryDTO> searchForDropdown(String query, ContactType type);

    Contact getEntityById(int id);
}
