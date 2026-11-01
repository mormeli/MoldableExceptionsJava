package ch.moldexj.demos.constraints;

import ch.moldexj.demos.domain.ApiContractMismatchException;
import jakarta.validation.*;

import java.util.Set;

public class ConstraintViolationExceptionDemo {
    public static void main(String[] args) {
        // 1. Validator aufsetzen
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        // 2. Ungültige Daten erstellen (Name zu kurz, Alter unter 18)
        User invalidUser = new User("A", 15);

        Set<ConstraintViolation<User>> violations = validator.validate(invalidUser);

        if (!violations.isEmpty()) {
        }
    }
}
