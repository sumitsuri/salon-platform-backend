package com.salonplatform.sales.application;

import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.sales.domain.enums.LeadSource;
import com.salonplatform.sales.domain.enums.LeadStage;
import com.salonplatform.sales.domain.enums.LeadType;
import com.salonplatform.sales.domain.repository.SalesLeadRepository;
import com.salonplatform.sales.dto.CreatePublicSalesLeadRequest;
import com.salonplatform.sales.dto.CreateSalesLeadRequest;
import com.salonplatform.sales.dto.SalesLeadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicSalesLeadService {

    private final SalesLeadService salesLeadService;
    private final UserRepository userRepository;
    private final SalesLeadRepository leadRepository;

    @Transactional
    public SalesLeadResponse createFromMarketing(CreatePublicSalesLeadRequest request) {
        CreateSalesLeadRequest internal = new CreateSalesLeadRequest();
        internal.setBusinessName(request.getName());
        internal.setContactName(request.getName());
        internal.setEmail(request.getEmail());
        internal.setPhone(request.getPhone());
        internal.setLeadType(parseLeadType(request.getBranches()));
        internal.setSource(LeadSource.MARKETING_WEB);
        internal.setExpectedBranches(parseBranchCount(request.getBranches()));
        internal.setUseCase("Demo request from marketing site");
        internal.setNotes(buildNotes(request));
        internal.setAssignedRepId(assignRoundRobin());
        return salesLeadService.createPublic(internal);
    }

    private UUID assignRoundRobin() {
        List<User> reps = userRepository.findByRoleAndActiveTrue(UserRole.SALES_EXECUTIVE);
        if (reps.isEmpty()) {
            return null;
        }
        return reps.stream()
                .min(Comparator.comparingLong(r ->
                        leadRepository.countByAssignedRepIdAndStageNot(r.getId(), LeadStage.LOST)))
                .map(User::getId)
                .orElse(reps.get(0).getId());
    }

    private LeadType parseLeadType(String branches) {
        if (branches == null) return LeadType.SHOP;
        if (branches.contains("16") || branches.contains("6-15")) return LeadType.BRAND;
        return LeadType.SHOP;
    }

    private int parseBranchCount(String branches) {
        if (branches == null) return 1;
        return switch (branches) {
            case "1-2" -> 1;
            case "3-5" -> 4;
            case "6-15" -> 8;
            case "16+" -> 16;
            default -> 1;
        };
    }

    private String buildNotes(CreatePublicSalesLeadRequest request) {
        StringBuilder sb = new StringBuilder("Branches: ");
        sb.append(request.getBranches() != null ? request.getBranches() : "unknown");
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            sb.append("\n").append(request.getNotes());
        }
        return sb.toString();
    }
}
