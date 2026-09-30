package com.masterantique.service;

import static com.masterantique.service.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.masterantique.api.dto.AuditLogPage;
import com.masterantique.api.dto.AuditLogRow;
import com.masterantique.model.AuditAction;
import com.masterantique.model.AuditEntityKind;
import com.masterantique.model.AuditLog;
import com.masterantique.repo.AuditLogRepository;
import com.masterantique.repo.CommentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests of the audit log (action 14), with mocked repositories (no database). */
class AuditLogServiceTest {

    private TestData d;
    private AuditLogRepository auditLogs;
    private CommentRepository comments;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        d = new TestData();
        auditLogs = mock(AuditLogRepository.class);
        comments = mock(CommentRepository.class);
        service = new AuditLogService(d.actingUser, auditLogs, d.users, comments);
    }

    private static AuditLog row(int id, int actor, AuditAction action, AuditEntityKind kind, int entityId) {
        AuditLog row = AuditLog.record(actor, action, kind, entityId, NOW.minusMinutes(id));
        ReflectionTestUtils.setField(row, "id", id);
        return row;
    }

    @Test
    void action14_rowsWithActorUsernameNamesCodesAndTicketToOpen() {
        List<AuditLog> rows = List.of(
                row(4, 5, AuditAction.AddComment, AuditEntityKind.Comment, 30),
                row(3, 5, AuditAction.AddComment, AuditEntityKind.Comment, 31),
                row(2, 2, AuditAction.AssignTicket, AuditEntityKind.Ticket, 7),
                row(1, 1, AuditAction.CreateUser, AuditEntityKind.User, 5));
        when(auditLogs.findAllByOrderByTimestampDescIdDesc(PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(rows, PageRequest.of(0, 10), 79));
        when(d.users.findAllById(List.of(5, 2, 1))).thenReturn(List.of(d.customer, d.employee, d.manager));
        when(comments.findTicketIdById(30)).thenReturn(Optional.of(7));
        when(comments.findTicketIdById(31)).thenReturn(Optional.empty());   // the comment is gone

        AuditLogPage page = service.page(d.manager.getId(), null, 0, 10);

        assertThat(page.totalRows()).isEqualTo(79);
        assertThat(page.totalPages()).isEqualTo(8);
        assertThat(page.rows()).extracting(AuditLogRow::id).containsExactly(4, 3, 2, 1);
        AuditLogRow first = page.rows().get(0);
        assertThat(first.actorUsername()).isEqualTo("customer1");
        assertThat(first.action()).isEqualTo("AddComment");
        assertThat(first.actionCode()).isEqualTo(5);
        assertThat(first.entityType()).isEqualTo("Comment");
        assertThat(first.entityTypeCode()).isEqualTo(2);
        assertThat(page.rows()).extracting(AuditLogRow::ticketId).containsExactly(7, null, 7, null);
        assertThat(page.rows().get(2).actionCode()).isEqualTo(3);
        assertThat(page.rows().get(3).actorUsername()).isEqualTo("manager");
    }

    @Test
    void action14_filterByEntityIdOfAnyTypeWithPageSize20() {
        when(auditLogs.findByEntityIdOrderByTimestampDescIdDesc(7, PageRequest.of(1, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 20), 0));

        AuditLogPage page = service.page(d.manager.getId(), 7, 1, 20);

        assertThat(page.rows()).isEmpty();
        assertThat(page.size()).isEqualTo(20);
        verify(auditLogs, never()).findAllByOrderByTimestampDescIdDesc(any());
    }

    @Test
    void action14_pageSizeOtherThan10Or20Is400() {
        assertThatThrownBy(() -> service.page(d.manager.getId(), null, 0, 50))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("The page size must be 10 or 20.");
        assertThatThrownBy(() -> service.page(d.manager.getId(), null, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void action14_onlyAManagerMayReadTheAuditLog() {
        assertThatThrownBy(() -> service.page(d.employee.getId(), null, 0, 10)).isInstanceOf(ForbiddenException.class);
        verify(auditLogs, never()).findByEntityIdOrderByTimestampDescIdDesc(anyInt(), any());
    }
}
