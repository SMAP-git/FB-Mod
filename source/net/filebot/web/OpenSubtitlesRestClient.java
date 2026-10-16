package net.filebot.web;

import static net.filebot.util.JsonUtilities.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javax.swing.Icon;

import net.filebot.ResourceManager;

public class OpenSubtitlesRestClient implements SubtitleProvider, VideoHashSubtitleService {
	private static final URI ENDPOINT = URI.create("https://api.opensubtitles.com/api/v1/");
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
	private String username = java.lang.System.getenv("OPENSUBTITLES_USERNAME");
	private String password = java.lang.System.getenv("OPENSUBTITLES_PASSWORD");
	private String token;
	private long tokenExpires;

	@Override
	public String getIdentifier() {
		return "OpenSubtitles.com";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.opensubtitles");
	}

	@Override
	public URI getLink() {
		return URI.create("https://www.opensubtitles.com/");
	}

	public synchronized void setCredentials(String username, String password) {
		if ((password == null || password.isEmpty()) && java.util.Objects.equals(this.username, username)) {
			token = null;
			return;
		}
		this.username = username;
		this.password = password;
		token = null;
		tokenExpires = 0;
	}

	protected String getApiKey() {
		String environmentKey = java.lang.System.getenv("OPENSUBTITLES_API_KEY");
		return java.lang.System.getProperty("net.filebot.OpenSubtitles.apiKey",
				environmentKey == null ? Preferences.userNodeForPackage(OpenSubtitlesRestClient.class).get("apiKey", "") : environmentKey);
	}

