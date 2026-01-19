Tôi là chuyên gia architecture. Tôi đang cần xây dựng 1 hệ thống microservice để xử lý các tác vụ data extraction file từ nhiều hệ thống. Quy trình xử lý file bao gồm các bước:
- Kéo file từ các hệ thống khác về qua SFTP protocol hoặc Object Storage như MinIO cần common hoá các logic này áp dụng cho nhiều hệ thống. Toàn bộ các file khi kéo về lưu trữ tập trung trong 1 object storage như MinIO và lưu trữ trong 1 khoảng thời gian cho phép cấu hình
- Thực hiện unzip file, file chứa các file pdf. Tuỳ theo từng logic nghiệp vụ sẽ kiểm đếm số lượng file, check một vài key trong từng file pdf thực hiện rename file. Nếu file không đủ số lượng hoặc không có key trong file pdf thì sẽ không thực hiện rename file và dừng quy trình extract trả lại lỗi
- Thực hiện check nội dung trong file, extract các thông tin cần thiết tuỳ theo từng logic nghiệp vụ, tác các content sang 1 object thực hiện lưu trữ vào database, cần common hoá, áp dụng cho nhiều Object khác nhau ở nhiều hệ thống khác nhau
- Data trong database thực hiện validate các logic nghiệp vụ, check trùng, check valid dữ liệu thực hiện tạo master data vào database
- Xác định ngôn ngữ công nghệ phù hợp xử lý lượng dữ liệu lớn với file dung lượng lớn
- Xác định kiến trúc dự án, throughput, đáp ứng tải lượng dữ liệu lớn
- Xây dựng 1 Quy trình ETL pipeline để xử lý các tác vụ data extraction file từ nhiều hệ thống trong đó file có nhiều định dạng excel, word, pdf, txt, csv, json, xml. Common hoá các logic xử lý file và áp dụng cho nhiều DTo khác nhau

Ngoài ra tôi cần xây dựng bổ sung 1 service giao tiếp với third-party system gọi là integration-service. Tôi cần xây dựng 1 integration-service mục đích dùng để giao tiếp với các hệ thống bên ngoài, third-party, forward request vào các downstream internal service trong microservices. Integration-service không có database chỉ có forward request vào RabbitMQ, các các downstream internal service listen và xử lý request. Tôi cần xây dựng 1 base internal service hoàn chỉnh full-feature cho 1 hệ thống giao tiếp service to service.

Hệ thống sẽ bao gồm thêm **Executor Service**, đóng vai trò trung gian giữa Integration Service và ETL Engine để tối ưu hóa flow, tránh block và đảm bảo khả năng mở rộng:
- **Executor Service (Java/Spring Boot)**: Sử dụng Java 21 & Virtual Threads để xử lý I/O bound (SFTP/S3). Service này nhận request từ RabbitMQ, thực hiện download/upload file vào MinIO, sau đó bắn event sang **Kafka** để trigger ETL.
- **Workflow**: Integration Service -> RabbitMQ -> Executor Service -> **Kafka** -> ETL Engine.
    - **Luồng 1 (Upload)**: API upload file -> Integration Service -> RabbitMQ -> Executor (Validate & Store) -> Kafka -> ETL Engine.
    - **Luồng 2 (Event)**: API gửi sự kiện pull -> Integration Service -> RabbitMQ -> Executor (Download từ SFTP/S3) -> Kafka -> ETL Engine.

Stack công nghệ đề xuất (Latest Stable)

Để đảm bảo tính hiện đại, hiệu năng và khả năng mở rộng (scalability):



Java: 21 (LTS) - Tận dụng Virtual Threads cho throughput cao.

Framework: Spring Boot 3.3.x.

Messaging Abstraction: Spring Cloud Stream (Function programming model). Đây là chuẩn mực để decouple business logic khỏi hạ tầng message broker.

Broker: RabbitMQ (Stream & Quorum Queues cho độ tin cậy dữ liệu cao).

Observability: Micrometer Tracing (với OpenTelemetry).

Resilience: Resilience4j (Circuit Breaker, Retry).

Utility: Jackson (JSON), Lombok.

Kiến trúc tổng quan (Architecture Overview)

Chúng ta sẽ xây dựng Integration Service với các lớp phòng thủ (Defense Layers):

Đối với integration-service tôi cần kiểm soát lượng truy cập vào ingest với mỗi hệ thống giới hạn 100 request/ngày dựa theo phương thức bảo mật API Key + rate limiting, sau mỗi ngày hệ thống reset lại số lượng request, cần đảm bảo giải pháp tối ưu hiệu năng, giảm round trip, ngoài ra có report về lượng request trong ngày, lượng request vượt quá giới hạn theo từng hệ thống, hệ thống triển khai cache multi level Caffein L1, Redis L2 