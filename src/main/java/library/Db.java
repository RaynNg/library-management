package library;

import javax.swing.table.DefaultTableModel;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/** Quản lý kết nối MySQL và các hàm truy vấn dùng chung. */
public final class Db {

    private static String url;
    private static String user;
    private static String password;

    private Db() {}

    public static void configure(String host, int port, String database, String u, String p) {
        url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
        user = u;
        password = p;
    }

    public static Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Thử kết nối, ném lỗi nếu thất bại. */
    public static void test() throws SQLException {
        try (Connection c = open()) {
            c.isValid(3);
        }
    }

    // ------------------------------------------------------------------ đọc

    /** Chạy câu SELECT và trả về TableModel chỉ đọc (dùng nhãn cột làm tiêu đề). */
    public static DefaultTableModel query(String sql, Object... params) throws SQLException {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return toModel(rs);
            }
        }
    }

    /** Dòng đầu tiên của câu SELECT, hoặc null nếu không có. */
    public static Object[] row(String sql, Object... params) throws SQLException {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int n = rs.getMetaData().getColumnCount();
                Object[] r = new Object[n];
                for (int i = 0; i < n; i++) r[i] = rs.getObject(i + 1);
                return r;
            }
        }
    }

    /** Danh sách lựa chọn dạng (id, nhãn) cho combobox / danh sách. */
    public static List<Item> items(String sql, Object... params) throws SQLException {
        List<Item> list = new ArrayList<>();
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(new Item(rs.getInt(1), rs.getString(2)));
            }
        }
        return list;
    }

    /** Các id trả về từ câu SELECT một cột. */
    public static List<Integer> ids(String sql, Object... params) throws SQLException {
        List<Integer> list = new ArrayList<>();
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getInt(1));
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ ghi

    /** INSERT / UPDATE / DELETE, trả về số dòng bị ảnh hưởng. */
    public static int update(String sql, Object... params) throws SQLException {
        try (Connection c = open()) {
            return update(c, sql, params);
        }
    }

    public static int update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /** INSERT và trả về khóa tự tăng vừa tạo. */
    public static int insert(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /** Công việc chạy trong một transaction. */
    @FunctionalInterface
    public interface Work<T> { T run(Connection c) throws SQLException; }

    /** Chạy work trong transaction: lỗi thì rollback toàn bộ. */
    public static <T> T tx(Work<T> work) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                T r = work.run(c);
                c.commit();
                return r;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ------------------------------------------------------------- procedure

    /** Gọi sp_issue_book, trả về mã phiếu mượn mới. */
    public static int issueBook(int bookId, int memberId, int staffId, Integer days) throws SQLException {
        try (Connection c = open(); CallableStatement cs = c.prepareCall("{call sp_issue_book(?,?,?,?)}")) {
            cs.setInt(1, bookId);
            cs.setInt(2, memberId);
            cs.setInt(3, staffId);
            if (days == null) cs.setNull(4, Types.INTEGER); else cs.setInt(4, days);
            if (cs.execute()) {
                try (ResultSet rs = cs.getResultSet()) {
                    if (rs.next()) return rs.getInt("issue_id");
                }
            }
            return -1;
        }
    }

    /** Gọi sp_return_book, trả về {số ngày trễ, tiền phạt}. */
    public static long[] returnBook(int issueId) throws SQLException {
        try (Connection c = open(); CallableStatement cs = c.prepareCall("{call sp_return_book(?)}")) {
            cs.setInt(1, issueId);
            if (cs.execute()) {
                try (ResultSet rs = cs.getResultSet()) {
                    if (rs.next()) return new long[]{rs.getLong("days_late"), rs.getLong("fine_amount")};
                }
            }
            return new long[]{0, 0};
        }
    }

    // ---------------------------------------------------------------- tiện ích

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p instanceof java.time.LocalDate d) ps.setDate(i + 1, java.sql.Date.valueOf(d));
            else ps.setObject(i + 1, p);
        }
    }

    private static DefaultTableModel toModel(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        Vector<String> cols = new Vector<>();
        for (int i = 1; i <= n; i++) cols.add(md.getColumnLabel(i));
        Vector<Vector<Object>> rows = new Vector<>();
        while (rs.next()) {
            Vector<Object> r = new Vector<>(n);
            for (int i = 1; i <= n; i++) r.add(rs.getObject(i));
            rows.add(r);
        }
        return new DefaultTableModel(rows, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                for (Vector<Object> row : rows) {
                    Object v = row.get(c);
                    if (v != null) return v.getClass();
                }
                return Object.class;
            }
        };
    }

    /** Thông báo dễ đọc từ lỗi SQL (gồm thông báo SIGNAL của trigger/procedure). */
    public static String message(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && !(t instanceof SQLException)) t = t.getCause();
        if (t instanceof SQLException s) {
            switch (s.getErrorCode()) {
                case 1451: return "Không thể xóa: dữ liệu này đang được dùng ở nơi khác "
                        + "(phiếu mượn, đặt sách, sách thuộc thể loại...).";
                case 1452: return "Dữ liệu tham chiếu không tồn tại (sách, độc giả hoặc nhân viên).";
                case 1062: return "Giá trị bị trùng với dữ liệu đã có:\n" + s.getMessage();
                case 3819: case 4025: return "Dữ liệu vi phạm ràng buộc kiểm tra (CHECK):\n" + s.getMessage();
                default: break;
            }
        }
        String m = t.getMessage();
        // Driver MariaDB thêm tiền tố "(conn=12) " vào thông báo; bỏ đi cho gọn
        return m == null ? t.toString() : m.replaceAll("\\(conn=\\d+\\)\\s*", "");
    }

    /** Một lựa chọn trong combobox. */
    public record Item(int id, String label) {
        @Override public String toString() { return label; }
    }
}
