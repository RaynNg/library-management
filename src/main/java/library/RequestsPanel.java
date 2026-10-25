package library;

import javax.swing.*;
import java.time.LocalDate;
import java.util.List;

/** Yêu cầu đặt sách (book_request): tạo mới, đổi trạng thái. */
public class RequestsPanel extends TablePanel {

    static final String BASE = """
            SELECT r.request_id AS `Mã YC`,
                   CONCAT(m.last_name, ' ', m.first_name) AS `Độc giả`,
                   b.book_title AS `Sách`,
                   b.copies_available AS `Sách còn`,
                   r.request_date AS `Ngày yêu cầu`,
                   rs.available_status AS `Trạng thái`,
                   r.nearest_available_date AS `Dự kiến có sách`
            FROM book_request r
            JOIN members m              ON m.member_id = r.member_id
            JOIN book b                 ON b.book_id = r.book_id
            JOIN book_request_status rs ON rs.available_status_id = r.available_status_id
            """;

    private final JCheckBox onlyPending = new JCheckBox("Chỉ yêu cầu chưa xong (pending, available)", true);

    public RequestsPanel() {
        super(BASE);
        onlyPending.addActionListener(e -> reload());
        toolbar.add(onlyPending);
        button("Thêm yêu cầu", this::add);
        button("Đổi trạng thái", this::changeStatus);
        onDoubleClick(this::changeStatus);
    }

    @Override protected String currentSql() {
        return BASE + (onlyPending.isSelected()
                ? " WHERE rs.available_status IN ('pending', 'available')" : "")
                + " ORDER BY r.request_date DESC, r.request_id DESC";
    }

    private void add() {
        try {
            List<Db.Item> books = Db.items("SELECT book_id, CONCAT(book_title, ' (còn ', copies_available, ')') "
                    + "FROM book ORDER BY book_title");
            List<Db.Item> members = Db.items("SELECT member_id, CONCAT(member_id, ' - ', last_name, ' ', first_name) "
                    + "FROM members ORDER BY member_id");
            Object pending = Db.row("SELECT available_status_id FROM book_request_status "
                    + "WHERE available_status = 'pending'")[0];

            FormDialog d = new FormDialog(this, "Thêm yêu cầu đặt sách");
            JComboBox<Db.Item> book = d.add("Sách *:", FormDialog.combo(books, null));
            JComboBox<Db.Item> member = d.add("Độc giả *:", FormDialog.combo(members, null));
            JTextField date = d.add("Ngày yêu cầu *:", FormDialog.field(LocalDate.now(), 10));
            JTextField nearest = d.add("Dự kiến có sách:", FormDialog.field(null, 10));
            JButton guess = new JButton("Lấy hạn trả gần nhất");
            d.add("", guess);
            guess.addActionListener(e -> {
                Db.Item b = (Db.Item) book.getSelectedItem();
                if (b == null) return;
                try {
                    Object[] r = Db.row("SELECT MIN(due_date) FROM book_issue WHERE book_id = ? "
                            + "AND return_date IS NULL AND issue_status <> 'lost'", b.id());
                    nearest.setText(r == null || r[0] == null ? "" : r[0].toString());
                } catch (Exception ex) {
                    Ui.error(d, Db.message(ex));
                }
            });
            d.note("Yêu cầu mới có trạng thái 'pending'.");

            boolean ok = d.open(() -> Db.update("""
                    INSERT INTO book_request (book_id, member_id, request_date, available_status_id,
                                              nearest_available_date)
                    VALUES (?, ?, ?, ?, ?)""",
                    FormDialog.choice(book, "sách").id(), FormDialog.choice(member, "độc giả").id(),
                    FormDialog.date(date, "ngày yêu cầu", true), pending,
                    FormDialog.date(nearest, "ngày dự kiến có sách", false)));
            if (ok) App.refreshAll();
        } catch (Exception ex) {
            Ui.error(this, Db.message(ex));
        }
    }

    private void changeStatus() {
        Integer id = selectedId("yêu cầu");
        if (id == null) return;
        try {
            List<Db.Item> statuses = Db.items("SELECT available_status_id, available_status "
                    + "FROM book_request_status ORDER BY available_status_id");
            Object[] r = Db.row("SELECT available_status_id, nearest_available_date FROM book_request "
                    + "WHERE request_id = ?", id);
            FormDialog d = new FormDialog(this, "Cập nhật yêu cầu số " + id);
            d.add("Độc giả:", new JLabel(String.valueOf(selectedValue(1))));
            d.add("Sách:", new JLabel(selectedValue(2) + " (còn " + selectedValue(3) + " bản)"));
            JComboBox<Db.Item> status = d.add("Trạng thái:", FormDialog.combo(statuses, r[0]));
            JTextField nearest = d.add("Dự kiến có sách:", FormDialog.field(r[1], 10));
            d.note("pending: đang chờ · available: sách đã sẵn sàng, báo độc giả đến lấy<br>"
                    + "fulfilled: độc giả đã nhận sách · cancelled: đã hủy");
            boolean ok = d.open(() -> Db.update(
                    "UPDATE book_request SET available_status_id = ?, nearest_available_date = ? WHERE request_id = ?",
                    FormDialog.choice(status, "trạng thái").id(),
                    FormDialog.date(nearest, "ngày dự kiến có sách", false), id));
            if (ok) App.refreshAll();
        } catch (Exception ex) {
            Ui.error(this, Db.message(ex));
        }
    }
}
