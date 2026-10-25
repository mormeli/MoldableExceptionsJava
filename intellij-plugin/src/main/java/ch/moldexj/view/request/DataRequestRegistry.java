package ch.moldexj.view.request;

import ch.moldexj.view.request.psi.exception.PsiExceptionDataRequestDeclarationProcessor;
import ch.moldexj.view.request.psi.exception.PsiExceptionDataRequestProcessor;
import ch.moldexj.view.request.psi.frames.PsiStackFrameDataRequestDeclarationProcessor;
import ch.moldexj.view.request.psi.frames.PsiStackFrameDataRequestProcessor;

import java.util.*;

public class DataRequestRegistry {
    private final List<DataRequestDeclarationProcessor> processors;
    private final Map<Class<?>, DataRequestProcessor> processorMap;


    public DataRequestRegistry() {
        this.processors     = new ArrayList<>();
        this.processorMap   = new HashMap<>();
        register(new PsiExceptionDataRequestDeclarationProcessor(), new PsiExceptionDataRequestProcessor());
        register(new PsiStackFrameDataRequestDeclarationProcessor(), new PsiStackFrameDataRequestProcessor());
    }

    public Optional<DataRequestArguments> createArguments(Object request) {
        for (DataRequestDeclarationProcessor processor : processors) {
            if(processor.accept(request)) {
                return Optional.of(processor.createRequest(request));
            }
        }
        return Optional.empty();
    }

    public void register(DataRequestDeclarationProcessor declarationProcessor, DataRequestProcessor processor) {
        this.processors.add(declarationProcessor);
        this.processorMap.put(declarationProcessor.getClass(), processor);
    }
}
