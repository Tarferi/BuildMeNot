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
	public final static ImageIcon StreamIcon = IconLoader.LoadIcon("stream.png");
	public final static ImageIcon ThreadIcon = IconLoader.LoadIcon("thread.png");
	public final static ImageIcon PHPIcon = IconLoader.LoadIcon("php.png");

	public final static ImageIcon JSIcon = IconLoader.LoadIcon("js.png");
	public final static ImageIcon CSSIcon = IconLoader.LoadIcon("css.png");
	public final static ImageIcon HTMLIcon = IconLoader.LoadIcon("html.png");
	public final static ImageIcon EXEIcon = IconLoader.LoadIcon("exe.png");

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
		} else if (path.endsWith(".stream")) {
			return StreamIcon;
		} else if (path.endsWith(".thread")) {
			return StreamIcon;
		} else if (path.endsWith(".php")) {
			return PHPIcon;
		} else if (path.endsWith(".css")) {
			return CSSIcon;
		} else if (path.endsWith(".js")) {
			return JSIcon;
		} else if (path.endsWith(".html")) {
			return HTMLIcon;
		} else if (path.endsWith(".exe")) {
			return EXEIcon;
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
