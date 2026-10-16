package net.filebot.util.ui;

import java.awt.Color;
import java.awt.event.ActionListener;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.Painter;
import javax.swing.UIDefaults;

import net.filebot.ui.Theme;
import net.miginfocom.swing.MigLayout;

public class ActionPopup extends JPopupMenu {

	protected final JLabel headerLabel = new JLabel();
	protected final JLabel descriptionLabel = new JLabel();
	protected final JLabel statusLabel = new JLabel();

	protected final JPanel actionPanel = new JPanel(new MigLayout("nogrid, insets 0, fill"));

	public ActionPopup(String label, Icon icon) {
		// fix text color (especially on Linux with dark GTK theme)
		setForeground(new JMenuItem().getForeground());
		if (Theme.isDark()) {
			Color background = new Color(0x232529);
			setBackground(background);
			setForeground(new Color(0xE6E6E6));
			UIDefaults overrides = new UIDefaults();
			overrides.put("PopupMenu[Enabled].backgroundPainter", (Painter<JPopupMenu>) (graphics, component, width, height) -> {
				graphics.setColor(background);
				graphics.fillRect(0, 0, width, height);
				graphics.setColor(new Color(0x62666E));
				graphics.drawRect(0, 0, width - 1, height - 1);
			});
			putClientProperty("Nimbus.Overrides", overrides);
			putClientProperty("Nimbus.Overrides.InheritDefaults", true);
		}

		headerLabel.setText(label);
		headerLabel.setForeground(getForeground());
		headerLabel.setIcon(icon);
		headerLabel.setIconTextGap(5);

		actionPanel.setOpaque(false);

		statusLabel.setFont(statusLabel.getFont().deriveFont(10f));
		statusLabel.setForeground(Theme.isDark() ? new Color(0xBDC3CC) : Color.GRAY);

		setLayout(new MigLayout("nogrid, fill, insets 0"));

		add(headerLabel, "gapx 5px 5px, gapy 3px 1px, wrap 3px");
		add(new JSeparator(), "growx, wrap 1px");
		add(actionPanel, "growx, wrap 0px");
		add(new JSeparator(), "growx, wrap 0px");
		add(statusLabel, "growx, h 11px!, gapx 3px, wrap 1px");

		// make it look better (e.g. window shadows) by forcing heavy-weight windows
		setLightWeightPopupEnabled(false);
	}

	protected JLabel createLabel(String text) {
		JLabel label = new JLabel(text);
		label.setForeground(getForeground());
		return label;
	}

	public void addDescription(String text) {
		actionPanel.add(createLabel(text), "gapx 4px 4px, growx, wrap 3px");
	}

	public void clearActions() {
		actionPanel.removeAll();
	}

	@Override
	public void addSeparator() {
		actionPanel.add(new JSeparator(), "growx, wrap 1px");
	}

	@Override
	public JMenuItem add(Action a) {
		LinkButton link = new LinkButton(a);

		// underline text
		link.setText(String.format("<html><nobr><u>%s</u></nobr></html>", link.getText()));

		// use rollover color
		link.setRolloverEnabled(false);
		link.setColor(Theme.isDark() ? new Color(0x8FCBFF) : link.getRolloverColor());

		// close popup when action is triggered
		link.addActionListener(closeListener);

		actionPanel.add(link, "gapx 12px 12px, growx, wrap");
		return null;
	}

	@Override
	public String getLabel() {
		return headerLabel.getText();
	}

	public void setStatus(String string) {
		statusLabel.setText(string);
	}

	private final ActionListener closeListener = evt -> setVisible(false);

}
