package library;

import javax.swing.*;
import java.awt.*;

/** Tổng quan: các số liệu chính và sách được mượn nhiều nhất. */
public class DashboardPanel extends JPanel implements Reloadable {

    static final String STATS = """
            SELECT (SELECT COUNT(*) FROM book),
                   (SELECT COALESCE(SUM(copies_total), 0) FROM book),
                   (SELECT COALESCE(SUM(copies_available), 0) FROM book),
                   (SELECT COUNT(*) FROM book_issue WHERE return_date IS NULL AND issue_status <> 'lost'),
                   (SELECT COUNT(*) FROM v_overdue_books),
                   (SELECT COUNT(*) FROM members m JOIN member_status ms
                         ON ms.active_status_id = m.active_status_id WHERE ms.account_status = 'active'),
                   (SELECT COUNT(*) FROM members),
                   (SELECT COALESCE(SUM(balance), 0) FROM v_member_fine_balance WHERE balance > 0),
                   (SELECT COUNT(*) FROM book_request r JOIN book_request_status s
                         ON s.available_status_id = r.available_status_id
                     WHERE s.available_status IN ('pending', 'available'))
            """;

    static final String TOP = """
            SELECT b.book_title AS `Sách`, COUNT(*) AS `Số lượt mượn`,
                   b.copies_available AS `Còn lại`
            FROM book_issue bi JOIN book b ON b.book_id = bi.book_id
            GROUP BY b.book_id, b.book_title, b.copies_available
            ORDER BY COUNT(*) DESC, b.book_title
            LIMIT 8
            """;

    private final JLabel[] values = new JLabel[6];
    private final JLabel[] subs = new JLabel[6];
    private final JTable top = new JTable();

    public DashboardPanel() {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        String[] titles = {"Đầu sách", "Bản sao còn trên kệ", "Đang được mượn",
                "Phiếu quá hạn", "Độc giả đang active", "Tổng nợ phạt (VND)"};
        Color[] accents = {new Color(0x2F6FB0), new Color(0x2E8B57), new Color(0x6A5ACD),
                new Color(0xC0392B), new Color(0x1F7A8C), new Color(0xB9770E)};
        JPanel cards = new JPanel(new GridLayout(2, 3, 12, 12));
        for (int i = 0; i < titles.length; i++) {
            JPanel card = new JPanel(new BorderLayout(0, 2));
            card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 5, 0, 0, accents[i]),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(0xDDDDDD)),
                            BorderFactory.createEmptyBorder(10, 14, 10, 14))));
            JLabel t = new JLabel(titles[i]);
            t.setForeground(new Color(0x555555));
            values[i] = new JLabel("…");
            values[i].setFont(values[i].getFont().deriveFont(Font.BOLD, 28f));
            values[i].setForeground(accents[i]);
            subs[i] = new JLabel(" ");
            subs[i].setForeground(Color.GRAY);
            card.add(t, BorderLayout.NORTH);
            card.add(values[i], BorderLayout.CENTER);
            card.add(subs[i], BorderLayout.SOUTH);
            cards.add(card);
        }

        top.setRowHeight(24);
        Ui.styleTable(top);
        top.setFillsViewportHeight(true);
        JPanel topPanel = new JPanel(new BorderLayout(0, 6));
        JLabel topTitle = new JLabel("Sách được mượn nhiều nhất");
        topTitle.setFont(topTitle.getFont().deriveFont(Font.BOLD, 14f));
        topPanel.add(topTitle, BorderLayout.NORTH);
        topPanel.add(new JScrollPane(top), BorderLayout.CENTER);

        add(cards, BorderLayout.NORTH);
        add(topPanel, BorderLayout.CENTER);
    }

    @Override public void reload() {
        Ui.async(this, () -> new Object[]{Db.row(STATS), Db.query(TOP)}, r -> {
            Object[] s = (Object[]) r[0];
            long[] v = new long[s.length];
            for (int i = 0; i < s.length; i++) v[i] = FormDialog.asLong(s[i]);
            values[0].setText(String.valueOf(v[0]));
            subs[0].setText(v[1] + " bản sao");
            values[1].setText(String.valueOf(v[2]));
            subs[1].setText("trên tổng " + v[1] + " bản");
            values[2].setText(String.valueOf(v[3]));
            subs[2].setText(v[8] + " yêu cầu đặt sách đang chờ");
            values[3].setText(String.valueOf(v[4]));
            subs[3].setText("chưa trả, đã quá hạn");
            values[4].setText(String.valueOf(v[5]));
            subs[4].setText("trên tổng " + v[6] + " độc giả");
            values[5].setText(LoansPanel.money(v[7]));
            subs[5].setText("chưa thanh toán");
            top.setModel((javax.swing.table.TableModel) r[1]);
            Ui.fitColumns(top);
        });
    }
}
