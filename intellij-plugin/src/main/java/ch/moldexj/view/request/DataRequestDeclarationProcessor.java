package ch.moldexj.view.request;

import org.jetbrains.annotations.NotNull;

public interface DataRequestDeclarationProcessor<T> {
    boolean accept(Object  declaration);

    DataRequestArguments createRequest(@NotNull T declaration);
}
