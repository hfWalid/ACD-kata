package fr.walid.acd.contribution.adapters.rest;

import fr.walid.acd.contribution.application.exception.DocumentConflictException;
import fr.walid.acd.contribution.application.exception.DocumentNotFoundException;
import fr.walid.acd.contribution.domain.DomainException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

@RestControllerAdvice(basePackageClasses = DocumentController.class)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> invalidRequest(IllegalArgumentException exception, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request values are invalid", request);
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<Object> notFound(DocumentNotFoundException exception, WebRequest request) {
        return problem(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "The document does not exist", request);
    }

    @ExceptionHandler(DocumentConflictException.class)
    ResponseEntity<Object> conflict(DocumentConflictException exception, WebRequest request) {
        return problem(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The document has changed; reload its current version", request);
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> domainRule(DomainException exception, WebRequest request) {
        return problem(HttpStatus.CONFLICT, exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpectedFailure(Exception exception, WebRequest request) {
        logger.error("Unexpected failure while processing a document request", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request failed", request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
                                                             HttpHeaders headers, HttpStatusCode status,
                                                             WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status,
                status.is4xxClientError() ? "The request could not be processed" : "The request failed");
        problem.setProperty("code", status.is5xxServerError() ? "INTERNAL_ERROR"
                : status.value() == 404 ? "RESOURCE_NOT_FOUND" : "INVALID_REQUEST");
        setInstance(problem, request);
        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }

    private ResponseEntity<Object> problem(HttpStatus status, String code, String detail, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        setInstance(problem, request);
        return ResponseEntity.status(status).body(problem);
    }

    private static void setInstance(ProblemDetail problem, WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
        }
    }
}
