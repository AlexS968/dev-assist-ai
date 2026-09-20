package dev.alexey.devassist.incident.exception;

import dev.alexey.devassist.incident.controller.IncidentController;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(assignableTypes = IncidentController.class)
public class IncidentExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(IncidentNotFoundException.class)
	public ProblemDetail handleIncidentNotFound(IncidentNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}
}
