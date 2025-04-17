package com.nextgenmanager.nextgenmanager.production.model;


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workOrderProductionTemplateDocument")
public class WorkOrderProductionTemplateDocument {


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workOrderProductionTemplate_id", referencedColumnName = "id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private WorkOrderProductionTemplate workOrderProductionTemplate;


    private String fileName;
    private String fileType;
    private String filePath;

    @Temporal(TemporalType.TIMESTAMP)
    private Date uploadedDate = new Date();
}
