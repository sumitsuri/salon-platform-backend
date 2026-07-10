package com.salonplatform.dto.enquiry;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class EnquiryListFilter {
    private String name;
    private String society;
    private String email;
    private String mobile;
    private String message;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private int page;
    private int size;
}
