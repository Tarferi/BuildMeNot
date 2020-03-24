package cz.rion.buildserver.ui.utils;

import javax.swing.JLabel;

public class MyLabel extends JLabel {

	public MyLabel() {
		this("");
	}

	public MyLabel(String contents) {
		super(contents);
		setFont(FontProvider.LabelFont);
	}
}
