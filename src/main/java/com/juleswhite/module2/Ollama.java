package com.juleswhite.module2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Ollama {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final OkHttpClient httpClient;
	private String model;
	private String baseUrl;

	/**
	 * Class to represent a prompt for the LLM, including messages and optional tools
	 */
	public static class Prompt {
		private List<Message> messages;
		private List<Tool> tools;
		private Map<String, Object> metadata;

		public Prompt(List<Message> messages) {
			this.messages = messages;
			this.tools = new ArrayList<>();
			this.metadata = new HashMap<>();
		}

		public Prompt(List<Message> messages, List<Tool> tools) {
			this.messages = messages;
			this.tools = tools != null ? tools : new ArrayList<>();
			this.metadata = new HashMap<>();
		}

		public Prompt(List<Message> messages, List<Tool> tools, Map<String, Object> metadata) {
			this.messages = messages;
			this.tools = tools != null ? tools : new ArrayList<>();
			this.metadata = metadata != null ? metadata : new HashMap<>();
		}

		public List<Message> getMessages() {
			return messages;
		}

		public List<Tool> getTools() {
			return tools;
		}

		public Map<String, Object> getMetadata() {
			return metadata;
		}

	}

	public Ollama() {
		this.model = "llama3.2";  // Default Llama 3.2 model
		this.baseUrl = "http://localhost:11434";  // Default Ollama URL
		this.httpClient = new OkHttpClient.Builder()
				.connectTimeout(30, TimeUnit.SECONDS)
				.writeTimeout(30, TimeUnit.SECONDS)
				.readTimeout(120, TimeUnit.SECONDS)  // Longer read timeout for LLM responses
				.build();
	}

	public Ollama(String model) {
		this.model = model;
		this.baseUrl = "http://localhost:11434";
		this.httpClient = new OkHttpClient.Builder()
				.connectTimeout(30, TimeUnit.SECONDS)
				.writeTimeout(30, TimeUnit.SECONDS)
				.readTimeout(120, TimeUnit.SECONDS)  // Longer read timeout for LLM responses
				.build();
	}

	public Ollama(String model, String baseUrl) {
		this.model = model;
		this.baseUrl = baseUrl;
		this.httpClient = new OkHttpClient.Builder()
				.connectTimeout(30, TimeUnit.SECONDS)
				.writeTimeout(30, TimeUnit.SECONDS)
				.readTimeout(120, TimeUnit.SECONDS)  // Longer read timeout for LLM responses
				.build();
	}

	/**
	 * Generates an LLM response based on the provided prompt.
	 *
	 * @param prompt A Prompt object containing messages, optional tools, and metadata.
	 * @return The generated response as a String.
	 */
	public String generateResponse(Prompt prompt) {
		try {
			List<Message> messages = prompt.getMessages();
			List<Tool> tools = prompt.getTools();

			// Build request body
			Map<String, Object> requestBody = new HashMap<>();
			requestBody.put("model", this.model);
			requestBody.put("messages", convertMessagesToOllamaFormat(messages));
			requestBody.put("stream", false);  // We want the complete response, not streaming

			// Add options for better control
			Map<String, Object> options = new HashMap<>();
			options.put("temperature", 0.7);
			options.put("top_p", 0.9);
			options.put("max_tokens", 1024);
			requestBody.put("options", options);

			String result = null;

			// Handle cases with and without tools
			if (tools.isEmpty()) {
				// No tools, just get normal completion
				result = makeOllamaRequest(requestBody);
			} else {
				// Add tools to the request
				requestBody.put("tools", convertToolsToOllamaFormat(tools));

				String response = makeOllamaRequest(requestBody);

				// Check if the response contains tool calls
				JsonNode responseJson = objectMapper.readTree(response);
				if (responseJson.has("message") && responseJson.get("message").has("tool_calls")) {
					JsonNode toolCalls = responseJson.get("message").get("tool_calls");
					if (toolCalls.isArray() && toolCalls.size() > 0) {
						JsonNode toolCall = toolCalls.get(0);

						// Format the response as a JSON string
						Map<String, Object> toolResponse = new HashMap<>();
						toolResponse.put("tool", toolCall.get("function").get("name").asText());
						toolResponse.put("args", objectMapper.convertValue(toolCall.get("function").get("arguments"), Map.class));

						result = objectMapper.writeValueAsString(toolResponse);
					} else {
						// Model chose to respond with text instead of using a tool
						result = responseJson.get("message").get("content").asText();
					}
				} else {
					// No tool calls, extract regular content
					result = responseJson.get("message").get("content").asText();
				}
			}

			return result;

		} catch (Exception e) {
			System.err.println("Error generating response: " + e.getMessage());
			e.printStackTrace();

			System.out.println("Prompt details:");
			for (Message message : prompt.getMessages()) {
				System.out.println("Message: " + message.getRole() + " - " + message.getContent());
			}

			if (!prompt.getTools().isEmpty()) {
				System.out.println("Tools:");
				for (Tool tool : prompt.getTools()) {
					System.out.println("Tool: " + tool.getToolName() + " - " + tool.getDescription());
				}
			}

			System.out.println("Model: " + this.model);
			System.out.println("Base URL: " + this.baseUrl);

			throw new RuntimeException("Failed to generate response", e);
		}
	}

	/**
	 * Convenience method to generate a response from just messages
	 */
	public String generateResponse(List<Message> messages) {
		return generateResponse(new Prompt(messages));
	}

	/**
	 * Makes HTTP request to Ollama API
	 */
	private String makeOllamaRequest(Map<String, Object> requestBody) throws IOException {
		String jsonBody = objectMapper.writeValueAsString(requestBody);

		RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
		Request request = new Request.Builder()
				.url(baseUrl + "/api/chat")
				.post(body)
				.addHeader("Content-Type", "application/json")
				.build();

		try (Response response = httpClient.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				throw new IOException("Unexpected response code: " + response.code() +
											  " - " + (response.body() != null ? response.body().string() : ""));
			}

			String responseBody = response.body().string();

			System.out.println("Ollama Response: " + responseBody);

			// Parse the response to extract the content
//			JsonNode responseJson = objectMapper.readTree(responseBody);
//			if (responseJson.has("message") && responseJson.get("message").has("content")) {
//				return responseJson.get("message").get("content").asText();
//			} else {
//				// Return the full response if we can't extract content
//				return responseBody;
//			}
			return responseBody;
		}
	}

	/**
	 * Converts our Message objects to Ollama's format
	 */
	private List<Map<String, Object>> convertMessagesToOllamaFormat(List<Message> messages) {
		List<Map<String, Object>> ollamaMessages = new ArrayList<>();

		for (Message message : messages) {
			Map<String, Object> ollamaMessage = new HashMap<>();
			ollamaMessage.put("role", message.getRole());
			ollamaMessage.put("content", message.getContent());
			ollamaMessages.add(ollamaMessage);
		}

		return ollamaMessages;
	}

	/**
	 * Converts our Tool objects to Ollama's format
	 */
	private List<Map<String, Object>> convertToolsToOllamaFormat(List<Tool> tools) {
		List<Map<String, Object>> ollamaTools = new ArrayList<>();

		for (Tool tool : tools) {
			Map<String, Object> function = new HashMap<>();
			function.put("name", tool.getToolName());
			function.put("description", tool.getDescription());
			function.put("parameters", tool.getParameters());

			Map<String, Object> ollamaTool = new HashMap<>();
			ollamaTool.put("type", "function");
			ollamaTool.put("function", function);

			ollamaTools.add(ollamaTool);
		}

		return ollamaTools;
	}

	/**
	 * Check if Ollama is running and the model is available
	 */
	public boolean isModelAvailable() {
		try {
			Request request = new Request.Builder()
					.url(baseUrl + "/api/tags")
					.get()
					.build();

			try (Response response = httpClient.newCall(request).execute()) {
				if (response.isSuccessful()) {
					String responseBody = response.body().string();
					JsonNode json = objectMapper.readTree(responseBody);
					JsonNode models = json.get("models");

					if (models != null && models.isArray()) {
						for (JsonNode modelNode : models) {
							if (modelNode.get("name").asText().equals(this.model)) {
								return true;
							}
						}
					}
				}
				return false;
			}
		} catch (Exception e) {
			System.err.println("Error checking model availability: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Pull a model if it's not available
	 */
	public boolean pullModel() {
		try {
			Map<String, Object> requestBody = new HashMap<>();
			requestBody.put("name", this.model);

			String jsonBody = objectMapper.writeValueAsString(requestBody);
			RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

			Request request = new Request.Builder()
					.url(baseUrl + "/api/pull")
					.post(body)
					.addHeader("Content-Type", "application/json")
					.build();

			try (Response response = httpClient.newCall(request).execute()) {
				return response.isSuccessful();
			}
		} catch (Exception e) {
			System.err.println("Error pulling model: " + e.getMessage());
			return false;
		}
	}

	// Getters and setters
	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

}
