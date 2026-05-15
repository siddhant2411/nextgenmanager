package com.nextgenmanager.nextgenmanager.sales.repository;

import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SalesOrderSpecification {

    public static Specification<SalesOrder> filterOrders(
            String orderNumber, LocalDate orderDate, String customerName,
            String poNumber, BigDecimal netAmount, SalesOrderStatus status) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (orderNumber != null && !orderNumber.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("orderNumber")), "%" + orderNumber.toLowerCase() + "%"));
            }

            if (orderDate != null) {
                predicates.add(cb.equal(root.get("orderDate"), orderDate));
            }

            if (customerName != null && !customerName.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("customer").get("companyName")), "%" + customerName.toLowerCase() + "%"));
            }

            if (poNumber != null && !poNumber.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("poNumber")), "%" + poNumber.toLowerCase() + "%"));
            }

            if (netAmount != null) {
                predicates.add(cb.equal(root.get("netAmount"), netAmount));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            // Only non-deleted orders
            predicates.add(cb.isNull(root.get("deletedDate")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
