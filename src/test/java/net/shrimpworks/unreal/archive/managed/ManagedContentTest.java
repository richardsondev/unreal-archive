package net.shrimpworks.unreal.archive.managed;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import net.shrimpworks.unreal.archive.common.ArchiveUtil;
import net.shrimpworks.unreal.archive.common.Platform;
import net.shrimpworks.unreal.archive.common.YAML;
import net.shrimpworks.unreal.archive.content.Content;
import net.shrimpworks.unreal.archive.www.ManagedContent;
import net.shrimpworks.unreal.archive.www.SiteFeatures;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ManagedContentTest {

	@Test
	public void managedYaml() throws IOException {
		final Managed man = mockContent();

		// serialise and de-serialise between YAML and instance
		final String stringMan = YAML.toString(man);
		final Managed newMan = YAML.fromString(stringMan, Managed.class);

		assertEquals(man, newMan);
		assertNotSame(man, newMan);
		assertEquals(man.downloads.get(0), newMan.downloads.get(0));

		// fake syncing the download, downloads don't count as changes, so they can be managed while syncing
		newMan.downloads.get(0).downloads.add(new Content.Download("https://cool-files.dl/file.exe", false));
		newMan.downloads.get(0).synced = true;

		assertEquals(man, newMan);
		assertNotEquals(man.downloads.get(0), newMan.downloads.get(0));
	}

	@Test
	public void contentProcess() throws IOException {
		final Managed man = mockContent();

		// create a simple on-disk structure containing a test document and metadata
		final Path tmpRoot = Files.createTempDirectory("test-managed");
		try {
			final Path outPath = Files.createDirectories(tmpRoot.resolve("test"));
			Files.write(outPath.resolve("managed.yml"), YAML.toString(man).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);

			try (InputStream is = getClass().getResourceAsStream("readme.md")) {
				Files.copy(is, outPath.resolve(man.document));
			}

			final ManagedContentRepository cm = new ManagedContentRepository.FileRepository(tmpRoot);
			assertTrue(cm.all().contains(man));

			try (Reader reader = Channels.newReader(cm.document(man), StandardCharsets.UTF_8)) {
				assertNotNull(reader);

				Parser parser = Parser.builder().build();
				HtmlRenderer renderer = HtmlRenderer.builder().build();
				String markdown = renderer.render(parser.parseReader(reader));
				assertNotNull(markdown);
				assertTrue(markdown.contains("Testing Document"));
			}
		} finally {
			// cleanup temp files
			ArchiveUtil.cleanPath(tmpRoot);
		}
	}

	@Test
	public void contentWww() throws IOException {
		Managed man = mockContent();

		// create a simple on-disk structure containing a test document and metadata
		Path tmpRoot = Files.createTempDirectory("test-managed");
		Path wwwRoot = Files.createTempDirectory("test-managed-www");
		try {
			final Path outPath = Files.createDirectories(tmpRoot.resolve("test"));
			Files.write(outPath.resolve("managed.yml"), YAML.toString(man).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);

			try (InputStream is = getClass().getResourceAsStream("readme.md")) {
				Files.copy(is, outPath.resolve(man.document));
			}

			final ManagedContentRepository cm = new ManagedContentRepository.FileRepository(tmpRoot);
			assertTrue(cm.all().contains(man));

			ManagedContent content = new ManagedContent(cm, wwwRoot, wwwRoot, SiteFeatures.ALL);
			assertEquals(4, content.generate().size());
		} finally {
			// cleanup temp files
			ArchiveUtil.cleanPath(tmpRoot);
			ArchiveUtil.cleanPath(wwwRoot);
		}
	}

	private Managed mockContent() {
		final Managed man = new Managed();
		man.createdDate = LocalDate.now().minusDays(3);
		man.updatedDate = LocalDate.now();
		man.group = "Testing & Stuff";
		man.game = "General";
		man.path = "Tests";
		man.title = "Testing Things";
		man.author = "Bob";
		man.description = "There is no description";
		man.homepage = "https://unreal.com/";
		man.document = "readme.md";

		final Managed.ManagedFile file = new Managed.ManagedFile();
		file.platform = Platform.WINDOWS;
		file.localFile = "file.exe";
		file.synced = false;
		file.title = "The File";
		file.version = "1.0";

		man.downloads.add(file);

		return man;
	}

}
