package ch.moldexj.view;

import java.util.List;

public record View(String qualifiedClassName, String methodName, List<Parameter> parameters) {
}

