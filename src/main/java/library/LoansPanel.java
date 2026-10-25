package library;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Mượn / trả sách: gọi sp_issue_book, sp_return_book; báo mất sách. */
public class LoansPanel extends TablePanel {

    static final String BASE = """
            SELECT bi.issue_id   AS `Mã phiếu`,
                   m.member_id   AS `Mã ĐG`,
                   CONCAT(m.last_name, ' ', m.first_name) AS `Độc giả`,
                   b.book_title  AS `Sách`,
                   bi.issue_date AS `Ngày mượn`,
                   bi.due_date   AS `Hạn trả`,
                   bi.return_date AS `Ngày trả`,
                   CASE WHEN bi.issue_status = 'lost'   THEN 'Báo mất'
                        WHEN bi.return_date IS NOT NULL THEN 'Đã trả'
                        WHEN bi.due_date < CURDATE()    THEN 'Quá hạn'
                        ELSE 'Đang mượn' END AS `Trạng thái`,
                   s.staff_name  AS `Người lập`
            FROM book_issue bi
            JOIN members m       ON m.member_id = bi.member_id
            JOIN book b          ON b.book_id = bi.book_id
            JOIN library_staff s ON s.staff_id = bi.issued_by_id
            """;
    private static final int COL_STATUS = 7;

    private final JComboBox<Db.Item> bookBox = new JComboBox<>();
    private final JComboBox<Db.Item> memberBox = new JComboBox<>();
    private final JComboBox<Db.Item> staffBox = new JComboBox<>();
    private final JSpinner daysSpinner = new JSpinner(new SpinnerNumberModel(14, 1, 365, 1));
    private final JCheckBox onlyOpen = new JCheckBox("Chỉ phiếu chưa trả", true);

    public LoansPanel() {
        super(BASE);
        onlyOpen.addActionListener(e -> reload());
        toolbar.add(onlyOpen);
        button("Trả sách", this::returnSelected);
        button("Báo mất sách", this::reportLost);
        onDoubleClick(this::returnSelected);
        add(buildIssueForm(), BorderLayout.WEST);
    }

