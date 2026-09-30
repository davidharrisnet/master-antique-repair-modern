package com.masterantique.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The OpenAPI document behind Swagger UI: title, the acting-user stand-in and the error format. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI masterAntiqueOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MasterAntiqueRepair API (Oracle)")
                .version("0.0.1")
                .description("""
                        The actions of the legacy MasterAntiqueRepair application, numbered as in the controller \
                        contract (docs/phase2/controller/CONTROLLER.md); every summary starts with its action number. \
                        Sign-up, login, password reset and log off (actions 1-5) belong to the security component and \
                        have no endpoint here.

                        **No authentication yet.** Every request names its user in the header `X-Acting-User-Id`: \
                        an active user (else 400) whose role in user_roles allows the action (else 403). The server \
                        listens on 127.0.0.1 only; this is a local stand-in, not deployable.

                        **Errors** are RFC 9457 ProblemDetail JSON: 400 invalid input, 403 wrong role, 404 unknown or \
                        not yours, 409 wrong state or duplicate active username."""));
    }

    /** Describes the acting-user header wherever an operation declares it. */
    @Bean
    public OperationCustomizer actingUserHeaderDescription() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() != null) {
                operation.getParameters().stream()
                        .filter(p -> "header".equals(p.getIn()) && ActingUserHeader.NAME.equals(p.getName()))
                        .forEach(p -> p.description(ActingUserHeader.DESCRIPTION));
            }
            return operation;
        };
    }
}
