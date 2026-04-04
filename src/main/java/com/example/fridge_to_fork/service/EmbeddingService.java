package com.example.fridge_to_fork.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Service
public class EmbeddingService {
    
    private final BedrockRuntimeClient client;

    public EmbeddingService(BedrockRuntimeClient client){
        this.client = client;
    }

    public String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1)
                sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public float[] getEmbedding(String text){
        
        JSONObject payload = new JSONObject()
                .put("inputText", text);

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId("amazon.titan-embed-text-v2:0")
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(payload.toString()))
                .build();

        try {
            InvokeModelResponse response = client.invokeModel(request);
            JSONObject responseBody = new JSONObject(response.body().asUtf8String());
            
            // Extract the embedding array
            JSONArray embeddingArray = responseBody.getJSONArray("embedding");
            float[] vector = new float[embeddingArray.length()];
            
            for (int i = 0; i < embeddingArray.length(); i++) {
                vector[i] = embeddingArray.getFloat(i);
            }
            return vector;

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate embedding via Bedrock", e);
        }
    }
}
