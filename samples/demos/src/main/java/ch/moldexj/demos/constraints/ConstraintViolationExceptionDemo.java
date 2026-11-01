package ch.moldexj.demos.constraints;

import ch.moldexj.demos.domain.ApiContractMismatchException;
import jakarta.validation.*;

import java.util.Set;

public class ConstraintViolationExceptionDemo {
    public static void main(String[] args) {
        // 1. Setup validator
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        // 2. Create User object with invalid values.
        User invalidUser = new User("A", 15);

        // 3. Validate constraints
        Set<ConstraintViolation<User>> violations = validator.validate(invalidUser);

        // 4. Throw if validation failed
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException("Validation failed!", violations);
        }
    }
}
