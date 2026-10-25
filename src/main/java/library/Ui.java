package library;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Tiện ích giao diện: chạy truy vấn ở luồng nền, báo lỗi, định dạng bảng. */
public final class Ui {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat MONEY = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));

    private Ui() {}

    /** Chạy task ở luồng nền, rồi gọi onDone trên luồng giao diện. Lỗi hiện thành hộp thoại. */
    public static <T> void async(Component owner, Callable<T> task, Consumer<T> onDone) {
        async(owner, task, onDone, null);
    }

    /** Như trên, thêm onFail chạy sau khi đã báo lỗi. */
    public static <T> void async(Component owner, Callable<T> task, Consumer<T> onDone, Runnable onFail) {
        Window w = owner == null ? null
                : owner instanceof Window win ? win : SwingUtilities.getWindowAncestor(owner);
        Cursor old = w == null ? null : w.getCursor();
        if (w != null) w.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return task.call(); }
            @Override protected void done() {
                if (w != null) w.setCursor(old);
                T result;
                try {
                    result = get();
                } catch (Exception ex) {
                    error(owner, Db.message(ex));
                    if (onFail != null) onFail.run();
                    return;
                }
                onDone.accept(result);
            }
        }.execute();
    }

    public static void error(Component owner, String msg) {
        JOptionPane.showMessageDialog(owner, msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component owner, String msg) {
        JOptionPane.showMessageDialog(owner, msg, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Ngày dạng dd/MM/yyyy; số tiền có dấu chấm ngăn cách; số khác giữ nguyên. */
    public static void styleTable(JTable table) {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                     boolean focus, int row, int col) {
                String name = t.getColumnName(col);
                Object shown = v;
                int align = LEFT;
                if (v instanceof java.sql.Date d) shown = d.toLocalDate().format(DATE);
                else if (v instanceof LocalDate d) shown = d.format(DATE);
                else if (v instanceof LocalDateTime d) shown = d.toLocalDate().format(DATE);
                else if (v instanceof Number n) {
                    align = RIGHT;
                    shown = isMoney(name) ? MONEY.format(n.longValue()) : String.valueOf(v);
                }
                super.getTableCellRendererComponent(t, shown, sel, focus, row, col);
                setHorizontalAlignment(align);
                return this;
            }
        };
        table.setDefaultRenderer(Object.class, r);
        table.setDefaultRenderer(Number.class, r);
        table.setDefaultRenderer(java.util.Date.class, r);
    }

    private static boolean isMoney(String column) {
        String c = column.toLowerCase();
        return c.contains("vnd") || c.contains("nợ") || c.contains("phạt") || c.contains("số tiền");
    }

    /** Đặt độ rộng cột theo nội dung (tối đa 340px), phần dư chia cho các cột. */
    public static void fitColumns(JTable table) {
        FontMetrics hfm = table.getTableHeader().getFontMetrics(table.getTableHeader().getFont());
        int rows = Math.min(table.getRowCount(), 300);
        for (int c = 0; c < table.getColumnCount(); c++) {
            TableColumn col = table.getColumnModel().getColumn(c);
            int w = hfm.stringWidth(table.getColumnName(c)) + 18;
            for (int r = 0; r < rows; r++) {
                Component comp = table.prepareRenderer(table.getCellRenderer(r, c), r, c);
                w = Math.max(w, comp.getPreferredSize().width + 10);
            }
            col.setPreferredWidth(Math.min(w, 340));
        }
        table.doLayout();
    }
}
