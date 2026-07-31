package com.salonplatform.reviews.infrastructure;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.reviews.application.ReviewInvitationService;
import com.salonplatform.reviews.domain.port.ReviewInvitationPort;
import com.salonplatform.reviews.dto.ReviewInvitationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalReviewInvitationAdapter implements ReviewInvitationPort {

    private final ReviewInvitationService reviewInvitationService;

    @Override
    public ReviewInvitationDto createAfterPayment(Invoice invoice, Branch branch, Customer customer) {
        return reviewInvitationService.createAfterPayment(invoice, branch, customer);
    }
}
