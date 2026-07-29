package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.BookingLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingLineItemRepository extends JpaRepository<BookingLineItem, UUID> {
    List<BookingLineItem> findByBookingId(UUID bookingId);
    List<BookingLineItem> findByBookingIdIn(Collection<UUID> bookingIds);
    void deleteByBookingId(UUID bookingId);
}
