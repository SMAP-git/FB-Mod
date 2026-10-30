package net.filebot.postprocess;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.filebot.postprocess.RenamePostProcessor.Action;
import net.filebot.web.Episode;
import net.filebot.web.Movie;
import net.filebot.web.SeriesInfo;
import net.filebot.web.SimpleDate;

public class RenamePostProcessorTest {
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	private final Map<String, String> artworkSettings = new LinkedHashMap<String, String>();

	@Before
	public void resetArtworkNaming() {
		for (Action action : EnumSet.of(Action.Artwork, Action.SeasonArtwork)) {
			for (String suffix : new String[] { "DefaultFilename", "Filename" }) {
				String key = action.name() + suffix;
				artworkSettings.put(key, RenamePostProcessor.preferences().get(key, null));
				RenamePostProcessor.preferences().remove(key);
			}
		}
	}

	@After
	public void restoreArtworkNaming() {
		artworkSettings.forEach((key, value) -> {
			if (value == null) {
				RenamePostProcessor.preferences().remove(key);
			} else {
				RenamePostProcessor.preferences().put(key, value);
			}
		});
	}

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
	public void downloadsOneSeasonPosterPerSeasonAndPreservesIt() throws Exception {
		File video = temporary.newFile("Episode.mkv");
		File source = temporary.newFile("season.png");
		javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20, 30, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", source);
		SeriesInfo series = new SeriesInfo();
		series.setDatabase("test");
		series.setId(123);
		Episode episode = new Episode("Series", 2, 1, "Episode", null, null, null, null, series);
		int[] calls = { 0 };
		RenamePostProcessor processor = new RenamePostProcessor() {
			@Override
			protected java.net.URL getSeasonPoster(Episode info, Locale locale) throws Exception {
				calls[0]++;
				return source.toURI().toURL();
			}
		};
		assertTrue(processor.process(video, episode, EnumSet.of(Action.SeasonArtwork), Locale.ENGLISH).isEmpty());
		File poster = new File(temporary.getRoot(), "Season 02-poster.jpg");
		assertEquals(20, javax.imageio.ImageIO.read(poster).getWidth());
		processor.process(video, episode, EnumSet.of(Action.SeasonArtwork), Locale.ENGLISH);
		assertEquals(1, calls[0]);
	}

	@Test
	public void seasonArtworkSkipsMoviesAndSpecialEpisodes() throws Exception {
		File video = temporary.newFile("Video.mkv");
		RenamePostProcessor processor = new RenamePostProcessor() {
			@Override
			protected java.net.URL getSeasonPoster(Episode info, Locale locale) {
				fail("Season artwork should not be requested for specials");
				return null;
			}
		};
		assertTrue(processor.process(video, new Movie("Movie", 2025), EnumSet.of(Action.SeasonArtwork), Locale.ENGLISH).isEmpty());
		assertTrue(processor.process(video, new Episode("Series", null, null, "Special"), EnumSet.of(Action.SeasonArtwork), Locale.ENGLISH).isEmpty());
		assertEquals(1, temporary.getRoot().list().length);
	}

	@Test
	public void customArtworkNamesAreIndependentAndPreserveExistingFiles() throws Exception {
		File video = temporary.newFile("Episode.mkv");
		File source = temporary.newFile("image.png");
		javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20, 30, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", source);
		int[] calls = { 0, 0 };
		RenamePostProcessor processor = new RenamePostProcessor() {
			@Override
			protected java.net.URL getPoster(Object info, Locale locale) throws Exception {
				calls[0]++;
				return source.toURI().toURL();
			}

			@Override
			protected java.net.URL getSeasonPoster(Episode info, Locale locale) throws Exception {
				calls[1]++;
				return source.toURI().toURL();
			}
		};
		Episode episode = new Episode("Series", 2, 1, "Episode");
		RenamePostProcessor.preferences().putBoolean("ArtworkDefaultFilename", false);
		RenamePostProcessor.preferences().put("ArtworkFilename", "poster.jpeg");
		EnumSet<Action> actions = EnumSet.of(Action.Artwork, Action.SeasonArtwork);
		assertTrue(processor.process(video, episode, actions, Locale.ENGLISH).isEmpty());
		assertEquals(20, javax.imageio.ImageIO.read(new File(temporary.getRoot(), "poster.jpeg")).getWidth());
		assertTrue(new File(temporary.getRoot(), "Season 02-poster.jpg").isFile());
		assertFalse(new File(temporary.getRoot(), "Episode-poster.jpg").exists());

		RenamePostProcessor.preferences().putBoolean("SeasonArtworkDefaultFilename", false);
		RenamePostProcessor.preferences().put("SeasonArtworkFilename", "season-cover.jpg");
		assertTrue(processor.process(video, episode, actions, Locale.ENGLISH).isEmpty());
		File seasonPoster = new File(temporary.getRoot(), "season-cover.jpg");
		assertEquals(20, javax.imageio.ImageIO.read(seasonPoster).getWidth());
		Files.writeString(seasonPoster.toPath(), "existing");
		assertTrue(processor.process(video, episode, actions, Locale.ENGLISH).isEmpty());
		assertEquals("existing", Files.readString(seasonPoster.toPath()));
		assertArrayEquals(new int[] { 1, 2 }, calls);

		RenamePostProcessor.preferences().putBoolean("ArtworkDefaultFilename", true);
		assertTrue(processor.process(video, new Movie("Movie", 2025), EnumSet.of(Action.Artwork), Locale.ENGLISH).isEmpty());
		assertTrue(new File(temporary.getRoot(), "Episode-poster.jpg").isFile());
	}

	@Test
	public void rejectsInvalidArtworkNames() {
		for (String name : new String[] { "", "  ", "../poster.jpg", "folder/poster.jpg", "folder\\poster.jpg", "/poster.jpg", "poster.png", "poster", "bad\nname.jpg", "bad:name.jpg" }) {
			try {
				RenamePostProcessor.validateArtworkFilename(name);
				fail("Accepted invalid filename: " + name);
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("filename"));
			}
		}
		assertEquals("Poster.JPG", RenamePostProcessor.validateArtworkFilename(" Poster.JPG "));
	}

	@Test
	public void invalidCustomNameReportsFailureWithoutDownloading() throws Exception {
		File video = temporary.newFile("Movie.mkv");
		RenamePostProcessor.preferences().putBoolean("ArtworkDefaultFilename", false);
		RenamePostProcessor.preferences().put("ArtworkFilename", "../poster.jpg");
		RenamePostProcessor processor = new RenamePostProcessor() {
			@Override
			protected java.net.URL getPoster(Object info, Locale locale) {
				fail("Invalid custom filenames must be rejected before downloading");
				return null;
			}
		};
		assertEquals(1, processor.process(video, new Movie("Movie", 2025), EnumSet.of(Action.Artwork, Action.Metadata), Locale.ENGLISH).size());
		assertTrue(new File(temporary.getRoot(), "Movie.nfo").isFile());
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
