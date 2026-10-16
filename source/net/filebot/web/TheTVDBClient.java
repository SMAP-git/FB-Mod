package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.CachedResource.fetchIfModified;
import static net.filebot.Logging.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.util.StringUtilities.*;
import static net.filebot.web.EpisodeUtilities.*;
import static net.filebot.web.WebRequest.*;

import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

import javax.swing.Icon;

import net.filebot.Cache;
import net.filebot.CacheType;
import net.filebot.ResourceManager;
import net.filebot.similarity.SeriesNameMatcher;

public class TheTVDBClient extends AbstractEpisodeListProvider implements ArtworkProvider {

	private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

	private String apikey;
	private String pin;
	private final Object credentialsLock = new Object();

	public static Preferences preferences() {
		return Preferences.userNodeForPackage(TheTVDBClient.class).node("TheTVDB");
	}

	public static String getConfiguredApiKey() {
		return System.getProperty("net.filebot.WebServices.TheTVDB.apikey", preferences().get("apiKey", "")).trim();
	}

	public static String getConfiguredPin() {
		return System.getProperty("net.filebot.WebServices.TheTVDB.pin", preferences().get("pin", "")).trim();
	}

	public static boolean isConfigured() {
		return !getConfiguredApiKey().isEmpty();
	}

	public void setCredentials(String apikey, String pin) {
		synchronized (credentialsLock) {
			this.apikey = apikey;
			this.pin = pin;
			token = null;
			tokenExpireInstant = null;
			legacyToken = null;
			legacyTokenExpireInstant = null;
		}
	}

	public TheTVDBClient(String apikey) {
		this(apikey, null);
	}

	public TheTVDBClient(String apikey, String pin) {
		this.apikey = apikey;
		this.pin = pin;
	}

	@Override
	public String getIdentifier() {
		return "TheTVDB";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.thetvdb");
	}

	@Override
	public boolean hasSeasonSupport() {
		return true;
	}

	protected Object postJson(String path, Object object) throws Exception {
		// curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' 'https://api4.thetvdb.com/v4/login' --data '{"apikey":"XXXXX"}'
		ByteBuffer response = post(getEndpoint(path), json(object, false).getBytes(UTF_8), "application/json", null);
		return readJson(UTF_8.decode(response));
	}

	protected Object requestJson(String path, Locale locale, Duration expirationTime) throws Exception {
		Cache cache = Cache.getCache(locale == null || locale == Locale.ROOT ? getName() + "_v4" : getName() + "_v4_" + locale.getLanguage(), CacheType.Monthly);
		return cache.json(path, this::getEndpoint).fetch(fetchIfModified(() -> getRequestHeader(locale))).expire(expirationTime).get();
	}

	protected Object requestLegacyJson(String path, Locale locale, Duration expirationTime) throws Exception {
		Cache cache = Cache.getCache(locale == null || locale == Locale.ROOT ? getName() + "_v3" : getName() + "_v3_" + locale.getLanguage(), CacheType.Monthly);
		return cache.json(path, key -> new URL("https://api.thetvdb.com/" + key)).fetch(fetchIfModified(() -> getLegacyRequestHeader(locale))).expire(expirationTime).get();
	}

	protected URL getEndpoint(String path) throws Exception {
		return new URL("https://api4.thetvdb.com/v4/" + path);
	}

	private Map<String, String> getRequestHeader(Locale locale) {
		Map<String, String> header = new LinkedHashMap<String, String>(3);

		getApiLanguageCode(locale).ifPresent(languageCode -> {
			header.put("Accept-Language", languageCode);
		});

		header.put("Accept", "application/json");
		header.put("Authorization", "Bearer " + getAuthorizationToken());

		return header;
	}

	private Map<String, String> getLegacyRequestHeader(Locale locale) {
		Map<String, String> header = new LinkedHashMap<String, String>(3);
		getLegacyLanguageCode(locale).ifPresent(languageCode -> header.put("Accept-Language", languageCode));
		header.put("Accept", "application/json");
		header.put("Authorization", "Bearer " + getLegacyAuthorizationToken());
		return header;
	}

	private Optional<String> getApiLanguageCode(Locale locale) {
		return Optional.ofNullable(locale).map(language -> {
			if (language.getLanguage().isEmpty()) {
				return "";
			}
			try {
				return language.getISO3Language();
			} catch (MissingResourceException e) {
				throw new IllegalArgumentException("Unsupported TheTVDB language: " + language, e);
			}
		}).filter(code -> !code.isEmpty());
	}

