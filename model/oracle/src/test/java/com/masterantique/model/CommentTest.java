package com.masterantique.model;

import static com.masterantique.model.Fixtures.T0;
import static com.masterantique.model.Fixtures.submitted;
import static com.masterantique.model.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Add-only comments: completed tickets only, the ticket's own people only, the text rules. */
class CommentTest {

    private final AppUser customer = user(5, "customer1", UserKind.Customer);
    private final AppUser otherCustomer = user(6, "customer2", UserKind.Customer);
    private final AppUser employee = user(2, "employee1", UserKind.Employee);
    private final AppUser manager = user(1, "manager", UserKind.Manager);

    private Ticket completed() {
        Ticket t = submitted(40, customer);
        t.take(employee, T0);
        t.complete(employee, T0.plusDays(1));
        return t;
    }

    @Test
    void customerAndAssigneeCommentOnACompletedTicket() {
        Ticket t = completed();
        Comment byCustomer = Comment.add(customer, t, "  Thank you!  ", T0.plusDays(2));
        assertThat(byCustomer.getText()).isEqualTo("Thank you!");
        assertThat(byCustomer.getAuthor()).isSameAs(customer);
        assertThat(byCustomer.getTicket()).isSameAs(t);
        assertThat(byCustomer.getCreatedAt()).isEqualTo(T0.plusDays(2));
        assertThat(Comment.add(employee, t, "Glue cured overnight.", T0.plusDays(2)).getAuthor()).isSameAs(employee);
    }

    @Test
    void notCompletedIsAConflict() {
        Ticket t = submitted(40, customer);
        assertThatThrownBy(() -> Comment.add(customer, t, "Any news?", T0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Comments can only be added to completed tickets.");
        t.take(employee, T0);
        assertThatThrownBy(() -> Comment.add(employee, t, "Started.", T0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void strangersAndManagersCannotComment() {
        Ticket t = completed();
        assertThatThrownBy(() -> Comment.add(otherCustomer, t, "Hi", T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Comment.add(manager, t, "Hi", T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textRulesApply() {
        Ticket t = completed();
        assertThatThrownBy(() -> Comment.add(customer, t, " ", T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Comment is required.");
        assertThatThrownBy(() -> Comment.add(customer, t, "a".repeat(2001), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Comment.add(customer, t, "tab\tok but form feed\f not", T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Comment.validText(" ok \n")).isEqualTo("ok");
    }
}
