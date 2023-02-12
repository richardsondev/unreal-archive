package net.shrimpworks.unreal.archive.indexing;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.shrimpworks.unreal.archive.common.ArchiveUtil;
import net.shrimpworks.unreal.archive.common.Util;
import net.shrimpworks.unreal.packages.Umod;

public class Incoming implements Closeable {

	private static final Duration EXTRACT_TIMEOUT = Duration.ofMinutes(2);

	public enum FileType {
		CODE(true, "u"),
		MAP(true, "unr", "ut2", "ut3", "un2", "run"),
		PACKAGE(true, "upk"), /* UT3 catch-all format */
		TEXTURE(true, "utx"),
		MUSIC(true, "umx", "ogg"),
		SOUNDS(true, "uax"),
		ANIMATION(true, "ukx"),
		STATICMESH(true, "usx", "usm"), /* usm added to Unreal 227g */
		PREFAB(true, "upx"),
		PHYSICS(true, "ka"),
		PLAYER(true, "upl"),
		INT(false, "int"),
		INI(false, "ini"),
		UCL(false, "ucl"),
		UMOD(true, "umod", "ut2mod", "ut4mod", "rmod"),
		TEXT(false, "txt"),
		HTML(false, "html", "htm"),
		IMAGE(false, "jpg", "jpeg", "bmp", "png", "gif"),
		;

		public static final FileType[] PACKAGES = { CODE, MAP, TEXTURE, SOUNDS, ANIMATION, STATICMESH, PACKAGE, MUSIC };

		public static final FileType[] ALL = FileType.values();

		public final boolean important;
		public final Collection<String> ext;

		FileType(boolean important, String... ext) {
			this.important = important;
			this.ext = Collections.unmodifiableCollection(Arrays.asList(ext));
		}

		public boolean matches(String path) {
			return (ext.contains(Util.extension(path).toLowerCase()));
		}

		public static boolean important(Path path) {
			return important(path.toString());
		}

		public static boolean important(String path) {
			for (FileType type : values()) {
				if (type.important && type.ext.contains(Util.extension(path).toLowerCase())) return true;
			}
			return false;
		}

		public static FileType forFile(String path) {
			for (FileType type : values()) {
				if (type.ext.contains(Util.extension(path).toLowerCase())) return type;
			}
			return null;
		}
	}

	public final Submission submission;
	public final String hash;
	public final int fileSize;
	public final IndexLog log;

	private final Set<Umod> umods;

	public Path contentRoot;
	public Map<String, Object> files;

	private Path repackPath;

	public Incoming(Submission submission) throws IOException, UnsupportedOperationException {
		this(submission, IndexLog.NOP);
	}

	public Incoming(Submission submission, IndexLog log) throws IOException, UnsupportedOperationException {
		this.submission = submission;
		this.hash = Util.hash(submission.filePath);
		this.fileSize = (int)Files.size(submission.filePath);
		this.umods = new HashSet<>();
		this.log = log;
	}

	public Incoming prepare() throws IOException {
		this.contentRoot = Files.createTempDirectory("archive-incoming-");
		unpackFiles(submission.filePath, this.contentRoot);
		this.files = listFiles(this.contentRoot);
		return this;
	}

	@Override
	public void close() {
		for (Umod v : umods) {
			try {
				v.close();
			} catch (Exception e) {
				log.log(IndexLog.EntryType.INFO, "Failed cleaning up Umod file " + v, e);
			}
		}

		umods.clear();

		if (files != null) files.clear();

		// clean up contentRoot
		if (contentRoot != null) {
			try {
				ArchiveUtil.cleanPath(contentRoot);
			} catch (Exception e) {
				log.log(IndexLog.EntryType.INFO, "Failed cleaning up content path " + contentRoot, e);
			}
		}

		if (repackPath != null) {
			try {
				ArchiveUtil.cleanPath(repackPath);
			} catch (Exception e) {
				log.log(IndexLog.EntryType.INFO, "Failed cleaning up repack path " + repackPath, e);
			}
		}
	}

	public Path getRepack(String repackName) throws IOException, InterruptedException {
		if (Util.extension(submission.filePath).equalsIgnoreCase("zip")) return null;

		repackPath = Files.createTempDirectory("archive-repack-");

		Path dest = repackPath.resolve(repackName + ".zip");

		if (contentRoot != null) {
			return ArchiveUtil.createZip(contentRoot, dest, Duration.ofSeconds(60));
		}

		return null;
	}

	public Set<IncomingFile> files(FileType... type) {
		Set<IncomingFile> res = new HashSet<>();
		for (FileType t : type) {
			res.addAll(files.keySet().stream()
							.filter(t::matches)
							.map(IncomingFile::new)
							.collect(Collectors.toSet()));
		}
		return Collections.unmodifiableSet(res);
	}