	private Optional<String> getLegacyLanguageCode(Locale locale) {
		return Optional.ofNullable(locale).map(Locale::getLanguage).map(code -> {
			switch (code) {
			case "iw":
				return "he";
			case "in":
				return "id";
			case "":
				return null;
			default:
				return code;
			}
		});
	}

	private String token = null;
	private Instant tokenExpireInstant = null;
	private Duration tokenExpireDuration = Duration.ofDays(29); // TheTVDB v4 tokens expire after one month

	private String getAuthorizationToken() {
		synchronized (credentialsLock) {
			if (token == null || (tokenExpireInstant != null && Instant.now().isAfter(tokenExpireInstant))) {
				try {
					Map<String, String> credentials = new LinkedHashMap<String, String>();
					credentials.put("apikey", apikey);
					if (pin != null && !pin.isEmpty()) {
						credentials.put("pin", pin);
					}
					Object json = postJson("login", credentials);
					token = getString(getMap(json, "data"), "token");
					if (token == null) {
						throw new IllegalStateException("TheTVDB v4 login response did not contain an authorization token");
					}
					tokenExpireInstant = Instant.now().plus(tokenExpireDuration);
				} catch (Exception e) {
					throw new IllegalStateException("Failed to retrieve authorization token: " + e.getMessage(), e);
				}
			}
			return token;
		}
	}

	private String legacyToken = null;
	private Instant legacyTokenExpireInstant = null;
	private Duration legacyTokenExpireDuration = Duration.ofHours(23);

	private String getLegacyAuthorizationToken() {
		synchronized (credentialsLock) {
			if (legacyToken == null || (legacyTokenExpireInstant != null && Instant.now().isAfter(legacyTokenExpireInstant))) {
				try {
					ByteBuffer response = post(new URL("https://api.thetvdb.com/login"), json(singletonMap("apikey", apikey), false).getBytes(UTF_8), "application/json", null);
					Object json = readJson(UTF_8.decode(response));
					legacyToken = getString(json, "token");
					if (legacyToken == null) {
						throw new IllegalStateException("TheTVDB v3 login response did not contain an authorization token");
					}
					legacyTokenExpireInstant = Instant.now().plus(legacyTokenExpireDuration);
				} catch (Exception e) {
					throw new IllegalStateException("Failed to retrieve TheTVDB v3 authorization token: " + e.getMessage(), e);
				}
			}
			return legacyToken;
		}
	}

	protected List<SearchResult> search(String path, Map<String, Object> query, Locale locale, Duration expirationTime) throws Exception {
		Object json = requestJson(path + "?" + encodeParameters(query, true), locale, expirationTime);

		return streamJsonObjects(json, "data").map(it -> {
			// v4 search IDs are strings such as "series-12345".
			String id = getString(it, "tvdb_id");
			if (id == null) {
				id = getString(it, "id");
			}
			if (id == null) {
				id = getString(it, "objectID");
			}
			Integer seriesId = parseSeriesID(id);
			String seriesName = Optional.ofNullable(getString(it, "name")).orElseGet(() -> getString(it, "name_translated"));
			String[] aliasNames = Arrays.stream(getArray(it, "aliases")).map(Object::toString).toArray(String[]::new);

			if (seriesId == null || seriesName == null || seriesName.startsWith("**") || seriesName.endsWith("**")) {
				debug.warning(format("Ignore invalid series: %s [%s]", seriesName, id));
				return null;
			}

			return new SearchResult(seriesId, seriesName, aliasNames);
		}).filter(Objects::nonNull).collect(toList());
	}

	@Override
	protected Cache getCache(String section) {
		return Cache.getCache(getName() + "_v4_" + section, CacheType.Daily);
	}

	private Integer parseSeriesID(String id) {
		if (id == null) {
			return null;
		}
		String numericID = id.startsWith("series-") ? id.substring("series-".length()) : id;
		return getStringValue(singletonMap("id", numericID), "id", Integer::parseInt);
	}

	@Override
	public List<SearchResult> fetchSearchResult(String query, Locale locale) throws Exception {
		query = SeriesNameMatcher.getSeriesQuery(query, false);
		Map<String, Object> parameters = new LinkedHashMap<String, Object>();
		parameters.put("query", query);
		parameters.put("type", "series");
		return search("search", parameters, locale, Cache.ONE_DAY);
	}

	public SearchResultPreview getPreview(SearchResult series, Locale locale) throws Exception {
		Object data = getMap(requestJson("series/" + series.getId(), Locale.ROOT, Cache.ONE_WEEK), "data");
		String image = getString(data, "image");
		String overview = null;
		try {
			Object translation = getMap(requestJson("series/" + series.getId() + "/translations/eng", Locale.ROOT, Cache.ONE_WEEK), "data");
			overview = getString(translation, "overview");
		} catch (Exception e) {
			// overview is optional
		}
		return new SearchResultPreview(image == null || image.isEmpty() ? null : new URL(image), getString(data, "originalCountry"), overview, getString(data, "year"));
	}

