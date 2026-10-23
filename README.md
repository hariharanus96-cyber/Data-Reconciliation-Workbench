# Data Reconciliation Workbench

A local operations workbench for importing order and payment CSVs, finding imperfect matches, investigating exceptions, and recording resolutions.

## Run Locally

Requirements: Docker Desktop with Compose enabled.

```powershell
docker compose up --build
```

Open `http://localhost:5173`. The default amount tolerance is `0.02` in each transaction's currency. Override it before starting with `$env:RECONCILIATION_TOLERANCE = "0.05"`.

The API is available at `http://localhost:8080` when running the backend separately. The Docker web container proxies `/api` to the Spring service. Postgres data is stored in the `postgres_data` volume. `docker compose down` stops services; remove the volume only when intentionally resetting the database.

For frontend development without Docker, run the API on port 8080, then use `npm install` and `npm run dev` from `frontend`. For the API alone, run `mvn spring-boot:run` in `backend`; it uses a local H2 file by default. Java 21 and Maven are required for that route.

## Sample Data

Generate seeded files containing at least 10,000 rows per input:

```powershell
node scripts/generate_sample_data.mjs --records 12000 --seed 20261023 --output sample-data
```

The generator uses only Node's standard library. It includes missing settlements, extra payments, above-tolerance amount differences, minor rounding differences, later settlements, and orphan payment references. IDs and random data are reproducible for a given seed.

## Architecture

- `backend`: Java 21, Spring Boot, Spring Data JPA, PostgreSQL in Compose, H2 for standalone development. CSV parsing and matching use `BigDecimal`; valid uploaded rows are preserved in relational tables.
- `frontend`: React, TypeScript, Vite, and an operations dashboard. Imports and reconciliation run as background jobs so the interface can poll progress rather than wait for 10k-row processing.
- `scripts`: deterministic sample data generator.

Reconciliation matches normalized order references case-insensitively. A manually linked order ID takes precedence over the original payment reference. For multiple payments on one order, the closest amount is selected as primary (earliest paid time, then payment ID break ties); remaining payments are reported as duplicates. An absolute difference at or below tolerance matches, amounts above tolerance are mismatches, and currency differences are mismatches. Payment timestamps may be later than order timestamps; they do not prevent matching. Unreferenced payments are orphans, and orders without payments are missing-payment exceptions.

Reruns rebuild open generated discrepancies by stable category/order/payment signatures. A unique signature constraint prevents duplicate results. Open stale generated issues are removed; resolved issues are preserved. Manual links and resolutions produce audit events with a required reason. Imports skip invalid rows and already-imported IDs without rejecting other valid records.

Summary values are grouped by currency and include loaded, matched, and unmatched transaction totals. Before the first run, matched counts remain zero and the dashboard prompts the operator to reconcile. An order with an amount mismatch is considered unmatched; a duplicate payment does not make its primary order unmatched.

## API

- `POST /api/imports` — multipart fields `orders` and `payments`; returns an asynchronous import job.
- `POST /api/reconciliations` — starts an asynchronous reconciliation job.
- `GET /api/jobs/{id}` — import or reconciliation job status, row errors, and counts.
- `GET /api/summary` — counts, open discrepancy categories, per-currency totals, and tolerance.
- `GET /api/discrepancies?q=&category=&status=&page=&size=` — paginated, searchable discrepancy list.
- `GET /api/orders?q=` — order lookup for manual matching (maximum 100 returned).
- `GET /api/payments/{paymentId}` — inspect one payment record.
- `POST /api/payments/{paymentId}/link` — JSON `{ "orderId": "ORD-1", "reason": "..." }`.
- `POST /api/discrepancies/{id}/resolve` — JSON `{ "reason": "..." }`.
- `GET /api/audit` — manual link and resolution history.

Errors use JSON with `status`, `message`, and `timestamp`. Resolution and linking reject blank reasons. CSV headers are case-sensitive and must include `order_id,customer_id,amount,currency,created_at` or `payment_id,order_reference,amount,currency,paid_at`. Timestamps accept ISO local datetime or ISO datetime with offset. Monetary amounts are non-negative and support up to four decimal places.

## Demo Walkthrough

1. Generate the sample data and upload both files; show the asynchronous job counts and row-level validation errors.
2. Run reconciliation and point out the category counts and per-currency totals.
3. Search for an orphan payment, open its details, link it to a valid order with a reason, and show the refreshed reconciliation state.
4. Resolve an amount mismatch with a reason, then open the audit history.
5. Run reconciliation again and show that the result set has no duplicate exceptions and the resolution remains recorded.

This repository includes the walkthrough, not a screen-recording artifact; capture the 3–5 minute recording in the interview's preferred format.

## AI Usage Notes

AI assistance was used to scaffold the application, implement the first pass of the reconciliation workflow and dashboard, and draft tests and documentation. The implementation choices to discuss independently are the primary-payment selection rule, tolerance boundary (`<=`), currency-separated totals, stable discrepancy identity for reruns, and preservation of audit-backed manual decisions. Validate those choices against the business's actual settlement rules before production use.

## Trade-offs and Limitations

- Jobs are held in process memory; a service restart loses job status, though imported data and discrepancy history remain in the database.
- Reconciliation currently loads source rows into application memory. It is intended for the stated 10k-record scale, not unbounded multi-million-row feeds.
- Payment references are matched as normalized exact identifiers; fuzzy matching is not implemented because it can silently link unrelated transactions. Manual linking is available for exceptions.
- Duplicate detection treats extra payments per order as duplicates; split or partial settlements need a separate business rule.
- Amount tolerance is one absolute value applied across currencies, not a currency-specific configuration.
- Authentication, user identity, file archival, durable job storage, schema migrations, and production deployment are out of scope.
- Audit events record manual actions but do not store an authenticated actor.

## Tests

Backend tests cover exact tolerance boundaries, above-tolerance differences, late payments, duplicates, missing/orphan records, idempotent reruns, and preservation of resolved discrepancies. Run with `mvn test` in `backend`, or let the Docker build resolve dependencies. Frontend production compilation runs with `npm run build` in `frontend`.