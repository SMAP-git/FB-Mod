package net.filebot.web;

import static org.junit.Assert.*;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;

public class TheTVDBClientV4Test {

	@Test
	public void searchSeries() throws Exception {
		TheTVDBClient client = new TheTVDBClient("test") {
			@Override
			protected Object requestJson(String path, Locale locale, Duration expirationTime) {
				assertTrue(path.startsWith("search?"));
				assertTrue(path.contains("query=Firefly"));
				assertTrue(path.contains("type=series"));
				return Map.of("data", new Object[] {
					Map.of("id", "series-78874", "name", "Firefly", "aliases", new Object[] { "Serenity" })
				});
			}
		};

		SearchResult result = client.fetchSearchResult("Firefly.S01E07.Jayne.1080p.mkv", Locale.ENGLISH).get(0);
		assertEquals(78874, result.getId());
		assertEquals("Firefly", result.getName());
		assertArrayEquals(new String[] { "Serenity" }, result.getAliasNames());
	}

	@Test
	public void fetchEpisodes() throws Exception {
		TheTVDBClient client = new TheTVDBClient("test") {
			@Override
			protected Object requestJson(String path, Locale locale, Duration expirationTime) {
				if (path.equals("series/12345")) {
					return Map.of("data", Map.of("id", 12345, "name", "Example Show", "firstAired", "2020-01-01", "averageRuntime", 30));
				}
				assertEquals("series/12345/episodes/default/eng?page=0", path);
				return Map.of(
					"data", Map.of("episodes", new Object[] {
						Map.of("id", 67890, "name", "Pilot", "seasonNumber", 1, "number", 1, "absoluteNumber", 1, "aired", "2020-01-01")
					}),
					"links", Map.of("total_items", 1, "page_size", 500)
				);
			}
		};

		Episode episode = client.getEpisodeList(new SearchResult(12345, "Example Show"), SortOrder.Airdate, Locale.ENGLISH).get(0);
		assertEquals("Example Show", episode.getSeriesName());
		assertEquals("Pilot", episode.getTitle());
		assertEquals(Integer.valueOf(1), episode.getSeason());
		assertEquals(Integer.valueOf(1), episode.getEpisode());
		assertEquals("2020-01-01", episode.getAirdate().toString());
	}
}
