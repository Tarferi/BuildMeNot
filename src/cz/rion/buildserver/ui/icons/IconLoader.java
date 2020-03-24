package cz.rion.buildserver.ui.icons;

import java.net.URL;

import javax.swing.ImageIcon;

public class IconLoader {

	public static ImageIcon LoadIcon(String path) {
		String iconPath = IconLoader.class.getPackage().getName().replaceAll("\\.", "/") + "/" + path;
		URL imageURL = IconLoader.class.getClassLoader().getResource(iconPath);
		return new ImageIcon(imageURL);
	}

}
