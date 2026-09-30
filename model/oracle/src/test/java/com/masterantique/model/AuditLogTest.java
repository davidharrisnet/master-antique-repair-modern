package com.masterantique.model;

import static com.masterantique.model.Fixtures.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The legacy audit codes (ordinals) and the new-row factory. */
class AuditLogTest {

    @Test
    void actionCodesMatchTheLegacyActionType() {
        assertThat(Arrays.stream(AuditAction.values()).map(Enum::name)).containsExactly(
                "CreateUser", "Login", "CreateTicket", "AssignTicket", "CompleteTicket", "AddComment",
                "EditComment", "DeleteComment", "RequestPasswordReset", "ResetPassword", "EditUser", "DeleteUser");
        assertThat(AuditAction.DeleteUser.ordinal()).isEqualTo(11);
    }

    @Test
    void entityKindCodesMatchTheLegacyEntityKind() {
        assertThat(Arrays.stream(AuditEntityKind.values()).map(Enum::name)).containsExactly("User", "Ticket", "Comment");
    }

    @Test
    void recordHoldsIdsAndTimestampOnly() {
        AuditLog row = AuditLog.record(2, AuditAction.AssignTicket, AuditEntityKind.Ticket, 40, T0);
        assertThat(row.getUserId()).isEqualTo(2);
        assertThat(row.getAction()).isEqualTo(AuditAction.AssignTicket);
        assertThat(row.getEntityType()).isEqualTo(AuditEntityKind.Ticket);
        assertThat(row.getEntityId()).isEqualTo(40);
        assertThat(row.getTimestamp()).isEqualTo(T0);
        assertThat(row.getId()).isNull();
        assertThat(Arrays.stream(AuditLog.class.getDeclaredFields()).map(f -> f.getType().getSimpleName()))
                .doesNotContain("String");                        // nowhere to put comment text or descriptions
    }

    @Test
    void recordNeedsEveryValue() {
        assertThatThrownBy(() -> AuditLog.record(null, AuditAction.Login, AuditEntityKind.User, 1, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditLog.record(1, AuditAction.Login, AuditEntityKind.User, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