	@Override
	public TheTVDBSeriesInfo getSeriesInfo(int id, Locale language) throws Exception {
		return getSeriesInfo(new SearchResult(id), language);
	}

	@Override
	public TheTVDBSeriesInfo getSeriesInfo(SearchResult series, Locale locale) throws Exception {
		Object json = requestJson("series/" + series.getId(), locale, Cache.ONE_WEEK);
		Object data = getMap(json, "data");

		TheTVDBSeriesInfo info = new TheTVDBSeriesInfo(this, locale, series.getId());
		info.setSlug(getString(data, "slug"));
		info.setAliasNames(Stream.of(series.getAliasNames(), getArray(data, "aliases")).flatMap(Arrays::stream).map(it -> {
			return it instanceof Map ? getString(it, "name") : Objects.toString(it, null);
		}).filter(Objects::nonNull).distinct().toArray(String[]::new));

		info.setName(getString(data, "name"));
		info.setStatus(getString(getMap(data, "status"), "name"));
		info.setRuntime(getInteger(data, "averageRuntime"));
		info.setStartDate(getStringValue(data, "firstAired", SimpleDate::parse));

		return info;
	}

	@Override
	protected SeriesData fetchSeriesData(SearchResult series, SortOrder sortOrder, Locale locale) throws Exception {
		// fetch series info
		SeriesInfo info = getSeriesInfo(series, locale);
		info.setOrder(sortOrder.name());

		// ignore preferred language if basic series information isn't even available
		if (info.getName() == null && !locale.equals(DEFAULT_LOCALE)) {
			return fetchSeriesData(series, sortOrder, DEFAULT_LOCALE);
		}

		// if series name isn't even available in English then just use whatever value we've got
		if (info.getName() == null) {
			info.setName(series.getName());
		}

		// fetch episode data
		List<Episode> episodes = new ArrayList<Episode>();
		List<Episode> specials = new ArrayList<Episode>();

		String seasonType = sortOrder == SortOrder.DVD ? "dvd" : sortOrder == SortOrder.Absolute ? "absolute" : "default";
		String language = getApiLanguageCode(locale).orElse("eng");
		for (int page = 0, pageCount = 1; page < pageCount; page++) {
			Object json = requestJson("series/" + series.getId() + "/episodes/" + seasonType + "/" + language + "?page=" + page, locale, Cache.ONE_DAY);

			Map<?, ?> links = getMap(json, "links");
			Integer totalItems = getInteger(links, "total_items");
			Integer pageSize = getInteger(links, "page_size");
			if (totalItems != null && pageSize != null && pageSize > 0) {
				pageCount = (totalItems + pageSize - 1) / pageSize;
			} else if (getString(links, "next") != null) {
				pageCount = Math.max(pageCount, page + 2);
			}

			streamJsonObjects(getMap(json, "data"), "episodes").forEach(it -> {
				Integer id = getInteger(it, "id");
				String episodeName = getString(it, "name");

				// default to English episode title if the preferred language is not available
				if (episodeName == null && !locale.equals(DEFAULT_LOCALE)) {
					try {
						episodeName = getEpisodeList(series, sortOrder, DEFAULT_LOCALE).stream().filter(e -> id.equals(e.getId())).findFirst().map(Episode::getTitle).orElse(null);
					} catch (Exception e) {
						debug.warning(cause("Failed to retrieve default episode title", e));
					}
				}

				Integer absoluteNumber = getInteger(it, "absoluteNumber");
				SimpleDate airdate = getStringValue(it, "aired", SimpleDate::parse);

				// default numbering
				Integer episodeNumber = getInteger(it, "number");
				Integer seasonNumber = getInteger(it, "seasonNumber");

				// adjust for forced absolute numbering (if possible)
				if (sortOrder == SortOrder.Absolute && absoluteNumber != null && absoluteNumber > 0) {
					seasonNumber = null;
					episodeNumber = absoluteNumber;
				} else if (sortOrder == SortOrder.AbsoluteAirdate && airdate != null) {
					// use airdate as absolute episode number
					seasonNumber = null;
					episodeNumber = airdate.getYear() * 1_00_00 + airdate.getMonth() * 1_00 + airdate.getDay();
				}

				if (seasonNumber == null || seasonNumber > 0) {
					// handle as normal episode
					episodes.add(new Episode(info.getName(), seasonNumber, episodeNumber, episodeName, absoluteNumber, null, airdate, id, new SeriesInfo(info)));
				} else {
					// handle as special episode
					specials.add(new Episode(info.getName(), null, null, episodeName, absoluteNumber, episodeNumber, airdate, id, new SeriesInfo(info)));
				}
			});
		}

		// episodes my not be ordered by DVD episode number
		episodes.sort(episodeComparator());

		// add specials at the end
		episodes.addAll(specials);

		return new SeriesData(info, episodes);
	}

