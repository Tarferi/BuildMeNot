package cz.rion.buildserver.ui.icons;

import java.awt.Image;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;

public class IconManager {

	public final static ImageIcon FolderIcon = IconLoader.LoadIcon("folder.png");
	public final static ImageIcon CFGIcon = IconLoader.LoadIcon("cfg.png");
	public final static ImageIcon JSONIcon = IconLoader.LoadIcon("json.png");
	public final static ImageIcon DBIcon = IconLoader.LoadIcon("db.png");
	public final static ImageIcon FileIcon = IconLoader.LoadIcon("file.png");
	public final static ImageIcon UpIcon = IconLoader.LoadIcon("folder.png");
	public final static ImageIcon TableIcon = IconLoader.LoadIcon("table.png");
	public final static ImageIcon ViewIcon = IconLoader.LoadIcon("view.png");
	public final static ImageIcon IniIcon = IconLoader.LoadIcon("ini.png");

	private static final Map<String, ImageIcon> icons = new HashMap<>();

	private static final ImageIcon loadIcon(String path) {
		if (path.endsWith(".jsn") || path.endsWith(".json")) {
			return JSONIcon;
		} else if (path.endsWith("..")) {
			return UpIcon;
		} else if (path.endsWith(".cfg")) {
			return CFGIcon;
		} else if (path.endsWith(".db")) {
			return DBIcon;
		} else if (path.endsWith(".table")) {
			return TableIcon;
		} else if (path.endsWith(".view")) {
			return ViewIcon;
		} else if (path.endsWith(".ini")) {
			return IniIcon;
		} else { // Coult be a folder
			int pos1 = path.lastIndexOf("/");
			int pos2 = path.lastIndexOf(".");
			if (pos2 == -1 || pos1 > pos2) {
				return FolderIcon;
			}
		}
		return FileIcon;
	}

	public static final ImageIcon IconForFile(String path, int width, int height) {
		path = path.toLowerCase();
		String mapPath = path + "@" + width + ":" + height;
		if (!icons.containsKey(mapPath)) {
			ImageIcon ico = loadIcon(path);
			ico.setImage(ico.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
			icons.put(mapPath, ico);
		}
		return icons.get(mapPath);
	}
}
