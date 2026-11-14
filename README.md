# ChatSocial
Xây dựng mạng xã hội kết nối bạn bè và nhắn tin thời gian thực.

## Quản trị người dùng

Tài khoản có `role = ADMIN` có giao diện riêng, chỉ có hai mục Tổng quan và Quản trị
người dùng cùng nút đăng xuất. Sau khi đăng nhập, admin được chuyển đến `/admin`;
các đường dẫn giao diện người dùng cũng chuyển admin về trang này.

Trang `/admin` hiển thị tổng tài khoản, tài khoản mới hôm nay, số đang hoạt động và
số bị khóa; biểu đồ cột tài khoản mới theo ngày (7/30 ngày, gồm hôm nay) và biểu đồ
tròn trạng thái hiện tại. Ngày được tính theo `Asia/Ho_Chi_Minh` (UTC+7), ngày không
có tài khoản mới vẫn được hiển thị. Dữ liệu lấy từ `GET /api/admin/overview?days=7`.

Trang `/admin/users` có danh sách mới nhất, tìm theo một phần username/email,
xem trạng thái và khóa/mở khóa kèm lý do bắt buộc.
Danh sách, tìm kiếm, phân trang và tổng người dùng tại trang này chỉ tính role `USER`.
Danh sách có phân trang 20 tài khoản; tổng bao gồm mọi trạng thái, kể cả đã xóa mềm.

Backend hiện dùng Hibernate `ddl-auto: update`; khi khởi động sẽ thêm cột `role`
(mặc định `USER`) và thông tin thao tác quản trị gần nhất vào `chatsocial.users`.
Đăng ký thông thường luôn tạo `USER`, không nhận quyền từ client.

Để cấp admin lần đầu, đăng ký và xác minh email như bình thường, sau đó người quản lý
database chạy SQL với email tài khoản đã chọn (thay giá trị ví dụ):

```sql
UPDATE chatsocial.users
SET role = 'ADMIN'
WHERE email = 'your-admin@example.com' AND status = 'ACTIVE';
```

Đăng nhập lại hoặc tải lại trang sau khi cấp quyền; mục **Quản trị** sẽ xuất hiện
trong thanh điều hướng. Backend kiểm tra quyền từ database trên mỗi request.
Không có tài khoản/mật khẩu admin mặc định.

Riêng máy phát triển hiện tại có `service/LocalAdminBootstrapService.java` được
`.gitignore` bỏ qua. Class chạy lúc backend khởi động (ngoài profile `test`), chỉ
tạo admin khi chưa có bất kỳ tài khoản `ADMIN` nào. Có thể ghi đè cấu hình bằng
biến môi trường `ADMIN_EMAIL` và `ADMIN_PASSWORD`. File này không đi theo bản clone
của repository; nếu email đã thuộc tài khoản thường, khởi tạo sẽ dừng thay vì tự
nâng quyền hoặc đổi mật khẩu tài khoản đó.

Khóa chuyển `ACTIVE` sang `SUSPENDED` và thu hồi toàn bộ phiên đăng nhập. Mở khóa
chỉ chuyển `SUSPENDED` về `ACTIVE`, không khôi phục phiên cũ. Không hỗ trợ thay đổi
trạng thái tài khoản admin, chưa xác minh, tự vô hiệu hóa hoặc đã xóa qua trang này.
Lý do, người thực hiện và thời điểm cập nhật gần nhất được lưu cùng tài khoản;
đây không phải lịch sử đầy đủ của mọi lần thao tác.
