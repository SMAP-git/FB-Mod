package net.filebot.ui.rename;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Test;

import net.filebot.similarity.Match;
import net.filebot.web.Episode;
import net.filebot.web.SearchResult;
import net.filebot.web.SortOrder;
import net.filebot.web.TMDbTVClient;
import net.filebot.web.TVMazeClient;
import net.filebot.web.EpisodeListProvider;
import net.filebot.web.AnidbClient;

public class TMDbEpisodeMatchingTest {
	@Test
	public void identifiesNewSeriesAndEpisodeWithoutManualInput() throws Exception {
		List<String> searches = new CopyOnWriteArrayList<String>();
		TMDbTVClient provider = new TMDbTVClient(null) {
			@Override
			public List<SearchResult> search(String query, Locale locale) {
				searches.add(query);
				return Collections.singletonList(new SearchResult(287620, query));
			}

			@Override
			public List<Episode> getEpisodeList(SearchResult series, SortOrder order, Locale locale) {
				return Arrays.asList(new Episode(series.getName(), 1, 6, "Episode Six"), new Episode(series.getName(), 1, 7, "Spoiler: Dexys Midnight Runners"));
			}
		};
		File file = new File("/tmp/Downloads/Stuart.Fails.to.save.the.Universe.S01E07.Spoiler.Dexys.Midnight.Runners.1080p.WEB-DL.mkv");
		assertMatches(provider, file, searches, false);
	}

	@Test
	public void otherProvidersUseTheSameDetectionPath() throws Exception {
		List<String> searches = new CopyOnWriteArrayList<String>();
		TVMazeClient provider = new TVMazeClient() {
			@Override
			public List<SearchResult> search(String query, Locale locale) {
				searches.add(query);
				return Collections.singletonList(new SearchResult(1, query));
			}

			@Override
			public List<Episode> getEpisodeList(SearchResult series, SortOrder order, Locale locale) {
				return Collections.singletonList(new Episode(series.getName(), 1, 7, "Pilot"));
			}
		};
		assertMatches(provider, new File("/tmp/Downloads/Stuart.Fails.to.save.the.Universe.S01E07.1080p.mkv"), searches, false);
	}

	@Test
	public void animeAbsoluteNumberIsKeptForEpisodeMatching() throws Exception {
		List<String> searches = new CopyOnWriteArrayList<String>();
		AnidbClient provider = new AnidbClient("test", 1) {
			@Override
			public List<SearchResult> search(String query, Locale locale) {
				searches.add(query);
				return Collections.singletonList(new SearchResult(1, query));
			}

			@Override
			public List<Episode> getEpisodeList(SearchResult series, SortOrder order, Locale locale) {
				return Collections.singletonList(new Episode(series.getName(), null, 7, "Pilot", 7, null, null, null, null));
			}
		};
		File file = new File("/tmp/Downloads/[Group] Stuart Fails to save the Universe - 07 [1080p].mkv");
		assertMatches(provider, file, searches, true);
	}

	private void assertMatches(EpisodeListProvider provider, File file, List<String> searches, boolean anime) throws Exception {
		for (boolean strict : new boolean[] { true, false }) {
			if (anime && strict) {
				continue;
			}
			searches.clear();
			List<Match<File, ?>> results = new EpisodeListMatcher(provider, anime).match(Collections.singletonList(file), strict, SortOrder.Airdate, Locale.ENGLISH, true, null);
			assertEquals(Collections.singletonList("Stuart Fails to save the Universe"), searches);
			assertEquals(1, results.size());
			assertEquals(file, results.get(0).getValue());
			assertEquals(Integer.valueOf(7), ((Episode) results.get(0).getCandidate()).getEpisode());
		}
	}
}
