package ch.moldexj.view.request;


public interface DataRequestProcessor {
    Object process(DataRequestArguments request, DataRequestContext context);
}

