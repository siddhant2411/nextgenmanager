package com.nextgenmanager.nextgenmanager.common.spec;


import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.common.dto.FilterCriteria;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import java.sql.Timestamp;
import java.util.*;

public class GenericSpecification<T> {

    public static <T> Specification<T> buildSpecification(
            List<FilterCriteria> filters,
            Map<String, String> joinFieldMap
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            for (FilterCriteria filter : filters) {
                String field = filter.getField();
                String operator = filter.getOperator();
                String value = filter.getValue();

                Path<?> path = resolvePath(root, joinFieldMap.getOrDefault(field, field));

                Class<?> javaType = path.getJavaType();

                // Handle nulls
                if (value == null || value.equalsIgnoreCase("null")) {
                    if ("!=".equals(operator)) predicates.add(cb.isNotNull(path));
                    else predicates.add(cb.isNull(path));
                    continue;
                }

                // Handle Strings
                if (javaType == String.class) {
                    String lowerValue = value.toLowerCase();
                    Expression<String> stringPath = cb.lower(path.as(String.class));

                    switch (operator) {
                        case "contains" -> predicates.add(cb.like(stringPath, "%" + lowerValue + "%"));
                        case "=" -> predicates.add(cb.equal(stringPath, lowerValue));
                        case "!=" -> predicates.add(cb.notEqual(stringPath, lowerValue));
                        default -> throw new IllegalArgumentException("Invalid operator for string field: " + operator);
                    }
                }

                // Handle Numbers
                else if (Number.class.isAssignableFrom(javaType) || javaType.isPrimitive()) {
                    Double numValue = Double.valueOf(value);
                    Expression<Double> numericPath = path.as(Double.class);

                    switch (operator) {
                        case ">" -> predicates.add(cb.greaterThan(numericPath, numValue));
                        case "<" -> predicates.add(cb.lessThan(numericPath, numValue));
                        case "=" -> predicates.add(cb.equal(numericPath, numValue));
                        case ">=" -> predicates.add(cb.greaterThanOrEqualTo(numericPath, numValue));
                        case "<=" -> predicates.add(cb.lessThanOrEqualTo(numericPath, numValue));
                    }
                }

                // Handle Enums
                else if (javaType.isEnum()) {
                    Object enumValue = Enum.valueOf((Class<Enum>) javaType, value.toUpperCase());
                    if(operator.equals("="))
                        predicates.add(cb.equal(path, enumValue));
                    else if(operator.equals("!="))
                        predicates.add(cb.notEqual(path, enumValue));
                     else
                        throw new IllegalArgumentException("Invalid operator for enum field: " + operator);
                }

                // Handle Dates
                else if (javaType == java.util.Date.class ||
                        javaType == Timestamp.class ||
                        javaType == java.time.LocalDate.class ||
                        javaType == java.time.LocalDateTime.class) {

                    Timestamp dateValue = parseToTimestamp(value);
                    Expression<java.util.Date> datePath = path.as(java.util.Date.class);

                    switch (operator) {
                        case "=" -> predicates.add(cb.equal(datePath, dateValue));
                        case ">" -> predicates.add(cb.greaterThan(datePath, dateValue));
                        case "<" -> predicates.add(cb.lessThan(datePath, dateValue));
                        case ">=" -> predicates.add(cb.greaterThanOrEqualTo(datePath, dateValue));
                        case "<=" -> predicates.add(cb.lessThanOrEqualTo(datePath, dateValue));
                    }
                }


                else {
                    predicates.add(cb.equal(path, value));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Handles arbitrary nested joins dynamically
    private static <T> Path<?> resolvePath(Root<T> root, String fieldPath) {
        String[] parts = fieldPath.split("\\.");
        From<?, ?> from = root;
        for (int i = 0; i < parts.length - 1; i++) {
            from = from.join(parts[i], JoinType.LEFT);
        }
        return from.get(parts[parts.length - 1]);
    }


    private static Timestamp parseToTimestamp(String value) {
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd-MM-yy"),
                DateTimeFormatter.ISO_DATE_TIME
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter.toString().contains("H")) {
                    LocalDateTime ldt = LocalDateTime.parse(value, formatter);
                    return Timestamp.valueOf(ldt);
                } else {
                    LocalDate ld = LocalDate.parse(value, formatter);
                    return Timestamp.valueOf(ld.atStartOfDay());
                }
            } catch (Exception ignored) {}
        }

        throw new IllegalArgumentException("Invalid date format: " + value);
    }

}
