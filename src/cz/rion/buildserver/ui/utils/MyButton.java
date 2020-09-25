package cz.rion.buildserver.ui.utils;

import javax.swing.JButton;

public class MyButton extends JButton {

	private static final long serialVersionUID = 1L;
	
	public MyButton() {
		this("");
	}

	public MyButton(String contents) {
		super(contents);
		setFont(FontProvider.LabelFont);
	}
}
