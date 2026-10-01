package com.robot.platform.ai.memory.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Component
public class RemoteMemoryHttp {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    public JsonNode request(MemoryProviderProperties.Remote config,String method,String path,Object body,int timeoutMillis,String scheme) {
        try {
            URI uri=URI.create(config.getBaseUrl().replaceAll("/+$","")+path);
            if (!java.util.Set.of("http","https").contains(uri.getScheme()))throw new IllegalArgumentException("Invalid memory endpoint");
            HttpRequest.Builder request=HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(Math.max(100,timeoutMillis)))
                    .header("Authorization",scheme+" "+config.getApiKey()).header("Content-Type","application/json");
            request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(body)));
            HttpResponse<String> response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Memory HTTP status "+response.statusCode());
            if(response.body().length()>2*1024*1024)throw new IllegalStateException("Memory response too large");
            return response.body().isBlank()?JsonUtils.getObjectMapper().createObjectNode():JsonUtils.parseTree(response.body());
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("Memory request interrupted");}
        catch(java.io.IOException error){throw new IllegalStateException("Memory transport unavailable");}
    }
}
