package com.eventledger.api.repository;

import com.eventledger.api.domain.Event;
import com.eventledger.api.domain.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, String> {

    List<Event> findByAccountIdOrderByEventTimestampAsc(String accountId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Event e WHERE e.accountId = :accountId AND e.type = :type")
    BigDecimal sumAmountByAccountIdAndType(@Param("accountId") String accountId, @Param("type") EventType type);

    long countByAccountId(String accountId);
}
