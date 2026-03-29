package com.nextgenmanager.nextgenmanager.production.specifications;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import org.springframework.data.jpa.domain.Specification;

public class ProductionJobSpecification {

    public static Specification<ProductionJob> hasJobNameLike(String search) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("jobName")), "%" + search + "%");
    }


    public static Specification<ProductionJob> isDeleted() {
        return (root, query, cb) ->
                cb.isNull(root.get("deletedDate"));
    }
}
