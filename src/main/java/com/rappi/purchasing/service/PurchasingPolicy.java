package com.rappi.purchasing.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Business parameters (see application.yml). */
@Component
@Getter
public class PurchasingPolicy {
    @Value("${policy.review-days:7}") private int reviewDays;
    @Value("${policy.safety-days:2}") private int safetyDays;
    @Value("${policy.max-cover-days:21}") private int maxCoverDays;
    @Value("${policy.approval-value:50000}") private double approvalValue;
}
