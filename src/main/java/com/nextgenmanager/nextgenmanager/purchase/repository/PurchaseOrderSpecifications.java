package com.nextgenmanager.nextgenmanager.purchase.repository;

import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderFilter;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Turns a {@link PurchaseOrderFilter} into one ANDed predicate.
 *
 * <p>One specification rather than a branch per filter, so that supplying two filters narrows the
 * result the way a caller expects instead of one of them winning and the other being dropped.
 */
public final class PurchaseOrderSpecifications {

    private PurchaseOrderSpecifications() {}

    public static Specification<PurchaseOrder> from(PurchaseOrderFilter f) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();

            // Soft deletes are never a caller's concern -- they are excluded unconditionally.
            where.add(cb.isNull(root.get("deletedDate")));

            // Free-text box: PO number OR reference OR vendor name. ORed among themselves, then
            // ANDed with everything else, so a search inside a status filter stays inside it.
            if (f.getQuery() != null) {
                String q = "%" + f.getQuery().toLowerCase() + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("purchaseOrderNumber")), q),
                        cb.like(cb.lower(root.get("reference")), q),
                        cb.like(cb.lower(root.join("vendor", jakarta.persistence.criteria.JoinType.LEFT)
                                .get("companyName")), q)));
            }
            if (f.getPoNumber() != null) {
                where.add(cb.equal(cb.lower(root.get("purchaseOrderNumber")),
                        f.getPoNumber().toLowerCase()));
            }
            if (f.getPoNumberContains() != null) {
                where.add(like(cb, root.get("purchaseOrderNumber"), f.getPoNumberContains()));
            }
            if (f.getReference() != null) {
                where.add(cb.equal(root.get("reference"), f.getReference()));
            }
            if (f.getReferenceContains() != null) {
                where.add(like(cb, root.get("reference"), f.getReferenceContains()));
            }
            if (f.getVendorId() != null) {
                where.add(cb.equal(root.get("vendor").get("id"), f.getVendorId()));
            }
            if (f.getVendorName() != null) {
                where.add(like(cb, root.join("vendor").get("companyName"), f.getVendorName()));
            }
            if (f.getStatus() != null) {
                where.add(cb.equal(root.get("status"), f.getStatus()));
            }
            if (f.getApprovalStatus() != null) {
                where.add(cb.equal(root.get("approvalStatus"), f.getApprovalStatus()));
            }
            if (f.getPoType() != null) {
                where.add(cb.equal(root.get("poType"), f.getPoType()));
            }
            if (f.getSalesOrderId() != null) {
                where.add(cb.equal(root.get("salesOrder").get("id"), f.getSalesOrderId()));
            }
            // orderDate is a java.util.Date, so the range has to span the whole closing day --
            // a plain <= toDate would drop every PO raised on toDate itself if a time was stored.
            if (f.getFromDate() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("orderDate"), startOfDay(f.getFromDate())));
            }
            if (f.getToDate() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("orderDate"), endOfDay(f.getToDate())));
            }
            if (f.getMinTotal() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("grandTotal"), f.getMinTotal()));
            }
            if (f.getMaxTotal() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("grandTotal"), f.getMaxTotal()));
            }

            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    private static Predicate like(jakarta.persistence.criteria.CriteriaBuilder cb,
                                  jakarta.persistence.criteria.Expression<String> path,
                                  String value) {
        return cb.like(cb.lower(path), "%" + value.toLowerCase() + "%");
    }

    private static Date startOfDay(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date endOfDay(LocalDate d) {
        return Date.from(d.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant());
    }
}
