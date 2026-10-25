package library;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Danh mục sách: xem, thêm, sửa, xóa; gán tác giả (book_author). */
public class BooksPanel extends TablePanel {

    static final String SQL = """
            SELECT book_id AS `Mã sách`, isbn_code AS `ISBN`, book_title AS `Tên sách`,
                   authors AS `Tác giả`, category_name AS `Thể loại`, publisher_name AS `NXB`,
                   publication_year AS `Năm XB`, book_language AS `Ngôn ngữ`,
                   copies_total AS `Tổng số`, copies_available AS `Còn lại`,
                   location_text AS `Vị trí`
            FROM v_book_catalog ORDER BY book_title
            """;

    public BooksPanel() {
        super(SQL);
        button("Thêm sách", () -> edit(null));
        button("Sửa", () -> { Integer id = selectedId("sách"); if (id != null) edit(id); });
        button("Xóa", this::delete);
        onDoubleClick(() -> edit(selectedId("sách")));
    }

    private void edit(Integer bookId) {
        try {
            List<Db.Item> categories = Db.items("SELECT category_id, category_name FROM category ORDER BY category_name");
            List<Db.Item> publishers = Db.items("SELECT publisher_id, publisher_name FROM publisher ORDER BY publisher_name");
            List<Db.Item> locations = new ArrayList<>();
            locations.add(new Db.Item(0, "(chưa xếp kệ)"));
            locations.addAll(Db.items("SELECT location_id, CONCAT('Tầng ', floor_no, ' - Kệ ', shelf_no, "
                    + "IFNULL(CONCAT(' (', shelf_name, ')'), '')) FROM location ORDER BY floor_no, shelf_no"));
            List<Db.Item> authors = Db.items("SELECT author_id, CONCAT(last_name, ' ', first_name) "
                    + "FROM author ORDER BY last_name, first_name");

            Object[] b = bookId == null ? new Object[12] : Db.row("""
                    SELECT isbn_code, book_title, category_id, publisher_id, publication_year,
                           book_edition, book_language, publication_type, copies_total,
                           copies_available, location_id
                    FROM book WHERE book_id = ?""", bookId);
            if (b == null) { Ui.error(this, "Không tìm thấy sách."); return; }
            List<Integer> chosen = bookId == null ? List.of()
                    : Db.ids("SELECT author_id FROM book_author WHERE book_id = ?", bookId);
            int onLoan = bookId == null ? 0
                    : (int) (FormDialog.asLong(b[8]) - FormDialog.asLong(b[9]));

            FormDialog d = new FormDialog(this, bookId == null ? "Thêm sách" : "Sửa sách – mã " + bookId);
            JTextField title = d.add("Tên sách *:", FormDialog.field(b[1], 30));
            JTextField isbn = d.add("ISBN:", FormDialog.field(b[0], 16));
            JComboBox<Db.Item> cat = d.add("Thể loại *:", FormDialog.combo(categories, b[2]));
            JComboBox<Db.Item> pub = d.add("Nhà xuất bản *:", FormDialog.combo(publishers, b[3]));
            JTextField year = d.add("Năm xuất bản:", FormDialog.field(b[4], 6));
            JTextField edition = d.add("Lần xuất bản:", FormDialog.field(b[5], 16));
            JTextField lang = d.add("Ngôn ngữ:", FormDialog.field(b[6] == null ? "Tiếng Việt" : b[6], 16));
            JTextField type = d.add("Loại ấn phẩm:", FormDialog.field(b[7] == null ? "Sách in" : b[7], 16));
            JTextField total = d.add("Tổng số bản *:", FormDialog.field(b[8] == null ? 1 : b[8], 6));
            JComboBox<Db.Item> loc = d.add("Vị trí:", FormDialog.combo(locations, b[10] == null ? 0 : b[10]));

            // Tác giả đang gán cho sách được đưa lên đầu danh sách để dễ thấy
            List<Db.Item> ordered = new ArrayList<>();
            for (Db.Item a : authors) if (chosen.contains(a.id())) ordered.add(a);
            int nChosen = ordered.size();
            for (Db.Item a : authors) if (!chosen.contains(a.id())) ordered.add(a);
            JList<Db.Item> authorList = new JList<>(ordered.toArray(new Db.Item[0]));
            authorList.setVisibleRowCount(7);
            if (nChosen > 0) authorList.setSelectionInterval(0, nChosen - 1);
            JScrollPane authorScroll = new JScrollPane(authorList);
            authorScroll.setPreferredSize(new Dimension(260, 150));
            d.add("Tác giả:", authorScroll);
            d.note("Giữ Ctrl để chọn nhiều tác giả. Thêm tác giả, thể loại, NXB, kệ mới ở tab \"Danh mục phụ\"."
                    + (onLoan > 0 ? "<br>Sách đang có " + onLoan + " bản được mượn/báo mất, "
                    + "tổng số bản không được nhỏ hơn số này." : ""));

            boolean ok = d.open(() -> {
                String t = FormDialog.text(title, "tên sách", true);
                Integer y = FormDialog.integer(year, "năm xuất bản", false);
                if (y != null && (y < 1000 || y > 2100))
                    throw new IllegalArgumentException("Năm xuất bản phải trong khoảng 1000 - 2100.");
                Integer tot = FormDialog.integer(total, "tổng số bản", true);
                if (tot < onLoan)
                    throw new IllegalArgumentException("Tổng số bản không được nhỏ hơn " + onLoan
                            + " (số bản đang được mượn hoặc báo mất).");
                int catId = FormDialog.choice(cat, "thể loại").id();
                int pubId = FormDialog.choice(pub, "nhà xuất bản").id();
                Integer locId = FormDialog.choice(loc, "vị trí").id();
                if (locId == 0) locId = null;
                Object[] common = {FormDialog.text(isbn, "ISBN", false), t, catId, pubId, y,
                        FormDialog.text(edition, "lần xuất bản", false),
                        FormDialog.text(lang, "ngôn ngữ", false),
                        FormDialog.text(type, "loại ấn phẩm", false)};
                List<Db.Item> picked = authorList.getSelectedValuesList();
                Integer locFinal = locId;

                Db.tx(c -> {
                    int id;
                    if (bookId == null) {
                        id = Db.insert(c, """
                                INSERT INTO book (isbn_code, book_title, category_id, publisher_id,
                                    publication_year, book_edition, book_language, publication_type,
                                    copies_total, copies_available, location_id)
                                VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                                concat(common, tot, tot, locFinal));
                    } else {
                        id = bookId;
                        // Số bản còn lại thay đổi theo đúng phần chênh lệch của tổng số bản
                        Db.update(c, """
                                UPDATE book SET isbn_code=?, book_title=?, category_id=?, publisher_id=?,
                                    publication_year=?, book_edition=?, book_language=?, publication_type=?,
                                    copies_available = copies_available + (? - copies_total),
                                    copies_total=?, location_id=?
                                WHERE book_id=?""",
                                concat(common, tot, tot, locFinal, id));
                        Db.update(c, "DELETE FROM book_author WHERE book_id = ?", id);
                    }
                    for (Db.Item a : picked) {
                        Db.update(c, "INSERT INTO book_author (book_id, author_id) VALUES (?, ?)", id, a.id());
                    }
                    return id;
                });
            });
            if (ok) App.refreshAll();
        } catch (Exception ex) {
            Ui.error(this, Db.message(ex));
        }
    }

    private void delete() {
        Integer id = selectedId("sách");
        if (id == null) return;
        int ok = JOptionPane.showConfirmDialog(this, "Xóa sách \"" + selectedValue(2) + "\"?\n"
                + "Chỉ xóa được sách chưa từng có phiếu mượn hay yêu cầu đặt.", "Xóa sách",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        Ui.async(this, () -> Db.update("DELETE FROM book WHERE book_id = ?", id), n -> App.refreshAll());
    }

    static Object[] concat(Object[] a, Object... b) {
        Object[] r = new Object[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
