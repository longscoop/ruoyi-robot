package com.robot.platform.ai.memory.provider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class RemoteMemoryHttpTest {
    @Test void queryDeadlineAndAuthorizationAreEnforced() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/query",request -> {
            assertEquals("Bearer test",request.getRequestHeaders().getFirst("Authorization"));
            try{Thread.sleep(800);}catch(InterruptedException error){Thread.currentThread().interrupt();}
            try{request.sendResponseHeaders(200,2);request.getResponseBody().write("{}".getBytes());}finally{request.close();}
        });
        server.start();
        try {
            var config=new MemoryProviderProperties.Remote("http://127.0.0.1:"+server.getAddress().getPort());config.setApiKey("test");
            long started=System.nanoTime();
            assertThrows(IllegalStateException.class,()->new RemoteMemoryHttp().request(config,"POST","/query",Map.of(),150,"Bearer"));
            assertTrue(java.time.Duration.ofNanos(System.nanoTime()-started).toMillis()<700);
        }finally{server.stop(0);}
    }
}
