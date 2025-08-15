package com.juleswhite.module1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class Ollama {

	private static final String OLLAMA_URL = "http://localhost:11434/api/chat";
	private static final String MODEL = "llama3.2:latest";  // Use the name you started in `ollama run llama3`

	public String generateResponse(List<Message> messages) {
		try {
			// Create the HTTP connection
			URL url = new URL(OLLAMA_URL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json");

			// Build the JSON request body
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode requestJson = mapper.createObjectNode();
			requestJson.put("model", MODEL);
			requestJson.put("stream", false);

			// Add messages
			var jsonMessages = mapper.createArrayNode();
			for (Message message : messages) {
				ObjectNode msgNode = mapper.createObjectNode();
				msgNode.put("role", message.getRole());
				msgNode.put("content", message.getContent());
				jsonMessages.add(msgNode);
			}
			requestJson.set("messages", jsonMessages);

			// Send the request
			try (OutputStream os = conn.getOutputStream()) {
				byte[] input = mapper.writeValueAsBytes(requestJson);
				os.write(input, 0, input.length);
			}

			// Read the response
			StringBuilder response = new StringBuilder();
			try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
				String line;
				while ((line = br.readLine()) != null) {
					response.append(line);
				}
			}

			// Parse the response JSON
			ObjectNode responseJson = (ObjectNode) mapper.readTree(response.toString());

			conn.disconnect();
			return responseJson.get("message").get("content").asText();

		} catch (IOException e) {
			throw new RuntimeException("Failed to get response from Ollama", e);
		}
	}

}

