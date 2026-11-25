# Enterprise RAG Architecture with Java 21, Spring AI & pgvector

Arquitetura de referência para **Retrieval-Augmented Generation (RAG)** em ambientes corporativos Java, projetada com foco em **governança de dados, resiliência e FinOps**.

## Arquitetura da Solução

```mermaid
flowchart LR
    Client[Cliente REST] -->|POST /ingest/pdf| API[Spring Boot 3 / Java 21]
    Client -->|POST /chat| API
    API -->|Chunking & Embeddings| Embed[Ollama: nomic-embed-text]
    Embed -->|768d Vectors| DB[(PostgreSQL + pgvector)]
    API -->|Similarity Search >= 0.70| DB
    API -->|Context + Prompt + Retry| LLM[Ollama: llama3.2]