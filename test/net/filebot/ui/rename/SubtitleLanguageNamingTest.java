package net.filebot.ui.rename;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.Test;

import net.filebot.Language;
import net.filebot.media.ReleaseInfo;
import net.filebot.similarity.Match;
import net.filebot.subtitle.SubtitleUtilities;
import net.filebot.web.Episode;
import net.filebot.web.Movie;
import net.filebot.web.EpisodeFormat;

public class SubtitleLanguageNamingTest {
	@Test
	public void recognizesBracketedAndSuffixCodes() {
		assertLanguage("English", "Special.Ops.Lioness.S03E01.1080p_track3_[eng].srt");
		assertLanguage("English", "Show.[ENG].srt");
		assertLanguage("French", "Show.[fra].srt");
		assertLanguage("French", "Show.[fre].srt");
		assertLanguage("German", "Show.[ger].srt");
		assertLanguage("German", "Show.[deu].srt");
		assertLanguage("Portuguese", "Show.[por].srt");
		assertLanguage("Brazilian Portuguese", "Show.[pt-BR].srt");
		assertLanguage("English", "Show.en.forced.srt");
		assertLanguage("English", "Show.English.srt");
		assertLanguage("Hindi", "Show.[hi].srt");
		assertNull(SubtitleUtilities.getSubtitleLanguageName(new File("Show.hi.srt")));
		assertNull(SubtitleUtilities.getSubtitleLanguageName(new File("Show.[eng].mkv")));
		assertNull(SubtitleUtilities.getSubtitleLanguageName(new File("Show.[1080p][unknown].srt")));
		assertNull(SubtitleUtilities.getSubtitleLanguageName(new File("English.Show.srt")));
	}

	@Test
	public void recognizesEveryMappedISOLanguage() {
		ReleaseInfo info = new ReleaseInfo();
		for (String code : Locale.getISOLanguages()) {
			Locale language = new Locale(code);
			assertNotNull(code, info.getSubtitleLanguageTag("Show.[" + code + "]"));
			assertNotNull(code, info.getSubtitleLanguageTag("Show.[" + language.getISO3Language() + "]"));
			assertNotNull(code, SubtitleUtilities.getSubtitleLanguageName(new File("Show.[" + code + "].srt")));
		}
		for (Language language : Language.availableLanguages()) {
			assertLanguage(language.getName(), "Show.[" + language.getISO3() + "].srt");
			assertLanguage(language.getName(), "Show.[" + language.getISO3B() + "].srt");
		}
	}

	@Test
	public void standardNamesIncludeLanguageButCustomFormatsDoNot() throws Exception {
		net.filebot.web.SeriesInfo series = new net.filebot.web.SeriesInfo();
		series.setDatabase("TheMovieDB::TV");
		Episode episode = new Episode("Special Ops Lioness", 3, 1, "Pilot", null, null, null, null, series);
		assertRename(episode, "Show.S03E01_track3_[eng].srt", null, true, "Special Ops Lioness - 3x01 - Pilot.English.srt");
		assertRename(episode, "Show.S03E01_track3_[eng].mkv", null, true, "Special Ops Lioness - 3x01 - Pilot.mkv");
		assertRename(episode, "Show.S03E01.[unknown].srt", null, true, "Special Ops Lioness - 3x01 - Pilot.srt");
		assertRename(episode, "Show.S03E01.[fra].srt", null, false, "Special Ops Lioness - 3x01 - Pilot.French");
		Movie movie = new Movie("Example", 2026, 12345);
		assertRename(movie, "Example.2026.[jpn].srt", new MovieFormatter(), true, "Example (2026).Japanese.srt");
		assertRename(episode, "Show.S03E01.[eng].srt", new ExpressionFormatter("{n}", EpisodeFormat.SeasonEpisode, Episode.class), true, "Special Ops Lioness.srt");
		assertRename(episode, "Show.S03E01.[eng].srt", new ExpressionFormatter("{n}.{lang.name}", EpisodeFormat.SeasonEpisode, Episode.class), true, "Special Ops Lioness.English.srt");
		java.util.ResourceBundle examples = java.util.ResourceBundle.getBundle("net.filebot.ui.rename.FormatDialog");
		assertRename(episode, "Show.S03E01.[eng].srt", new ExpressionFormatter(examples.getString("episode.example[6]"), EpisodeFormat.SeasonEpisode, Episode.class), true, "Special Ops Lioness - S03E01 - Pilot.English.srt");
		assertRename(episode, "Show.S03E01.[eng].srt", new ExpressionFormatter(examples.getString("episode.example[7]"), EpisodeFormat.SeasonEpisode, Episode.class), true, "Special Ops Lioness - S03E01 - Pilot.eng.srt");
		assertRename(movie, "Example.[fra].srt", new ExpressionFormatter(examples.getString("movie.example[6]"), net.filebot.web.MovieFormat.NameYear, Movie.class), true, "Example (2026).French.srt");
	}

	private static void assertLanguage(String name, String file) {
		assertEquals(file, name, SubtitleUtilities.getSubtitleLanguageName(new File(file)));
	}

	private static void assertRename(Object value, String filename, MatchFormatter formatter, boolean preserveExtension, String expected) throws Exception {
		RenameModel model = new RenameModel();
		File file = new File(filename);
		SwingUtilities.invokeAndWait(() -> {
			model.setPreserveExtension(preserveExtension);
			if (formatter != null) {
				model.useFormatter(value.getClass(), formatter);
			}
			model.matches().add(new Match<Object, File>(value, file));
		});
		model.names().get(0).get(10, TimeUnit.SECONDS);
		assertEquals(expected, model.getRenameMap().get(file).getPath());
		if (!(formatter instanceof ExpressionFormatter)) {
			String stem = preserveExtension ? expected.substring(0, expected.lastIndexOf('.')) : expected;
			assertEquals(stem, model.names().get(0).preview());
		}
	}
}
