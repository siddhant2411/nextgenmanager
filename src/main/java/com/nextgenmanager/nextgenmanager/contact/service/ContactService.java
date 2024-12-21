package com.nextgenmanager.nextgenmanager.contact.service;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ContactService {

    public Contact createContact(Contact contact);

    public Contact getContact(int contactId);

    public Contact updateContact(Contact updatedContact, int id);

    public void deleteContact(int contactId);

    public Page<Contact> getContactList(int page, int size, String sortBy, String sortDir, String companyName, String gstNumber, String state);

}
