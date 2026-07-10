package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.MarketingEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface MarketingEnquiryRepository extends JpaRepository<MarketingEnquiry, UUID>, JpaSpecificationExecutor<MarketingEnquiry> {
}
