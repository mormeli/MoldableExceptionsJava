package ch.moldexj.view.request;

public class DataRequestArguments {
    private final Class<?> processor;


    public DataRequestArguments(Class<?> processor) {
        this.processor          = processor;
    }


    public Class<?> getProcessor() {
        return processor;
    }
}

