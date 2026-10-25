package library;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Điểm khởi chạy: hộp thoại kết nối rồi cửa sổ chính dạng tab. */
public class App {

    static final String OVERDUE = """
            SELECT issue_id AS `Mã phiếu`, member_id AS `Mã ĐG`, member_name AS `Độc giả`,
                   mobile_no AS `Điện thoại`, book_title AS `Sách`, issue_date AS `Ngày mượn`,
                   due_date AS `Hạn trả`, days_overdue AS `Số ngày quá hạn`,
                   days_overdue * 2000 AS `Phạt nếu trả hôm nay (VND)`
            FROM v_overdue_books ORDER BY days_overdue DESC
            """;

    private static final List<Reloadable> SCREENS = new ArrayList<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::start);
    }

    private static void start() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // giữ giao diện mặc định
        }
        if (!loadDriver()) {
            Ui.error(null, "Không tìm thấy MySQL JDBC driver (mysql-connector-j).\n"
                    + "Hãy build bằng Maven hoặc thêm file .jar của driver vào classpath.");
            System.exit(1);
        }

        LoginDialog login = new LoginDialog();
        login.setVisible(true);
        if (!login.isOk()) System.exit(0);

        JFrame frame = buildMainFrame(login.summary());
        frame.setVisible(true);
        refreshAll();
    }

    /** Nạp driver MySQL (hoặc MariaDB nếu dùng MariaDB). */
    static boolean loadDriver() {
        for (String d : new String[]{"com.mysql.cj.jdbc.Driver", "org.mariadb.jdbc.Driver"}) {
            try {
                Class.forName(d);
                return true;
            } catch (ClassNotFoundException ignored) {
                // thử driver kế tiếp
            }
        }
        return false;
    }

    static JFrame buildMainFrame(String connectionSummary) {
        JFrame frame = new JFrame("Quản lý thư viện");
        JTabbedPane tabs = new JTabbedPane();
        SCREENS.clear();
        addTab(tabs, "Tổng quan", new DashboardPanel());
        addTab(tabs, "Mượn / Trả", new LoansPanel());
        addTab(tabs, "Sách quá hạn", new TablePanel(OVERDUE));
        addTab(tabs, "Sách", new BooksPanel());
        addTab(tabs, "Độc giả", new MembersPanel());
        addTab(tabs, "Công nợ phạt", new FinesPanel());
        addTab(tabs, "Đặt sách", new RequestsPanel());
        addTab(tabs, "Danh mục phụ", new LookupPanel());

        JLabel status = new JLabel("  Đã kết nối: " + connectionSummary);
        status.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        frame.getContentPane().add(tabs, BorderLayout.CENTER);
        frame.getContentPane().add(status, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1280, 740);
        frame.setLocationRelativeTo(null);
        return frame;
    }

    private static void addTab(JTabbedPane tabs, String title, JComponent screen) {
        tabs.addTab(title, screen);
        SCREENS.add((Reloadable) screen);
    }

    /** Nạp lại mọi màn hình, gọi sau mỗi lần dữ liệu thay đổi. */
    public static void refreshAll() {
        SCREENS.forEach(Reloadable::reload);
    }
}
