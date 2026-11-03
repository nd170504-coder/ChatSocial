# ChatSocial Frontend

React + TypeScript + Vite, quản lý cùng repo với Spring Boot backend.

## Chạy local

```powershell
cd frontend
npm install
# Chỉ copy nếu chưa có .env.local:
Copy-Item .env.example .env.local
npm run dev
```

- Frontend: http://localhost:5173 (port cố định; báo lỗi nếu đang bận).
- `.env.local`: `VITE_API_BASE_URL=http://localhost:8080/api`.
- Backend chạy port 8080 với `CORS_ALLOWED_ORIGINS=http://localhost:5173` và các biến DB/mail/secret hiện có.
- Spring Boot không tự đọc env của frontend. Không đặt secret trong biến `VITE_`.
- Restart Vite sau khi đổi env. `.env.local` không commit; `.env.example` có commit.

## Feature Auth

| Route | Chức năng |
| --- | --- |
| `/login` | Đăng nhập; dẫn tới xác minh email nếu tài khoản chưa xác minh |
| `/register` | Tạo tài khoản rồi chuyển tới nhập OTP |
| `/verify-email` | Xác minh email, gửi lại mã với bộ đếm 60 giây |
| `/forgot-password` | Yêu cầu OTP đặt lại mật khẩu |
| `/reset-password` | Nhập OTP và mật khẩu mới, hỗ trợ gửi lại mã |
| `/` | Trang chào sau đăng nhập, thông tin user và logout |

Trang `/` được bảo vệ; các trang Auth chuyển về `/` khi đã đăng nhập.
Đổi mật khẩu khi đã đăng nhập thuộc feature Account, chưa làm trong branch Auth.
Hồ sơ và upload ảnh thuộc feature Profile, chưa triển khai giao diện.

## Cấu trúc

```text
src/
  app/router.tsx             # Route public/protected
  features/auth/
    api.ts                  # Public auth endpoints
    types.ts                # Request/response theo DTO backend
    session.ts              # Store, khôi phục phiên, refresh, logout
    errors.ts               # Thông báo lỗi và validation mật khẩu
    hooks/                  # useAuth, useAuthForm, useCooldown
    components/             # AuthGate, FormFeedback
    pages/                  # Các màn hình của feature
  components/ui/Field.tsx    # Input và hiện/ẩn mật khẩu dùng chung
  layouts/AuthLayout.tsx    # Khung xác thực responsive
  api/client.ts             # Fetch JSON/FormData và ApiError
  config/env.ts             # API base URL
  types/api.ts              # ApiResponse, Spring ProblemDetail
```

## Phiên đăng nhập

- Lưu token theo tab trong `sessionStorage` để tải lại trang không mất phiên; không lưu mật khẩu.
- Khi khởi động, gọi `/users/me` để kiểm tra phiên và lấy user mới nhất.
- `authenticatedRequest<T>()` gắn Bearer token, refresh khi gặp 401 và retry tối đa một lần.
- Các request dùng chung một promise refresh. Sau rotation, lưu cả access token và refresh token mới.
- API public như login/OTP không tự refresh khi lỗi.
- Refresh bị từ chối hoặc retry vẫn 401: xóa phiên và quay về login.
- Lỗi mạng/server khi khôi phục: giữ token và cho thử lại.
- Logout thu hồi session phía BE trước khi xóa token local. Nếu lỗi mạng, hiển thị lỗi để thử lại.
- Token trong sessionStorage vẫn có thể bị JavaScript đọc khi có XSS. Backend hiện chưa dùng cookie HttpOnly.
- Không đồng bộ phiên giữa các tab. Tránh duplicate tab đang đăng nhập: trình duyệt có thể sao chép sessionStorage và hai tab sẽ dùng chung refresh token xoay vòng.
- Bộ đếm gửi OTP phía FE chỉ hỗ trợ UX; giới hạn gửi thực tế do BE quyết định.

Khi triển khai các feature tiếp theo, dùng `authenticatedRequest` cho JSON endpoint được bảo vệ.
`apiRequest` hiện yêu cầu response JSON theo `ApiResponse<T>`, chưa dùng cho 204/file download.

## Kiểm tra

```powershell
npm run lint
npm test
npm run build
```

Test tự động dùng API giả lập để kiểm tra khôi phục phiên, refresh đồng thời, hết phiên và logout.
Kiểm tra tích hợp thật cần BE, PostgreSQL và cấu hình SMTP hợp lệ.

## Kiểm thử thủ công với BE

1. Đăng ký → nhận OTP → xác minh → đăng nhập.
2. Reload trang `/` → thông tin tài khoản vẫn hiển thị.
3. Logout → quay lại login; truy cập `/` khi chưa đăng nhập phải bị chuyển về login.
4. Login sai mật khẩu → thông báo lỗi, không gọi refresh.
5. Login tài khoản chờ xác minh → có liên kết xác minh email.
6. Quên mật khẩu → nhập OTP và mật khẩu mới → đăng nhập lại.
7. Thử email không tồn tại khi quên mật khẩu: thông báo không tiết lộ tài khoản tồn tại hay không.
8. Kiểm tra trên điện thoại, nút hiện/ẩn mật khẩu, OTP sai/hết hạn và lỗi mạng.

Nếu deploy static hosting, cấu hình SPA fallback về `index.html` cho các route của React Router.
