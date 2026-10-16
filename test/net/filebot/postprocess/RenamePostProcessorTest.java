package net.filebot.postprocess;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Locale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.filebot.postprocess.RenamePostProcessor.Action;
import net.filebot.web.Movie;
import net.filebot.web.SimpleDate;

public class RenamePostProcessorTest {
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void metadataEscapesTextAndPreservesExistingSidecar() throws Exception {
		File video = temporary.newFile("Movie.mkv");
		Movie movie = new Movie("A & <B>", 2025, 123);
		RenamePostProcessor processor = new RenamePostProcessor();
		assertTrue(processor.process(video, movie, EnumSet.of(Action.Metadata), Locale.ENGLISH).isEmpty());
		File nfo = new File(temporary.getRoot(), "Movie.nfo");
		String contents = Files.readString(nfo.toPath());
		assertTrue(contents.contains("A &amp; &lt;B&gt;"));
		assertTrue(contents.contains("tt0000123"));
		Files.writeString(nfo.toPath(), "existing", StandardCharsets.UTF_8);
		processor.process(video, movie, EnumSet.of(Action.Metadata), Locale.ENGLISH);
		assertEquals("existing", Files.readString(nfo.toPath()));
	}

	@Test
	public void noActionsDoNotWriteSidecars() throws Exception {
		File video = temporary.newFile("Movie.mkv");
		assertTrue(new RenamePostProcessor().process(video, new Movie("Movie", 2025), EnumSet.noneOf(Action.class), Locale.ENGLISH).isEmpty());
		assertEquals(1, temporary.getRoot().list().length);
	}

	@Test
	public void setsExactReleaseDate() throws Exception {
		File video = temporary.newFile("Movie.mkv");
		SimpleDate date = new SimpleDate(2025, 2, 3);
		RenamePostProcessor processor = new RenamePostProcessor() {
			@Override
			protected SimpleDate getReleaseDate(Object info, Locale locale) {
				return date;
			}
		};
		assertTrue(processor.process(video, new Movie("Movie", 2025), EnumSet.of(Action.FileDate), Locale.ENGLISH).isEmpty());
		assertEquals(date.getTimeStamp(), video.lastModified());
	}

	@Test
	public void reportsFailureWithoutPreventingMetadata() throws Exception {
		File video = temporary.newFile("Movie.mkv");
		RenamePostProcessor processor = new RenamePostProcessor() {
			@Override
			protected SimpleDate getReleaseDate(Object info, Locale locale) {
				return null;
			}
		};
		assertEquals(1, processor.process(video, new Movie("Movie", 2025), EnumSet.of(Action.FileDate, Action.Metadata), Locale.ENGLISH).size());
		assertTrue(new File(temporary.getRoot(), "Movie.nfo").isFile());
	}

	@Test
	public void downloadsPosterAsJpegAndPreservesIt() throws Exception {
		File video = temporary.newFile("Movie.mkv");
		File source = temporary.newFile("image.png");
		javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20, 30, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", source);
		int[] calls = { 0 };
		RenamePostProcessor processor = new RenamePostProcessor() {
			@Override
			protected java.net.URL getPoster(Object info, Locale locale) throws Exception {
				calls[0]++;
				return source.toURI().toURL();
			}
		};
		assertTrue(processor.process(video, new Movie("Movie", 2025), EnumSet.of(Action.Artwork), Locale.ENGLISH).isEmpty());
		File poster = new File(temporary.getRoot(), "Movie-poster.jpg");
		assertEquals(20, javax.imageio.ImageIO.read(poster).getWidth());
		processor.process(video, new Movie("Movie", 2025), EnumSet.of(Action.Artwork), Locale.ENGLISH);
		assertEquals(1, calls[0]);
	}

	@Test
	public void downloadsSubtitleAndPreservesIt() throws Exception {
		File video = temporary.newFile("Movie.mkv");
		RenamePostProcessor processor = new RenamePostProcessor() {
			@Override
			protected java.util.List<net.filebot.web.SubtitleDescriptor> findSubtitles(File destination, Object info, Locale locale) {
				return java.util.Collections.singletonList(new net.filebot.web.SubtitleDescriptor() {
					public String getPath() { return "subtitle.srt"; }
					public String getName() { return "subtitle"; }
					public String getType() { return "srt"; }
					public String getLanguageName() { return "English"; }
					public long getLength() { return 8; }
					public File toFile() { return new File("subtitle.srt"); }
					public java.nio.ByteBuffer fetch() { return java.nio.ByteBuffer.wrap("subtitle".getBytes(StandardCharsets.UTF_8)); }
				});
			}
		};
		assertTrue(processor.process(video, new Movie("Movie", 2025), EnumSet.of(Action.Subtitles), Locale.ENGLISH).isEmpty());
		File subtitle = new File(temporary.getRoot(), "Movie.en.srt");
		assertEquals("subtitle", Files.readString(subtitle.toPath()));
		Files.writeString(subtitle.toPath(), "existing");
		processor.process(video, new Movie("Movie", 2025), EnumSet.of(Action.Subtitles), Locale.ENGLISH);
		assertEquals("existing", Files.readString(subtitle.toPath()));
		assertFalse(java.util.Arrays.stream(temporary.getRoot().list()).anyMatch(it -> it.endsWith(".part")));
	}
}
