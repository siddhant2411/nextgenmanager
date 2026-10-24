package com.nextgenmanager.nextgenmanager.marketing.quotation.service;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactPersonDetail;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Who the quotation PDF prints under Kind Attention. */
class QuotationKindAttentionTest {

    private static ContactPersonDetail person(String name, boolean primary) {
        ContactPersonDetail p = new ContactPersonDetail();
        p.setPersonName(name);
        p.setPrimary(primary);
        return p;
    }

    private static Contact customerWith(ContactPersonDetail... people) {
        Contact c = new Contact();
        c.setPersonDetails(List.of(people));
        return c;
    }

    @Test
    void thePersonNamedOnTheEnquiryComesFirst() {
        Enquiry enquiry = new Enquiry();
        enquiry.setContactPersonName("Ravi Patel");
        enquiry.setContactPersonPhone("9876543210");
        enquiry.setContactPersonEmail("ravi@example.com");

        ContactPersonDetail p = QuotationServiceImp.kindAttention(enquiry, customerWith(person("Someone Else", true)));

        assertEquals("Ravi Patel", p.getPersonName());
        assertEquals("9876543210", p.getPhoneNumber());
        assertEquals("ravi@example.com", p.getEmailId());
    }

    @Test
    void elseTheCustomersPrimaryPerson() {
        ContactPersonDetail p = QuotationServiceImp.kindAttention(new Enquiry(),
                customerWith(person("First", false), person("Primary", true)));
        assertEquals("Primary", p.getPersonName());
    }

    @Test
    void elseTheirFirstNamedPerson() {
        ContactPersonDetail p = QuotationServiceImp.kindAttention(new Enquiry(),
                customerWith(person(" ", false), person("Named", false)));
        assertEquals("Named", p.getPersonName());
    }

    @Test
    void nobodyToName() {
        assertNull(QuotationServiceImp.kindAttention(null, null));
        assertNull(QuotationServiceImp.kindAttention(new Enquiry(), new Contact()));
    }
}
