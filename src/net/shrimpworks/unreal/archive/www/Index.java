package net.shrimpworks.unreal.archive.www;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.shrimpworks.unreal.archive.content.ContentManager;
import net.shrimpworks.unreal.archive.docs.DocumentManager;
import net.shrimpworks.unreal.archive.managed.ManagedContentManager;

public class Index implements PageGenerator {

	private final DocumentManager documents;
	private final ManagedContentManager updates;
	private final ContentManager content;
	private final Path root;
	private final Path staticRoot;

	public Index(ContentManager content, DocumentManager documents, ManagedContentManager updates, Path output, Path staticRoot) {
		this.content = content;
		this.documents = documents;
		this.updates = updates;

		this.root = output;
		this.staticRoot = staticRoot;
	}

	@Override
	public Set<SiteMap.Page> generate() {
		Set<SiteMap.Page> pages = new HashSet<>();
		Map<String, Long> contentCount = new HashMap<>();
		content.countByType().forEach((k, v) -> contentCount.put(k.getSimpleName(), v));
		contentCount.put("Documents", documents.all().stream().filter(d -> d.published).count());
		contentCount.put("Updates", updates.all().stream().filter(d -> d.published).count());

		try {
			pages.add(Templates.template("index.ftl", SiteMap.Page.of(1f, SiteMap.ChangeFrequency.weekly))
							   .put("static", root.relativize(staticRoot))
							   .put("title", "Home")
							   .put("count", contentCount)
							   .put("siteRoot", root)
							   .write(root.resolve("index.html")));
		} catch (IOException e) {
			throw new RuntimeException("Failed to render page", e);
		}

		return pages;
	}
}
