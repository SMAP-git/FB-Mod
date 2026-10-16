package net.filebot.ui.rename;

import static org.junit.Assert.*;
import static net.filebot.media.MediaDetection.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import net.filebot.similarity.Match;
import net.filebot.web.Movie;
import net.filebot.web.MovieIdentificationService;
import net.filebot.web.OMDbClient;
import net.filebot.web.TMDbClient;
import net.filebot.web.SortOrder;

public class MovieFilenameMatchingTest {
	@Test
	public void extractsTitleAndYearWithoutReleaseInformation() {
		assertEquals("Coyote Vs Acme 2026", getMovieQuery("Coyote.Vs..Acme.2026.1080p.WEBRip.x265.10bit.AAC5.1-[YTS.GG - YTS.BZ]"));
		assertEquals("Blade Runner 2049 2017", getMovieQuery("Blade.Runner.2049.2017.1080p.mkv"));
		assertEquals("1917 2019", getMovieQuery("1917.2019.1080p.mkv"));
		assertEquals("Movie 2026", getMovieQuery("[Group] Movie (2026) [1080p WEBRip]"));
		assertNull(getMovieQuery("1917"));
		assertNull(getMovieQuery("Movie.1080p.WEBRip"));
	}

	@Test
	public void searchesProvidersDirectlyWithoutTitlePrompt() throws Exception {
		List<String> queries = new ArrayList<String>();
		Movie expected = new Movie("Coyote Vs. Acme", 2026, 1756855);
		TMDbClient tmdb = new TMDbClient("", false) {
			@Override
			public List<Movie> searchMovie(String title, int year, Locale locale, boolean extendedInfo) {
				queries.add(title + " " + year);
				return Collections.singletonList(expected);
			}

			@Override
			public Movie getMovieDescriptor(Movie movie, Locale locale) {
				return movie;
			}
		};
		OMDbClient omdb = new OMDbClient("") {
			@Override
			public List<Movie> searchMovie(String title, int year) {
				queries.add(title + " " + year);
				return Collections.singletonList(expected);
			}

			@Override
			public Movie getMovieDescriptor(Movie movie, Locale locale) {
				return movie;
			}
		};
		File file = new File("/tmp/movie-direct-tests/Coyote.Vs..Acme.2026.1080p.WEBRip.x265.10bit.AAC5.1-[YTS.GG - YTS.BZ].mp4");
		for (MovieIdentificationService provider : new MovieIdentificationService[] { tmdb, omdb }) {
			queries.clear();
			assertEquals(Collections.singletonList(expected), detectMovieWithYear(file, provider, Locale.ENGLISH, true));
			assertEquals(Collections.singletonList("Coyote Vs Acme 2026"), queries);
			MovieMatcher matcher = new MovieMatcher(provider);
			assertEquals(expected, matcher.grabMovieName(file, Collections.singletonList(expected), false, Locale.ENGLISH, true, null));
			for (boolean strict : new boolean[] { false, true }) {
				queries.clear();
				List<Match<File, ?>> matches = matcher.match(Collections.singletonList(file), strict, SortOrder.Airdate, Locale.ENGLISH, true, null);
				assertEquals(Collections.singletonList("Coyote Vs Acme 2026"), queries);
				assertEquals(1, matches.size());
				assertEquals(file, matches.get(0).getValue());
				assertEquals(expected, matches.get(0).getCandidate());
			}
		}
	}
}
