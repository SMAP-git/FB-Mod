package net.filebot.postprocess;

import static net.filebot.util.FileUtilities.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.filebot.WebServices;
import net.filebot.web.Episode;
import net.filebot.web.Movie;
import net.filebot.web.SearchResult;
import net.filebot.web.SeriesInfo;
import net.filebot.web.SimpleDate;
import net.filebot.web.SubtitleDescriptor;

public class RenamePostProcessor {
	public enum Action {
		Artwork("Download poster artwork"),
		Subtitles("Download subtitles (OpenSubtitles.com)"),
		Metadata("Write metadata (.nfo)"),
		FileDate("Set modified date to air/release date");

		public final String label;

		Action(String label) {
			this.label = label;
		}
	}

	public static Preferences preferences() {
		return Preferences.userNodeForPackage(RenamePostProcessor.class);
	}

	public static EnumSet<Action> selectedActions() {
		EnumSet<Action> selected = EnumSet.noneOf(Action.class);
		for (Action action : Action.values()) {
			if (preferences().getBoolean(action.name(), false)) {
				selected.add(action);
			}
		}
		return selected;
	}

	public List<String> process(File destination, Object info, EnumSet<Action> actions, Locale locale) {
		List<String> failures = new ArrayList<String>();
		if (!(info instanceof Movie) && !(info instanceof Episode)) {
			return failures;
		}
		for (Action action : actions) {
			try {
				switch (action) {
				case Artwork:
					writeArtwork(destination, info, locale);
					break;
				case Subtitles:
					writeSubtitles(destination, info, locale);
					break;
				case Metadata:
					writeMetadata(destination, info);
					break;
				case FileDate:
					SimpleDate date = getReleaseDate(info, locale);
					if (date == null) {
						throw new IOException("No air/release date available");
					}
					Files.setLastModifiedTime(destination.toPath(), FileTime.from(date.toInstant()));
					break;
				}
			} catch (Exception e) {
				String message = action.label + " for " + destination.getName() + ": " + (e.getMessage() == null ? e.toString() : e.getMessage());
				net.filebot.Logging.debug.log(java.util.logging.Level.WARNING, message, e);
				failures.add(message);
			}
		}
		return failures;
	}

	protected SimpleDate getReleaseDate(Object info, Locale locale) throws Exception {
		if (info instanceof Episode) {
			return ((Episode) info).getAirdate();
		}
		Movie movie = (Movie) info;
		return movie.getTmdbId() > 0 ? WebServices.TheMovieDB.getMovieInfo(movie, locale, false).getReleased()
				: WebServices.OMDb.getMovieInfo(movie).getReleased();
	}

	protected URL getPoster(Object info, Locale locale) throws Exception {
		if (info instanceof Movie) {
			Movie movie = (Movie) info;
			return movie.getTmdbId() > 0 ? WebServices.TheMovieDB.getPoster("movie", movie.getTmdbId(), locale)
					: WebServices.OMDb.getMovieInfo(movie).getPoster();
		}
		Episode episode = (Episode) info;
		SeriesInfo series = episode.getSeriesInfo();
		if (series == null) {
			throw new IOException("Series ID is missing");
		}
		SearchResult result = new SearchResult(series.getId(), episode.getSeriesName());
		switch (series.getDatabase()) {
		case "TheMovieDB::TV":
			return WebServices.TheMovieDB_TV.getPoster(result, locale);
		case "TVmaze":
			return WebServices.TVmaze.getPreview(result).poster;
		case "AniDB":
			return WebServices.AniDB.getPreview(result).poster;
		case "TheTVDB":
			return WebServices.TheTVDB.getPreview(result, locale).poster;
		default:
			throw new IOException("No artwork provider for " + series.getDatabase());
		}
	}

	private void writeArtwork(File destination, Object info, Locale locale) throws Exception {
		Path target = sidecar(destination, "-poster.jpg");
		if (Files.exists(target)) {
			return;
		}
		URL poster = getPoster(info, locale);
		if (poster == null) {
			throw new IOException("No poster artwork available");
		}
		URLConnection connection = poster.openConnection();
		connection.setConnectTimeout(20000);
		connection.setReadTimeout(40000);
		try (InputStream input = connection.getInputStream()) {
			BufferedImage image = ImageIO.read(input);
			if (image == null) {
				throw new IOException("Poster response is not an image");
			}
			BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D graphics = rgb.createGraphics();
			graphics.drawImage(image, 0, 0, null);
			graphics.dispose();
			writeNewFile(target, output -> {
				if (!ImageIO.write(rgb, "jpg", output)) {
					throw new IOException("JPEG encoder is unavailable");
				}
			});
		}
	}

