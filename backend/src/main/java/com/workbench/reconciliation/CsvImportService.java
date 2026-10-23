package com.workbench.reconciliation;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

@Service
public class CsvImportService {
    record ImportResult(List<OrderRecord> orders, List<PaymentRecord> payments, List<String> errors, int invalidRows) {}

    ImportResult parse(String ordersCsv, String paymentsCsv) {
        List<String> errors = new ArrayList<>();
        List<OrderRecord> orders = parseOrders(ordersCsv, errors);
        List<PaymentRecord> payments = parsePayments(paymentsCsv, errors);
        return new ImportResult(orders, payments, errors.stream().limit(500).toList(), errors.size());
    }

    private List<OrderRecord> parseOrders(String csv, List<String> errors) {
        List<OrderRecord> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try (CSVParser parser = parser(csv, Set.of("order_id", "customer_id", "amount", "currency", "created_at"))) {
            for (CSVRecord row : parser) {
                try {
                    String id = required(row, "order_id");
                    if (!ids.add(id)) throw new IllegalArgumentException("duplicate order_id in file");
                    result.add(new OrderRecord(id, required(row, "customer_id"), amount(row, "amount"),
                            currency(row, "currency"), date(row, "created_at")));
                } catch (RuntimeException exception) {
                    errors.add("Orders row " + row.getRecordNumber() + ": " + exception.getMessage());
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new InvalidRequestException("Orders CSV is invalid: " + exception.getMessage());
        }
        return result;
    }

    private List<PaymentRecord> parsePayments(String csv, List<String> errors) {
        List<PaymentRecord> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try (CSVParser parser = parser(csv, Set.of("payment_id", "order_reference", "amount", "currency", "paid_at"))) {
            for (CSVRecord row : parser) {
                try {
                    String id = required(row, "payment_id");
                    if (!ids.add(id)) throw new IllegalArgumentException("duplicate payment_id in file");
                    result.add(new PaymentRecord(id, required(row, "order_reference"), amount(row, "amount"),
                            currency(row, "currency"), date(row, "paid_at")));
                } catch (RuntimeException exception) {
                    errors.add("Payments row " + row.getRecordNumber() + ": " + exception.getMessage());
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new InvalidRequestException("Payments CSV is invalid: " + exception.getMessage());
        }
        return result;
    }

    private static CSVParser parser(String csv, Set<String> requiredHeaders) throws IOException {
        CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get()
                .parse(new StringReader(csv));
        if (!parser.getHeaderMap().keySet().containsAll(requiredHeaders)) {
            parser.close();
            throw new IllegalArgumentException("required columns: " + String.join(", ", requiredHeaders));
        }
        return parser;
    }

    private static String required(CSVRecord row, String key) {
        String value = row.get(key).trim();
        if (value.isEmpty()) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static BigDecimal amount(CSVRecord row, String key) {
        BigDecimal value = new BigDecimal(required(row, key));
        if (value.scale() > 4 || value.precision() > 19 || value.signum() < 0) {
            throw new IllegalArgumentException(key + " must be a non-negative amount with at most 4 decimals");
        }
        return value;
    }

    private static String currency(CSVRecord row, String key) {
        String value = required(row, key).toUpperCase();
        if (!value.matches("[A-Z]{3}")) throw new IllegalArgumentException(key + " must be a 3-letter code");
        return value;
    }

    private static LocalDateTime date(CSVRecord row, String key) {
        String value = required(row, key);
        try {
            return LocalDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException ignored) {
            return OffsetDateTime.parse(value).toLocalDateTime();
        }
    }
}