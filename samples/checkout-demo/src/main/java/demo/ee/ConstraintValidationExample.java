package demo.ee;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class ConstraintValidationExample {
    public static void main(String[] args) {
        // 1. Validator aufsetzen
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        // 2. Ungültige Daten erstellen (Name zu kurz, Alter unter 18)
        User falscherBenutzer = new User("A", 15);

        // 3. Validieren
        var violations = validator.validate(falscherBenutzer);

        // 4. Wenn Fehler gefunden wurden, Exception werfen
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException("Validierung fehlgeschlagen!", violations);
        }
    }
}
