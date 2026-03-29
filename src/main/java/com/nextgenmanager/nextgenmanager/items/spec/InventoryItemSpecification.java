package com.nextgenmanager.nextgenmanager.items.spec;


import com.nextgenmanager.nextgenmanager.common.dto.FilterCriteria;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InventoryItemSpecification {

    public static Specification<InventoryItem> buildSpecification(List<FilterCriteria> filters, Map<String, String> JOIN_FIELD_MAP) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            for (FilterCriteria filter : filters) {
                String field = filter.getField();
                String operator = filter.getOperator();
                String value = filter.getValue();

                Path<?> path;

                if (JOIN_FIELD_MAP.containsKey(field)) {
                    String[] joinParts = JOIN_FIELD_MAP.get(field).split("\\.");
                    Join<Object, Object> join = root.join(joinParts[0], JoinType.LEFT);
                    path = join.get(joinParts[1]);
                } else {
                    path = root.get(field);
                }

                Class<?> javaType = path.getJavaType();


                if (value == null || value.equalsIgnoreCase("null")) {
                    if ("!=".equals(operator)) {
                        predicates.add(cb.isNotNull(path));
                    } else {
                        predicates.add(cb.isNull(path));
                    }
                    continue;
                }

                if (javaType == String.class) {
                    String lowerValue = value.toLowerCase();
                    Expression<String> stringPath = cb.lower(path.as(String.class));

                    switch (operator) {
                        case "contains" -> predicates.add(cb.like(stringPath, "%" + lowerValue + "%"));
                        case "=" -> predicates.add(cb.equal(stringPath, lowerValue));
                        default -> throw new IllegalArgumentException("Invalid operator for string field: " + operator);
                    }
                }

                else if (Number.class.isAssignableFrom(javaType) || javaType.isPrimitive()) {
                    try {
                        Double numValue = Double.valueOf(value);
                        Expression<Double> numericPath = path.as(Double.class);

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


                else if (javaType.isEnum()) {
                    try {
                        Object enumValue = Enum.valueOf((Class<Enum>) javaType, value.toUpperCase());
                        predicates.add(cb.equal(path, enumValue));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid enum constant for field: " + field + " → " + value);
                    }
                }

                else if (javaType == java.util.Date.class || javaType == java.sql.Timestamp.class) {
                    try {
                        java.sql.Timestamp dateValue = java.sql.Timestamp.valueOf(value);
                        Expression<java.util.Date> datePath = path.as(java.util.Date.class);

                        switch (operator) {
                            case "=" -> predicates.add(cb.equal(datePath, dateValue));
                            case ">" -> predicates.add(cb.greaterThan(datePath, dateValue));
                            case "<" -> predicates.add(cb.lessThan(datePath, dateValue));
                            case ">=" -> predicates.add(cb.greaterThanOrEqualTo(datePath, dateValue));
                            case "<=" -> predicates.add(cb.lessThanOrEqualTo(datePath, dateValue));
                        }
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid date format for field: " + field + " → " + value);
                    }
                }

                else {
                    predicates.add(cb.equal(path, value));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

    }

}