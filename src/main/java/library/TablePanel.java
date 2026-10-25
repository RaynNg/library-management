package library;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Pattern;

/** Bảng dữ liệu chung: tìm kiếm nhanh, nút làm mới, nạp từ một câu SELECT. */
public class TablePanel extends JPanel implements Reloadable {

    private final String sql;
    protected final JTable table = new JTable();
    private final JTextField search = new JTextField(22);
    private final JLabel count = new JLabel(" ");
    protected final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    private Runnable onDoubleClick;
    private Runnable afterLoad;

    public TablePanel(String sql) {
        super(new BorderLayout());
        this.sql = sql;

        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Ui.styleTable(table);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && onDoubleClick != null && table.getSelectedRow() >= 0) {
                    onDoubleClick.run();
                }
            }
        });

        JButton refresh = new JButton("Làm mới");
        refresh.addActionListener(e -> reload());
        toolbar.add(new JLabel("Tìm:"));
        toolbar.add(search);
        toolbar.add(refresh);
        toolbar.add(Box.createHorizontalStrut(12));

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bottom.add(count, BorderLayout.WEST);

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    /** Thêm nút vào thanh công cụ. */
    protected JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        toolbar.add(b);
        return b;
    }

    protected void onDoubleClick(Runnable r) { onDoubleClick = r; }

    protected void afterLoad(Runnable r) { afterLoad = r; }

    /** Câu SQL thực tế (lớp con có thể đổi theo bộ lọc). */
    protected String currentSql() { return sql; }

    @Override public void reload() {
        Object keep = selectedValue(0);
        Ui.async(this, () -> Db.query(currentSql()), model -> {
            table.setModel(model);
            table.setRowSorter(new TableRowSorter<>(model));
            Ui.fitColumns(table);
            applyFilter();
            if (keep != null) reselect(keep);
            if (afterLoad != null) afterLoad.run();
        });
    }

    private void reselect(Object id) {
        for (int i = 0; i < table.getRowCount(); i++) {
            if (id.equals(table.getValueAt(i, 0))) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    private void applyFilter() {
        if (!(table.getRowSorter() instanceof TableRowSorter<?> s)) return;
        @SuppressWarnings("unchecked")
        TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) s;
        String text = search.getText().trim();
        sorter.setRowFilter(text.isEmpty() ? null
                : RowFilter.regexFilter("(?iu)" + Pattern.quote(text)));
        count.setText(table.getRowCount() + " / " + table.getModel().getRowCount() + " dòng");
    }

    /** Giá trị ở cột col (theo model) của dòng đang chọn, hoặc null. */
    public Object selectedValue(int col) {
        int r = table.getSelectedRow();
        if (r < 0 || col >= table.getModel().getColumnCount()) return null;
        return table.getModel().getValueAt(table.convertRowIndexToModel(r), col);
    }

    /** Mã (cột đầu) của dòng đang chọn; báo lỗi và trả về null nếu chưa chọn. */
    protected Integer selectedId(String what) {
        Object v = selectedValue(0);
        if (v == null) {
            Ui.error(this, "Hãy chọn một " + what + " trong bảng.");
            return null;
        }
        return ((Number) v).intValue();
    }
}
