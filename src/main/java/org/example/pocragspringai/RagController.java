package org.example.pocragspringai;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    public record ChatRequest(String pergunta) {}
    public record ChatResponseDto(String resposta) {}

    @PostMapping(value = "/ingest/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> ingestPdf(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ragService.ingerirPdf(file));
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponseDto> chat(@RequestBody ChatRequest request) {
        String resposta = ragService.perguntar(request.pergunta());
        return ResponseEntity.ok(new ChatResponseDto(resposta));
    }
}