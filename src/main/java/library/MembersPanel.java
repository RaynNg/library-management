package library;

import javax.swing.*;
import java.time.LocalDate;
import java.util.List;

/** Độc giả: xem, thêm, sửa, xóa. */
public class MembersPanel extends TablePanel {

    static final String SQL = """
            SELECT m.member_id AS `Mã ĐG`,
                   CONCAT(m.last_name, ' ', m.first_name) AS `Họ tên`,
                   m.mobile_no AS `Điện thoại`, m.email_id AS `Email`, m.city AS `Thành phố`,
                   ms.account_type AS `Loại TK`, ms.account_status AS `Trạng thái`,
                   m.membership_end_date AS `Hết hạn thẻ`,
                   (SELECT COUNT(*) FROM book_issue bi
                     WHERE bi.member_id = m.member_id AND bi.return_date IS NULL
                       AND bi.issue_status <> 'lost') AS `Đang mượn`,
                   fb.balance AS `Còn nợ phạt`
            FROM members m
            JOIN member_status ms ON ms.active_status_id = m.active_status_id
            LEFT JOIN v_member_fine_balance fb ON fb.member_id = m.member_id
            ORDER BY m.member_id
            """;

    public MembersPanel() {
        super(SQL);
        button("Thêm độc giả", () -> edit(null));
        button("Sửa", () -> { Integer id = selectedId("độc giả"); if (id != null) edit(id); });
        button("Xóa", this::delete);
        onDoubleClick(() -> edit(selectedId("độc giả")));
    }

    private void edit(Integer memberId) {
        try {
            List<Db.Item> statuses = Db.items("SELECT active_status_id, CONCAT(account_type, ' - ', account_status) "
                    + "FROM member_status ORDER BY account_type DESC, active_status_id");
            Object[] m = memberId == null ? new Object[9] : Db.row("""
                    SELECT last_name, first_name, city, mobile_no, email_id, date_of_birth,
                           active_status_id, membership_start_date, membership_end_date
                    FROM members WHERE member_id = ?""", memberId);
            if (m == null) { Ui.error(this, "Không tìm thấy độc giả."); return; }
            if (memberId == null) {
                m[6] = 1;
                m[7] = LocalDate.now();
                m[8] = LocalDate.now().plusYears(1).minusDays(1);
            }

            FormDialog d = new FormDialog(this, memberId == null ? "Thêm độc giả" : "Sửa độc giả – mã " + memberId);
            JTextField last = d.add("Họ và tên đệm *:", FormDialog.field(m[0], 20));
            JTextField first = d.add("Tên *:", FormDialog.field(m[1], 12));
            JTextField phone = d.add("Điện thoại:", FormDialog.field(m[3], 14));
            JTextField email = d.add("Email:", FormDialog.field(m[4], 24));
            JTextField city = d.add("Thành phố:", FormDialog.field(m[2], 16));
            JTextField dob = d.add("Ngày sinh:", FormDialog.field(m[5], 10));
            JComboBox<Db.Item> status = d.add("Loại TK - trạng thái *:", FormDialog.combo(statuses, m[6]));
            JTextField start = d.add("Ngày cấp thẻ *:", FormDialog.field(m[7], 10));
            JTextField end = d.add("Ngày hết hạn thẻ:", FormDialog.field(m[8], 10));
            d.note("Ngày nhập theo dạng năm-tháng-ngày, ví dụ 2026-10-26.<br>"
                    + "Chỉ độc giả có trạng thái 'active' mới được mượn sách.");

            boolean ok = d.open(() -> {
                String ln = FormDialog.text(last, "họ và tên đệm", true);
                String fn = FormDialog.text(first, "tên", true);
                LocalDate s = FormDialog.date(start, "ngày cấp thẻ", true);
                LocalDate e = FormDialog.date(end, "ngày hết hạn thẻ", false);
                if (e != null && e.isBefore(s))
                    throw new IllegalArgumentException("Ngày hết hạn thẻ phải sau ngày cấp thẻ.");
                String mail = FormDialog.text(email, "email", false);
                if (mail != null && !mail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
                    throw new IllegalArgumentException("Email không hợp lệ.");
                Object[] p = {fn, ln, FormDialog.text(city, "thành phố", false),
                        FormDialog.text(phone, "điện thoại", false), mail,
                        FormDialog.date(dob, "ngày sinh", false),
                        FormDialog.choice(status, "trạng thái").id(), s, e};
                if (memberId == null) {
                    Db.update("""
                            INSERT INTO members (first_name, last_name, city, mobile_no, email_id, date_of_birth,
                                active_status_id, membership_start_date, membership_end_date)
                            VALUES (?,?,?,?,?,?,?,?,?)""", p);
                } else {
                    Db.update("""
                            UPDATE members SET first_name=?, last_name=?, city=?, mobile_no=?, email_id=?,
                                date_of_birth=?, active_status_id=?, membership_start_date=?, membership_end_date=?
                            WHERE member_id=?""", BooksPanel.concat(p, memberId));
                }
            });
            if (ok) App.refreshAll();
        } catch (Exception ex) {
            Ui.error(this, Db.message(ex));
        }
    }

    private void delete() {
        Integer id = selectedId("độc giả");
        if (id == null) return;
        int ok = JOptionPane.showConfirmDialog(this, "Xóa độc giả \"" + selectedValue(1) + "\"?\n"
                + "Chỉ xóa được độc giả chưa có lịch sử mượn, đặt sách hay thanh toán.\n"
                + "Với độc giả cũ, hãy đổi trạng thái sang 'expired' thay vì xóa.", "Xóa độc giả",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        Ui.async(this, () -> Db.update("DELETE FROM members WHERE member_id = ?", id), n -> App.refreshAll());
    }
}
