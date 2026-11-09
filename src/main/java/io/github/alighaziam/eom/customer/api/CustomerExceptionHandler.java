package io.github.alighaziam.eom.customer.api;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
@RestControllerAdvice
public class CustomerExceptionHandler {
 @ExceptionHandler(CustomerNotFoundException.class)
 ProblemDetail notFound(CustomerNotFoundException ex) {
  ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,ex.getMessage()); p.setTitle("Customer not found"); return p;
 }
 @ExceptionHandler(CustomerAlreadyExistsException.class)
 ProblemDetail duplicate(CustomerAlreadyExistsException ex) {
  ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,ex.getMessage()); p.setTitle("Customer already exists"); return p;
 }
 @ExceptionHandler(DataIntegrityViolationException.class)
 ProblemDetail integrity(DataIntegrityViolationException ex) {
  ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"The request conflicts with an existing customer record.");
  p.setTitle("Customer data conflict"); return p;
 }
}