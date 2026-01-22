# So Sánh DocumentProcessingClient vs EtlClient

## Tổng Quan

Hệ thống hiện tại có **2 Feign Client** gọi cùng một ETL Engine API nhưng với cách tiếp cận khác nhau:

1. **DocumentProcessingClient** - Type-safe với DTO objects
2. **EtlClient** - Generic với `Map<String, Object>`

---

## Bảng So Sánh Chi Tiết

| Tiêu chí | DocumentProcessingClient | EtlClient |
|----------|-------------------------|-----------|
| **Package** | `com.extraction.executor.client` | `com.extraction.executor.client` |
| **Feign Client Name** | `document-processing-client` | `etl-engine-client` |
| **Base URL Config** | `${document-processing.api.base-url}` | `${etl-engine.url:http://localhost:8089}` |
| **Request Type** | DTO Objects (SplitRenameRequest, etc.) | `Map<String, Object>` |
| **Response Type** | DTO Objects (SplitRenameResponse, etc.) | `Map<String, Object>` |
| **Type Safety** | ✅ Type-safe | ❌ Không type-safe |
| **Compile-time Validation** | ✅ Có | ❌ Không |
| **API Methods** | 3 methods (split, check, extract) | 4 methods (+ crossCheck) |
| **Fallback Methods** | ✅ Có (throw RuntimeException) | ✅ Có (throw RuntimeException) |
| **Circuit Breaker** | ✅ Có | ✅ Có |
| **Retry** | ✅ Có | ✅ Có |
| **FeignClientConfig** | ✅ Có | ✅ Có |
| **Sử dụng ở đâu** | `DocumentProcessingService` | `ProcessingListener`, `IngestRequestListener` |

---

## Chi Tiết API Methods

### DocumentProcessingClient

```java
// Stage 1
SplitRenameResponse splitAndRename(@RequestBody SplitRenameRequest request);

// Stage 2
CheckCompletenessResponse checkCompleteness(@RequestBody CheckCompletenessRequest request);

// Stage 3
ExtractDataResponse extractData(@RequestBody ExtractDataRequest request);
```

**Thiếu**: Không có `crossCheck` method

### EtlClient

```java
// Stage 1
Map<String, Object> splitRename(@RequestBody Map<String, Object> request);

// Stage 2
Map<String, Object> checkCompleteness(@RequestBody Map<String, Object> request);

// Stage 3
Map<String, Object> extractData(@RequestBody Map<String, Object> request);

// Stage 4
Map<String, Object> crossCheck(@RequestBody Map<String, Object> request);
```

**Có đầy đủ**: Bao gồm cả `crossCheck`

---

## Nơi Sử Dụng

### DocumentProcessingClient

**File**: `DocumentProcessingService.java`

```java
@Service
public class DocumentProcessingService {
    private final DocumentProcessingClient documentProcessingClient;
    
    public SplitRenameResponse splitAndRename(String s3Uri) {
        SplitRenameRequest request = SplitRenameRequest.builder()
            .s3Uri(s3Uri)
            .build();
        return documentProcessingClient.splitAndRename(request);
    }
}
```

**Đặc điểm**:
- High-level service layer
- Type-safe với DTO objects
- Có business logic và history tracking
- Sử dụng cho REST API endpoints

### EtlClient

**File 1**: `ProcessingListener.java` (RabbitMQ RPC Listener)

```java
@Component
public class ProcessingListener {
    private final EtlClient etlClient;
    
    @RabbitListener(queues = RabbitMQConfig.QUEUE_SPLIT)
    public Map<String, Object> handleSplitRename(SplitRenameRequest request) {
        Map<String, Object> payload = objectMapper.convertValue(request, Map.class);
        return etlClient.splitRename(payload);
    }
}
```

**File 2**: `IngestRequestListener.java` (RabbitMQ Async Listener)

```java
@Component
public class IngestRequestListener {
    private final EtlClient etlClient;
    
    private String processSplitRename(String jobId, String s3Uri, String fileName) {
        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("s3_uri", s3Uri);
        Map<String, Object> result = etlClient.splitRename(requestPayload);
        // ...
    }
}
```

**Đặc điểm**:
- Integration layer (RabbitMQ listeners)
- Generic với Map để dễ serialize/deserialize
- Xử lý message từ queue
- Cần convert DTO → Map

---

## Vấn Đề Hiện Tại

### 1. **Trùng Lặp Code**
- Cả 2 client gọi cùng API endpoints
- Cả 2 đều có Circuit Breaker, Retry, Fallback
- Cả 2 đều dùng `FeignClientConfig`

### 2. **Inconsistency**
- `DocumentProcessingClient` thiếu method `crossCheck`
- `EtlClient` không type-safe
- 2 cách tiếp cận khác nhau cho cùng một service

### 3. **Maintenance Overhead**
- Phải maintain 2 clients
- Khi API thay đổi, phải update cả 2
- Dễ bị out-of-sync

