package net.filebot.ui;

import static java.awt.Cursor.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.util.Collection;
import java.util.prefs.Preferences;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import net.filebot.ResourceManager;
import net.filebot.util.ui.DefaultFancyListCellRenderer;
import net.filebot.web.SearchResult;
import net.filebot.web.SearchResultPreview;
import net.miginfocom.swing.MigLayout;

public class SelectDialog<T> extends JDialog {

	private JLabel messageLabel = new JLabel();
	private JCheckBox autoRepeatCheckBox = new JCheckBox();

	public interface PreviewProvider<T> {
		SearchResultPreview getPreview(T value) throws Exception;
	}

	private static class LoadedPreview {
		SearchResultPreview preview;
		Icon icon;
	}

	private static final int THUMB_WIDTH = 40;
	private static final int THUMB_HEIGHT = 60;

	private final Map<Object, LoadedPreview> previews = new ConcurrentHashMap<Object, LoadedPreview>();
	private PreviewProvider<T> previewProvider = null;
	private ExecutorService previewExecutor = null;
	private Icon placeholderIcon = null;

	private JList<T> list;
	private String command = null;

	public SelectDialog(Component parent, Collection<? extends T> options) {
		this(parent, options, false, false, null);
	}

	public SelectDialog(Component parent, Collection<? extends T> options, boolean autoRepeatEnabled, boolean autoRepeatSelected, JComponent header) {
		super(getWindow(parent), "Select", ModalityType.DOCUMENT_MODAL);

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		// initialize list
		list = new JList(options.toArray());

		// select first element
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setSelectedIndex(0);

		DefaultFancyListCellRenderer renderer = new DefaultFancyListCellRenderer(4) {

			@Override
			public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, convertValueToString(value), index, isSelected, cellHasFocus);
				configureValue(this, value);
				if (previewProvider != null) {
					LoadedPreview p = previews.get(value);
					setIcon(p != null && p.icon != null ? p.icon : placeholderIcon);
				}
				return this;
			}
		};

		renderer.setHighlightingEnabled(false);

		list.setCellRenderer(renderer);
		list.addMouseListener(mouseListener);

		JComponent c = (JComponent) getContentPane();
		c.setLayout(new MigLayout("insets 1.5mm 1.5mm 2.7mm 1.5mm, nogrid, novisualpadding, fill", "", header == null ? "[pref!][fill][pref!]" : "[min!][min!][fill][pref!]"));

		if (header != null) {
			c.add(header, "wmin 150px, hmin pref, growx, wrap");
		}
		c.add(messageLabel, "wmin 150px, hmin pref, growx, wrap");
		c.add(new JScrollPane(list), "wmin 150px, hmin 150px, grow, wrap 2mm");

		c.add(new JButton(selectAction), "align center, id select");
		c.add(new JButton(cancelAction), "gap unrel, id cancel");

		// add repeat button
		if (autoRepeatEnabled) {
			autoRepeatCheckBox.addChangeListener(evt -> autoRepeatCheckBox.setToolTipText(autoRepeatCheckBox.isSelected() ? "Select and remember for next time" : "Select once and ask again next time"));
			autoRepeatCheckBox.setCursor(getPredefinedCursor(HAND_CURSOR));
			autoRepeatCheckBox.setIcon(ResourceManager.getIcon("button.repeat"));
			autoRepeatCheckBox.setSelectedIcon(ResourceManager.getIcon("button.repeat.selected"));
			autoRepeatCheckBox.setSelected(autoRepeatSelected);
			c.add(autoRepeatCheckBox, "pos 1al select.y n select.y2");
		}

		// set default size and location
		setMinimumSize(new Dimension(400, 330));

		// Shortcut Enter
		installAction(list, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), selectAction);
	}

	public void setPreviewProvider(PreviewProvider<T> provider, int maxItems) {
		this.previewProvider = provider;
		this.placeholderIcon = createPlaceholderIcon();
		list.setFixedCellHeight(THUMB_HEIGHT + 12);

		previewExecutor = Executors.newFixedThreadPool(4, r -> {
			Thread t = new Thread(r, "SelectDialog-Preview");
			t.setDaemon(true);
			return t;
		});

		for (int i = 0; i < list.getModel().getSize() && i < maxItems; i++) {
			T value = list.getModel().getElementAt(i);
			previewExecutor.submit(() -> loadPreview(value));
		}
	}

	private Icon createPlaceholderIcon() {
		BufferedImage image = new BufferedImage(THUMB_WIDTH, THUMB_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(0, 0, 0, 90));
		g.fillRect(0, 0, THUMB_WIDTH, THUMB_HEIGHT);
		g.setColor(new Color(255, 255, 255, 50));
		g.drawRect(0, 0, THUMB_WIDTH - 1, THUMB_HEIGHT - 1);
		g.dispose();
		return new ImageIcon(image);
	}

	private void loadPreview(T value) {
		try {
			LoadedPreview loaded = new LoadedPreview();
			loaded.preview = previewProvider.getPreview(value);

			if (loaded.preview != null && loaded.preview.poster != null) {
				BufferedImage image = ImageIO.read(loaded.preview.poster);
				if (image != null) {
					BufferedImage thumb = new BufferedImage(THUMB_WIDTH, THUMB_HEIGHT, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g = thumb.createGraphics();
					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
					g.drawImage(image.getScaledInstance(THUMB_WIDTH, THUMB_HEIGHT, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
					g.dispose();
					loaded.icon = new ImageIcon(thumb);
				}
			}

			previews.put(value, loaded);
			SwingUtilities.invokeLater(list::repaint);
		} catch (Exception e) {
			// preview is optional, ignore failures
		}
	}

	@Override
	public void dispose() {
		if (previewExecutor != null) {
			previewExecutor.shutdownNow();
		}
		super.dispose();
	}

	protected String convertValueToString(Object value) {
		return value.toString();
	}

	protected void configureValue(DefaultFancyListCellRenderer render, Object value) {
		if (value instanceof SearchResult) {
			LoadedPreview p = previews.get(value);
			render.setToolTipText(p != null && p.preview != null ? getPreviewTooltipText((SearchResult) value, p.preview) : getTooltipText((SearchResult) value));
		} else if (value instanceof File) {
			render.setToolTipText(((File) value).getAbsolutePath());
		} else {
			render.setToolTipText(null);
		}
	}

	protected String getPreviewTooltipText(SearchResult item, SearchResultPreview p) {
		StringBuilder html = new StringBuilder(256);
		html.append("<html><body style='width: 320px'><b>").append(escapeHTML(item.toString())).append("</b>");
		if (p.year != null) {
			html.append(" (").append(escapeHTML(p.year)).append(")");
		}
		html.append("<br><br>");
		if (p.country != null) {
			html.append("<b>Country:</b> ").append(escapeHTML(p.country)).append("<br>");
		}
		if (p.overview != null && !p.overview.isEmpty()) {
			html.append("<b>Overview:</b> ").append(escapeHTML(p.overview)).append("<br>");
		}
		html.append("<b>ID:</b> ").append(Integer.toString(item.getId())).append("<br>");
		String[] names = item.getAliasNames();
		if (names.length > 0) {
			html.append("<b>AKA:</b> ");
			for (int i = 0; i < names.length && i < 25; i++) {
				html.append(i > 0 ? " | " : "").append(escapeHTML(names[i]));
			}
		}
		html.append("</body></html>");
		return html.toString();
	}

	protected String getTooltipText(SearchResult item) {
		StringBuilder html = new StringBuilder(64);
		html.append("<html><b>").append(escapeHTML(item.toString())).append("</b><br>");
		String[] names = item.getAliasNames();
		if (names.length > 0) {
			html.append("<br>AKA:<br>");
			for (String n : names) {
				html.append("• ").append(escapeHTML(n)).append("<br>");
			}
		}
		html.append("<br>ID: <br>• ").append(Integer.toString(item.getId())).append("</html>");
		return html.toString();
	}

	public JLabel getMessageLabel() {
		return messageLabel;
	}

	public JCheckBox getAutoRepeatCheckBox() {
		return autoRepeatCheckBox;
	}

	public String getSelectedAction() {
		return command;
	}

	public T getSelectedValue() {
		return SELECT.equals(command) ? list.getSelectedValue() : null;
	}

	public void close() {
		setVisible(false);
		dispose();
	}

	public Action getSelectAction() {
		return selectAction;
	}

	public Action getCancelAction() {
		return cancelAction;
	}

	public static final String SELECT = "Select";
	public static final String CANCEL = "Cancel";

	private final Action selectAction = newAction(SELECT, ResourceManager.getIcon("dialog.continue"), evt -> {
		command = SELECT;
		close();
	});

	private final Action cancelAction = newAction(CANCEL, ResourceManager.getIcon("dialog.cancel"), evt -> {
		command = CANCEL;
		close();
	});

	private final MouseAdapter mouseListener = mouseClicked(evt -> {
		if (SwingUtilities.isLeftMouseButton(evt) && (evt.getClickCount() == 2)) {
			selectAction.actionPerformed(new ActionEvent(evt.getSource(), ActionEvent.ACTION_PERFORMED, SELECT));
		}
	});

	private static final int MAX_RESTORED_WIDTH = 700;
	private static final String KEY_REPEAT = "dialog.select.repeat";
	private static final String KEY_WIDTH = "dialog.select.width";
	private static final String KEY_HEIGHT = "dialog.select.height";

	public void saveState(Preferences prefs) {
		prefs.putBoolean(KEY_REPEAT, autoRepeatCheckBox.isSelected());
		prefs.putInt(KEY_WIDTH, getWidth());
		prefs.putInt(KEY_HEIGHT, getHeight());
	}

	public void restoreState(Preferences prefs) {
		autoRepeatCheckBox.setSelected(prefs.getBoolean(KEY_REPEAT, autoRepeatCheckBox.isSelected()));
		setSize(Math.min(prefs.getInt(KEY_WIDTH, getWidth()), MAX_RESTORED_WIDTH), prefs.getInt(KEY_HEIGHT, getHeight()));
	}

}
