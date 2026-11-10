package com.sayem.incidentcopilot.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Converts expected request and service failures into consistent JSON error
 * responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	/**
	 * Returns the first bean-validation failure as a client-readable bad-request
	 * message.
	 *
	 * @param e validation exception raised while binding a request
	 * @return HTTP 400 response containing the invalid field and validation message
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
		String msg = e.getBindingResult().getFieldErrors().stream().findFirst()
				.map(f -> f.getField() + ": " + f.getDefaultMessage()).orElse("invalid request");
		return ResponseEntity.badRequest().body(Map.of("error", msg));
	}

	/**
	 * Maps invalid request values to HTTP 400.
	 *
	 * @param e exception describing the invalid value
	 * @return HTTP 400 response containing the exception message
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
	}

	/**
	 * Maps unavailable service state to HTTP 503.
	 *
	 * @param e exception describing the unavailable service
	 * @return HTTP 503 response containing the exception message
	 */
	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<Map<String, String>> unavailable(IllegalStateException e) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(Map.of("error", String.valueOf(e.getMessage())));
	}
}
