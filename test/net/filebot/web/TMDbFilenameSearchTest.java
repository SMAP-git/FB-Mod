package net.filebot.web;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.Test;
import net.filebot.similarity.SeriesNameMatcher;

public class TMDbFilenameSearchTest {
	@Test
	public void removeEpisodeAndReleaseSuffix() {
		assertEquals("Stuart Fails to save the Universe", SeriesNameMatcher.getSeriesQuery("Stuart Fails to save the Universe.S01E07.Spoiler.Dexys.Midnight.Runners.1080p.WEB.h264.mkv", false));
		assertEquals("Breaking Bad", SeriesNameMatcher.getSeriesQuery("Breaking.Bad.1x02.Cats.in.the.Bag.720p.mkv", false));
		assertEquals("The Last of Us", SeriesNameMatcher.getSeriesQuery("[Group] The.Last.of.Us.S02E03.2160p.WEB-DL.mkv", false));
	}

	@Test
	public void preserveTitlesWithoutEpisodeMarkers() {
		assertEquals("The 100", SeriesNameMatcher.getSeriesQuery("The 100", false));
		assertEquals("1899", SeriesNameMatcher.getSeriesQuery("1899", false));
		assertEquals("Doctor Who (2005)", SeriesNameMatcher.getSeriesQuery("Doctor Who (2005)", false));
	}

	@Test
	public void seriesSearchUsesCleanTitleAndYear() throws Exception {
		String[] searched = new String[1];
		int[] year = new int[1];
		TMDbTVClient client = new TMDbTVClient(new TMDbClient("", false)) {
			@Override
			public List<SearchResult> searchTV(String name, int startYear, Locale locale, boolean extendedInfo) {
				searched[0] = name;
				year[0] = startYear;
				return Collections.singletonList(new SearchResult(1, name));
			}
		};
		client.fetchSearchResult("Doctor.Who.(2005).S01E02.The.End.of.the.World.mkv", Locale.ENGLISH);
		assertEquals("Doctor Who", searched[0]);
		assertEquals(2005, year[0]);
	}
}
