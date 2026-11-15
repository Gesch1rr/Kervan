package com.eyup.kervan.order.repository;

import com.eyup.kervan.order.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    // Returns 1 for the first delivery, 0 for a duplicate. A concurrent duplicate waits on the primary key
    // until the first transaction commits and then also gets 0. (Insert-and-catch would not work: in
    // PostgreSQL a failed statement aborts the whole transaction.)
    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, :now) ON CONFLICT (event_id) DO NOTHING")
    int insertIfAbsent(UUID eventId, Instant now);
}
