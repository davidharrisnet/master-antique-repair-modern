package com.masterantique.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.masterantique.service.CommentService;
import com.masterantique.service.ForbiddenException;
import com.masterantique.service.NotFoundException;
import com.masterantique.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The web layer's error mapping (ApiExceptionHandler -> ProblemDetail), on the ticket endpoints with the services
 * mocked: no database, no service logic.
 */
@WebMvcTest(TicketController.class)
class ApiExceptionHandlerTest {

    private static final String H = ActingUserHeader.NAME;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TicketService tickets;

    @MockitoBean
    private CommentService comments;

    @Test
    void action8_blankDescriptionIs400ProblemDetailBeforeTheService() throws Exception {
        mvc.perform(post("/api/tickets").header(H, 5).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("description: Description is required."));
        verifyNoInteractions(tickets);
    }

    @Test
    void action8_missingActingUserHeaderIs400() throws Exception {
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void action8_serviceValidationIs400() throws Exception {
        when(tickets.submit(eq(5), any())).thenThrow(new IllegalArgumentException("Description is too long."));
        mvc.perform(post("/api/tickets").header(H, 5).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Description is too long."));
    }

    @Test
    void action11_takenTicketIs409() throws Exception {
        when(tickets.take(2, 7)).thenThrow(new IllegalStateException("This ticket has already been taken."));
        mvc.perform(post("/api/tickets/7/take").header(H, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("This ticket has already been taken."));
    }

    @Test
    void action12_notMineIs404AndBodyIsOptional() throws Exception {
        when(tickets.complete(2, 7, null)).thenThrow(new NotFoundException("Ticket not found."));
        mvc.perform(post("/api/tickets/7/complete").header(H, 2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void action26_wrongRoleIs403() throws Exception {
        when(tickets.allTickets(5)).thenThrow(new ForbiddenException("This action needs the Manager role."));
        mvc.perform(get("/api/tickets").header(H, 5))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This action needs the Manager role."));
    }

    @Test
    void action26_nonNumericIdIs400() throws Exception {
        mvc.perform(get("/api/tickets/abc").header(H, 1)).andExpect(status().isBadRequest());
    }

    @Test
    void action7_duplicateKeyFromTheDatabaseIs409() throws Exception {
        when(comments.add(eq(5), eq(7), any())).thenThrow(new DataIntegrityViolationException("ORA-00001"));
        mvc.perform(post("/api/tickets/7/comments").header(H, 5).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isConflict());
    }
}
