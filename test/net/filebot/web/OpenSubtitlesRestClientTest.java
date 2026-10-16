package net.filebot.web;

import static net.filebot.util.JsonUtilities.*;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;

public class OpenSubtitlesRestClientTest {
	private static class MockClient extends OpenSubtitlesRestClient {
		final List<String> paths = new ArrayList<String>();
		int downloadedId;

		@Override
		protected Object request(String method, String path, Map<String, ?> body, boolean authenticated) {
			paths.add(path);
			if (path.startsWith("features?")) {
				return readJson("{\"data\":[{\"attributes\":{\"imdb_id\":123,\"title\":\"Show\",\"year\":2025,\"feature_type\":\"Tvshow\"}}]}");
			}
			return readJson("{\"total_pages\":1,\"data\":[{\"attributes\":{\"language\":\"en\",\"files\":[{\"file_id\":99,\"file_name\":\"show.srt\"}]}}]}");
		}

		@Override
		protected ByteBuffer download(int id) {
			downloadedId = id;
			return ByteBuffer.wrap("subtitle".getBytes(StandardCharsets.UTF_8));
		}
	}

	@Test
	public void searchFeatures() throws Exception {
		MockClient client = new MockClient();
		List<SubtitleSearchResult> results = client.search("Show & Test");
		assertEquals("features?query=Show+%26+Test", client.paths.get(0));
		assertEquals(1, results.size());
		assertTrue(results.get(0).isSeries());
		assertEquals(123, results.get(0).getImdbId());
	}

	@Test
	public void episodeFiltersAndDownloadUseCorrectIds() throws Exception {
		MockClient client = new MockClient();
		SubtitleSearchResult series = client.search("Show").get(0);
		List<SubtitleDescriptor> files = client.getSubtitleList(series, new int[][] { { 1, 2 }, { 3, -1 } }, Locale.ENGLISH);
		assertTrue(client.paths.get(1).contains("parent_imdb_id=123"));
		assertTrue(client.paths.get(1).contains("season_number=1"));
		assertTrue(client.paths.get(1).contains("episode_number=2"));
		assertTrue(client.paths.get(2).contains("season_number=3"));
		assertFalse(client.paths.get(2).contains("episode_number"));
		assertEquals("English", files.get(0).getLanguageName());
		assertEquals("srt", files.get(0).getType());
		assertEquals("subtitle", StandardCharsets.UTF_8.decode(files.get(0).fetch()).toString());
		assertEquals(99, client.downloadedId);
	}

	@Test
	public void requestJsonHasNoJavaTypeMetadata() {
		String body = OpenSubtitlesRestClient.jsonBody(java.util.Collections.singletonMap("file_id", 42));
		assertEquals("{\"file_id\":42}", body);
		assertFalse(body.contains("@type"));
	}

	@Test
	public void paginationCollectsEveryPage() throws Exception {
		MockClient client = new MockClient() {
			@Override
			protected Object request(String method, String path, Map<String, ?> body, boolean authenticated) {
				paths.add(path);
				return readJson("{\"total_pages\":2,\"data\":[{\"attributes\":{\"language\":\"en\",\"files\":[{\"file_id\":99,\"file_name\":\"show.srt\"}]}}]}");
			}
		};
		assertEquals(2, client.findSubtitles(java.util.Collections.singletonMap("imdb_id", 123), Locale.ENGLISH).size());
		assertTrue(client.paths.get(1).contains("page=2"));
	}

	@Test
	public void missingKeyHasActionableError() throws Exception {
		OpenSubtitlesRestClient client = new OpenSubtitlesRestClient() {
			@Override
			protected String getApiKey() { return ""; }
		};
		try {
			client.search("Show");
			fail("Missing credentials must fail explicitly");
		} catch (java.io.IOException e) {
			assertTrue(e.getMessage().contains("API key"));
			assertTrue(e.getMessage().contains("Preferences"));
		}
	}
}
