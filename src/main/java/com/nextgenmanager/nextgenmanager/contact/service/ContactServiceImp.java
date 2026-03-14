package com.nextgenmanager.nextgenmanager.contact.service;

import com.nextgenmanager.nextgenmanager.contact.dto.*;
import com.nextgenmanager.nextgenmanager.contact.model.*;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ContactServiceImp implements ContactService {

    @Autowired
    private ContactRepository contactRepository;

    // ──────────────────────────── CRUD ────────────────────────────

    @Override
    public ContactDTO create(ContactRequestDTO request) {
        Contact contact = new Contact();
        mapRequestToEntity(request, contact);
        contact.setContactCode(generateCode(request.getContactType()));
        return toDTO(contactRepository.save(contact));
    }

    @Override
    @Transactional(readOnly = true)
    public ContactDTO getById(int id) {
        return toDTO(getActiveEntity(id));
    }

    @Override
    public ContactDTO update(int id, ContactRequestDTO request) {
        Contact contact = getActiveEntity(id);
        mapRequestToEntity(request, contact);
        return toDTO(contactRepository.save(contact));
    }

    @Override
    public void delete(int id) {
        Contact contact = getActiveEntity(id);
        contact.setDeletedDate(new Date());
        contactRepository.save(contact);
    }

    // ──────────────────────────── search ────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<ContactDTO> search(String query, ContactType type, int page, int size, String sortBy, String sortDir) {
        var pageable = PageRequest.of(page, size,
                sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending());
        return contactRepository.searchContacts(query, type, pageable).map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactSummaryDTO> searchForDropdown(String query, ContactType type) {
        var pageable = PageRequest.of(0, 20, Sort.by("companyName").ascending());
        String q = query != null ? query : "";
        return contactRepository.searchForDropdown(q, type, pageable)
                .stream().map(this::toSummaryDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Contact getEntityById(int id) {
        return getActiveEntity(id);
    }

    // ──────────────────────────── helpers ────────────────────────────

    private Contact getActiveEntity(int id) {
        return contactRepository.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Contact not found: " + id));
    }

    private void mapRequestToEntity(ContactRequestDTO req, Contact c) {
        c.setCompanyName(req.getCompanyName());
        c.setTradeName(req.getTradeName());
        c.setContactType(req.getContactType());
        c.setGstNumber(req.getGstNumber());
        c.setGstType(req.getGstType() != null ? req.getGstType() : GstType.REGULAR);
        c.setPanNumber(req.getPanNumber());
        c.setMsmeRegistered(req.isMsmeRegistered());
        c.setMsmeNumber(req.getMsmeNumber());
        c.setDefaultPaymentTerms(req.getDefaultPaymentTerms());
        c.setCreditDays(req.getCreditDays());
        c.setCurrency(req.getCurrency() != null ? req.getCurrency() : "INR");
        c.setWebsite(req.getWebsite());
        c.setPhone(req.getPhone());
        c.setEmail(req.getEmail());
        c.setNotes(req.getNotes());

        // Addresses
        if (req.getAddresses() != null) {
            if (c.getAddresses() != null) c.getAddresses().clear();
            else c.setAddresses(new java.util.ArrayList<>());
            for (ContactAddressDTO dto : req.getAddresses()) {
                ContactAddress addr = new ContactAddress();
                addr.setAddressType(dto.getAddressType() != null ? dto.getAddressType() : AddressType.BILLING);
                addr.setDefault(dto.isDefault());
                addr.setStreet1(dto.getStreet1());
                addr.setStreet2(dto.getStreet2());
                addr.setCity(dto.getCity());
                addr.setState(dto.getState());
                addr.setPinCode(dto.getPinCode());
                addr.setCountry(dto.getCountry() != null ? dto.getCountry() : "India");
                addr.setContact(c);
                c.getAddresses().add(addr);
            }
        }

        // Person details
        if (req.getPersonDetails() != null) {
            if (c.getPersonDetails() != null) c.getPersonDetails().clear();
            else c.setPersonDetails(new java.util.ArrayList<>());
            for (ContactPersonDetailDTO dto : req.getPersonDetails()) {
                ContactPersonDetail person = new ContactPersonDetail();
                person.setPersonName(dto.getPersonName());
                person.setDesignation(dto.getDesignation());
                person.setDepartment(dto.getDepartment());
                person.setEmailId(dto.getEmailId());
                person.setPhoneNumber(dto.getPhoneNumber());
                person.setWhatsappNumber(dto.getWhatsappNumber());
                person.setPrimary(dto.isPrimary());
                person.setContact(c);
                c.getPersonDetails().add(person);
            }
        }
    }

    /**
     * Generates next sequential contactCode.
     * VENDOR → V-001, CUSTOMER → C-001, BOTH → B-001
     */
    private String generateCode(ContactType type) {
        String prefix = switch (type) {
            case VENDOR -> "V";
            case CUSTOMER -> "C";
            case BOTH -> "B";
        };
        var pageable = PageRequest.of(0, 1);
        List<String> last = contactRepository.findLastCodeByPrefix(prefix + "-", pageable);
        int next = 1;
        if (!last.isEmpty()) {
            try {
                next = Integer.parseInt(last.get(0).split("-")[1]) + 1;
            } catch (Exception ignored) {}
        }
        return String.format("%s-%03d", prefix, next);
    }

    // ──────────────────────────── mappers ────────────────────────────

    private ContactDTO toDTO(Contact c) {
        return ContactDTO.builder()
                .id(c.getId())
                .contactCode(c.getContactCode())
                .companyName(c.getCompanyName())
                .tradeName(c.getTradeName())
                .contactType(c.getContactType())
                .gstNumber(c.getGstNumber())
                .gstType(c.getGstType())
                .panNumber(c.getPanNumber())
                .msmeRegistered(c.isMsmeRegistered())
                .msmeNumber(c.getMsmeNumber())
                .defaultPaymentTerms(c.getDefaultPaymentTerms())
                .creditDays(c.getCreditDays())
                .currency(c.getCurrency())
                .website(c.getWebsite())
                .phone(c.getPhone())
                .email(c.getEmail())
                .notes(c.getNotes())
                .addresses(c.getAddresses() == null ? null :
                        c.getAddresses().stream().map(this::toAddressDTO).collect(Collectors.toList()))
                .personDetails(c.getPersonDetails() == null ? null :
                        c.getPersonDetails().stream().map(this::toPersonDTO).collect(Collectors.toList()))
                .creationDate(c.getCreationDate())
                .updatedDate(c.getUpdatedDate())
                .build();
    }

    private ContactSummaryDTO toSummaryDTO(Contact c) {
        String primaryName = null;
        if (c.getPersonDetails() != null) {
            primaryName = c.getPersonDetails().stream()
                    .filter(ContactPersonDetail::isPrimary)
                    .map(ContactPersonDetail::getPersonName)
                    .findFirst()
                    .orElse(c.getPersonDetails().isEmpty() ? null : c.getPersonDetails().get(0).getPersonName());
        }

        String location = null;
        if (c.getAddresses() != null) {
            location = c.getAddresses().stream()
                    .filter(ContactAddress::isDefault)
                    .findFirst()
                    .or(() -> c.getAddresses().stream().findFirst())
                    .map(a -> List.of(a.getCity(), a.getState()).stream()
                            .filter(s -> s != null && !s.isBlank())
                            .collect(Collectors.joining(", ")))
                    .orElse(null);
        }

        return ContactSummaryDTO.builder()
                .id(c.getId())
                .contactCode(c.getContactCode())
                .companyName(c.getCompanyName())
                .tradeName(c.getTradeName())
                .contactType(c.getContactType())
                .gstNumber(c.getGstNumber())
                .gstType(c.getGstType())
                .msmeRegistered(c.isMsmeRegistered())
                .phone(c.getPhone())
                .email(c.getEmail())
                .primaryContactName(primaryName)
                .location(location)
                .build();
    }

    private ContactAddressDTO toAddressDTO(ContactAddress a) {
        return ContactAddressDTO.builder()
                .id(a.getId())
                .addressType(a.getAddressType())
                .isDefault(a.isDefault())
                .street1(a.getStreet1())
                .street2(a.getStreet2())
                .city(a.getCity())
                .state(a.getState())
                .pinCode(a.getPinCode())
                .country(a.getCountry())
                .build();
    }

    private ContactPersonDetailDTO toPersonDTO(ContactPersonDetail p) {
        return ContactPersonDetailDTO.builder()
                .id(p.getId())
                .personName(p.getPersonName())
                .designation(p.getDesignation())
                .department(p.getDepartment())
                .emailId(p.getEmailId())
                .phoneNumber(p.getPhoneNumber())
                .whatsappNumber(p.getWhatsappNumber())
                .isPrimary(p.isPrimary())
                .build();
    }
}
