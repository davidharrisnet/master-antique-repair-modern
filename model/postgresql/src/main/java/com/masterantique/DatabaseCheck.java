package com.masterantique;

import com.masterantique.model.TicketState;
import com.masterantique.repo.AppUserRepository;
import com.masterantique.repo.AuditLogRepository;
import com.masterantique.repo.CommentRepository;
import com.masterantique.repo.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logs what the service is connected to at start-up, through JDBC and JPA. Getting this far also means Hibernate
 * validated every entity against the live schema (ddl-auto=validate).
 */
@Component
@Profile("!demo")
public class DatabaseCheck implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCheck.class);

    private final JdbcTemplate jdbc;
    private final AppUserRepository users;
    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final AuditLogRepository auditLogs;

    public DatabaseCheck(JdbcTemplate jdbc, AppUserRepository users, TicketRepository tickets,
                         CommentRepository comments, AuditLogRepository auditLogs) {
        this.jdbc = jdbc;
        this.users = users;
        this.tickets = tickets;
        this.comments = comments;
        this.auditLogs = auditLogs;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(String... args) {
        String who = jdbc.queryForObject(
                "select current_user || ' @ ' || current_database() || ', PostgreSQL ' || current_setting('server_version')",
                String.class);
        log.info("Connected: {}", who);
        log.info("users={} tickets={} comments={} audit_logs={}",
                users.count(), tickets.count(), comments.count(), auditLogs.count());
        log.info("customers={} employees={} managers={}, must reset password={}",
                users.countByDiscriminator("Customer"), users.countByDiscriminator("Employee"),
                users.countByDiscriminator("Manager"), users.countByMustResetPasswordTrue());
        log.info("tickets SUBMITTED={} INPROGRESS={} COMPLETED={}",
                tickets.countByState(TicketState.SUBMITTED), tickets.countByState(TicketState.INPROGRESS),
                tickets.countByState(TicketState.COMPLETED));
    }
}
