package net.filebot.web;

import static org.junit.Assert.*;

import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.prefs.Preferences;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import net.filebot.WebServices;

public class TheTVDBConfigurationTest {
	@Test
	public void configuredKeyControlsProviderOrderAndOverrides() {
		Preferences preferences = TheTVDBClient.preferences();
		String oldKey = preferences.get("apiKey", "");
		String oldPin = preferences.get("pin", "");
		String override = System.getProperty("net.filebot.WebServices.TheTVDB.apikey");
		try {
			System.clearProperty("net.filebot.WebServices.TheTVDB.apikey");
			preferences.put("apiKey", "");
			preferences.put("pin", "test-pin");
			assertFalse(TheTVDBClient.isConfigured());
			assertArrayEquals(new EpisodeListProvider[] { WebServices.TheMovieDB_TV, WebServices.AniDB, WebServices.TVmaze }, WebServices.getEpisodeListProviders());
			preferences.put("apiKey", "test-key");
			assertTrue(TheTVDBClient.isConfigured());
			assertArrayEquals(new EpisodeListProvider[] { WebServices.TheMovieDB_TV, WebServices.TheTVDB, WebServices.AniDB, WebServices.TVmaze }, WebServices.getEpisodeListProviders());
			System.setProperty("net.filebot.WebServices.TheTVDB.apikey", "override-key");
			assertEquals("override-key", TheTVDBClient.getConfiguredApiKey());
		} finally {
			preferences.put("apiKey", oldKey);
			preferences.put("pin", oldPin);
			if (override == null) {
				System.clearProperty("net.filebot.WebServices.TheTVDB.apikey");
			} else {
				System.setProperty("net.filebot.WebServices.TheTVDB.apikey", override);
			}
		}
	}

	@Test
	public void credentialChangesResetLoginAndUseNewKeyAndPin() throws Exception {
		List<String> logins = java.util.Collections.synchronizedList(new ArrayList<String>());
		List<String> authorizations = java.util.Collections.synchronizedList(new ArrayList<String>());
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String response;
			if (exchange.getRequestURI().getPath().endsWith("/login")) {
				logins.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
				response = "{\"data\":{\"token\":\"token-" + logins.size() + "\"}}";
			} else {
				authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
				response = "{\"data\":[]}";
			}
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (java.io.OutputStream output = exchange.getResponseBody()) {
				output.write(bytes);
			}
			exchange.close();
		});
		server.start();
		try {
			TheTVDBClient client = new TheTVDBClient("first-key", "first-pin") {
				@Override
				protected URL getEndpoint(String path) throws Exception {
					return new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/v4/" + path);
				}
			};
			client.fetchSearchResult("First " + UUID.randomUUID(), Locale.ENGLISH);
			client.fetchSearchResult("Second " + UUID.randomUUID(), Locale.ENGLISH);
			assertEquals(1, logins.size());
			client.setCredentials("second-key", "second-pin");
			client.fetchSearchResult("Third " + UUID.randomUUID(), Locale.ENGLISH);
			assertEquals(2, logins.size());
			assertTrue(logins.get(0).contains("\"apikey\":\"first-key\""));
			assertTrue(logins.get(0).contains("\"pin\":\"first-pin\""));
			assertTrue(logins.get(1).contains("\"apikey\":\"second-key\""));
			assertTrue(logins.get(1).contains("\"pin\":\"second-pin\""));
			assertEquals(java.util.Arrays.asList("Bearer token-1", "Bearer token-1", "Bearer token-2"), authorizations);
		} finally {
			server.stop(0);
		}
	}
}
