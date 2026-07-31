package com.salonplatform.reviews.domain.port;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.reviews.dto.ReviewInvitationDto;

/**
 * Outbound port for review invitations. Today implemented in-process; swap for an HTTP
 * adapter when reviews is extracted to its own service.
 */
public interface ReviewInvitationPort {

    ReviewInvitationDto createAfterPayment(Invoice invoice, Branch branch, Customer customer);
}