	public SearchResult lookupByID(int id, Locale locale) throws Exception {
		if (id <= 0) {
			throw new IllegalArgumentException("Illegal TheTVDB ID: " + id);
		}

		SeriesInfo info = getSeriesInfo(new SearchResult(id), locale);
		return new SearchResult(id, info.getName(), info.getAliasNames());
	}

	public SearchResult lookupByIMDbID(int imdbid, Locale locale) throws Exception {
		if (imdbid <= 0) {
			throw new IllegalArgumentException("Illegal IMDbID ID: " + imdbid);
		}

		Map<String, Object> query = new LinkedHashMap<String, Object>();
		query.put("remote_id", String.format("tt%07d", imdbid));
		query.put("type", "series");
		List<SearchResult> result = search("search", query, locale, Cache.ONE_MONTH);
		return result.size() > 0 ? result.get(0) : null;
	}

	@Override
	public URI getEpisodeListLink(SearchResult searchResult) {
		return URI.create("https://www.thetvdb.com/?tab=seasonall&id=" + searchResult.getId());
	}

	@Override
	public List<Artwork> getArtwork(int id, String category, Locale locale) throws Exception {
		Object json = requestLegacyJson("series/" + id + "/images/query?keyType=" + category, locale, Cache.ONE_MONTH);

		return streamJsonObjects(json, "data").map(it -> {
			String subKey = getString(it, "subKey");
			String resolution = getString(it, "resolution");
			URL url = getStringValue(it, "fileName", this::resolveImage);
			Double rating = getDouble(getMap(it, "ratingsInfo"), "average");

			return new Artwork(Stream.of(category, subKey, resolution), url, locale, rating);
		}).sorted(Artwork.RATING_ORDER).collect(toList());
	}

	protected URL resolveImage(String path) {
		if (path == null || path.isEmpty()) {
			return null;
		}

		// TheTVDB API v2 does not have a dedicated banner mirror
		try {
			return new URL("https://thetvdb.com/banners/" + path);
		} catch (Exception e) {
			throw new IllegalArgumentException(path, e);
		}
	}

	public List<String> getLanguages() throws Exception {
		Object response = requestLegacyJson("languages", Locale.ROOT, Cache.ONE_MONTH);
		return streamJsonObjects(response, "data").map(it -> getString(it, "abbreviation")).collect(toList());
	}

	public List<Person> getActors(int seriesId, Locale locale) throws Exception {
		Object response = requestLegacyJson("series/" + seriesId + "/actors", locale, Cache.ONE_MONTH);

		// e.g. [id:68414, seriesId:78874, name:Summer Glau, role:River Tam, sortOrder:2, image:actors/68414.jpg, imageAuthor:513, imageAdded:0000-00-00 00:00:00, lastUpdated:2011-08-18 11:53:14]
		return streamJsonObjects(response, "data").map(it -> {
			String name = getString(it, "name");
			String character = getString(it, "role");
			Integer order = getInteger(it, "sortOrder");
			URL image = getStringValue(it, "image", this::resolveImage);

			return new Person(name, character, Person.ACTOR, null, order, image);
		}).sorted(Person.CREDIT_ORDER).collect(toList());
	}

	public EpisodeInfo getEpisodeInfo(int id, Locale locale) throws Exception {
		Object response = requestLegacyJson("episodes/" + id, locale, Cache.ONE_MONTH);
		Object data = getMap(response, "data");

		Integer seriesId = getInteger(data, "seriesId");
		String overview = getString(data, "overview");

		Double rating = getDouble(data, "siteRating");
		Integer votes = getInteger(data, "siteRatingCount");

		List<Person> people = new ArrayList<Person>();

		for (Object it : getArray(data, "directors")) {
			people.add(new Person(it.toString(), Person.DIRECTOR));
		}
		for (Object it : getArray(data, "writers")) {
			people.add(new Person(it.toString(), Person.WRITER));
		}
		for (Object it : getArray(data, "guestStars")) {
			people.add(new Person(it.toString(), Person.GUEST_STAR));
		}

		return new EpisodeInfo(this, locale, seriesId, id, people, overview, rating, votes);
	}

}