	protected Object request(String method, String path, Map<String, ?> body, boolean authenticated) throws Exception {
		String key = getApiKey();
		if (key.isBlank()) {
			throw new IOException("OpenSubtitles.com needs your API key. Open Rename Options > Preferences.");
		}
		HttpRequest.Builder builder = HttpRequest.newBuilder(ENDPOINT.resolve(path)).timeout(Duration.ofSeconds(40))
				.header("Api-Key", key).header("User-Agent", "FB-Mod v4.8.5")
				.header("Accept", "application/json");
		if (authenticated) {
			builder.header("Authorization", "Bearer " + getToken());
		}
		if ("POST".equals(method)) {
			builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonBody(body)));
		} else {
			builder.GET();
		}
		HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		Object data;
		try {
			data = readJson(response.body());
		} catch (RuntimeException e) {
			throw new IOException("OpenSubtitles.com HTTP " + response.statusCode() + ": invalid JSON response", e);
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			String message = getString(data, "message");
			throw new IOException("OpenSubtitles.com HTTP " + response.statusCode() + (message == null ? "" : ": " + message));
		}
		return data;
	}

	static String jsonBody(Map<String, ?> body) {
		return com.cedarsoftware.util.io.JsonWriter.objectToJson(body, Collections.singletonMap(com.cedarsoftware.util.io.JsonWriter.TYPE, false));
	}

	private synchronized String getToken() throws Exception {
		if (token != null && java.lang.System.currentTimeMillis() < tokenExpires) {
			return token;
		}
		if (username == null || username.isBlank() || password == null || password.isEmpty()) {
			throw new IOException("OpenSubtitles.com download needs your username and password. Enter them in Rename Options > Preferences (password is session-only).");
		}
		Map<String, Object> login = new LinkedHashMap<String, Object>();
		login.put("username", username);
		login.put("password", password);
		Object response = request("POST", "login", login, false);
		token = getString(response, "token");
		if (token == null || token.isEmpty()) {
			throw new IOException("OpenSubtitles.com login did not return a token.");
		}
		tokenExpires = java.lang.System.currentTimeMillis() + Duration.ofHours(12).toMillis();
		return token;
	}

	private static String query(Map<String, ?> parameters) {
		return parameters.entrySet().stream().map(it -> java.net.URLEncoder.encode(it.getKey(), java.nio.charset.StandardCharsets.UTF_8)
				+ "=" + java.net.URLEncoder.encode(String.valueOf(it.getValue()), java.nio.charset.StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
	}

	@Override
	public List<SubtitleSearchResult> search(String query) throws Exception {
		Object response = request("GET", "features?query=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8), Collections.emptyMap(), false);
		List<SubtitleSearchResult> results = new ArrayList<SubtitleSearchResult>();
		for (Map<?, ?> it : getMapArray(response, "data")) {
			Map<?, ?> feature = getMap(it, "attributes");
			Integer imdb = getInteger(feature, "imdb_id");
			String title = getString(feature, "title");
			Integer year = getInteger(feature, "year");
			if (imdb != null && imdb > 0 && title != null) {
				results.add(new SubtitleSearchResult(imdb, title, year == null ? 0 : year, "Tvshow".equalsIgnoreCase(getString(feature, "feature_type")) ? "tv series" : "Movie", 0));
			}
		}
		return results;
	}

	@Override
	public List<SubtitleSearchResult> guess(String tag) throws Exception {
		return search(tag);
	}

	@Override
	public List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult result, int[][] episodeFilter, Locale locale) throws Exception {
		Map<String, Object> parameters = new LinkedHashMap<String, Object>();
		if (result.isSeries()) {
			parameters.put("parent_imdb_id", result.getImdbId());
			parameters.put("type", "episode");
		} else {
			parameters.put("imdb_id", result.getImdbId());
			parameters.put("type", "movie");
		}
		List<SubtitleDescriptor> subtitles = new ArrayList<SubtitleDescriptor>();
		if (episodeFilter == null || episodeFilter.length == 0) {
			subtitles.addAll(findSubtitles(parameters, locale));
		} else {
			for (int[] episode : episodeFilter) {
				if (episode.length > 0 && episode[0] >= 0) {
					parameters.put("season_number", episode[0]);
				} else {
					parameters.remove("season_number");
				}
				if (episode.length > 1 && episode[1] >= 0) {
					parameters.put("episode_number", episode[1]);
				} else {
					parameters.remove("episode_number");
				}
				subtitles.addAll(findSubtitles(parameters, locale));
			}
		}
		return subtitles;
	}

	public List<SubtitleDescriptor> findSubtitles(Map<String, ?> criteria, Locale locale) throws Exception {
		Map<String, Object> parameters = new LinkedHashMap<String, Object>(criteria);
		if (locale != null && !locale.getLanguage().isEmpty()) {
			parameters.put("languages", locale.getLanguage());
		}
		parameters.put("order_by", "download_count");
		parameters.put("order_direction", "desc");
		List<SubtitleDescriptor> results = new ArrayList<SubtitleDescriptor>();
		int totalPages = 1;
		for (int page = 1; page <= totalPages; page++) {
			parameters.put("page", page);
			Object response = request("GET", "subtitles?" + query(parameters), Collections.emptyMap(), false);
			Integer pages = getInteger(response, "total_pages");
			totalPages = pages == null ? 1 : pages;
			for (Map<?, ?> item : getMapArray(response, "data")) {
				Map<?, ?> attributes = getMap(item, "attributes");
				String language = getString(attributes, "language");
				for (Map<?, ?> file : getMapArray(attributes, "files")) {
					Integer id = getInteger(file, "file_id");
					String name = getString(file, "file_name");
					if (id != null && name != null) {
						results.add(new RestSubtitle(id, name, language));
					}
				}
			}
		}
		return results;
	}

	@Override
	public Map<File, List<SubtitleDescriptor>> getSubtitleList(File[] videoFiles, Locale locale) throws Exception {
		Map<File, List<SubtitleDescriptor>> results = new LinkedHashMap<File, List<SubtitleDescriptor>>();
		for (File file : videoFiles) {
			results.put(file, findSubtitles(Collections.singletonMap("moviehash", OpenSubtitlesHasher.computeHash(file)), locale));
		}
		return results;
	}

	@Override
	public CheckResult checkSubtitle(File videoFile, File subtitleFile) {
		throw new UnsupportedOperationException("OpenSubtitles.com upload is not supported; use the existing OpenSubtitles.org upload option.");
	}

	@Override
	public void uploadSubtitle(Object identity, Locale locale, File[] videoFiles, File[] subtitleFiles) {
		throw new UnsupportedOperationException("OpenSubtitles.com upload is not supported; use the existing OpenSubtitles.org upload option.");
	}

	@Override
	public URI getSubtitleListLink(SubtitleSearchResult result, Locale locale) {
		return URI.create("https://www.opensubtitles.com/en/search?imdb_id=" + result.getImdbId());
	}

	protected ByteBuffer download(int fileId) throws Exception {
		Object response = request("POST", "download", Collections.singletonMap("file_id", fileId), true);
		String link = getString(response, "link");
		if (link == null) {
			throw new IOException("OpenSubtitles.com download did not return a link.");
		}
		URI uri = URI.create(link);
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			throw new IOException("OpenSubtitles.com returned an insecure download link.");
		}
		HttpResponse<byte[]> content = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
		if (content.statusCode() != 200) {
			throw new IOException("Subtitle download failed: HTTP " + content.statusCode());
		}
		return ByteBuffer.wrap(content.body());
	}

	private class RestSubtitle implements SubtitleDescriptor {
		private final int id;
		private final String path;
		private final String language;

		RestSubtitle(int id, String path, String language) {
			this.id = id;
			this.path = new File(path).getName();
			this.language = language;
		}

		@Override
		public String getPath() { return path; }
		@Override
		public String getName() { return net.filebot.util.FileUtilities.getNameWithoutExtension(path); }
		@Override
		public String getType() { return net.filebot.util.FileUtilities.getExtension(path); }
		@Override
		public String getLanguageName() { return language == null ? "" : Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH); }
		@Override
		public long getLength() { return -1; }
		@Override
		public File toFile() { return new File(path); }
		@Override
		public ByteBuffer fetch() throws Exception { return download(id); }
		@Override
		public String toString() { return path; }
	}
}
