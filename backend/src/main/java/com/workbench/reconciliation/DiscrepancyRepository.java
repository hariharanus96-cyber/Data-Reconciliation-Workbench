package com.workbench.reconciliation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscrepancyRepository extends JpaRepository<Discrepancy, Long> {
    Discrepancy findBySignature(String signature);

    @Query("select d from Discrepancy d left join d.orderRecord o left join d.paymentRecord p "
            + "where (:category is null or d.category = :category) "
            + "and (:status is null or d.status = :status) and (:query = '' or "
            + "lower(coalesce(o.orderId, '')) like lower(concat('%', :query, '%')) or "
            + "lower(coalesce(p.paymentId, '')) like lower(concat('%', :query, '%')) or "
            + "lower(coalesce(p.orderReference, '')) like lower(concat('%', :query, '%')))")
    Page<Discrepancy> search(@Param("category") String category, @Param("status") String status,
                             @Param("query") String query, Pageable pageable);
}