	private Map<String, Object> listFiles(Path contentRoot) throws IOException {
		Map<String, Object> files = new HashMap<>();
		if (contentRoot != null && Files.exists(contentRoot)) {
			Files.walkFileTree(contentRoot, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.toString().toLowerCase().endsWith(".umod")
						|| file.toString().toLowerCase().endsWith(".ut2mod")
						|| file.toString().toLowerCase().endsWith(".ut4mod")) {
						files.putAll(umodFiles(file));
					}
					files.put(file.toString(), file);
					return FileVisitResult.CONTINUE;
				}
			});
		}
		return files;
	}

	private Map<String, Umod.UmodFile> umodFiles(Path path) throws IOException {
		final Map<String, Umod.UmodFile> fileList = new HashMap<>();

		Umod umod = new Umod(path);
		umods.add(umod);

		for (Umod.UmodFile file : umod.files) {
			if (file.name.equalsIgnoreCase("System\\Manifest.int") || file.name.equalsIgnoreCase("System\\Manifest.ini")) continue;
			fileList.put(file.name.replaceAll("\\\\", "/"), file);
		}

		return fileList;
	}

	private void unpackFiles(Path incoming, Path destination) throws IOException, UnsupportedOperationException {
		if (ArchiveUtil.isArchive(incoming)) {
			// it's an archive of files of some sort, unpack it to the root
			extract(incoming, destination);
		} else if (FileType.important(incoming)) {
			// it's simply a loose file of a type we're interested in
			Files.copy(incoming, destination.resolve(incoming.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		} else {
			throw new UnsupportedFileTypeException("Can't unpack file " + incoming);
		}
	}

	private void extract(Path archive, Path destination) throws IOException, UnsupportedOperationException {
		try {
			Path rootExtracted = ArchiveUtil.extract(archive, destination, EXTRACT_TIMEOUT, true);
			Files.walkFileTree(rootExtracted, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					try {
						if (ArchiveUtil.isArchive(file)) extract(file, destination.resolve(file.getFileName().toString() + ".ex"));
					} catch (ArchiveUtil.BadArchiveException ex) {
						// pass - don't fail on internal file extractions
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (InterruptedException e) {
			throw new IOException("Extract took too long", e);
		}
	}

	@Override
	public String toString() {
		return String.format("Incoming [submission=%s, contentRoot=%s, hash=%s]",
							 submission, contentRoot, hash);
	}

	public class IncomingFile {

		public final String file;

		private IncomingFile(String file) {
			this.file = file;
		}

		public SeekableByteChannel asChannel() {
			try {
				if (files.get(file) instanceof Path) {
					return FileChannel.open((Path)files.get(file));
				} else if (files.get(file) instanceof Umod.UmodFile) {
					return ((Umod.UmodFile)files.get(file)).read();
				}
			} catch (IOException e) {
				throw new IllegalStateException("Failed to open file for reading " + file, e);
			}

			return null;
		}

		public String fileName() {
			return Util.fileName(file);
		}

		public int fileSize() {
			try {
				if (files.get(file) instanceof Path) {
					return (int)Files.size((Path)files.get(file));
				} else if (files.get(file) instanceof Umod.UmodFile) {
					return ((Umod.UmodFile)files.get(file)).size;
				}
			} catch (IOException e) {
				throw new IllegalStateException("Failed to get file size for " + file, e);
			}
			return 0;
		}

		public LocalDateTime fileDate() {
			try {
				if (files.get(file) instanceof Path) {
					return Files.getLastModifiedTime((Path)files.get(file)).toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime(); // hmm?
				} else if (files.get(file) instanceof Umod.UmodFile) {
					Set<IncomingFile> umodMaybe = files(FileType.UMOD);
					if (!umodMaybe.isEmpty()) return umodMaybe.iterator().next().fileDate();
				}
			} catch (IOException e) {
				throw new IllegalStateException("Failed to get file date for " + file, e);
			}
			return LocalDateTime.now();
		}

		public FileType fileType() {
			return FileType.forFile(file);
		}

		public String hash() {
			try {
				if (files.get(file) instanceof Path) {
					return Util.hash((Path)files.get(file));
				} else if (files.get(file) instanceof Umod.UmodFile) {
					return ((Umod.UmodFile)files.get(file)).sha1();
				}
			} catch (IOException e) {
				throw new IllegalStateException("Failed to get hash for " + file, e);
			}
			return null;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			IncomingFile that = (IncomingFile)o;
			return Objects.equals(file, that.file);
		}

		@Override
		public int hashCode() {
			return Objects.hash(file);
		}

		@Override
		public String toString() {
			return String.format("IncomingFile [file=%s]", file);
		}
	}

	public static class UnsupportedFileTypeException extends UnsupportedOperationException {

		public UnsupportedFileTypeException(String message) {
			super(message);
		}
	}
}
