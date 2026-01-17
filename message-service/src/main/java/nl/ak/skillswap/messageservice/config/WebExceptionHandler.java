package nl.ak.skillswap.messageservice.config;

import nl.ak.skillswap.messageservice.service.UserIdResolverService;
import nl.ak.skillswap.messageservice.support.ForbiddenException;
import nl.ak.skillswap.messageservice.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class WebExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail notFound(NotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Not Found");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ProblemDetail forbidden(ForbiddenException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setTitle("Forbidden");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(UserIdResolverService.UserResolutionException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail userResolution(UserIdResolverService.UserResolutionException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        pd.setTitle("User Resolution Failed");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail validation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation error");
        pd.setDetail("Request validation failed");
        return pd;
    }
}

