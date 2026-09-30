package com.masterantique.api.dto;

import java.time.LocalDate;

/** Action 25: a day and a count. */
public record DayCount(LocalDate day, int count) {
}
