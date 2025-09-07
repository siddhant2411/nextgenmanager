package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.sales.dto.SalesOrderCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesOrderDto;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;

import java.util.List;

public interface SalesOrderService {

    public SalesOrderDto createSalesOrder(SalesOrderCreateDto dto);

    public List<SalesOrder> getAllSalesOrders();

    public SalesOrderDto getSalesOrderById(Long id);

    public SalesOrderDto updateSalesOrder(Long id, SalesOrderCreateDto dto);

//    public SalesOrderDto changeStatus(Long id, SalesOrderStatus status);

    public void deleteSalesOrder(Long id);

    public void salesOrderStatusChange(Long id, SalesOrderStatus newStatus, boolean isInventoryAction) throws Exception;
}
