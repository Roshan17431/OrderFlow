package com.orderflow.operations.controller;

import com.orderflow.operations.dto.PageResponse;
import com.orderflow.operations.dto.ResolutionRequest;
import com.orderflow.operations.service.OperationsQueryService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operations")
@PreAuthorize("hasRole('ADMIN')")
public class OperationsController {
    private final OperationsQueryService queries;
    public OperationsController(OperationsQueryService queries) { this.queries = queries; }

    @GetMapping("/overview") public Map<String, Object> overview() { return queries.overview(); }

    @GetMapping("/reports/daily")
    public PageResponse<Map<String, Object>> reports(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "reportDate,desc") String sort) {
        return queries.reports(from, to, page, size, sort);
    }

    @GetMapping("/reports/daily/{date}")
    public ResponseEntity<Map<String, Object>> report(@PathVariable LocalDate date) {
        return ResponseEntity.ofNullable(queries.report(date));
    }

    @GetMapping("/incidents")
    public PageResponse<Map<String, Object>> incidents(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.incidents(type, severity, resolved, from, to, page, size);
    }

    @PatchMapping("/incidents/{incidentId}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable UUID incidentId, @Valid @RequestBody ResolutionRequest request) {
        return queries.resolve(incidentId, request.resolutionNote())
                ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/jobs") public List<Map<String, Object>> jobs() { return queries.jobs(); }
}