### 4. **URL Configuration**
- `DocumentProcessingClient`: `${document-processing.api.base-url}`
- `EtlClient`: `${etl-engine.url:http://localhost:8089}`
- Có thể trỏ đến cùng service nhưng config khác nhau

---

## Đề Xuất: Gộp Thành 1 Client

### Option 1: Gộp Vào DocumentProcessingClient (Recommended)

**Ưu điểm**:
- ✅ Type-safe với DTO objects
- ✅ Compile-time validation
- ✅ Dễ maintain và test
- ✅ Phù hợp với Spring best practices

**Cách làm**:
1. Thêm method `crossCheck` vào `DocumentProcessingClient`
2. Update `ProcessingListener` và `IngestRequestListener` để dùng `DocumentProcessingClient`
3. Convert Map → DTO trong listeners
4. Xóa `EtlClient`

**Code Example**:

```java
// ProcessingListener.java
@RabbitListener(queues = RabbitMQConfig.QUEUE_SPLIT)
public Map<String, Object> handleSplitRename(SplitRenameRequest request) {
    // Convert DTO to Map for response
    SplitRenameResponse response = documentProcessingClient.splitAndRename(request);
    return objectMapper.convertValue(response, Map.class);
}

// IngestRequestListener.java
private String processSplitRename(String jobId, String s3Uri, String fileName) {
    SplitRenameRequest request = SplitRenameRequest.builder()
        .s3Uri(s3Uri)
        .build();
    SplitRenameResponse response = documentProcessingClient.splitAndRename(request);
    return response.getTransactionId();
}
```

### Option 2: Gộp Vào EtlClient

**Ưu điểm**:
- ✅ Đã có đầy đủ 4 methods
- ✅ Generic, flexible với Map
- ✅ Dễ serialize/deserialize từ RabbitMQ

**Nhược điểm**:
- ❌ Không type-safe
- ❌ Không có compile-time validation
- ❌ Phải convert DTO → Map ở nhiều nơi

### Option 3: Giữ Cả 2 Nhưng Đồng Bộ

**Cách làm**:
1. Thêm `crossCheck` vào `DocumentProcessingClient`
2. Đảm bảo cả 2 client có cùng methods
3. Document rõ khi nào dùng client nào
4. Sync URL config

**Nhược điểm**:
- ❌ Vẫn phải maintain 2 clients
- ❌ Dễ bị out-of-sync

---

## Khuyến Nghị: Option 1

### Lý Do

1. **Type Safety**: DTO objects giúp catch errors tại compile-time
2. **Maintainability**: Chỉ cần maintain 1 client
3. **Consistency**: Tất cả code dùng cùng một client
4. **Best Practices**: Type-safe là best practice trong Java/Spring

### Migration Plan

#### Step 1: Update DocumentProcessingClient

```java
@FeignClient(
    name = "etl-engine-client",
    url = "${etl-engine.url:http://localhost:8089}",
    configuration = FeignClientConfig.class
)
public interface DocumentProcessingClient {
    // ... existing methods ...
    
    // Add missing method
    @PostMapping("/api/v1/documents/cross-check")
    @CircuitBreaker(name = "documentProcessing", fallbackMethod = "crossCheckFallback")
    @Retry(name = "documentProcessing")
    CrossCheckResponse crossCheck(@RequestBody CrossCheckRequest request);
    
    default CrossCheckResponse crossCheckFallback(CrossCheckRequest request, Exception ex) {
        throw new RuntimeException("ETL Engine unavailable for cross-check", ex);
    }
}
```

#### Step 2: Update ProcessingListener

```java
@Component
@RequiredArgsConstructor
public class ProcessingListener {
    private final DocumentProcessingClient documentProcessingClient;
    private final ObjectMapper objectMapper;
    
    @RabbitListener(queues = RabbitMQConfig.QUEUE_SPLIT)
    public Map<String, Object> handleSplitRename(SplitRenameRequest request) {
        SplitRenameResponse response = documentProcessingClient.splitAndRename(
            SplitRenameRequest.builder().s3Uri(request.getS3_uri()).build()
        );
        return objectMapper.convertValue(response, Map.class);
    }
    
    // Similar for other methods...
}
```

#### Step 3: Update IngestRequestListener

```java
private String processSplitRename(String jobId, String s3Uri, String fileName) {
    SplitRenameRequest request = SplitRenameRequest.builder()
        .s3Uri(s3Uri)
        .build();
    SplitRenameResponse response = documentProcessingClient.splitAndRename(request);
    return response.getTransactionId();
}
```

#### Step 4: Remove EtlClient

- Xóa file `EtlClient.java`
- Update imports trong các file sử dụng

---

## Kết Luận

**Hiện tại**: 2 clients trùng lặp, không nhất quán

**Đề xuất**: Gộp thành `DocumentProcessingClient` với type-safe DTO objects

**Lợi ích**:
- ✅ Giảm code duplication
- ✅ Type safety và compile-time validation
- ✅ Dễ maintain hơn
- ✅ Consistency trong codebase

**Migration**: Có thể làm từng bước, không breaking changes
