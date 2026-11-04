package com.rappi.purchasing.entity;

/** OPEN/APPROVED = committed (counted as incoming stock). DRAFT/PENDING_APPROVAL are proposals. */
public enum PoStatus { OPEN, DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, CANCELLED }

