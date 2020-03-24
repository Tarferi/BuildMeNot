package cz.rion.buildserver.ui.utils;

import java.awt.Component;
import java.awt.Image;

import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;

import cz.rion.buildserver.ui.icons.IconManager;

public class BetterListCellRenderer extends DefaultListCellRenderer {

	private JLabel label;

	private boolean icons = false;

	@Override
	public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		label.setFont(list.getFont());
		if (icons) {
			String path = value.toString();
			ImageIcon ico = IconManager.IconForFile(path);
			ico.setImage(ico.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH));
			label.setIcon(ico);
		}
		return label;
	}

	public void setIconsEnabled(boolean b) {
		this.icons = b;
	}
}
