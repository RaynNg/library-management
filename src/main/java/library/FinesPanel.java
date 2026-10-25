package library;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

/** Công nợ phạt: tổng hợp theo độc giả, lịch sử phạt / thanh toán, thu tiền. */
public class FinesPanel extends TablePanel {

    static final String SQL = """
            SELECT member_id AS `Mã ĐG`, member_name AS `Độc giả`,
                   total_fine AS `Tổng phạt (VND)`, total_paid AS `Đã trả (VND)`,
                   balance AS `Còn nợ (VND)`
            FROM v_member_fine_balance
            WHERE total_fine > 0 OR total_paid > 0
            ORDER BY balance DESC, member_id
            """;

    static final String HISTORY = """
            SELECT x.ngay AS `Ngày`, x.loai AS `Loại`, x.so_tien AS `Số tiền (VND)`, x.ghi_chu AS `Ghi chú`
            FROM (
                SELECT fd.fine_date AS ngay, 'Phạt' AS loai, fd.fine_total AS so_tien,
                       CONCAT('Phiếu ', bi.issue_id, ': ', b.book_title,
                              IF(bi.issue_status = 'lost', ' (bồi thường mất sách)', '')) AS ghi_chu
                FROM fine_due fd
                JOIN book_issue bi ON bi.issue_id = fd.issue_id
                JOIN book b        ON b.book_id = bi.book_id
                WHERE bi.member_id = ?
                UNION ALL
                SELECT payment_date, 'Thanh toán', payment_amount, CONCAT('Biên lai số ', fine_payment_id)
                FROM fine_payment WHERE member_id = ?
            ) x
            ORDER BY x.ngay, x.loai DESC
            """;

    private final JTable history = new JTable();
    private final JLabel historyTitle = new JLabel(" Chọn một độc giả để xem lịch sử phạt và thanh toán");

    public FinesPanel() {
        super(SQL);
        button("Thu tiền phạt", this::collect);
        onDoubleClick(this::collect);

        history.setRowHeight(22);
        Ui.styleTable(history);
        history.setFillsViewportHeight(true);
        JPanel south = new JPanel(new BorderLayout());
        historyTitle.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        south.add(historyTitle, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(history);
        sp.setPreferredSize(new Dimension(100, 200));
        south.add(sp, BorderLayout.CENTER);

        // Đặt bảng chính và bảng lịch sử trong cùng một khung chia đôi
        Component main = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.CENTER);
        remove(main);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, main, south);
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadHistory();
        });
        afterLoad(this::loadHistory);
    }

    private void loadHistory() {
        Object id = selectedValue(0);
        if (id == null) {
            history.setModel(new javax.swing.table.DefaultTableModel());
            historyTitle.setText(" Chọn một độc giả để xem lịch sử phạt và thanh toán");
            return;
        }
        Object name = selectedValue(1);
        Ui.async(this, () -> Db.query(HISTORY, id, id), m -> {
            history.setModel(m);
            Ui.fitColumns(history);
            historyTitle.setText(" Lịch sử của " + name + " (mã " + id + ")");
        });
    }

    private void collect() {
        Integer id = selectedId("độc giả");
        if (id == null) return;
        long balance = FormDialog.asLong(selectedValue(4));
        if (balance <= 0) {
            Ui.info(this, selectedValue(1) + " không còn nợ phạt.");
            return;
        }
        FormDialog d = new FormDialog(this, "Thu tiền phạt");
        d.add("Độc giả:", new JLabel(selectedValue(1) + " (mã " + id + ")"));
        d.add("Đang nợ:", new JLabel(LoansPanel.money(balance) + " VND"));
        JTextField amount = d.add("Số tiền thu (VND) *:", FormDialog.field(balance, 12));
        JTextField date = d.add("Ngày thu *:", FormDialog.field(LocalDate.now(), 10));
        boolean ok = d.open(() -> {
            Integer v = FormDialog.integer(amount, "số tiền thu", true);
            if (v <= 0) throw new IllegalArgumentException("Số tiền thu phải lớn hơn 0.");
            if (v > balance)
                throw new IllegalArgumentException("Số tiền thu không được lớn hơn số đang nợ ("
                        + LoansPanel.money(balance) + " VND).");
            Db.update("INSERT INTO fine_payment (member_id, payment_date, payment_amount) VALUES (?, ?, ?)",
                    id, FormDialog.date(date, "ngày thu", true), v);
        });
        if (ok) {
            Ui.info(this, "Đã ghi nhận thanh toán.");
            App.refreshAll();
        }
    }
}
