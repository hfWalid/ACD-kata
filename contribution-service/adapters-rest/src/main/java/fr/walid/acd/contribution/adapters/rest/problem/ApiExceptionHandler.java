package fr.walid.acd.contribution.adapters.rest.problem;

import fr.walid.acd.contribution.adapters.rest.document.DocumentController;
import fr.walid.acd.contribution.application.exception.DocumentConflictException;
import fr.walid.acd.contribution.application.exception.DocumentNotFoundException;
import fr.walid.acd.contribution.application.exception.DocumentVersionNotFoundException;
import fr.walid.acd.contribution.domain.DomainException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice(basePackageClasses = DocumentController.class)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> invalidRequest(IllegalArgumentException exception, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request values are invalid", request);
    }

    /**
     * Bean Validation failures carry one entry per rejected field, so a client can point at the
     * offending input instead of guessing which value the server refused. Fields are ordered by
     * name, then message, so the same payload always produces the same body.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return validationProblem(status, exception.getBindingResult().getFieldErrors().stream()
                .map(error -> fieldError(error.getField(), error.getDefaultMessage()))
                .toList(), request);
    }

    /**
     * With parameter constraints on the endpoint (the `X-User` header, for instance), Spring MVC
     * reports body validation through method validation instead, so the same field list has to be
     * extracted from the method results. Header and body constraints share the response shape.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
                                                                           HttpHeaders headers, HttpStatusCode status,
                                                                           WebRequest request) {
        var errors = new ArrayList<Map<String, String>>();
        for (var result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors()
                        .forEach(error -> errors.add(fieldError(error.getField(), error.getDefaultMessage())));
            } else {
                var field = Objects.requireNonNullElse(result.getMethodParameter().getParameterName(), "request");
                result.getResolvableErrors()
                        .forEach(error -> errors.add(fieldError(field, error.getDefaultMessage())));
            }
        }
        return validationProblem(status, errors, request);
    }

    private ResponseEntity<Object> validationProblem(HttpStatusCode status, List<Map<String, String>> errors,
                                                     WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, "Request values are invalid");
        problem.setProperty("code", "INVALID_REQUEST");
        problem.setProperty("errors", errors.stream()
                .sorted(Comparator.comparing((Map<String, String> error) -> error.get("field"))
                        .thenComparing(error -> error.get("message")))
                .toList());
        setInstance(problem, request);
        return ResponseEntity.status(status).body(problem);
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", Objects.requireNonNullElse(message, "invalid"));
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<Object> notFound(DocumentNotFoundException exception, WebRequest request) {
        return problem(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "The document does not exist", request);
    }

    @ExceptionHandler(DocumentVersionNotFoundException.class)
    ResponseEntity<Object> versionNotFound(DocumentVersionNotFoundException exception, WebRequest request) {
        return problem(HttpStatus.NOT_FOUND, "DOCUMENT_VERSION_NOT_FOUND",
                "No values are stored for this document version", request);
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
