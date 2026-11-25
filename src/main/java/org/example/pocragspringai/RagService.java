package org.example.pocragspringai;

import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    // 1. Ingestão dinâmica de PDFs via MultipartFile
    public String ingerirPdf(MultipartFile arquivo) {
        log.info("Iniciando ingestão do PDF: {}", arquivo.getOriginalFilename());

        Resource pdfResource = arquivo.getResource();
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
        List<Document> documentos = pdfReader.get();

        TokenTextSplitter splitter = TokenTextSplitter.builder().build();
        List<Document> chunks = splitter.apply(documentos);

        vectorStore.add(chunks);

        log.info("PDF {} indexado com sucesso. Chunks gerados: {}", arquivo.getOriginalFilename(), chunks.size());
        return String.format("Arquivo '%s' indexado com sucesso! Chunks salvos: %d",
                arquivo.getOriginalFilename(), chunks.size());
    }

    // 2. Consulta com Similarity Threshold (FinOps) + Retry/Fallback
    @Retry(name = "llmRetry", fallbackMethod = "fallbackResposta")
    public String perguntar(String pergunta) {
        long inicio = System.currentTimeMillis();

        // Filtra apenas chunks com alta relevância (>= 0.70) para economizar tokens e evitar ruído
        List<Document> documentosSimilares = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(pergunta)
                        .topK(3)
                        .similarityThreshold(0.55)
                        .build()
        );

        if (documentosSimilares.isEmpty()) {
            log.info("[FinOps] Nenhum contexto atingiu o threshold mínimo. Chamada ao LLM evitada (0 tokens gastos).");
            return "Não encontrei informações suficientemente relevantes na base de conhecimento para responder a essa pergunta.";
        }

        String contexto = documentosSimilares.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        String systemPrompt = """
                Você é um assistente corporativo preciso.
                Responda à pergunta do usuário utilizando EXCLUSIVAMENTE o contexto fornecido abaixo.
                Se a resposta não estiver no contexto, diga exatamente: "Não encontrei essa informação na base de conhecimento."
                
                CONTEXTO:
                %s
                """.formatted(contexto);

        ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .user(pergunta)
                .call()
                .chatResponse();

        long duracaoMs = System.currentTimeMillis() - inicio;

        if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            log.info("[FinOps] Latência: {} ms | Prompt Tokens: {} | Completion Tokens: {} | Total: {}",
                    duracaoMs, usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        }

        return response != null ? response.getResult().getOutput().getText() : "Sem resposta do modelo.";
    }

    // 3. Fallback determinístico caso o LLM esteja indisponível após retentativas
    public String fallbackResposta(String pergunta, Throwable t) {
        log.error("Falha ao consultar o provedor de LLM após retentativas. Erro: {}", t.getMessage());
        return "O serviço de inteligência artificial está temporariamente indisponível. Por favor, tente novamente em instantes.";
    }
}