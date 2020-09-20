package cz.rion.buildserver.wrappers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.exceptions.FileWriteException;

public class MyFS {

	public static void writeFile(String fileName, String fileContents) throws FileWriteException {
		File f = new File(fileName);
		File absF = new File(f.getAbsolutePath());
		if (absF.getParentFile() != null) {
			if (!absF.getParentFile().exists()) {
				if (!absF.getParentFile().mkdirs()) {
					throw new FileWriteException("Failed to create directory for " + fileName);
				}
			}
		}
		if (f.exists()) {
			if (!f.delete()) {
				throw new FileWriteException("Failed to delete " + fileName);
			}
		}
		FileOutputStream fo;
		try {
			fo = new FileOutputStream(f);
		} catch (FileNotFoundException e) {
			throw new FileWriteException("Failed to open " + fileName);
		}
		try {
			fo.write(fileContents.getBytes(Settings.getDefaultCharset()));
		} catch (IOException e1) {
			try {
				fo.close();
			} catch (IOException e) {
			}
			throw new FileWriteException("Failed to write " + fileName);
		}
		try {
			fo.close();
		} catch (IOException e) {
			throw new FileWriteException("Failed to close " + fileName);
		}
	}

	public static String readFile(String fileName) throws FileReadException {
		File f = new File(fileName);
		if (!f.exists()) {
			throw new FileReadException("File does not exist: " + fileName);
		}
		try {
			byte[] data = Files.readAllBytes(f.toPath());
			return new String(data, Settings.getDefaultCharset());
		} catch (IOException e) {
			throw new FileReadException("Failed to read file: " + fileName, e);
		}
	}

	public static void copyFile(String src, String dst) throws FileCopyException {
		try {
			String contents = readFile(src);
			writeFile(dst, contents);
		} catch (FileReadException e) {
			throw new FileCopyException("Failed to read source file: " + src, e);
		} catch (FileWriteException e) {
			throw new FileCopyException("Failed to write target file: " + dst, e);
		}
	}

	public static void deleteFileSilent(String fileName) {
		File f = new File(fileName);
		if (f.exists()) {
			try {
				if (f.isDirectory()) {
					for (File ff : f.listFiles()) {
						deleteFileSilent(ff.getAbsolutePath());
					}
				}
				f.delete();
			} catch (Exception | Error e) {
			}
		}
	}
}
