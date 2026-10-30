package net.filebot.ui;

import static org.junit.Assert.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.junit.Test;

import net.filebot.ui.rename.RenamePanelBuilder;
import net.filebot.ui.subtitle.SubtitlePanelBuilder;
import net.filebot.util.ui.ActionPopup;
import net.filebot.web.TheTVDBClient;

public class UserInterfaceSmokeTest {
	@Test
	public void buttonPopupsGrowUpwards() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Theme.Light.apply();
			javax.swing.JFrame frame = new javax.swing.JFrame();
			try {
				JButton button = new JButton("Fetch Data");
				frame.add(button);
				frame.setBounds(100, 500, 200, 80);
				frame.setVisible(true);

				ActionPopup popup = new ActionPopup("Fetch & Match Data", null);
				popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
					@Override
					public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {
						// simulate menus that are populated while being shown
						for (int i = 0; i < 6; i++) {
							popup.add(new javax.swing.AbstractAction("Provider " + i) {
								@Override
								public void actionPerformed(java.awt.event.ActionEvent event) {
								}
							});
						}
					}

					@Override
					public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {
					}

					@Override
					public void popupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {
					}
				});

				net.filebot.util.ui.SwingUI.showPopupAbove(popup, button);
				try {
					assertTrue(popup.isShowing());
					int popupBottom = popup.getLocationOnScreen().y + popup.getHeight();
					assertTrue("popup should end above the button", popupBottom <= button.getLocationOnScreen().y);
				} finally {
					popup.setVisible(false);
				}
			} finally {
				frame.dispose();
			}
		});
	}

	@Test
	public void messageNotificationsLookLikeLightThemeForAllThemes() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			BufferedImage light = null;
			for (Theme theme : new Theme[] { Theme.Light, Theme.Dark, Theme.System }) {
				theme.apply();
				net.filebot.util.ui.notification.MessageNotification notification =
						new net.filebot.util.ui.notification.MessageNotification("Rename complete", "Renamed 2 files.", null);
				try {
					assertEquals(java.awt.Color.WHITE, notification.getContentPane().getBackground());
					for (Component component : descendants(notification.getContentPane())) {
						if (component instanceof javax.swing.JLabel || component instanceof javax.swing.JTextPane) {
							assertEquals(theme.name(), java.awt.Color.BLACK, component.getForeground());
						}
					}

					Container content = notification.getContentPane();
					content.setSize(content.getPreferredSize());
					layoutAll(content);
					BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
					java.awt.Graphics2D graphics = image.createGraphics();
					content.paint(graphics);
					graphics.dispose();

					if (light == null) {
						light = image;
					} else {
						assertEquals(theme.name() + " size", light.getWidth(), image.getWidth());
						assertEquals(theme.name() + " size", light.getHeight(), image.getHeight());
						int different = 0;
						for (int x = 0; x < image.getWidth(); x++) {
							for (int y = 0; y < image.getHeight(); y++) {
								if (light.getRGB(x, y) != image.getRGB(x, y)) {
									different++;
								}
							}
						}
						assertEquals(theme.name() + " differs from Light theme", 0, different);
					}
				} finally {
					notification.dispose();
				}
			}
		});
	}

	private static void layoutAll(Container container) {
		container.doLayout();
		for (Component component : container.getComponents()) {
			if (component instanceof Container) {
				layoutAll((Container) component);
			}
		}
	}

	@Test
	public void darkActionMenusHaveReadablePaintedContrast() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Theme.Dark.apply();
			ActionPopup popup = new ActionPopup("Rename Options", null);
			popup.addDescription("Action:");
			popup.add(new javax.swing.AbstractAction("Rename") {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent event) {
				}
			});
			popup.setStatus("Ready");
			popup.setSize(popup.getPreferredSize());
			popup.doLayout();
			for (Component component : popup.getComponents()) {
				if (component instanceof Container) {
					((Container) component).doLayout();
				}
			}
			BufferedImage image = new BufferedImage(popup.getWidth(), popup.getHeight(), BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D graphics = image.createGraphics();
			try {
				popup.paint(graphics);
			} finally {
				graphics.dispose();
			}
			java.awt.Color paintedBackground = new java.awt.Color(image.getRGB(popup.getWidth() - 5, popup.getHeight() - 5));
			assertEquals(new java.awt.Color(0x232529), paintedBackground);
			for (Component component : descendants(popup)) {
				if (component instanceof javax.swing.JLabel || component instanceof javax.swing.AbstractButton) {
					double contrast = (luminance(component.getForeground()) + 0.05) / (luminance(paintedBackground) + 0.05);
					assertTrue("Menu text contrast must be at least 4.5:1: " + contrast, contrast >= 4.5);
				}
			}
		});
	}

	private static double luminance(java.awt.Color color) {
		double[] channels = { color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0 };
		for (int i = 0; i < channels.length; i++) {
			channels[i] = channels[i] <= 0.04045 ? channels[i] / 12.92 : Math.pow((channels[i] + 0.055) / 1.055, 2.4);
		}
		return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
	}

	@Test
	public void darkThemePanelsAndPreferencesRender() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Theme.Dark.apply();
			assertTrue(Theme.isDark());
			assertTrue(UIManager.getColor("control").getRed() < 80);
			PanelBuilder[] panels = { new RenamePanelBuilder(), new SubtitlePanelBuilder() };
			MainFrame frame = new MainFrame(panels);
			PreferencesDialog preferences = null;
			try {
				frame.setVisible(true);
				frame.validate();
				BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
				frame.paint(image.getGraphics());
				assertTrue(frame.isShowing());
				frame.selectPanel(panels[1]);
				frame.validate();
				assertTrue(java.util.Arrays.stream(frame.getContentPane().getComponents()).anyMatch(it -> it instanceof net.filebot.ui.subtitle.SubtitlePanel && it.isVisible()));
				HeaderPanel header = new HeaderPanel();
				assertTrue(header.getTitleLabel().getForeground().getRed() > 200);
				preferences = new PreferencesDialog(frame);
				preferences.setModal(false);
				preferences.setVisible(true);
				assertEquals("FB-Mod Preferences", preferences.getTitle());
				assertTrue(preferences.isShowing());
				List<String> preferenceOptions = descendants(preferences.getContentPane()).stream()
						.filter(javax.swing.JCheckBox.class::isInstance)
						.map(javax.swing.JCheckBox.class::cast)
						.map(javax.swing.JCheckBox::getText)
						.collect(java.util.stream.Collectors.toList());
				assertTrue(preferenceOptions.contains("Download poster artwork per episode/movie"));
				assertTrue(preferenceOptions.contains("Download poster artwork per season (series only)"));
				assertTrue(preferences.getWidth() > 300);
				assertTrue(preferences.getHeight() < java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height);
				preferences.validate();
				BufferedImage settingsImage = new BufferedImage(preferences.getWidth(), preferences.getHeight(), BufferedImage.TYPE_INT_RGB);
				preferences.paint(settingsImage.getGraphics());
				String snapshot = System.getProperty("filebot.smoke.image");
				if (snapshot != null) {
					try {
						javax.imageio.ImageIO.write(image, "png", new java.io.File(snapshot));
						javax.imageio.ImageIO.write(settingsImage, "png", new java.io.File(snapshot + ".preferences.png"));
					} catch (java.io.IOException e) {
						throw new java.io.UncheckedIOException(e);
					}
				}
			} finally {
				if (preferences != null) {
					preferences.dispose();
				}
				frame.dispose();
			}
		});
	}

	@Test
	public void artworkFilenameControlsToggleAndPersistIndependently() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			java.util.prefs.Preferences settings = net.filebot.postprocess.RenamePostProcessor.preferences();
			java.util.Map<String, String> saved = new java.util.LinkedHashMap<String, String>();
			for (String key : new String[] { "ArtworkDefaultFilename", "ArtworkFilename", "SeasonArtworkDefaultFilename", "SeasonArtworkFilename", "Subtitles", "language" }) {
				saved.put(key, settings.get(key, null));
				settings.remove(key);
			}
			PreferencesDialog dialog = null;
			PreferencesDialog reopened = null;
			try {
				dialog = new PreferencesDialog(null);
				List<Component> components = descendants(dialog.getContentPane());
				javax.swing.JCheckBox artworkDefault = components.stream().filter(it -> "ArtworkDefaultFilename".equals(it.getName())).map(javax.swing.JCheckBox.class::cast).findFirst().orElseThrow();
				javax.swing.JCheckBox seasonDefault = components.stream().filter(it -> "SeasonArtworkDefaultFilename".equals(it.getName())).map(javax.swing.JCheckBox.class::cast).findFirst().orElseThrow();
				javax.swing.JTextField artworkName = components.stream().filter(it -> "ArtworkFilename".equals(it.getName())).map(javax.swing.JTextField.class::cast).findFirst().orElseThrow();
				javax.swing.JTextField seasonName = components.stream().filter(it -> "SeasonArtworkFilename".equals(it.getName())).map(javax.swing.JTextField.class::cast).findFirst().orElseThrow();
				assertTrue(artworkDefault.isSelected());
				assertTrue(seasonDefault.isSelected());
				assertFalse(artworkName.isEnabled());
				assertFalse(seasonName.isEnabled());
				artworkDefault.doClick();
				assertTrue(artworkName.isEnabled());
				assertFalse(seasonName.isEnabled());
				artworkName.setText("poster.jpg");
				seasonDefault.doClick();
				assertTrue(seasonName.isEnabled());
				seasonName.setText("season.jpeg");
				seasonDefault.doClick();
				assertFalse(seasonName.isEnabled());
				assertEquals("season.jpeg", seasonName.getText());
				components.stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(it -> "Save".equals(it.getText())).findFirst().orElseThrow().doClick();
				assertFalse(dialog.isDisplayable());
				assertFalse(settings.getBoolean("ArtworkDefaultFilename", true));
				assertTrue(settings.getBoolean("SeasonArtworkDefaultFilename", false));
				assertEquals("poster.jpg", settings.get("ArtworkFilename", ""));
				assertEquals("season.jpeg", settings.get("SeasonArtworkFilename", ""));
				reopened = new PreferencesDialog(null);
				for (Component component : descendants(reopened.getContentPane())) {
					if ("ArtworkFilename".equals(component.getName())) {
						assertTrue(component.isEnabled());
						assertEquals("poster.jpg", ((javax.swing.JTextField) component).getText());
					}
					if ("SeasonArtworkFilename".equals(component.getName())) {
						assertFalse(component.isEnabled());
						assertEquals("season.jpeg", ((javax.swing.JTextField) component).getText());
					}
				}
			} finally {
				if (dialog != null) {
					dialog.dispose();
				}
				if (reopened != null) {
					reopened.dispose();
				}
				saved.forEach((key, value) -> {
					if (value == null) {
						settings.remove(key);
					} else {
						settings.put(key, value);
					}
				});
			}
		});
	}

	@Test
	public void savingTheTVDBCredentialsUpdatesExistingMenu() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			java.util.prefs.Preferences settings = TheTVDBClient.preferences();
			String oldKey = settings.get("apiKey", "");
			String oldPin = settings.get("pin", "");
			PanelBuilder rename = new RenamePanelBuilder();
			MainFrame frame = new MainFrame(new PanelBuilder[] { rename });
			try {
				settings.put("apiKey", "");
				settings.put("pin", "");
				frame.selectPanel(rename);
				frame.setVisible(true);
				List<Component> components = descendants(frame.getContentPane());
				ActionPopup popup = components.stream().filter(JComponent.class::isInstance).map(JComponent.class::cast)
						.map(JComponent::getComponentPopupMenu).filter(ActionPopup.class::isInstance).map(ActionPopup.class::cast)
						.filter(it -> "Fetch & Match Data".equals(it.getLabel())).findFirst().orElseThrow();
				popup.show(frame, 20, 20);
				assertFalse(menuText(popup).contains("TheTVDB"));
				popup.setVisible(false);
				saveTVDB(frame, "ui-test-key", "ui-test-pin");
				assertTrue(TheTVDBClient.isConfigured());
				assertEquals("ui-test-pin", settings.get("pin", ""));
				popup.show(frame, 20, 20);
				String menu = menuText(popup);
				assertTrue(menu.contains("TheTVDB"));
				assertTrue(menu.indexOf("TheMovieDB") < menu.indexOf("TheTVDB"));
				assertTrue(menu.indexOf("TheTVDB") < menu.indexOf("AniDB"));
				popup.setVisible(false);
				saveTVDB(frame, "", "");
				popup.show(frame, 20, 20);
				assertFalse(menuText(popup).contains("TheTVDB"));
				popup.setVisible(false);
			} finally {
				settings.put("apiKey", oldKey);
				settings.put("pin", oldPin);
				net.filebot.WebServices.TheTVDB.setCredentials(oldKey, oldPin);
				frame.dispose();
			}
		});
	}

	private static void saveTVDB(MainFrame owner, String key, String pin) {
		PreferencesDialog dialog = new PreferencesDialog(owner);
		try {
			List<Component> components = descendants(dialog.getContentPane());
			components.stream().filter(it -> "tvdbApiKey".equals(it.getName())).map(JPasswordField.class::cast).findFirst().orElseThrow().setText(key);
			components.stream().filter(it -> "tvdbPin".equals(it.getName())).map(JPasswordField.class::cast).findFirst().orElseThrow().setText(pin);
			components.stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(it -> "Save".equals(it.getText())).findFirst().orElseThrow().doClick();
		} finally {
			dialog.dispose();
		}
	}

	private static String menuText(ActionPopup popup) {
		return descendants(popup).stream().filter(javax.swing.AbstractButton.class::isInstance).map(javax.swing.AbstractButton.class::cast)
				.map(javax.swing.AbstractButton::getText).collect(java.util.stream.Collectors.joining(" "));
	}

	private static List<Component> descendants(Container container) {
		List<Component> components = new ArrayList<Component>();
		for (Component component : container.getComponents()) {
			components.add(component);
			if (component instanceof Container) {
				components.addAll(descendants((Container) component));
			}
		}
		return components;
	}
}
