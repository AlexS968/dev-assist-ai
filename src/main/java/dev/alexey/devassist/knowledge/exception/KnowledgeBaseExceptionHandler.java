package dev.alexey.devassist.knowledge.exception;

import dev.alexey.devassist.knowledge.controller.KnowledgeBaseController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(assignableTypes = KnowledgeBaseController.class)
public class KnowledgeBaseExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(KnowledgeBaseNotFoundException.class)
    public ProblemDetail notFound(KnowledgeBaseNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "KnowledgeBase not found.");
    }

    @ExceptionHandler(KnowledgeBaseKeyAlreadyExistsException.class)
    public ProblemDetail duplicateKey(KnowledgeBaseKeyAlreadyExistsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "KnowledgeBase key already exists.");
    }
}
