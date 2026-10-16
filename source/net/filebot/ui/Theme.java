package net.filebot.ui;

import java.awt.Color;
import java.util.prefs.Preferences;

import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

import net.filebot.util.ui.SwingUI;

public enum Theme {
	System, Light, Dark;

	public static Theme getSelected() {
		return valueOf(Preferences.userNodeForPackage(Theme.class).get("theme", System.name()));
	}

	public void save() {
		Preferences.userNodeForPackage(Theme.class).put("theme", name());
	}

	public void apply() {
		UIManager.put("FileBot.darkTheme", this == Dark);
		if (this == System) {
			SwingUI.setSystemLookAndFeel();
			return;
		}
		if (this == Dark) {
			setColor("control", 48, 50, 54);
			setColor("info", 48, 50, 54);
			setColor("nimbusBase", 40, 44, 50);
			setColor("nimbusBlueGrey", 62, 66, 73);
			setColor("nimbusLightBackground", 35, 37, 41);
			setColor("text", 230, 230, 230);
			setColor("nimbusSelectedText", 255, 255, 255);
			setColor("nimbusSelectionBackground", 52, 103, 155);
			setColor("nimbusDisabledText", 145, 145, 145);
		}
		SwingUI.setNimbusLookAndFeel();
		if (this == Dark) {
			Color foreground = new ColorUIResource(230, 230, 230);
			for (String component : new String[] { "Label", "Button", "ToggleButton", "CheckBox", "RadioButton", "Menu", "MenuItem", "CheckBoxMenuItem", "RadioButtonMenuItem", "TextField", "TextArea", "EditorPane", "TextPane", "List", "Table", "Tree", "ComboBox", "ToolTip" }) {
				UIManager.put(component + ".foreground", foreground);
			}
		}
	}

	public static boolean isDark() {
		return UIManager.getBoolean("FileBot.darkTheme");
	}

	private static void setColor(String key, int red, int green, int blue) {
		UIManager.put(key, new ColorUIResource(new Color(red, green, blue)));
	}
}
