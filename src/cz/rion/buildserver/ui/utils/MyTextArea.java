package cz.rion.buildserver.ui.utils;

import javax.swing.JTextArea;

public class MyTextArea extends JTextArea {

	private static final long serialVersionUID = 1L;
	
	public MyTextArea() {
		this("");
	}

	public MyTextArea(String contents) {
		super(contents);
		setFont(FontProvider.LabelFont);
	}
}
