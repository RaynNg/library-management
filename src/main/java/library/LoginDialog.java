package library;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/** Hộp thoại kết nối MySQL. Nhớ lại thông tin (trừ mật khẩu) cho lần sau. */
public class LoginDialog extends JDialog {

    private final Preferences prefs = Preferences.userNodeForPackage(LoginDialog.class);
    private final JTextField host = new JTextField(prefs.get("host", "localhost"), 16);
    private final JTextField port = new JTextField(prefs.get("port", "3306"), 6);
    private final JTextField db = new JTextField(prefs.get("db", "library_db"), 16);
    private final JTextField user = new JTextField(prefs.get("user", "root"), 16);
    private final JPasswordField pass = new JPasswordField(16);
    private boolean ok;

    public LoginDialog() {
        super((Frame) null, "Kết nối cơ sở dữ liệu thư viện", true);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        String[] labels = {"Máy chủ:", "Cổng:", "Database:", "Tài khoản:", "Mật khẩu:"};
        JComponent[] fields = {host, port, db, user, pass};
        for (int i = 0; i < labels.length; i++) {
            g.gridx = 0; g.gridy = i; g.weightx = 0;
            form.add(new JLabel(labels[i]), g);
            g.gridx = 1; g.weightx = 1;
            form.add(fields[i], g);
        }

        JButton connect = new JButton("Kết nối");
        JButton cancel = new JButton("Thoát");
        connect.addActionListener(e -> connect(connect));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(connect);

        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(connect);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        SwingUtilities.invokeLater(pass::requestFocusInWindow);
    }

    private void connect(JButton btn) {
        int p;
        try {
            p = Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException ex) {
            Ui.error(this, "Cổng phải là số.");
            return;
        }
        Db.configure(host.getText().trim(), p, db.getText().trim(),
                user.getText().trim(), new String(pass.getPassword()));
        btn.setEnabled(false);
        Ui.async(this, () -> { Db.test(); return true; }, r -> {
            prefs.put("host", host.getText().trim());
            prefs.put("port", port.getText().trim());
            prefs.put("db", db.getText().trim());
            prefs.put("user", user.getText().trim());
            ok = true;
            dispose();
        }, () -> btn.setEnabled(true));
    }

    public String summary() {
        return user.getText().trim() + "@" + host.getText().trim() + ":" + port.getText().trim()
                + " / " + db.getText().trim();
    }

    public boolean isOk() { return ok; }
}
