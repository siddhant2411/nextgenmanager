package com.nextgenmanager.nextgenmanager.bom.spec;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
public class BomSpecifications {

    public static Specification<Bom> hasBomNameLike(String search) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("bomName")), "%" + search + "%");
    }

    public static Specification<Bom> hasParentItemNameLike(String search) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.join("parentInventoryItem").get("name")), "%" + search + "%");
    }

    public static Specification<Bom> hasParentItemCodeLike(String search) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.join("parentInventoryItem").get("itemCode")), "%" + search + "%");
    }

    public static Specification<Bom> hasIsActive(Boolean active) {
        return (root, query, cb) -> cb.equal(root.get("isActive"), active);
    }

    public static Specification<Bom> hasIsActiveVersion(Boolean v) {
        return (root, query, cb) -> cb.equal(root.get("isActiveVersion"), v);
    }

    public static Specification<Bom> isLatestVersion() {
        return (root, query, cb) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<Bom> subRoot = sub.from(Bom.class);

            sub.select(cb.max(subRoot.get("versionNumber")))
                    .where(cb.equal(subRoot.get("parentInventoryItem"),
                            root.get("parentInventoryItem")));

            return cb.equal(root.get("versionNumber"), sub);
        };
    }

    public static Specification<Bom> searchAcrossFields(String search) {
        return (root, query, cb) -> {
            Join<Bom, InventoryItem> item = root.join("parentInventoryItem");

            String like = "%" + search.toLowerCase() + "%";

            return cb.or(
                    cb.like(cb.lower(root.get("bomName")), like),
                    cb.like(cb.lower(item.get("name")), like),
                    cb.like(cb.lower(item.get("itemCode")), like)
            );
        };
    }

}


