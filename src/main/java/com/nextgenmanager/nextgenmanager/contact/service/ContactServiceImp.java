package com.nextgenmanager.nextgenmanager.contact.service;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.contact.model.ContactPersonDetail;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class ContactServiceImp implements ContactService {

    private static final Logger logger = LoggerFactory.getLogger(ContactServiceImp.class);

    @Autowired
    private ContactRepository contactRepository;

    @Override
    public Contact createContact(Contact contact) {
        try {
            if (contact.getPersonDetails() != null) {
                for (ContactPersonDetail personDetail : contact.getPersonDetails()) {
                    personDetail.setContact(contact);
                }
            }
            if (contact.getAddresses() != null) {
                for (ContactAddress address : contact.getAddresses()) {
                    address.setContact(contact);
                }
            }
            Contact savedContact = contactRepository.save(contact);
            logger.info("Contact created successfully with ID: {}", savedContact.getId());
            return savedContact;
        } catch (Exception e) {
            logger.error("Error creating contact: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create contact.");
        }
    }

    @Override
    public Contact getContact(int contactId) {
        try {
            Optional<Contact> contact = contactRepository.findById(contactId);
            if (contact.isPresent()) {
                logger.info("Contact retrieved successfully with ID: {}", contactId);
                return contact.get();
            } else {
                logger.warn("Contact not found with ID: {}", contactId);
                throw new RuntimeException("Contact not found.");
            }
        } catch (Exception e) {
            logger.error("Error retrieving contact with ID {}: {}", contactId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve contact.");
        }
    }

    @Override
    public Contact updateContact(Contact updatedContact, int id) {
        try {
            updatedContact.setId(id);
            if (updatedContact.getPersonDetails() != null) {
                for (ContactPersonDetail personDetail : updatedContact.getPersonDetails()) {
                    personDetail.setContact(updatedContact);
                }
            }
            if (updatedContact.getAddresses() != null) {
                for (ContactAddress address : updatedContact.getAddresses()) {
                    address.setContact(updatedContact);
                }
            }
            Contact savedContact = contactRepository.save(updatedContact);
            logger.info("Contact updated successfully with ID: {}", id);
            return savedContact;
        } catch (Exception e) {
            logger.error("Error updating contact with ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to update contact.");
        }
    }

    @Override
    public void deleteContact(int contactId) {
        try {
            Contact contact = contactRepository.findById(contactId)
                    .orElseThrow(() -> new RuntimeException("Contact not found."));
            contact.setDeletedDate(new Date());
            contactRepository.save(contact); // Persist the soft delete
            logger.info("Contact soft-deleted successfully with ID: {}", contactId);
        } catch (Exception e) {
            logger.error("Error deleting contact with ID {}: {}", contactId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete contact.");
        }
    }

    @Override
    public Page<Contact> getContactList(int page, int size, String sortBy, String sortDir, String companyName, String gstNumber, String state) {
        try {
            // Example: Add search functionality based on parameters
            // Create a pageable object
            Pageable pageable = PageRequest.of(page, size,
                    sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());

            Page<Contact> contacts = contactRepository.searchContacts(companyName,gstNumber,state,pageable);
            logger.info("Contact list retrieved successfully, total: {}", contacts.getTotalElements());
            return contacts;
        } catch (Exception e) {
            logger.error("Error retrieving contact list: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve contact list.");
        }
    }
}
