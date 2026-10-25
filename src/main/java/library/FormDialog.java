package library;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Hộp thoại nhập liệu dạng nhãn - ô nhập. Khi bấm Lưu, hành động onSave được chạy;
 * nếu nó ném lỗi thì hiện thông báo và giữ hộp thoại mở để sửa.
 */
public class FormDialog extends JDialog {

    /** Hành động lưu, có thể ném lỗi kiểm tra hoặc lỗi SQL. */
    @FunctionalInterface
    public interface Save { void run() throws Exception; }

    private final JPanel form = new JPanel(new GridBagLayout());
    private final GridBagConstraints g = new GridBagConstraints();
    private int rows;
    private boolean saved;

    public FormDialog(Component owner, String title) {
        super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), title,
                ModalityType.APPLICATION_MODAL);
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.NORTHWEST;
    }

    /** Thêm một dòng "nhãn: ô nhập" và trả lại chính ô nhập. */
    public <C extends JComponent> C add(String label, C comp) {
        g.gridx = 0; g.gridy = rows; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        JLabel l = new JLabel(label);
        l.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        form.add(l, g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        form.add(comp, g);
        rows++;
        return comp;
    }

    /** Thêm dòng ghi chú màu xám. */
    public void note(String html) {
        JLabel l = new JLabel("<html><small>" + html + "</small></html>");
        l.setForeground(Color.GRAY);
        g.gridx = 0; g.gridy = rows++; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL;
        form.add(l, g);
        g.gridwidth = 1;
    }

    /** Hiện hộp thoại; trả về true nếu đã lưu thành công. */
    public boolean open(Save onSave) {
        JButton save = new JButton("Lưu");
        JButton cancel = new JButton("Hủy");
        save.addActionListener(e -> {
            try {
                onSave.run();
                saved = true;
                dispose();
            } catch (IllegalArgumentException ex) {
                Ui.error(this, ex.getMessage());
            } catch (Exception ex) {
                Ui.error(this, Db.message(ex));
            }
        });
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(save);

        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(Math.max(getWidth(), 420), getHeight()));
        setLocationRelativeTo(getOwner());
        setVisible(true);
        return saved;
    }

    // ------------------------------------------------- đọc và kiểm tra dữ liệu

    /** Chuỗi đã cắt khoảng trắng; rỗng thì null (hoặc lỗi nếu bắt buộc). */
    public static String text(JTextField f, String name, boolean required) {
        String s = f.getText().trim();
        if (s.isEmpty()) {
            if (required) throw new IllegalArgumentException("Vui lòng nhập " + name + ".");
            return null;
        }
        return s;
    }

    public static Integer integer(JTextField f, String name, boolean required) {
        String s = text(f, name, required);
        if (s == null) return null;
        try {
            return Integer.valueOf(s.replace(".", "").replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " phải là số nguyên.");
        }
    }

    /** Ngày dạng yyyy-MM-dd. */
    public static LocalDate date(JTextField f, String name, boolean required) {
        String s = text(f, name, required);
        if (s == null) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " phải có dạng năm-tháng-ngày, ví dụ 2026-10-26.");
        }
    }

    public static Db.Item choice(JComboBox<Db.Item> box, String name) {
        Object o = box.getSelectedItem();
        if (o instanceof Db.Item it) return it;
        throw new IllegalArgumentException("Vui lòng chọn " + name + ".");
    }

    // ------------------------------------------------------- tạo ô nhập

    public static JTextField field(Object value, int cols) {
        return new JTextField(value == null ? "" : String.valueOf(value), cols);
    }

    public static JComboBox<Db.Item> combo(List<Db.Item> items, Object selectedId) {
        JComboBox<Db.Item> box = new JComboBox<>(items.toArray(new Db.Item[0]));
        select(box, selectedId);
        return box;
    }

    public static void select(JComboBox<Db.Item> box, Object id) {
        if (id == null) return;
        int want = ((Number) id).intValue();
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).id() == want) { box.setSelectedIndex(i); return; }
        }
    }

    public static long asLong(Object o) {
        if (o == null) return 0;
        if (o instanceof BigDecimal b) return b.longValue();
        return ((Number) o).longValue();
    }
}
