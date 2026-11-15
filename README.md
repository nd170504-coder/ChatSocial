# ChatSocial
ChatSocial là dự án web mạng xã hội kết hợp nhắn tin thời gian thực, giúp người dùng kết nối bạn bè, chia sẻ khoảnh khắc và trò chuyện trên cùng một nền tảng.
Dự án được xây dựng theo mô hình tách biệt Frontend và Backend, sử dụng React cho giao diện, Spring Boot để xử lý nghiệp vụ và PostgreSQL để lưu trữ dữ liệu.

## Tính năng chính
### Tài khoản và hồ sơ cá nhân
- Đăng ký, đăng nhập và xác minh email.
- Quên mật khẩu và đặt lại mật khẩu qua email.
- Xem, cập nhật thông tin và ảnh đại diện.
- Xem trang cá nhân của người dùng khác.

### Kết nối bạn bè
- Tìm kiếm người dùng.
- Gửi, chấp nhận hoặc từ chối lời mời kết bạn.
- Xem danh sách bạn bè và hủy kết bạn.
- Chặn và bỏ chặn người dùng.

### Bài viết và tương tác
- Tạo bài viết với nội dung và hình ảnh.
- Chỉnh sửa, xóa bài viết và lựa chọn phạm vi hiển thị.
- Xem bảng tin và bài viết trên trang cá nhân.
- Thích, bình luận và trả lời bình luận.

### Nhắn tin thời gian thực
- Trò chuyện trực tiếp và gửi hình ảnh.
- Xem lịch sử trò chuyện và thu hồi tin nhắn.
- Hiển thị trạng thái đang hoạt động, đang nhập và đã đọc.
- Nhận tin nhắn mới mà không cần tải lại trang.

### Thông báo
- Nhận thông báo về lời mời kết bạn và tương tác với bài viết.
- Xem và đánh dấu thông báo đã đọc.

## Công nghệ sử dụng

| Thành phần | Công nghệ |
| --- | --- |
| Frontend | React, TypeScript, Vite |
| Backend | Java 17, Spring Boot |
| Xác thực và phân quyền | Spring Security, JWT |
| Truy cập dữ liệu | Spring Data JPA, Hibernate |
| Cơ sở dữ liệu | PostgreSQL |
| Giao tiếp thời gian thực | WebSocket, STOMP |
| Gửi email | Brevo API |

## Cấu trúc dự án

```text
ChatSocial/
├── frontend/    # Giao diện và các chức năng phía người dùng
├── backend/     # REST API, xử lý nghiệp vụ và WebSocket
└── README.md    # Giới thiệu dự án
```

## Mục tiêu dự án

Dự án được thực hiện nhằm vận dụng kiến thức phát triển web full-stack vào một ứng dụng thực tế: từ xây dựng giao diện, thiết kế REST API, xác thực người dùng và quản lý dữ liệu đến xử lý giao tiếp thời gian thực.

ChatSocial hướng đến trải nghiệm sử dụng đơn giản, giúp người dùng dễ dàng chia sẻ nội dung, giữ liên lạc và tương tác với bạn bè.
