package com.workbench.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OrderRepository extends JpaRepository<OrderRecord, String> {
	@Query("select o from OrderRecord o where (:query = '' or "
			+ "lower(o.orderId) like lower(concat('%', :query, '%')) or "
			+ "lower(o.customerId) like lower(concat('%', :query, '%')))")
	List<OrderRecord> search(@Param("query") String query, Pageable pageable);
}