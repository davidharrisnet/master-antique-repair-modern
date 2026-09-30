package com.masterantique.api.dto;

import java.util.List;

/** Action 25: one active employee's completions per day of the window (zero-filled) and their total. */
public record EmployeeSeries(Integer employeeId, String username, List<Integer> completedPerDay, int total) {
}
