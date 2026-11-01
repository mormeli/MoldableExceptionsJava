package ch.moldexj.demos.mockito;

import java.net.Socket;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class MockitoDemo {
    public static void main(String[] args) {
        Socket socket = mock(Socket.class);
        socket.setPerformancePreferences(1234, 10, 5);
        verify(socket).setPerformancePreferences(1234, eq(10), 5);
    }
}

