package com.nextgenmanager.nextgenmanager.sales.controller;

import com.nextgenmanager.nextgenmanager.sales.dto.SalesOrderCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesOrderDto;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;
import com.nextgenmanager.nextgenmanager.sales.service.InvoicePdfService;
import com.nextgenmanager.nextgenmanager.sales.service.SalesOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales-orders")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_SALES_ADMIN','ROLE_SALES_USER')")
@RequiredArgsConstructor
public class SalesOrderController {

    private static final Logger logger = LoggerFactory.getLogger(SalesOrderController.class);

    @Autowired
    private final SalesOrderService salesOrderService;

    @Autowired
    private final InvoicePdfService invoicePdfService;
    /**
     * Create a new Sales Order in DRAFT status.
     */
    @PostMapping
    public ResponseEntity<SalesOrderDto> createSalesOrder(@Valid @RequestBody SalesOrderCreateDto dto) {
        logger.info("API Request: Create Sales Order");
        SalesOrderDto created = salesOrderService.createSalesOrder(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get all sales orders (basic list)
     */
    @GetMapping
    public ResponseEntity<List<SalesOrder>> getAllSalesOrders() {
        logger.info("API Request: Get All Sales Orders");
        List<SalesOrder> orders = salesOrderService.getAllSalesOrders();
        return ResponseEntity.ok(orders);
    }

    /**
     * Get a single sales order by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<SalesOrderDto> getSalesOrder(@PathVariable Long id) {
        logger.info("API Request: Get Sales Order with id={}", id);
        SalesOrderDto dto = salesOrderService.getSalesOrderById(id);
        return ResponseEntity.ok(dto);
    }


    @PostMapping("/{id}/change-status/")
    public ResponseEntity<?> changeStatus(@PathVariable Long id,@RequestBody SalesOrderStatus salesOrderStatus, @RequestParam(defaultValue = "true") String inventoryAction) throws Exception {
        boolean isInventoryAction = Boolean.parseBoolean(inventoryAction);
        salesOrderService.salesOrderStatusChange(id,salesOrderStatus,isInventoryAction);
        return ResponseEntity.ok("Status Changes Successfully");
    }

    /**
     * Update an existing sales order (if still editable)
     */
    @PutMapping("/{id}")
    public ResponseEntity<SalesOrderDto> updateSalesOrder(@PathVariable Long id,
                                                          @Valid @RequestBody SalesOrderCreateDto dto) {
        logger.info("API Request: Update Sales Order with id={}", id);
        SalesOrderDto updated = salesOrderService.updateSalesOrder(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * Change status of a sales order (e.g., DRAFT -> IN_PROGRESS)
//     */
//    @PatchMapping("/{id}/status")
//    public ResponseEntity<SalesOrderDto> changeStatus(@PathVariable Long id,
//                                                      @RequestParam("status") SalesOrderStatus status) {
//        logger.info("API Request: Change Sales Order status, id={}, newStatus={}", id, status);
//        SalesOrderDto updated = salesOrderService.changeStatus(id, status);
//        return ResponseEntity.ok(updated);
//    }

    /**
     * Soft delete a sales order
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSalesOrder(@PathVariable Long id) {
        logger.info("API Request: Delete Sales Order with id={}", id);
        salesOrderService.deleteSalesOrder(id);
        return ResponseEntity.noContent().build();
    }



    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable Long id) {
        SalesOrderDto order = salesOrderService.getSalesOrderById(id);


        byte[] pdf = invoicePdfService.generateInvoicePdf(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=invoice-" + order.getOrderNumber() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}

