package net.shrimpworks.unreal.archive.www;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.shrimpworks.unreal.archive.content.Content;
import net.shrimpworks.unreal.archive.content.ContentManager;
import net.shrimpworks.unreal.archive.wiki.WikiManager;

/**
 * Submits contents to Minimum Effort Search instance.
 * <p>
 * See https://github.com/shrimpza/minimum-effort-search
 */
public class MESSubmitter {

	private static ObjectMapper JSON_MAPPER = new ObjectMapper(new JsonFactory());

	private static final String ADD_ENDPOINT = "/index/add";
	private static final String ADD_BATCH_ENDPOINT = "/index/addBatch";

	public void submit(
		ContentManager contentManager, String rootUrl, String mseUrl, String mseToken, int batchSize,
		Consumer<Double> progress, Consumer<Boolean> done
	) throws IOException {
		Collection<Content> contents = contentManager.all();
		Path root = Paths.get("");
		final int count = contents.size();
		int i = 0;

		final List<Map<String, Object>> batchDocs = new ArrayList<>(batchSize);

		for (Content content : contents) {
			if (content.variationOf != null) continue;
			Map<String, Object> doc = Map.of(
				"id", content.hash,
				"score", 1.0d,
				"fields", Map.of(
					"name", content.name.replaceAll("-", "\\\\-"),
					"game", content.game,
					"type", content.friendlyType(),
					"author", content.authorName().replaceAll("-", "\\\\-"),
					"url", rootUrl + "/" + content.slugPath(root).toString() + ".html",
					"date", content.releaseDate,
					"description", content.autoDescription(),
					"image", content.attachments.stream()
												.filter(a -> a.type == Content.AttachmentType.IMAGE)
												.map(a -> a.url)
												.findFirst().orElse(""),
					"keywords", String.join(" ", content.autoTags())
				)
			);

			batchDocs.add(doc);

			if (batchDocs.size() >= batchSize) {
				post(mseUrl + ADD_BATCH_ENDPOINT, mseToken, JSON_MAPPER.writeValueAsString(Map.of("docs", batchDocs)));
				batchDocs.clear();
			}

			i++;

			if (i % 1000 == 0) progress.accept((double)i / (double)count);
		}

		progress.accept(1.0d);
		done.accept(true);
	}

	public void submit(WikiManager wikiManager, String site_url, String mse_wiki_url, String mse_wiki_token, int batchSize,
					   Consumer<Double> progress, Consumer<Boolean> done) {

	}

	private static boolean post(String url, String token, String payload) throws IOException {
		URL urlConnection = new URL(url);
		HttpURLConnection httpConn = (HttpURLConnection)urlConnection.openConnection();

		httpConn.setRequestMethod("POST");
		httpConn.setRequestProperty("Authorization", String.format("bearer %s", token));
		httpConn.setRequestProperty("Content-Length", Long.toString(payload.length()));

		httpConn.setDoOutput(true);
		httpConn.connect();

		try {
			try (OutputStreamWriter wr = new OutputStreamWriter(httpConn.getOutputStream(), StandardCharsets.UTF_8)) {
				wr.write(payload);
				wr.flush();
			}

			int response = httpConn.getResponseCode();
			return response >= 200 && response <= 299;
		} finally {
			String connection = httpConn.getHeaderField("Connection");
			if (connection == null || connection.equals("Close")) {
				httpConn.disconnect();
			}
		}
	}
}
