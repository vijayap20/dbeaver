package org.jkiss.dbeaver.model.claude;
import okhttp3.*;
import java.io.IOException;
import java.util.List;

import com.google.gson.Gson;

public class ClaudeConnect {
	
	String request ;
	String sqlResponse ; 
	
	public ClaudeConnect(String request) { 
		this.request = request ; 
	}
	
	public String parseResponseBody() {
		String responseContent = "";
		OkHttpClient client = new OkHttpClient();
		if (request==null) {
			request = "What is the US currency value for Indian Rupee";
		}

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, "{" +
        	    "  \"query\": \"You are a legal AI assistant.\",\n" +
        	    "  \"config\": {\n" +
        	    "    \"max_tokens_to_sample\": 2000,\n" +
        	    "    \"temperature\": 0\n" +
        	    "  },\n" +
        	    "  \"chat\": false\n" +
        	    "}");
        
        Request request = new Request.Builder()
                .url("https://cert-proxy-api.search.use1.dev-fos.nl.lexis.com/proxy/claude-v2")
                .post(body)
                .addHeader("tenant", "")
                .addHeader("content-type","application/json")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            responseContent = response.body().string();
            ResponseBody respBody = response.body();
            
        }catch(Exception e) {
        	System.out.println(e.getMessage());
        }
		return responseContent ; 
	}
	
	public static void main(String[] args) {
		ClaudeConnect cc = new ClaudeConnect("what is the time in New York now?");
		System.out.println(cc.parseResponseBody());
	}
}