package net.filebot.archive;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.filebot.vfs.FileInfo;

public class SevenZipExecutableTest {
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void parseFilesAndDirectories() throws Exception {
		List<FileInfo> entries = SevenZipExecutable.parseListing("Path = folder\nSize = 0\nFolder = +\n\nPath = folder/caf\u00e9.txt\nSize = 10\nAttributes = A\n\nPath = empty.txt\nSize = 0\n");
		assertEquals(2, entries.size());
		assertEquals("folder/caf\u00e9.txt", entries.get(0).getPath());
		assertEquals(0, entries.get(1).getLength());
	}

	@Test(expected = IOException.class)
	public void rejectEscapingPaths() throws Exception {
		SevenZipExecutable.parseListing("Path = ../outside.txt\nSize = 10\n");
	}

	@Test
	public void emptyFilterDoesNotExtractEverything() throws Exception {
		int[] extractions = { 0 };
		SevenZipExecutable extractor = new SevenZipExecutable(temporary.newFile("input.zip")) {
			@Override
			protected CharSequence execute(String... command) {
				if ("x".equals(command[1])) {
					extractions[0]++;
				}
				return "Path = keep.txt\nSize = 12\n";
			}
		};
		extractor.extract(temporary.newFolder(), file -> false);
		assertEquals(0, extractions[0]);
	}

	@Test
	public void oldExtractorNamesUseExecutable() throws Exception {
		File archive = temporary.newFile();
		assertTrue(Archive.Extractor.ApacheVFS.newInstance(archive) instanceof SevenZipExecutable);
		assertTrue(Archive.Extractor.SevenZipNativeBindings.newInstance(archive) instanceof SevenZipExecutable);
	}

	@Test
	public void extractRealZipWithFilterAndPreserveExistingFile() throws Exception {
		File archive = temporary.newFile("input.zip");
		try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(archive))) {
			for (String name : new String[] { "folder/", "folder/keep.txt", "folder/skip.txt" }) {
				zip.putNextEntry(new java.util.zip.ZipEntry(name));
				if (!name.endsWith("/")) {
					zip.write(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				}
				zip.closeEntry();
			}
		}
		SevenZipExecutable extractor = new SevenZipExecutable(archive);
		assertEquals(2, extractor.listFiles().size());
		File output = temporary.newFolder();
		extractor.extract(output, file -> file.getName().equals("keep.txt"));
		File extracted = new File(output, "folder/keep.txt");
		assertTrue(extracted.isFile());
		assertFalse(new File(output, "folder/skip.txt").exists());
		java.nio.file.Files.writeString(extracted.toPath(), "existing");
		extractor.extract(output);
		assertEquals("existing", java.nio.file.Files.readString(extracted.toPath()));
		assertTrue(new File(output, "folder/skip.txt").isFile());
	}
}
