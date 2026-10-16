package net.filebot.web;

import java.net.URL;

public class SearchResultPreview {

	public final URL poster;
	public final String country;
	public final String overview;
	public final String year;

	public SearchResultPreview(URL poster, String country, String overview, String year) {
		this.poster = poster;
		this.country = country;
		this.overview = overview;
		this.year = year;
	}

}
