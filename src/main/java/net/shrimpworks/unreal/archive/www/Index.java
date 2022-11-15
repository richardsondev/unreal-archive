package net.shrimpworks.unreal.archive.www;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.shrimpworks.unreal.archive.content.ContentManager;
import net.shrimpworks.unreal.archive.content.GameTypeManager;
import net.shrimpworks.unreal.archive.docs.DocumentManager;
import net.shrimpworks.unreal.archive.managed.ManagedContentManager;

public class Index implements PageGenerator {

	private final DocumentManager documents;
	private final ManagedContentManager updates;
	private final ContentManager content;
	private final GameTypeManager gametypes;
	private final Path root;
	private final Path staticRoot;
	private final SiteFeatures features;

	public Index(ContentManager content, GameTypeManager gametypes, DocumentManager documents, ManagedContentManager updates,
				 Path output, Path staticRoot, SiteFeatures features) {
		this.content = content;
		this.gametypes = gametypes;
		this.documents = documents;
		this.updates = updates;

		this.root = output;
		this.staticRoot = staticRoot;
		this.features = features;
	}

	@Override
	public Set<SiteMap.Page> generate() {
		Map<String, Long> contentCount = new HashMap<>();
		content.countByType().forEach((k, v) -> contentCount.put(k.getSimpleName(), v));
		contentCount.put("Documents", documents.all().stream().filter(d -> d.published).count());
		contentCount.put("Updates", updates.all().stream().filter(d -> d.published).count());
		contentCount.put("GameTypes", gametypes.all().stream().filter(d -> !d.deleted).count());

		Templates.PageSet pages = new Templates.PageSet("", features, root, staticRoot, root);

		pages.add("index.ftl", SiteMap.Page.of(1f, SiteMap.ChangeFrequency.weekly), "Home")
			 .put("count", contentCount)
			 .write(root.resolve("index.html"));

		pages.add("404.ftl", SiteMap.Page.of(0f, SiteMap.ChangeFrequency.never), "Not Found")
			 .write(root.resolve("404.html"));

		return pages.pages;
	}
}
