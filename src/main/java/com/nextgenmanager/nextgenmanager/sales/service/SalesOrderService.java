package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.sales.dto.*;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface SalesOrderService {

    SalesOrderDto createSalesOrder(SalesOrderCreateDto dto);

    List<SalesOrder> getAllSalesOrders();

    SalesOrderDto getSalesOrderById(Long id);

    SalesOrderDto updateSalesOrder(Long id, SalesOrderCreateDto dto);

    void deleteSalesOrder(Long id);

    void salesOrderStatusChange(Long id, SalesOrderStatus newStatus, boolean isInventoryAction);

    Page<SalesOrderDisplayDto> getSalesOrderDisplayList(
            int page, int size, String sortBy, String sortDir,
            String orderNumber, LocalDate orderDate, String customerName,
            String poNumber, BigDecimal netAmount, SalesOrderStatus status);

    SalesOrderDto updateStatus(Long id, SalesOrderStatus status);

    // — Approval workflow —
    SalesOrderDto submit(Long id);

    SalesOrderDto approve(Long id);

    SalesOrderDto reject(Long id, SalesOrderApprovalActionDto dto);

    SalesOrderDto send(Long id, String toEmail);

    SalesOrderDto cancel(Long id, SalesOrderApprovalActionDto dto);

    SalesOrderDto complete(Long id);

    SalesOrderDto recalculate(Long id);

    String nextNumber();

    // — Queries —
    Map<String, Object> getSalesAnalytics();

    List<SalesOrderDisplayDto> getPendingDispatch();

    List<SalesOrderDisplayDto> getOverdue();

    SalesOrderProfitabilityDto getProfitability(Long id);
}