    private JPanel buildIssueForm() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(8, 8, 8, 4),
                BorderFactory.createTitledBorder("Lập phiếu mượn")));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        Db.Item proto = new Db.Item(0, "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
        bookBox.setPrototypeDisplayValue(proto);
        memberBox.setPrototypeDisplayValue(proto);
        staffBox.setPrototypeDisplayValue(proto);

        int y = 0;
        y = row(p, g, y, "Sách (còn bản sao):", bookBox);
        y = row(p, g, y, "Độc giả (đang active):", memberBox);
        y = row(p, g, y, "Thủ thư lập phiếu:", staffBox);
        y = row(p, g, y, "Số ngày mượn:", daysSpinner);

        JButton issue = new JButton("Cho mượn");
        issue.addActionListener(e -> issue());
        g.gridy = y++;
        p.add(issue, g);

        JLabel note = new JLabel("<html><small>Database tự kiểm tra: độc giả phải active,<br>"
                + "tối đa 5 cuốn đang mượn, sách phải còn bản sao.<br>"
                + "Trả trễ bị phạt 2.000 VND / ngày.<br><br>"
                + "Nhấp đúp một phiếu trong bảng để trả sách.</small></html>");
        note.setForeground(Color.GRAY);
        g.gridy = y++;
        p.add(note, g);

        g.gridy = y; g.weighty = 1;
        p.add(Box.createVerticalGlue(), g);
        return p;
    }

    private static int row(JPanel p, GridBagConstraints g, int y, String label, JComponent c) {
        g.gridy = y;
        p.add(new JLabel(label), g);
        g.gridy = y + 1;
        p.add(c, g);
        return y + 2;
    }

    @Override protected String currentSql() {
        return BASE + (onlyOpen.isSelected() ? " WHERE bi.return_date IS NULL" : "")
                + " ORDER BY bi.issue_id DESC";
    }

    @Override public void reload() {
        super.reload();
        Ui.async(this, () -> List.of(
                Db.items("SELECT book_id, CONCAT(book_title, ' (còn ', copies_available, ')') "
                        + "FROM book WHERE copies_available > 0 ORDER BY book_title"),
                Db.items("SELECT m.member_id, CONCAT(m.member_id, ' - ', m.last_name, ' ', m.first_name) "
                        + "FROM members m JOIN member_status ms ON ms.active_status_id = m.active_status_id "
                        + "WHERE ms.account_status = 'active' ORDER BY m.member_id"),
                Db.items("SELECT staff_id, staff_name FROM library_staff ORDER BY staff_id")
        ), r -> {
            fill(bookBox, r.get(0));
            fill(memberBox, r.get(1));
            fill(staffBox, r.get(2));
        });
    }

    private static void fill(JComboBox<Db.Item> box, List<Db.Item> items) {
        Object keep = box.getSelectedItem();
        box.removeAllItems();
        for (Db.Item it : items) box.addItem(it);
        if (keep instanceof Db.Item k) FormDialog.select(box, k.id());
    }

    private void issue() {
        Db.Item book, member, staff;
        try {
            book = FormDialog.choice(bookBox, "sách");
            member = FormDialog.choice(memberBox, "độc giả");
            staff = FormDialog.choice(staffBox, "thủ thư");
        } catch (IllegalArgumentException ex) {
            Ui.error(this, ex.getMessage());
            return;
        }
        int days = (Integer) daysSpinner.getValue();
        Ui.async(this, () -> Db.issueBook(book.id(), member.id(), staff.id(), days), id -> {
            Ui.info(this, "Đã lập phiếu mượn số " + id + ".\n"
                    + "Sách: " + book.label().replaceAll(" \\(còn \\d+\\)$", "") + "\n"
                    + "Hạn trả sau " + days + " ngày.");
            App.refreshAll();
        });
    }

    /** Mã phiếu đang chọn nếu phiếu còn mở; ngược lại báo lỗi và trả null. */
    private Integer openSelected() {
        Integer id = selectedId("phiếu mượn");
        if (id == null) return null;
        Object status = selectedValue(COL_STATUS);
        if ("Đã trả".equals(status)) {
            Ui.error(this, "Phiếu " + id + " đã được trả.");
            return null;
        }
        if ("Báo mất".equals(status)) {
            Ui.error(this, "Phiếu " + id + " đã báo mất sách.");
            return null;
        }
        return id;
    }

    private void returnSelected() {
        Integer issueId = openSelected();
        if (issueId == null) return;
        String title = String.valueOf(selectedValue(3));
        int ok = JOptionPane.showConfirmDialog(this,
                "Trả sách \"" + title + "\" (phiếu số " + issueId + ")?", "Trả sách",
                JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        Ui.async(this, () -> Db.returnBook(issueId), r -> {
            String msg = r[0] > 0
                    ? "Trả trễ " + r[0] + " ngày.\nTiền phạt: " + money(r[1]) + " VND."
                    : "Trả đúng hạn, không bị phạt.";
            Ui.info(this, "Đã trả sách phiếu số " + issueId + ".\n" + msg);
            App.refreshAll();
        });
    }

    /** Đánh dấu mất sách và ghi khoản bồi thường vào fine_due. Bản sao không được cộng lại kho. */
    private void reportLost() {
        Integer issueId = openSelected();
        if (issueId == null) return;
        FormDialog d = new FormDialog(this, "Báo mất sách – phiếu " + issueId);
        d.add("Sách:", new JLabel(String.valueOf(selectedValue(3))));
        d.add("Độc giả:", new JLabel(String.valueOf(selectedValue(2))));
        JTextField amount = d.add("Tiền bồi thường (VND):", FormDialog.field(80000, 12));
        d.note("Phiếu chuyển sang trạng thái 'lost', khoản bồi thường được ghi vào công nợ phạt.<br>"
                + "Bản sao bị mất không được cộng lại vào số sách còn lại.");
        boolean done = d.open(() -> {
            Integer v = FormDialog.integer(amount, "tiền bồi thường", true);
            if (v <= 0) throw new IllegalArgumentException("Tiền bồi thường phải lớn hơn 0.");
            Db.tx(c -> {
                int n = Db.update(c, "UPDATE book_issue SET issue_status = 'lost' "
                        + "WHERE issue_id = ? AND return_date IS NULL AND issue_status <> 'lost'", issueId);
                if (n == 0) throw new IllegalArgumentException("Phiếu không còn ở trạng thái đang mượn.");
                Db.update(c, "INSERT INTO fine_due (issue_id, fine_date, fine_total) VALUES (?, CURDATE(), ?)",
                        issueId, v);
                return null;
            });
        });
        if (done) {
            Ui.info(this, "Đã ghi nhận mất sách và khoản bồi thường.");
            App.refreshAll();
        }
    }

    static String money(Object v) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(FormDialog.asLong(v));
    }
}
