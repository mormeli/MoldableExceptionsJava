package ch.moldexj.demos.constraints;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class User {

    @NotNull(message = "Der Name darf nicht leer sein!")
    @Size(min = 2, message = "Der Name muss mindestens 2 Zeichen lang sein!")
    private String name;

    @Min(value = 18, message = "Du musst mindestens 18 Jahre alt sein!")
    private int age;

    // Konstruktor, Getter und Setter...
    public User(String name, int alter) {
        this.name = name;
        this.age = alter;
    }
}
