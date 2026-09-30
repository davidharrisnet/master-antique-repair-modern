package com.masterantique.repo;

import com.masterantique.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** The audit trail: read newest first (action 14); rows are only added, by the services. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {

    /** All rows, newest first (timestamp, then id), one page. Pass an unsorted {@code PageRequest.of(page, size)}. */
    Page<AuditLog> findAllByOrderByTimestampDescIdDesc(Pageable pageable);

    /** Rows about one entity id (of any entity type, as the legacy filter), newest first, one page. */
    Page<AuditLog> findByEntityIdOrderByTimestampDescIdDesc(Integer entityId, Pageable pageable);
}
