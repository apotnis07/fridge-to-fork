package com.example.fridge_to_fork;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import org.json.JSONObject;
import org.json.JSONArray;

@Service
public class RecipeParsingService {

    private final BedrockRuntimeClient client;

    public RecipeParsingService(BedrockRuntimeClient client) {
        this.client = client;
    }

    public String parseRecipeWithLLM(String userDescription) {
        String prompt = """
                Human: Transform the following recipe description into a JSON object strictly following this schema:
                {
                  "name": "string",
                  "rawInput": "string (the full original description)",
                  "ingredients": [
                    {
                      "name": "string",
                      "quantity": "number (double) or null",
                      "unit": "string (e.g., 'g', 'oz', 'tbsp')"
                    }
                  ]
                }
                Return ONLY the valid JSON. No conversational text.
                If a quantity is not explicitly mentioned (e.g., 'a splash of soy sauce' or 'add ginger'), set the 'quantity' to null and the 'unit' to null. Do not invent numbers.
                Description: %s

                Assistant: """
                .formatted(userDescription);

        JSONObject payload = new JSONObject()
                .put("anthropic_version", "bedrock-2023-05-31")
                .put("max_tokens", 500)
                .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "user").put("content", prompt)));

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId("us.anthropic.claude-haiku-4-5-20251001-v1:0")
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(payload.toString()))
                .build();

        InvokeModelResponse response = client.invokeModel(request);

        String responseText = new JSONObject(response.body().asUtf8String())
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text");

        // Clean up in case the model wraps in markdown code blocks despite instructions
        return responseText
                .replace("```json", "")
                .replace("```", "")
                .trim();

    }
}
