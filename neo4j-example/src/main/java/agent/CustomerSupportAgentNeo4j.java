package agent;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;


import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Collectors;

public class CustomerSupportAgentNeo4j {

    public static void main(String[] args) {
        // Setup
        String openAiApiKey = System.getenv("OPENAI_API_KEY");

        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .build();

        AllMiniLmL6V2EmbeddingModel embeddingModel = AllMiniLmL6V2EmbeddingModel.builder()
                .apiKey(openAiApiKey)
                .build();

        Neo4jChatMemoryStore chatMemoryStore = Neo4jChatMemoryStore.builder()
                .withBasicAuth("bolt://localhost:7687", "neo4j", "password")
                .build();

        Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth("bolt://localhost:7687", "neo4j", "password")
                .dimension(1536)
                .build();

        // Create assistant with memory support
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .chatMemoryProvider(sessionId -> ChatMemoryImpl.builder()
                        .id(sessionId)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .build();

        AssistantService service = new AssistantService(assistant, embeddingStore, embeddingModel);

        // Interactive loop
        try (Scanner scanner = new Scanner(System.in)) {
            String sessionId = UUID.randomUUID().toString();

            System.out.println("Welcome to the Customer Support Assistant!");
            System.out.println("Type 'exit' to quit.");

            while (true) {
                System.out.print("\nYou: ");
                String input = scanner.nextLine();

                if ("exit".equalsIgnoreCase(input)) {
                    break;
                }

                String response = service.chat(sessionId, input);
                System.out.println("Assistant: " + response);
            }
        }
    }

    // AI service interface
    @AiService
    public interface Assistant {
        String chat(String userMessage);
    }

    // Business logic
    public static class AssistantService {

        private final Assistant assistant;
        private final Neo4jEmbeddingStore embeddingStore;
        private final OpenAiEmbeddingModel embeddingModel;

        public AssistantService(Assistant assistant,
                                Neo4jEmbeddingStore embeddingStore,
                                OpenAiEmbeddingModel embeddingModel) {
            this.assistant = assistant;
            this.embeddingStore = embeddingStore;
            this.embeddingModel = embeddingModel;
        }

        public String chat(String sessionId, String userMessage) {
            Embedding queryEmbedding = embeddingModel.embed(userMessage);
            List<Document> docs = embeddingStore.search(queryEmbedding, 3);
            String context = docs.stream()
                    .map(Document::content)
                    .collect(Collectors.joining("\n---\n"));

            String prompt = """
                You are a helpful customer support agent.
                Use the following context to answer the user:
                %s
                User: %s
                """.formatted(context, userMessage);

            return assistant.chat(prompt);
        }
    }
}
