package com.nextgenmanager.nextgenmanager.items.spec;


import com.nextgenmanager.nextgenmanager.common.dto.FilterCriteria;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class InventoryItemSpecification {

    public static Specification<InventoryItem> buildSpecification(List<FilterCriteria> filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            for (FilterCriteria filter : filters) {
                String field = filter.getField();
                String operator = filter.getOperator();
                String value = filter.getValue();

                Path<?> path = root.get(field);
                Class<?> javaType = path.getJavaType();

                // Handle String fields
                if (javaType == String.class) {
                    switch (operator) {
                        case "contains" ->
                                predicates.add(cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%"));
                        case "=" ->
                                predicates.add(cb.equal(cb.lower(root.get(field)), value.toLowerCase()));
                        default ->
                                throw new IllegalArgumentException("Invalid operator for string field: " + operator);
                    }
                }

                // Handle numeric fields
                else if (Number.class.isAssignableFrom(javaType) || javaType.isPrimitive()) {
                    try {
                        Double numValue = Double.valueOf(value);
                        Expression<Double> numericPath = root.get(field).as(Double.class);

                        switch (operator) {
                            case ">" -> predicates.add(cb.greaterThan(numericPath, numValue));
                            case "<" -> predicates.add(cb.lessThan(numericPath, numValue));
                            case "=" -> predicates.add(cb.equal(numericPath, numValue));
                            case ">=" -> predicates.add(cb.greaterThanOrEqualTo(numericPath, numValue));
                            case "<=" -> predicates.add(cb.lessThanOrEqualTo(numericPath, numValue));
                        }
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Expected numeric value for field: " + field + ", got: " + value);
                    }
                }

                // Handle Enum fields
                else if (javaType.isEnum()) {
                    try {
                        Object enumValue = Enum.valueOf((Class<Enum>) javaType, value.toUpperCase());
                        predicates.add(cb.equal(root.get(field), enumValue));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid enum constant for field: " + field + " → " + value);
                    }
                }

                // Fallback: treat as string comparison
                else {
                    predicates.add(cb.equal(root.get(field).as(String.class), value));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

}