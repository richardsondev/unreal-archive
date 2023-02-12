package net.shrimpworks.unreal.archive.content.maps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.shrimpworks.unreal.archive.content.ContentType;
import net.shrimpworks.unreal.archive.indexing.Incoming;
import net.shrimpworks.unreal.archive.indexing.IndexLog;
import net.shrimpworks.unreal.archive.indexing.Submission;
import net.shrimpworks.unreal.archive.indexing.maps.MapIndexHandler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class MapIndexerTest {

	@Test
	public void gametypeDetection() {
		assertEquals("Greed", GameTypes.forMap("CTF-GRD-Cake").name);
		assertEquals("Greed", GameTypes.forMap("VCTF-GRD-Cake").name);
		assertEquals("Capture The Flag", GameTypes.forMap("CTF-Lies").name);
		assertEquals("DeathMatch", GameTypes.forMap("DM-DeathMatch").name);
	}

	@Test
	public void utMap() throws IOException {
		Path tmpMap = Files.createTempFile("test-dm-longestyard", ".zip");
		try (InputStream is = getClass().getResourceAsStream("../maps/dm-longestyard.zip")) {
			Files.copy(is, tmpMap, StandardCopyOption.REPLACE_EXISTING);

			Submission sub = new Submission(tmpMap);
			IndexLog log = new IndexLog();
			Incoming incoming = new Incoming(sub, log).prepare();

			MapIndexHandler indexer = new MapIndexHandler();
			Map map = ContentType.MAP.newContent(incoming);
			indexer.index(incoming, map, r -> {
				assertEquals("Unreal Tournament", r.content.game);
				assertEquals("The Longest Yard", r.content.title);
				assertEquals("DeathMatch", r.content.gametype);
				assertFalse(r.files.isEmpty());
			});
		} finally {
			Files.deleteIfExists(tmpMap);
		}
	}

	@Test
	public void ut3Map() throws IOException {
		Path tmpMap = Files.createTempFile("test-ctf-power", ".rar");

		try (InputStream is = getClass().getResourceAsStream("../maps/ctf-power.rar")) {
			Files.copy(is, tmpMap, StandardCopyOption.REPLACE_EXISTING);

			Submission sub = new Submission(tmpMap);
			IndexLog log = new IndexLog();
			Incoming incoming = new Incoming(sub, log).prepare();

			MapIndexHandler indexer = new MapIndexHandler();
			Map map = ContentType.MAP.newContent(incoming);
			indexer.index(incoming, map, r -> {
				assertEquals("Unreal Tournament 3", r.content.game);
				assertEquals("Power", r.content.title);
				assertEquals("Capture The Flag", r.content.gametype);
				assertEquals("RedSteels_Fury", r.content.author);
			});
		} finally {
			Files.deleteIfExists(tmpMap);
		}
	}
}
