package com.masterantique.api.dto;

import java.util.List;

/** Action 25: the comments of the window. */
public record CommentMetrics(int total, double averagePerDay, int byEmployees, int byCustomers,
                             List<NameCount> perEmployee, List<NameCount> perCustomer) {
}
