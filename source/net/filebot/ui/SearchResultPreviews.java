package net.filebot.ui;

import java.util.Locale;

import net.filebot.web.AnidbClient;
import net.filebot.web.Movie;
import net.filebot.web.OMDbClient;
import net.filebot.web.SearchResult;
import net.filebot.web.SearchResultPreview;
import net.filebot.web.TMDbClient;
import net.filebot.web.TMDbTVClient;
import net.filebot.web.TVMazeClient;
import net.filebot.web.TheTVDBClient;

public final class SearchResultPreviews {

	private SearchResultPreviews() {
	}

	public static void install(SelectDialog<? extends SearchResult> dialog, Object service) {
		install(dialog, service, Locale.ENGLISH);
	}

	@SuppressWarnings("unchecked")
	public static void install(SelectDialog<? extends SearchResult> dialog, Object service, Locale locale) {
		SelectDialog<SearchResult> d = (SelectDialog<SearchResult>) dialog;

		if (service instanceof TMDbTVClient) {
			d.setPreviewProvider(it -> ((TMDbTVClient) service).getPreview(it, locale), Integer.MAX_VALUE);
		} else if (service instanceof TMDbClient) {
			d.setPreviewProvider(it -> it instanceof Movie && ((Movie) it).getTmdbId() > 0 ? ((TMDbClient) service).getPreview("movie", ((Movie) it).getTmdbId(), locale) : null, Integer.MAX_VALUE);
		} else if (service instanceof TheTVDBClient) {
			d.setPreviewProvider(it -> ((TheTVDBClient) service).getPreview(it, locale), Integer.MAX_VALUE);
		} else if (service instanceof TVMazeClient) {
			d.setPreviewProvider(it -> ((TVMazeClient) service).getPreview(it), Integer.MAX_VALUE);
		} else if (service instanceof OMDbClient) {
			d.setPreviewProvider(it -> it instanceof Movie ? ((OMDbClient) service).getPreview((Movie) it) : null, Integer.MAX_VALUE);
		} else if (service instanceof AnidbClient) {
			// AniDB bans clients that make too many requests
			d.setPreviewProvider(it -> ((AnidbClient) service).getPreview(it), 5);
		}
	}

}
