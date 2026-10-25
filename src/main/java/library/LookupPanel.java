package library;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Danh mục phụ: thể loại, nhà xuất bản, tác giả, vị trí kệ, nhân viên. */
public class LookupPanel extends JPanel implements Reloadable {

    /** Một cột có thể nhập. */
    record Field(String column, String label, boolean required, boolean number) {}

    /** Cấu hình một bảng danh mục. */
    record Spec(String title, String table, String idColumn, String extraSql, List<Field> fields) {}

    static final List<Spec> SPECS = List.of(
            new Spec("Thể loại", "category", "category_id",
                    "(SELECT COUNT(*) FROM book b WHERE b.category_id = t.category_id)",
                    List.of(new Field("category_name", "Tên thể loại", true, false))),
            new Spec("Nhà xuất bản", "publisher", "publisher_id",
                    "(SELECT COUNT(*) FROM book b WHERE b.publisher_id = t.publisher_id)",
                    List.of(new Field("publisher_name", "Tên NXB", true, false))),
            new Spec("Tác giả", "author", "author_id",
                    "(SELECT COUNT(*) FROM book_author ba WHERE ba.author_id = t.author_id)",
                    List.of(new Field("last_name", "Họ (và tên đệm)", true, false),
                            new Field("first_name", "Tên", true, false))),
            new Spec("Vị trí kệ", "location", "location_id",
                    "(SELECT COUNT(*) FROM book b WHERE b.location_id = t.location_id)",
                    List.of(new Field("floor_no", "Tầng", true, true),
                            new Field("shelf_no", "Mã kệ", true, false),
                            new Field("shelf_name", "Tên kệ", false, false))),
            new Spec("Nhân viên", "library_staff", "staff_id",
                    "(SELECT COUNT(*) FROM book_issue bi WHERE bi.issued_by_id = t.staff_id)",
                    List.of(new Field("staff_name", "Họ tên", true, false),
                            new Field("staff_designation", "Chức vụ", false, false)))
    );

    private final List<Reloadable> parts = new ArrayList<>();

    public LookupPanel() {
        super(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.LEFT);
        for (Spec s : SPECS) {
            SpecPanel p = new SpecPanel(s);
            parts.add(p);
            tabs.addTab(s.title(), p);
        }
        add(tabs, BorderLayout.CENTER);
    }

    @Override public void reload() { parts.forEach(Reloadable::reload); }

    static String selectSql(Spec s) {
        StringBuilder sb = new StringBuilder("SELECT t.").append(s.idColumn()).append(" AS `Mã`");
        for (Field f : s.fields()) sb.append(", t.").append(f.column()).append(" AS `").append(f.label()).append('`');
        String usage = s.table().equals("library_staff") ? "Số phiếu đã lập" : "Số sách";
        sb.append(", ").append(s.extraSql()).append(" AS `").append(usage).append('`');
        sb.append(" FROM ").append(s.table()).append(" t ORDER BY ");
        sb.append(s.table().equals("location") ? "t.floor_no, t.shelf_no" : "t." + s.idColumn());
        return sb.toString();
    }

    /** Bảng của một danh mục với nút thêm / sửa / xóa. */
    static class SpecPanel extends TablePanel {
        private final Spec spec;

        SpecPanel(Spec spec) {
            super(selectSql(spec));
            this.spec = spec;
            button("Thêm", () -> edit(null));
            button("Sửa", () -> { Integer id = selectedId("dòng"); if (id != null) edit(id); });
            button("Xóa", this::delete);
            onDoubleClick(() -> edit(selectedId("dòng")));
        }

        private void edit(Integer id) {
            FormDialog d = new FormDialog(this, (id == null ? "Thêm " : "Sửa ") + spec.title().toLowerCase());
            List<JTextField> inputs = new ArrayList<>();
            for (int i = 0; i < spec.fields().size(); i++) {
                Field f = spec.fields().get(i);
                Object v = id == null ? null : selectedValue(i + 1);
                inputs.add(d.add(f.label() + (f.required() ? " *:" : ":"), FormDialog.field(v, 24)));
            }
            boolean ok = d.open(() -> {
                List<Object> values = new ArrayList<>();
                List<String> cols = new ArrayList<>();
                for (int i = 0; i < spec.fields().size(); i++) {
                    Field f = spec.fields().get(i);
                    JTextField in = inputs.get(i);
                    String name = f.label().toLowerCase();
                    values.add(f.number() ? FormDialog.integer(in, name, f.required())
                            : FormDialog.text(in, name, f.required()));
                    cols.add(f.column());
                }
                String sql;
                if (id == null) {
                    sql = "INSERT INTO " + spec.table() + " (" + String.join(", ", cols) + ") VALUES ("
                            + String.join(", ", java.util.Collections.nCopies(cols.size(), "?")) + ")";
                } else {
                    sql = "UPDATE " + spec.table() + " SET " + String.join(" = ?, ", cols) + " = ? WHERE "
                            + spec.idColumn() + " = ?";
                    values.add(id);
                }
                Db.update(sql, values.toArray());
            });
            if (ok) App.refreshAll();
        }

        private void delete() {
            Integer id = selectedId("dòng");
            if (id == null) return;
            // Tác giả xóa sẽ tự gỡ khỏi sách (ON DELETE CASCADE), nên chặn trước ở đây cho mọi danh mục
            long used = FormDialog.asLong(selectedValue(spec.fields().size() + 1));
            if (used > 0) {
                Ui.error(this, "Không thể xóa \"" + selectedValue(1) + "\" vì đang được dùng ("
                        + used + "). Hãy chuyển các sách / phiếu liên quan trước.");
                return;
            }
            int ok = JOptionPane.showConfirmDialog(this, "Xóa \"" + selectedValue(1) + "\"?", "Xóa",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) return;
            Ui.async(this, () -> Db.update("DELETE FROM " + spec.table() + " WHERE " + spec.idColumn() + " = ?", id),
                    n -> App.refreshAll());
        }
    }
}
