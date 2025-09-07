package com.nextgenmanager.nextgenmanager.contact.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "contact_address")
public class ContactAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    private String street1;

    private String street2;

    private String state;

    private String country;

    private String pinCode;

    @ManyToOne
    @JoinColumn(name = "contact_id", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private Contact contact;


    @Override
    public String toString() {
        return Stream.of(street1, street2, state, country)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    // or, if you want a separate method:
    public String toFormattedString() {
        return Stream.of(street1, street2, state, country)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }
}
