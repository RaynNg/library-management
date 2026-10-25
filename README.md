# Quản lý thư viện – Giao diện Java Swing

Ứng dụng desktop cho database `library_db` (MySQL 8.0.16+ hoặc MariaDB 10.5+). Mọi quy tắc mượn/trả/phạt dùng trực tiếp trigger, stored procedure và view trong `routines.sql`.

## 1. Tạo database
Chạy 3 file trong thư mục `sql/` theo đúng thứ tự:
```bash
mysql -u root -p < sql/db_creation.sql
mysql -u root -p < sql/routines.sql
mysql -u root -p < sql/data.sql
```
(Trong MySQL Workbench: mở từng file và bấm ⚡ Execute theo thứ tự trên.)

## 2. Chạy ứng dụng
Cần JDK 17 trở lên.

**Cách 1 – Maven (khuyên dùng):**
```bash
mvn package
java -jar target/library-gui.jar
```

**Cách 2 – Không cần Maven:** tải `mysql-connector-j-8.4.0.jar` từ https://dev.mysql.com/downloads/connector/j/ rồi:
```bash
javac -encoding UTF-8 -d out src/main/java/library/*.java
java -cp "out;mysql-connector-j-8.4.0.jar" library.App      # Windows
java -cp "out:mysql-connector-j-8.4.0.jar" library.App      # macOS / Linux
```

**Cách 3 – IDE (IntelliJ / NetBeans / Eclipse):** mở thư mục dự án như một dự án Maven, chạy class `library.App`.

Khi mở, nhập máy chủ, cổng, tài khoản và mật khẩu MySQL. Thông tin (trừ mật khẩu) được nhớ cho lần sau.

## 3. Các màn hình
| Tab | Chức năng |
|---|---|
| Tổng quan | Số đầu sách, bản sao còn trên kệ, đang mượn, quá hạn, độc giả active, tổng nợ phạt; sách mượn nhiều nhất |
| Mượn / Trả | Lập phiếu mượn (`sp_issue_book`); trả sách (`sp_return_book`, tự tính phạt 2.000 VND/ngày); báo mất sách kèm tiền bồi thường |
| Sách quá hạn | Từ `v_overdue_books`, kèm số tiền phạt nếu trả hôm nay |
| Sách | Thêm / sửa / xóa sách, gán nhiều tác giả. Sửa tổng số bản thì số bản còn lại thay đổi tương ứng |
| Độc giả | Thêm / sửa / xóa độc giả, đổi loại tài khoản và trạng thái (active, suspended, expired) |
| Công nợ phạt | Tổng phạt, đã trả, còn nợ; lịch sử phạt và thanh toán của từng người; thu tiền phạt |
| Đặt sách | Tạo yêu cầu đặt sách, đổi trạng thái pending → available → fulfilled / cancelled |
| Danh mục phụ | Thể loại, nhà xuất bản, tác giả, vị trí kệ, nhân viên |

Mọi bảng đều có ô tìm kiếm, nút làm mới, sắp xếp bằng cách bấm tiêu đề cột. Nhấp đúp một dòng để sửa (hoặc trả sách, thu tiền tùy tab).

## 4. Quy tắc nghiệp vụ (do database kiểm tra)
- Chỉ độc giả có trạng thái `active` mới được mượn.
- Mỗi độc giả mượn tối đa 5 cuốn cùng lúc (không tính sách báo mất).
- Sách phải còn bản sao; số bản còn lại tự trừ khi mượn và tự cộng khi trả.
- Trả trễ: 2.000 VND mỗi ngày, ghi vào `fine_due`.

Khi vi phạm, ứng dụng hiện đúng thông báo lỗi của trigger hoặc procedure.

## 5. Cấu trúc mã nguồn
```
src/main/java/library/
  App.java            khởi chạy, cửa sổ chính
  LoginDialog.java    hộp thoại kết nối
  Db.java             kết nối JDBC, truy vấn, transaction, gọi procedure
  Ui.java             chạy nền, thông báo, định dạng bảng
  FormDialog.java     hộp thoại nhập liệu dùng chung + kiểm tra dữ liệu
  TablePanel.java     bảng có tìm kiếm / làm mới (lớp cha của các tab)
  DashboardPanel.java, LoansPanel.java, BooksPanel.java, MembersPanel.java,
  FinesPanel.java, RequestsPanel.java, LookupPanel.java
```
