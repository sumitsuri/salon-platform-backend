package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.BookingLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingLineItemRepository extends JpaRepository<BookingLineItem, UUID> {
    List<BookingLineItem> findByBookingId(UUID bookingId);
    void deleteByBookingId(UUID bookingId);
}
