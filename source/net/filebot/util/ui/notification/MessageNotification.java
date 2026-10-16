/*
 * Created on 16.03.2005
 */

package net.filebot.util.ui.notification;

import static javax.swing.BorderFactory.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicTextPaneUI;

public class MessageNotification extends NotificationWindow {

	public MessageNotification(String head, String text, Icon icon, int timeout) {
		this((Window) null, head, text, icon, timeout);
	}

	public MessageNotification(String head, String text, Icon icon) {
		this(head, text, icon, -1);
	}

	private int margin = 10;
	private Border marginBorder = createEmptyBorder(margin, margin, margin, margin);
	private Border border = createCompoundBorder(createEtchedBorder(new Color(245, 155, 15), Color.WHITE), marginBorder);

	private JLabel headLabel;
	private JTextPane textArea;
	private JLabel imageLabel;

	public MessageNotification(Window owner, String head, String text, Icon icon, int timeout) {
		super(owner, timeout);

		JComponent c = (JComponent) getContentPane();

		c.setLayout(new BorderLayout(5, 2));
		c.setBackground(Color.WHITE);
		c.setBorder(border);

		JPanel textPanel = new JPanel(new BorderLayout());
		textPanel.setOpaque(false);
		textPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

		headLabel = new JLabel(head);
		headLabel.setForeground(Color.BLACK);
		headLabel.setHorizontalAlignment(SwingConstants.CENTER);
		headLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		textPanel.add(headLabel, BorderLayout.NORTH);

		// use a look-and-feel independent UI so the popup looks the same as the light theme in every theme
		textArea = new JTextPane() {

			@Override
			public void updateUI() {
				setUI(new BasicTextPaneUI());
			}
		};
		textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		textArea.setForeground(Color.BLACK);
		textArea.setBackground(Color.WHITE);
		textArea.setCaretColor(Color.BLACK);
		textArea.setSelectionColor(new Color(57, 105, 138));
		textArea.setSelectedTextColor(Color.WHITE);
		textArea.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		textArea.setText(text);
		textArea.setEditable(false);
		textArea.setOpaque(false);
		textPanel.add(textArea, BorderLayout.CENTER);

		if (icon != null) {
			imageLabel = new JLabel(icon);
			c.add(imageLabel, BorderLayout.WEST);
		}

		c.add(textPanel, BorderLayout.CENTER);
		pack();

		// copy message to clipboard
		getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		getGlassPane().addMouseListener(mouseClicked(evt -> copyToClipboard(text)));
	}

}