	protected List<SubtitleDescriptor> findSubtitles(File destination, Object info, Locale locale) throws Exception {
		List<SubtitleDescriptor> hashMatches = WebServices.OpenSubtitlesCom.getSubtitleList(new File[] { destination }, locale).get(destination);
		if (!hashMatches.isEmpty()) {
			return hashMatches;
		}
		Map<String, Object> criteria = new LinkedHashMap<String, Object>();
		if (info instanceof Movie) {
			Movie movie = (Movie) info;
			if (movie.getImdbId() > 0) {
				criteria.put("imdb_id", movie.getImdbId());
			} else if (movie.getTmdbId() > 0) {
				criteria.put("tmdb_id", movie.getTmdbId());
			} else {
				throw new IOException("Movie ID is missing");
			}
			criteria.put("type", "movie");
		} else {
			Episode episode = (Episode) info;
			if (episode.getSeason() == null || episode.getEpisode() == null) {
				throw new IOException("Season/episode numbers are required for subtitle lookup");
			}
			criteria.put("query", episode.getSeriesName());
			criteria.put("season_number", episode.getSeason());
			criteria.put("episode_number", episode.getEpisode());
			criteria.put("type", "episode");
		}
		return WebServices.OpenSubtitlesCom.findSubtitles(criteria, locale);
	}

	private void writeSubtitles(File destination, Object info, Locale locale) throws Exception {
		Path target = sidecar(destination, "." + locale.getLanguage() + ".srt");
		if (Files.exists(target) || Files.exists(sidecar(destination, ".srt"))) {
			return;
		}
		SubtitleDescriptor subtitle = findSubtitles(destination, info, locale).stream().filter(it -> "srt".equalsIgnoreCase(it.getType())).findFirst()
				.orElseThrow(() -> new IOException("No SRT subtitles found"));
		ByteBuffer bytes = subtitle.fetch().duplicate();
		writeNewFile(target, output -> {
			byte[] buffer = new byte[8192];
			while (bytes.hasRemaining()) {
				int count = Math.min(buffer.length, bytes.remaining());
				bytes.get(buffer, 0, count);
				output.write(buffer, 0, count);
			}
		});
	}

	private void writeMetadata(File destination, Object info) throws Exception {
		Path target = sidecar(destination, ".nfo");
		if (Files.exists(target)) {
			return;
		}
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = document.createElement(info instanceof Episode ? "episodedetails" : "movie");
		document.appendChild(root);
		if (info instanceof Movie) {
			Movie movie = (Movie) info;
			add(root, "title", movie.getName());
			add(root, "year", movie.getYear());
			addId(root, "imdb", movie.getImdbId() > 0 ? String.format("tt%07d", movie.getImdbId()) : null);
			addId(root, "tmdb", movie.getTmdbId() > 0 ? movie.getTmdbId() : null);
		} else {
			Episode episode = (Episode) info;
			add(root, "title", episode.getTitle());
			add(root, "showtitle", episode.getSeriesName());
			add(root, "season", episode.getSeason());
			add(root, "episode", episode.getEpisode());
			add(root, "aired", episode.getAirdate());
			if (episode.getSeriesInfo() != null && episode.getId() != null) {
				String database = episode.getSeriesInfo().getDatabase();
				addId(root, "TheMovieDB::TV".equals(database) ? "tmdb" : database.toLowerCase(Locale.ROOT), episode.getId());
			}
		}
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		writeNewFile(target, output -> transformer.transform(new DOMSource(document), new StreamResult(output)));
	}

	private static void add(Element parent, String key, Object value) {
		if (value != null) {
			Element element = parent.getOwnerDocument().createElement(key);
			element.setTextContent(value.toString());
			parent.appendChild(element);
		}
	}

	private static void addId(Element parent, String type, Object value) {
		if (value != null) {
			Element id = parent.getOwnerDocument().createElement("uniqueid");
			id.setAttribute("type", type);
			id.setTextContent(value.toString());
			parent.appendChild(id);
		}
	}

	static Path sidecar(File file, String suffix) {
		return new File(file.getParentFile(), getName(file) + suffix).toPath();
	}

	@FunctionalInterface
	private interface Writer {
		void write(OutputStream output) throws Exception;
	}

	private static void writeNewFile(Path target, Writer writer) throws Exception {
		Path temporary = Files.createTempFile(target.getParent(), ".filebot-", ".part");
		try {
			try (OutputStream output = Files.newOutputStream(temporary)) {
				writer.write(output);
			}
			Files.move(temporary, target);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
