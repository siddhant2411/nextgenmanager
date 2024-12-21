package com.nextgenmanager.nextgenmanager.contact.controller;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.service.ContactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/contact")
@CrossOrigin(origins = "http://localhost:3000")
public class ContactController {

    @Autowired
    private ContactService contactService;

    private static final Logger logger = LoggerFactory.getLogger(ContactController.class);

    @PostMapping
    public ResponseEntity<Contact> createContact(@RequestBody Contact contact) {
        try {
            logger.info("Creating a new contact with companyName: {}", contact.getCompanyName());
            Contact createdContact = contactService.createContact(contact);
            return new ResponseEntity<>(createdContact, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Error occurred while creating contact", e);
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Contact> getContact(@PathVariable int id) {
        try {
            logger.info("Fetching contact with id: {}", id);
            Contact contact = contactService.getContact(id);
            return new ResponseEntity<>(contact, HttpStatus.OK);
        } catch (NoSuchElementException e) {
            logger.error("Contact not found with id: {}", id, e);
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            logger.error("Error occurred while fetching contact with id: {}", id, e);
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Contact> updateContact(@RequestBody Contact updatedContact, @PathVariable int id) {
        try {
            logger.info("Updating contact with id: {}", id);
            Contact contact = contactService.updateContact(updatedContact, id);
            return new ResponseEntity<>(contact, HttpStatus.OK);
        } catch (NoSuchElementException e) {
            logger.error("Contact not found with id: {}", id, e);
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            logger.error("Error occurred while updating contact with id: {}", id, e);
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable int id) {
        try {
            logger.info("Deleting contact with id: {}", id);
            contactService.deleteContact(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (NoSuchElementException e) {
            logger.error("Contact not found with id: {}", id, e);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            logger.error("Error occurred while deleting contact with id: {}", id, e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping
    public ResponseEntity<Page<Contact>> getContactList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String gstNumber,
            @RequestParam(required = false) String state) {
        try {
            logger.info("Fetching contact list with filters - companyName: {}, gstNumber: {}, state: {}",
                    companyName, gstNumber, state);
            Page<Contact> contacts = contactService.getContactList(page, size, sortBy, sortDir, companyName, gstNumber, state);
            return new ResponseEntity<>(contacts, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error occurred while fetching contact list", e);
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
