package cz.rion.buildserver.ui.utils;

import javax.swing.JTextArea;

public class MyTextArea extends JTextArea {

	public MyTextArea() {
		this("");
	}

	public MyTextArea(String contents) {
		super(contents);
		setFont(FontProvider.LabelFont);
	}
}
