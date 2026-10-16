package net.filebot.web;

import static org.junit.Assert.*;

import java.util.Locale;
import java.util.Map;

import org.junit.Test;

import net.filebot.similarity.SeriesNameMatcher;

public class EpisodeFilenameSearchTest {
	@Test
	public void tvmazeSearchesTitleOnly() throws Exception {
		TVMazeClient client = new TVMazeClient() {
			@Override
			protected Object request(String path) {
				assertEquals("search/shows?q=Stuart+Fails+to+save+the+Universe", path);
				return new Object[] { Map.of("show", Map.of("id", 1, "name", "Stuart Fails to save the Universe")) };
			}
		};
		assertEquals(1, client.fetchSearchResult("Stuart.Fails.to.save.the.Universe.S01E07.1080p.WEB-DL.mkv", Locale.ENGLISH).size());
	}

	@Test
	public void anidbSearchesTitleOnly() throws Exception {
		AnidbClient client = new AnidbClient("test", 1) {
			@Override
			public SearchResult[] getAnimeTitles() {
				return new SearchResult[] { new SearchResult(1, "Example Anime") };
			}
		};
		assertEquals("Example Anime", client.fetchSearchResult("[Group] Example.Anime - 07 [1080p][ABCDEF12].mkv", Locale.ENGLISH).get(0).getName());
		assertEquals("Example Anime", client.fetchSearchResult("Example.Anime.S01E07.1080p.mkv", Locale.ENGLISH).get(0).getName());
	}

	@Test
	public void absoluteEpisodeParsingIsAnimeOnly() {
		assertEquals("Example Anime", SeriesNameMatcher.getSeriesQuery("[Group] Example.Anime - 07v2 [1080p].mkv", true));
		assertEquals("Example Anime - 07", SeriesNameMatcher.getSeriesQuery("Example Anime - 07", false));
		assertEquals("The 100", SeriesNameMatcher.getSeriesQuery("The 100", true));
		assertEquals("86", SeriesNameMatcher.getSeriesQuery("86", true));
	}
}
