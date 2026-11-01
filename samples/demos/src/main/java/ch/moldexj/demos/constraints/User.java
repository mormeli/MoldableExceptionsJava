package ch.moldexj.demos.constraints;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class User {
    @NotNull(message = "The name must not be empty!")
    @Size(min = 2, message = "The name must be at least 2 characters long!")
    private String name;

    @Min(value = 18, message = "You must be at least 18 years old!")
    private int age;

    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }
}
