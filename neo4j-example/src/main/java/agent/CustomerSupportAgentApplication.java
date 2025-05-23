package agent;

import dev.langchain4j.AiServices;
import dev.langchain4j.chat.ChatLanguageModel;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.community.store.memory.chat.neo4j.Neo4jChatMemoryStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryImpl;
import dev.langchain4j.model.chat.OpenAiChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.openai.OpenAiEmbeddingModel;
import dev.langchain4j.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

@SpringBootApplication
public class CustomerSupportAgentApplication {

    private static final Logger log = LoggerFactory.getLogger(CustomerSupportAgentApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CustomerSupportAgentApplication.class, args);
    }

    @Bean
    public Neo4jEmbeddingStore embeddingStore() {
        return Neo4jEmbeddingStore.builder()
                .withBasicAuth("bolt://localhost:7687", "neo4j", "password")
                .dimension(1536) // OpenAI embedding dimension
                .build();
    }

    @Bean
    public Neo4jChatMemoryStore chatMemoryStore() {
        return Neo4jChatMemoryStore.builder()
                .withBasicAuth("bolt://localhost:7687", "neo4j", "password")
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();
    }

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();
    }

    @Bean
    public Assistant assistant(ChatLanguageModel chatLanguageModel, Neo4jChatMemoryStore chatMemoryStore) {
        return AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(sessionId -> ChatMemoryImpl.builder()
                        .id(sessionId)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .build();
    }

    @Bean
    public AssistantService assistantService(Assistant assistant, Neo4jEmbeddingStore embeddingStore,
                                             OpenAiEmbeddingModel embeddingModel) {
        return new AssistantService(assistant, embeddingStore, embeddingModel);
    }

    @Bean
    public ApplicationRunner runner(AssistantService assistantService) {
        return args -> {
            try (Scanner scanner = new Scanner(System.in)) {
                String sessionId = "user-123";
                while (true) {
                    log.info("==================================================");
                    log.info("User: ");
                    String userQuery = scanner.nextLine();
                    if ("exit".equalsIgnoreCase(userQuery)) break;
                    log.info("==================================================");
                    String response = assistantService.chat(sessionId, userQuery);
                    log.info("==================================================");
                    log.info("Assistant: " + response);
                }
            }
        };
    }

    @AiService
    public interface Assistant {
        String chat(String userMessage);
    }

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
            var queryEmbedding = embeddingModel.embed(userMessage);
            List<Document> docs = embeddingStore.search(queryEmbedding, 3);
            String context = docs.stream().map(Document::content).collect(Collectors.joining("\n---\n"));
            String enrichedPrompt = """
                    You are a helpful customer support agent.
                    Use the following context to answer the user:
                    %s
                    User: %s
                    """.formatted(context, userMessage);
            return assistant.chat(enrichedPrompt);
        }
    }
}
