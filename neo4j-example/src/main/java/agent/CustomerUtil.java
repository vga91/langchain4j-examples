package agent;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;

import java.util.List;
import java.util.stream.Collectors;

public class CustomerUtil {
    public interface Assistant {
        String chat(String userMessage);
    }

    public static class AssistantService {

        private final Assistant assistant;
        private final Neo4jEmbeddingStore embeddingStore;
        private final EmbeddingModel embeddingModel;

        public AssistantService(Assistant assistant,
                                Neo4jEmbeddingStore embeddingStore,
                                EmbeddingModel embeddingModel) {
            this.assistant = assistant;
            this.embeddingStore = embeddingStore;
            this.embeddingModel = embeddingModel;
        }

        public String chat(String sessionId, String userMessage) {
            Embedding queryEmbedding = embeddingModel.embed(userMessage).content();
            final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(3)
                    .build();
            final List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
            String context = matches.stream().map(i -> i.embedded().text()).collect(Collectors.joining("\n---\n"));

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
