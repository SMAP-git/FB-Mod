package net.filebot.archive;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static net.filebot.Logging.*;
import static net.filebot.util.RegularExpressions.*;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import net.filebot.util.ByteBufferOutputStream;
import net.filebot.vfs.FileInfo;
import net.filebot.vfs.SimpleFileInfo;

public class SevenZipExecutable implements ArchiveExtractor {

	private File archive;

	public SevenZipExecutable(File file) throws Exception {
		if (!file.exists()) {
			throw new FileNotFoundException(file.getAbsolutePath());
		}

		this.archive = file.getCanonicalFile();
	}

	protected String get7zCommand() {
		// use 7z executable path as specified by the cmdline or default to "7z" and let the shell figure it out
		return System.getProperty("net.filebot.Archive.7z", "7z");
	}

	protected CharSequence execute(String... command) throws IOException {
		Process process = new ProcessBuilder(command).redirectError(Redirect.INHERIT).start();
		process.getOutputStream().close();

		try (ByteBufferOutputStream bb = new ByteBufferOutputStream(8 * 1024)) {
			bb.transferFully(process.getInputStream());

			int returnCode = process.waitFor();
			String output = UTF_8.decode(bb.getByteBuffer()).toString();

			// DEBUG
			debug.fine(format("Execute: %s", asList(command)));
			debug.finest(output);

			if (returnCode == 0) {
				return output;
			} else {
				throw new IOException(String.format("%s failed with exit code %d: %s", get7zCommand(), returnCode, SPACE.matcher(output).replaceAll(" ").trim()));
			}
		} catch (InterruptedException e) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new IOException(String.format("%s interrupted", get7zCommand()), e);
		}
	}

	@Override
	public List<FileInfo> listFiles() throws IOException {
		// e.g. 7z l -y archive.7z
		return parseListing(execute(get7zCommand(), "l", "-slt", "-ba", "-sccUTF-8", "-y", "--", archive.getPath()));
	}

	static List<FileInfo> parseListing(CharSequence output) throws IOException {
		List<FileInfo> paths = new ArrayList<FileInfo>();
		Map<String, String> entry = new LinkedHashMap<String, String>();
		for (String line : output.toString().split("\\r?\\n", -1)) {
			if (line.trim().isEmpty()) {
				addEntry(entry, paths);
				entry.clear();
				continue;
			}
			int split = line.indexOf(" = ");
			if (split < 0) {
				continue;
			}
			entry.put(line.substring(0, split), line.substring(split + 3));
		}
		addEntry(entry, paths);
		return paths;
	}

	private static void addEntry(Map<String, String> entry, List<FileInfo> paths) throws IOException {
		String path = entry.get("Path");
		String size = entry.get("Size");
		if (path == null || size == null || "+".equals(entry.get("Folder")) || entry.getOrDefault("Attributes", "").startsWith("D")) {
			return;
		}
		java.nio.file.Path relative = new File(path.replace('\\', '/')).toPath().normalize();
		if (relative.isAbsolute() || relative.startsWith("..") || path.matches("^[A-Za-z]:.*")) {
			throw new IOException("Unsafe archive entry: " + path);
		}
		try {
			paths.add(new SimpleFileInfo(path, Long.parseLong(size)));
		} catch (NumberFormatException e) {
			throw new IOException("Invalid archive entry size: " + size, e);
		}
	}

	@Override
	public void extract(File outputDir) throws IOException {
		// e.g. 7z x -y -aos archive.7z
		extract(outputDir, null);
	}

	@Override
	public void extract(File outputDir, FileFilter filter) throws IOException {
		// e.g. 7z x -y -aos archive.7z file.txt image.png info.nfo
		List<FileInfo> entries = listFiles();
		List<String> selection = new ArrayList<String>();
		for (FileInfo entry : entries) {
			if (filter == null || filter.accept(entry.toFile())) {
				selection.add(entry.getPath());
			}
		}
		if (selection.isEmpty()) {
			return;
		}
		java.nio.file.Path listing = java.nio.file.Files.createTempFile("filebot-7z-", ".lst");
		try {
			java.nio.file.Files.write(listing, selection, UTF_8);
			execute(get7zCommand(), "x", "-y", "-aos", "-spd", "-sccUTF-8", "-scsUTF-8", "-o" + outputDir.getCanonicalPath(), "-i@" + listing, "--", archive.getPath());
			for (String path : selection) {
				if (!new File(outputDir, path).isFile()) {
					throw new IOException("7-Zip did not extract the selected entry: " + path);
				}
			}
		} finally {
			java.nio.file.Files.deleteIfExists(listing);
		}
	}

}
