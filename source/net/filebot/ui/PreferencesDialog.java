package net.filebot.ui;

import java.awt.Window;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import net.filebot.WebServices;
import net.filebot.postprocess.RenamePostProcessor;
import net.filebot.postprocess.RenamePostProcessor.Action;
import net.filebot.web.OpenSubtitlesRestClient;
import net.filebot.web.TheTVDBClient;
import net.miginfocom.swing.MigLayout;

public class PreferencesDialog extends JDialog {
	public PreferencesDialog(Window owner) {
		super(owner, "FB-Mod Preferences", ModalityType.DOCUMENT_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new MigLayout("insets 15, wrap 2", "[right][grow,fill]"));

		JComboBox<Theme> theme = new JComboBox<Theme>(Theme.values());
		theme.setSelectedItem(Theme.getSelected());
		add(new JLabel("Theme:"));
		add(theme);
		add(new JLabel("Theme changes take effect after restarting FileBot."), "span 2");

		add(new JLabel("After rename (movies and episodes):"), "span 2, gapy 12");
		Map<Action, JCheckBox> actions = new EnumMap<Action, JCheckBox>(Action.class);
		for (Action action : Action.values()) {
			JCheckBox checkbox = new JCheckBox(action.label, RenamePostProcessor.preferences().getBoolean(action.name(), false));
			actions.put(action, checkbox);
			add(checkbox, "span 2");
		}
		add(new JLabel("Existing sidecar files are never overwritten."), "span 2");
		JTextField language = new JTextField(RenamePostProcessor.preferences().get("language", "en"), 6);
		add(new JLabel("Subtitle language (e.g. en, pt):"));
		add(language);

		Preferences subtitleSettings = Preferences.userNodeForPackage(OpenSubtitlesRestClient.class);
		add(new JLabel("OpenSubtitles.com:"), "span 2, gapy 12");
		JPasswordField apiKey = new JPasswordField(subtitleSettings.get("apiKey", ""), 30);
		JTextField username = new JTextField(subtitleSettings.get("username", ""), 30);
		JPasswordField password = new JPasswordField(30);
		add(new JLabel("API key:"));
		add(apiKey);
		add(new JLabel("Username:"));
		add(username);
		add(new JLabel("Password (this session only):"));
		add(password);
		add(new JLabel("Your key and username are saved locally; password is not saved."), "span 2");
		JButton site = new JButton("OpenSubtitles.com API registration");
		site.addActionListener(evt -> net.filebot.util.ui.SwingUI.openURI("https://www.opensubtitles.com/en/consumers"));
		add(site, "span 2");

		Preferences tvdbSettings = TheTVDBClient.preferences();
		add(new JLabel("TheTVDB (API v4):"), "span 2, gapy 12");
		JPasswordField tvdbApiKey = new JPasswordField(tvdbSettings.get("apiKey", ""), 30);
		JPasswordField tvdbPin = new JPasswordField(tvdbSettings.get("pin", ""), 30);
		tvdbApiKey.setName("tvdbApiKey");
		tvdbPin.setName("tvdbPin");
		add(new JLabel("API key:"));
		add(tvdbApiKey);
		add(new JLabel("Subscriber PIN (if required):"));
		add(tvdbPin);
		add(new JLabel("A configured API key enables TheTVDB below TheMovieDB."), "span 2");
		add(new JLabel("Key and PIN are saved locally, unencrypted. Clear both to hide it."), "span 2");
		JButton tvdbSite = new JButton("TheTVDB API access");
		tvdbSite.addActionListener(evt -> net.filebot.util.ui.SwingUI.openURI("https://thetvdb.com/api-information"));
		add(tvdbSite, "span 2");

		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(evt -> dispose());
		add(cancel, "tag cancel");
		JButton save = new JButton("Save");
		save.addActionListener(evt -> {
			String code = language.getText().trim().toLowerCase(Locale.ROOT);
			if (!java.util.Arrays.asList(Locale.getISOLanguages()).contains(code)) {
				JOptionPane.showMessageDialog(this, "Enter a supported two-letter language code (e.g. en or pt).", "Invalid subtitle language", JOptionPane.WARNING_MESSAGE);
				return;
			}
			if (actions.get(Action.Subtitles).isSelected() && apiKey.getPassword().length == 0) {
				JOptionPane.showMessageDialog(this, "Enter your OpenSubtitles.com API key before enabling subtitle downloads.", "API key required", JOptionPane.WARNING_MESSAGE);
				return;
			}
			String tvdbKeyValue = new String(tvdbApiKey.getPassword()).trim();
			String tvdbPinValue = new String(tvdbPin.getPassword()).trim();
			if (tvdbKeyValue.isEmpty() && !tvdbPinValue.isEmpty()) {
				JOptionPane.showMessageDialog(this, "Enter a TheTVDB API v4 key, or clear the PIN to disable TheTVDB.", "TheTVDB API key required", JOptionPane.WARNING_MESSAGE);
				return;
			}
			((Theme) theme.getSelectedItem()).save();
			actions.forEach((action, checkbox) -> RenamePostProcessor.preferences().putBoolean(action.name(), checkbox.isSelected()));
			RenamePostProcessor.preferences().put("language", code);
			subtitleSettings.put("apiKey", new String(apiKey.getPassword()).trim());
			subtitleSettings.put("username", username.getText().trim());
			WebServices.OpenSubtitlesCom.setCredentials(username.getText().trim(), new String(password.getPassword()));
			tvdbSettings.put("apiKey", tvdbKeyValue);
			tvdbSettings.put("pin", tvdbPinValue);
			WebServices.TheTVDB.setCredentials(TheTVDBClient.isConfigured() ? TheTVDBClient.getConfiguredApiKey() : net.filebot.Settings.getApiKey("thetvdb"), TheTVDBClient.getConfiguredPin());
			password.setText("");
			dispose();
		});
		add(save, "tag ok");
		getRootPane().setDefaultButton(save);
		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
	}
}
