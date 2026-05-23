package com.eventledger.api.repository;

import com.eventledger.api.domain.Event;
import com.eventledger.api.domain.EventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, String> {

    List<Event> findByAccountIdOrderByEventTimestampAsc(String accountId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Event e WHERE e.accountId = :accountId AND e.type = :type")
    BigDecimal sumAmountByAccountIdAndType(@Param("accountId") String accountId, @Param("type") EventType type);

    long countByAccountId(String accountId);

    /** Returns the currency of the chronologically earliest event for an account. */
    @Query("SELECT e.currency FROM Event e WHERE e.accountId = :accountId ORDER BY e.eventTimestamp ASC")
    List<String> findCurrencyByAccountIdChronological(@Param("accountId") String accountId, Pageable pageable);
}
