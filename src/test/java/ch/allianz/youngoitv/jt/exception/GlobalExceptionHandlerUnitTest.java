package ch.allianz.youngoitv.jt.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void insufficientFundsMapsTo400WithMessage() {
        var response = handler.handleInsufficientFunds(new InsufficientFundsException("not enough cash"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("not enough cash");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void resourceNotFoundMapsTo404() {
        var response = handler.handleResourceNotFound(new ResourceNotFoundException("portfolio 42 not found"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("portfolio 42 not found");
    }

    @Test
    void unauthorizedAccessMapsTo403() {
        var response = handler.handleUnauthorizedAccess(new UnauthorizedAccessException("not your portfolio"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void unexpectedExceptionMapsTo500WithoutLeakingDetails() {
        var response = handler.handleUnexpected(new RuntimeException("db connection string: secret-stuff"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).doesNotContain("secret-stuff");
        assertThat(response.getBody().message()).doesNotContain("db connection string");
    }
}
