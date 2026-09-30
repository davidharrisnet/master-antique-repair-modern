package com.masterantique.model;

/**
 * audit_logs.action holds the position of these values, the legacy {@code AuditLog.ActionType} codes 0-11.
 * Never reorder or insert: the migrated rows and new ones must keep the same meaning.
 */
public enum AuditAction {
    CreateUser,            // 0
    Login,                 // 1
    CreateTicket,          // 2
    AssignTicket,          // 3
    CompleteTicket,        // 4
    AddComment,            // 5
    EditComment,           // 6  unused (comments are add-only), kept for the code numbering
    DeleteComment,         // 7  unused, kept for the code numbering
    RequestPasswordReset,  // 8
    ResetPassword,         // 9
    EditUser,              // 10
    DeleteUser             // 11
}
