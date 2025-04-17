package com.nextgenmanager.nextgenmanager.employee.model;


import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "employeedetails")
public class EmployeeDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(nullable = false)
    private EmployeeType employeeType;

    @OneToMany(mappedBy = "employeeDetails", fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<EmployeeRole> employeeRoles;  // Changed to List and renamed
}