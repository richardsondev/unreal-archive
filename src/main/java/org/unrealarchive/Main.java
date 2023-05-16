package org.unrealarchive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.shrimpworks.unreal.packages.Umod;

import org.unrealarchive.common.ArchiveUtil;
import org.unrealarchive.common.CLI;
import org.unrealarchive.common.Util;
import org.unrealarchive.common.Version;
import org.unrealarchive.common.YAML;
import org.unrealarchive.content.AuthorNames;
import org.unrealarchive.content.FileType;
import org.unrealarchive.content.Games;
import org.unrealarchive.content.addons.Addon;
import org.unrealarchive.content.addons.GameType;
import org.unrealarchive.content.addons.GameTypeRepository;
import org.unrealarchive.content.addons.SimpleAddonRepository;
import org.unrealarchive.content.addons.SimpleAddonType;
import org.unrealarchive.content.docs.DocumentRepository;
import org.unrealarchive.content.managed.ManagedContentRepository;
import org.unrealarchive.content.wiki.WikiRepository;
import org.unrealarchive.indexing.ContentEditor;
import org.unrealarchive.indexing.ContentManager;
import org.unrealarchive.indexing.GameTypeManager;
import org.unrealarchive.indexing.Incoming;
import org.unrealarchive.indexing.IndexLog;
import org.unrealarchive.indexing.Indexer;
import org.unrealarchive.indexing.ManagedContentManager;
import org.unrealarchive.indexing.Scanner;
import org.unrealarchive.indexing.Submission;
import org.unrealarchive.mirror.LocalMirrorClient;
import org.unrealarchive.mirror.Mirror;
import org.unrealarchive.storage.DataStore;
import org.unrealarchive.www.Documents;
import org.unrealarchive.www.Index;
import org.unrealarchive.www.MESSubmitter;
import org.unrealarchive.www.ManagedContent;
import org.unrealarchive.www.PageGenerator;
import org.unrealarchive.www.Search;
import org.unrealarchive.www.SiteFeatures;
import org.unrealarchive.www.SiteMap;
import org.unrealarchive.www.Submit;
import org.unrealarchive.www.Templates;
import org.unrealarchive.www.Wiki;
import org.unrealarchive.www.content.Authors;
import org.unrealarchive.www.content.FileDetails;
import org.unrealarchive.www.content.GameTypes;
import org.unrealarchive.www.content.Latest;
import org.unrealarchive.www.content.MapPacks;
import org.unrealarchive.www.content.Maps;
import org.unrealarchive.www.content.Models;
import org.unrealarchive.www.content.Mutators;
import org.unrealarchive.www.content.Packages;
import org.unrealarchive.www.content.Skins;
import org.unrealarchive.www.content.Voices;

import static org.unrealarchive.content.addons.Addon.UNKNOWN;

public class Main {

	static {
		// prepare the version
		Version.setVersion(Main.class);
	}

	private static final String CONTENT_DIR = "content";
	private static final String DOCUMENTS_DIR = "documents";
	private static final String GAMETYPES_DIR = "gametypes";
	private static final String MANAGED_DIR = "managed";
	private static final String AUTHORS_DIR = "authors";
	private static final String WIKIS_DIR = "wikis";

	private static final Path TMP = Paths.get(System.getProperty("java.io.tmpdir"));
	private static final String CONTENT_URL = System.getenv().getOrDefault("UA_CONTENT_URL",
																		   "https://github.com/unreal-archive/unreal-archive-data/archive/master.zip");

	public static void main(String[] args) throws IOException, InterruptedException, ReflectiveOperationException {
		System.err.printf("Unreal Archive version %s%n", Version.version());

		final CLI cli = CLI.parse(Collections.emptyMap(), args);

		if (cli.commands().length == 0) {
			usage();
			System.exit(1);
		}

		switch (cli.commands()[0].toLowerCase()) {
			case "index":
				SimpleAddonRepository indexRepo = contentRepo(cli);
				index(indexRepo, contentManager(cli, indexRepo), cli);
				break;
			case "scan":
				scan(contentRepo(cli), cli);
				break;
			case "edit":
				edit(contentManager(cli, contentRepo(cli)), cli);
				break;
			case "set":
				set(contentManager(cli, contentRepo(cli)), cli);
				break;
			case "gametype":
				GameTypeRepository gameTypeRepo = gameTypeRepo(cli);
				gametype(gameTypeRepo, gameTypeManager(cli, gameTypeRepo), cli);
				break;
			case "managed":
				ManagedContentRepository managedRepo = managedRepo(cli);
				managed(managedRepo, managedContentManager(cli, managedRepo), cli);
				break;
			case "mirror":
				SimpleAddonRepository mirrorRepo = contentRepo(cli);
				GameTypeRepository gameTypeMirrorRepo = gameTypeRepo(cli);
				ManagedContentRepository managedMirrorRepo = managedRepo(cli);
				mirror(mirrorRepo, contentManager(cli, mirrorRepo),
					   gameTypeMirrorRepo, gameTypeManager(cli, gameTypeMirrorRepo),
					   managedMirrorRepo, managedContentManager(cli, managedMirrorRepo),
					   cli);
				break;
			case "local-mirror":
				localMirror(contentRepo(cli), cli);
				break;
			case "www":
				www(contentRepo(cli), gameTypeRepo(cli), documentRepo(cli), managedRepo(cli), wikiRepo(cli), cli);
				break;
			case "search-submit":
				searchSubmit(contentRepo(cli), documentRepo(cli), managedRepo(cli), wikiRepo(cli), cli);
				break;
			case "summary":
				summary(contentRepo(cli));
				break;
			case "ls":
				list(contentRepo(cli), cli);
				break;
			case "show":
				show(contentRepo(cli), cli);
				break;
			case "unpack":
				unpack(cli);
			case "install":
				install(contentRepo(cli), cli);
				break;
			case "wiki":
				wiki(wikiRepo(cli));
				break;
			default:
				System.out.printf("Command \"%s\" does not exist!%n%n", cli.commands()[0]);
				usage();
		}

		System.exit(0);
	}

	private static void wiki(WikiRepository cli) throws IOException {
		// nothing to do
	}

	private static String userPrompt(String prompt, String defaultValue) {
		System.out.println(prompt);
		System.out.print("> ");
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
			String in = reader.readLine().trim();
			if (in.isEmpty()) return defaultValue;
			else return in;
		} catch (IOException e) {
			System.err.printf("Failed to read user input: %s", e);
			System.exit(254);
		}
		return defaultValue;
	}

	private static Path contentPath(CLI cli) throws IOException {
		if (cli.option("content-path", null) == null) {
			final String opt = userPrompt("content-path not specified. Download and use read-only content data? [Y/n]", "y").toLowerCase();
			if (opt.equalsIgnoreCase("y")) {
				Path tmpPath = TMP.resolve("ua-tmp");
				if (!Files.exists(tmpPath) || !Files.isDirectory(tmpPath)) {
					Files.createDirectories(tmpPath);
					Path tmpFile = tmpPath.resolve("archive.zip");
					System.out.printf("Downloading archive from %s... ", CONTENT_URL);
					Util.downloadTo(Util.toUriString(CONTENT_URL), tmpFile);
					System.out.println("Done");
					try {
						System.out.printf("Extracting archive from %s to %s... ", tmpFile, tmpPath);
						ArchiveUtil.extract(tmpFile, tmpPath, Duration.ofMinutes(10));
						System.out.println("Done");
					} catch (Throwable e) {
						// make sure to clean out the path, so we can try again next time
						ArchiveUtil.cleanPath(tmpPath);
						throw new IOException("Failed to extract downloaded content archive", e);
					}
				}

				// find the content directory within the extracted stuff
				try (Stream<Path> pathStream = Files.walk(tmpPath, 3, FileVisitOption.FOLLOW_LINKS)) {
					Optional<Path> contentParent = pathStream
						.filter(p -> Files.isDirectory(p) && p.getFileName().toString().equals(CONTENT_DIR))
						.map(Path::getParent)
						.findFirst();

					cli.putOption("content-path", contentParent.orElseThrow(IllegalArgumentException::new).toString());
				}
			} else {
				System.err.println("content-path must be specified!");
				System.exit(2);
			}
		}

		Path contentPath = Paths.get(cli.option("content-path", null));
		if (!Files.isDirectory(contentPath)) {
			System.err.println("content-path must be a directory!");
			System.exit(3);
		}

		return contentPath.toAbsolutePath();
	}

	private static SimpleAddonRepository contentRepo(CLI cli) throws IOException {
		Path contentPath = contentPath(cli);

		final long start = System.currentTimeMillis();
		final SimpleAddonRepository repo = new SimpleAddonRepository.FileRepository(contentPath.resolve(CONTENT_DIR));
		final double gigs = (repo.fileSize() / 1024d / 1024d / 1024d);
		System.err.printf("Loaded content index with %d items (%.2fGB) in %.2fs%n",
						  repo.size(), gigs, (System.currentTimeMillis() - start) / 1000f);

		return repo;
	}

	private static ContentManager contentManager(CLI cli, SimpleAddonRepository repo) {
		final DataStore imageStore = store(DataStore.StoreContent.IMAGES, cli);
		final DataStore attachmentStore = store(DataStore.StoreContent.ATTACHMENTS, cli);
		final DataStore contentStore = store(DataStore.StoreContent.CONTENT, cli);

		// prepare cleanup
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				imageStore.close();
				attachmentStore.close();
				contentStore.close();
			} catch (IOException e) {
				//
			}
		}));

		return new ContentManager(repo, contentStore, imageStore, attachmentStore);
	}

	private static DocumentRepository documentRepo(CLI cli) throws IOException {
		Path contentPath = contentPath(cli);

		final long start = System.currentTimeMillis();
		final DocumentRepository repo = new DocumentRepository.FileRepository(contentPath.resolve(DOCUMENTS_DIR));
		System.err.printf("Loaded document index with %d items in %.2fs%n",
						  repo.size(), (System.currentTimeMillis() - start) / 1000f);

		return repo;
	}

	private static ManagedContentRepository managedRepo(CLI cli) throws IOException {
		Path contentPath = contentPath(cli);

		final long start = System.currentTimeMillis();
		ManagedContentRepository managedContentManager = new ManagedContentRepository.FileRepository(contentPath.resolve(MANAGED_DIR));
		System.err.printf("Loaded managed content index with %d items in %.2fs%n",
						  managedContentManager.size(), (System.currentTimeMillis() - start) / 1000f);

		return managedContentManager;
	}

	private static ManagedContentManager managedContentManager(CLI cli, ManagedContentRepository repo) {
		final DataStore contentStore = store(DataStore.StoreContent.CONTENT, cli);

		// prepare cleanup
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				contentStore.close();
			} catch (IOException e) {
				//
			}
		}));
		return new ManagedContentManager(repo, contentStore);
	}

	private static GameTypeRepository gameTypeRepo(CLI cli) throws IOException {
		Path contentPath = contentPath(cli);
		final long start = System.currentTimeMillis();
		final GameTypeRepository repo = new GameTypeRepository.FileRepository(contentPath.resolve(GAMETYPES_DIR));
		System.err.printf("Loaded gametypes index with %d items in %.2fs%n",
						  repo.size(), (System.currentTimeMillis() - start) / 1000f);
		return repo;
	}

	private static GameTypeManager gameTypeManager(CLI cli, GameTypeRepository repo) {
		final DataStore imageStore = store(DataStore.StoreContent.IMAGES, cli);
		final DataStore contentStore = store(DataStore.StoreContent.CONTENT, cli);

		// prepare cleanup
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				imageStore.close();
				contentStore.close();
			} catch (IOException e) {
				//
			}
		}));
		return new GameTypeManager(repo, contentStore, imageStore);
	}

	private static WikiRepository wikiRepo(CLI cli) throws IOException {
		Path contentPath = contentPath(cli);

		final long start = System.currentTimeMillis();
		final WikiRepository wikis = new WikiRepository.FileRepository(contentPath.resolve(WIKIS_DIR));
		System.err.printf("Loaded wikis index with %d pages in %.2fs%n",
						  wikis.size(), (System.currentTimeMillis() - start) / 1000f);

		return wikis;
	}

	public static DataStore store(DataStore.StoreContent contentType, CLI cli) {
		String stringType = cli.option(contentType.name().toLowerCase() + "-store", cli.option("store", null));
		if (stringType == null) {
			System.err.printf("No %s store specified, this will be necessary for indexing new content. Falling back to no-op store.%n",
							  contentType.name().toLowerCase());
			stringType = "NOP";
		}

		DataStore.StoreType storeType = DataStore.StoreType.valueOf(stringType.toUpperCase());

		return storeType.newStore(contentType, cli);
	}

	private static void index(SimpleAddonRepository repo, ContentManager contentManager, CLI cli) throws IOException {
		if (cli.commands().length < 2) {
			System.err.println("An index path must be specified!");
			System.exit(2);
		}

		boolean verbose = Boolean.parseBoolean(cli.option("verbose", "false"));
		boolean force = Boolean.parseBoolean(cli.option("force", "false"));
		boolean newOnly = Boolean.parseBoolean(cli.option("new-only", "false"));
		int concurrency = Integer.parseInt(cli.option("concurrency", "1"));
		SimpleAddonType forceType = !cli.option("type", "").isEmpty()
			? SimpleAddonType.valueOf(cli.option("type", "").toUpperCase())
			: null;
		Games forceGame = !cli.option("game", "").isEmpty() ? Games.byName(cli.option("game", "")) : null;

		Indexer indexer = new Indexer(repo, contentManager, new Indexer.CLIEventPrinter(verbose));

		Path[] paths;

		// read file set from stdin
		if (cli.commands()[1].equals("-")) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
				String l;
				List<Path> inPaths = new ArrayList<>();
				while ((l = br.readLine()) != null) {
					Path p = Paths.get(l);
					if (!Files.exists(p)) {
						System.err.println("Input path does not exist: " + p);
						System.exit(4);
					}
					inPaths.add(p);
				}
				paths = inPaths.toArray(new Path[0]);
			}
		} else {
			paths = cliPaths(cli, 1, contentManager.repo()).toArray(Path[]::new);
		}

		indexer.index(force, newOnly, concurrency, forceType, forceGame, paths);
	}

	private static void scan(SimpleAddonRepository repository, CLI cli) throws IOException {
		if (cli.commands().length < 2) {
			System.err.println("An input path must be specified!");
			System.exit(2);
		}

		Scanner scanner = new Scanner(repository, cli);

		Path[] paths = cliPaths(cli, 1, repository).toArray(Path[]::new);

		scanner.scan(new Scanner.CLIEventPrinter(), paths);
	}

	private static void edit(ContentManager contentManager, CLI cli) throws IOException, InterruptedException {
		if (cli.commands().length < 2) {
			System.err.println("A content hash should be provided!");
			System.exit(2);
		}

		ContentEditor editor = new ContentEditor(contentManager);
		editor.edit(cli.commands()[1]);
	}

	private static void set(ContentManager contentManager, CLI cli) throws IOException, ReflectiveOperationException {
		if (cli.commands().length < 2) {
			System.err.println("A content hash should be provided!");
			System.exit(2);
		}

		if (cli.commands().length < 3) {
			System.err.println("A field to set should be provided");
			System.exit(2);
		}

		if (cli.commands().length < 4) {
			System.err.println("A new value to set should be provided");
			System.exit(2);
		}

		ContentEditor editor = new ContentEditor(contentManager);
		editor.set(cli.commands()[1], cli.commands()[2], cli.commands()[3]);
	}

	private static void gametype(GameTypeRepository repo, GameTypeManager gametypes, CLI cli) throws IOException {
		if (cli.commands().length < 2) {
			System.err.println("A gametype operation is required:");
			System.err.println("  init <game> <game type name>");
			System.err.println("    initialises a new game type structure under the specified game");
			System.err.println("  locate <game> <game type name>");
			System.err.println("    returns the content directory for the specified game type");
			System.err.println("  sync");
			System.err.println("    synchronises downloads, files, and dependencies for unsynced items");
			System.err.println("  index <game> <game type name> <release name>");
			System.err.println("    indexes the content of the release specified");
			System.err.println("  add <game> <game type name> <release name> <file>");
			System.err.println("    convenience, which adds a gametype if it does not yet exist, adds a release,");
			System.err.println("    and indexes the release. a `sync` command afterwards is still required to sync");
			System.err.println("    download files to mirrors. optional arguments:");
			System.err.println("      --title");
			System.err.println("      --version");
			System.err.println("      --releaseDate");
			System.err.println("      --description");
			System.err.println("      --platform");
			System.err.println("      --index");
			System.err.println("  addmirror <game> <game type name> <release name> <url>");
			System.err.println("    adds a secondary mirror to the gametype specified");
			System.exit(2);
		}

		switch (cli.commands()[1]) {
			case "init" -> {
				if (cli.commands().length < 3) {
					System.err.println("A game name is required");
					System.exit(1);
				}
				if (cli.commands().length < 4) {
					System.err.println("A game type name is required");
					System.exit(1);
				}

				Games game = Games.byName(cli.commands()[2]);
				repo.create(game, String.join(" ", Arrays.copyOfRange(cli.commands(), 3, cli.commands().length)), gt -> {
					System.out.printf("Gametype %s created%n", gt.name());
				});
			}
			case "sync" -> {
				gametypes.sync();
			}
			case "index" -> {
				final String gameName = cli.commands()[2];
				final String gameTypeName = cli.commands()[3];
				final String localFileName = cli.commands()[4];
				final GameType gameType = repo.findGametype(Games.byName(gameName), gameTypeName);
				if (gameType == null) {
					System.err.printf("Game type %s was not found%n", gameTypeName);
					System.exit(1);
				}
				// final reference hacks for use within lambda
				final GameType.Release[] release = { null };
				final GameType.ReleaseFile[] releaseFile = { null };
				gameType.releases.forEach(r -> {
					releaseFile[0] = r.files.stream()
											.filter(f -> f.localFile.equalsIgnoreCase(localFileName) ||
														 f.originalFilename.equalsIgnoreCase(Util.fileName(localFileName)))
											.findFirst().orElse(null);
					if (releaseFile[0] != null) release[0] = r;
				});

				if (release[0] == null || releaseFile[0] == null) {
					System.err.printf("Could not find a release for file %s%n", localFileName);
					System.exit(1);
				}

				gametypes.index(gameType, release[0], releaseFile[0]);
			}
			case "add" -> {
				if (cli.commands().length < 3) {
					System.err.println("A game name is required");
					System.exit(1);
				}
				if (cli.commands().length < 4) {
					System.err.println("A game type name is required");
					System.exit(1);
				}
				if (cli.commands().length < 5) {
					System.err.println("A release name or version is required");
					System.exit(1);
				}
				if (cli.commands().length < 6) {
					System.err.println("A local file to add is required");
					System.exit(1);
				}

				final Path localFile = Paths.get(cli.commands()[5]).toAbsolutePath();
				if (!Files.exists(localFile)) {
					System.err.printf("Local file %s was not found%n", localFile);
					System.exit(1);
				}

				final Games game = Games.byName(cli.commands()[2]);
				final String gameTypeName = cli.commands()[3];
				final String releaseName = cli.commands()[4];

				final Map<String, String> params = Map.of(
					"title", cli.option("title", releaseName),
					"version", cli.option("version", releaseName),
					"releaseDate", cli.option("releaseDate", "Unknown"),
					"description", cli.option("description", ""),
					"platform", cli.option("platform", "ANY")
				);

				gametypes.addRelease(game, gameTypeName, releaseName, localFile, params, ((gameType, release) -> {
					System.out.printf("Added release %s to gametype %s%n", release.title, gameType.name());
					if (Boolean.parseBoolean(cli.option("index", "false"))) {
						System.out.printf("Indexing release%n");
						gametypes.index(gameType, release,
										release.files.stream().filter(f -> f.localFile.equals(localFile.toString())).findFirst().get()
						);
					}
				}));
			}
			default -> {
				System.err.println("Unknown game type operation" + cli.commands()[1]);
				System.exit(3);
			}
		}
	}

	private static void managed(ManagedContentRepository repo, ManagedContentManager managed, CLI cli) throws IOException {
		if (cli.commands().length < 2) {
			System.err.println("A managed content operation is required:");
			System.err.println("  init <game> <group> <path> <title>");
			System.err.println("    initialises a new managed content structure under the specified game");
			System.err.println("  add <game> <group> <path> <title> <file>");
			System.err.println("    convenience, which adds a new managed content if it does not yet exist");
			System.err.println("    and adds a file. a `sync` command afterwards is still required to sync");
			System.err.println("    download files to mirrors. optional arguments:");
			System.err.println("      --title");
			System.err.println("      --version");
			System.err.println("      --description");
			System.err.println("      --platform");
			System.err.println("  sync");
			System.err.println("    synchronises files for unsynced items");
			System.exit(2);
		}

		switch (cli.commands()[1]) {
			case "init" -> {
				if (cli.commands().length < 3) {
					System.err.println("A game name is required");
					System.exit(1);
				}
				if (cli.commands().length < 4) {
					System.err.println("A game type name is required");
					System.exit(1);
				}

				repo.create(
					Games.byName(cli.commands()[2]),
					cli.commands()[3],
					cli.commands()[4],
					cli.commands()[5],
					added -> {
						System.out.println("Initialised content in directory:");
						System.out.printf("  - %s%n", added.contentPath(Paths.get(MANAGED_DIR)));
						System.out.println("\nPopulate the appropriate files, add images, etc.");
						System.out.println("To upload managed files, execute the `sync` command.");
					}
				);
			}
			case "sync" -> {
				managed.sync((total, progress) -> {
					if (!cli.option("proggers", "").isBlank()) Util.proggers(cli.option("proggers", ""), "sync_managed", total, progress);
				});
			}
			case "add" -> {
				if (cli.commands().length < 3) {
					System.err.println("A game name is required");
					System.exit(1);
				}
				if (cli.commands().length < 4) {
					System.err.println("A group name is required");
					System.exit(1);
				}
				if (cli.commands().length < 5) {
					System.err.println("A Managed content path is required");
					System.exit(1);
				}
				if (cli.commands().length < 6) {
					System.err.println("A Managed content title is required");
					System.exit(1);
				}
				if (cli.commands().length < 7) {
					System.err.println("A local file to add is required");
					System.exit(1);
				}

				final Path localFile = Paths.get(cli.commands()[6]).toAbsolutePath();
				if (!Files.exists(localFile)) {
					System.err.printf("Local file %s was not found%n", localFile);
					System.exit(1);
				}

				final String title = cli.commands()[5];
				final Map<String, String> params = Map.of(
					"title", cli.option("title", title),
					"version", cli.option("version", title),
					"description", cli.option("description", ""),
					"platform", cli.option("platform", "ANY")
				);

				managed.addFile(
					store(DataStore.StoreContent.CONTENT, cli),
					Games.byName(cli.commands()[2]),
					cli.commands()[3],
					cli.commands()[4],
					title,
					localFile,
					params
				);
			}
			default -> {
				System.err.println("Unknown managed content operation" + cli.commands()[1]);
				System.exit(3);
			}
		}
	}

	private static void mirror(SimpleAddonRepository contentRepo, ContentManager contentManager,
							   GameTypeRepository gameTypeRepo, GameTypeManager gameTypeManager,
							   ManagedContentRepository managedRepo, ManagedContentManager managedManaged,
							   CLI cli) {
		final DataStore mirrorStore = store(DataStore.StoreContent.CONTENT, cli);

		// default to mirror last 7 days of changes
		LocalDate since = LocalDate.now().minusDays(7);
		LocalDate until = LocalDate.now();

		try {
			if (!cli.option("since", "").isBlank()) since = LocalDate.parse(cli.option("since", ""));
		} catch (DateTimeParseException e) {
			System.err.println("Failed to parse date input " + cli.option("since", ""));
			System.exit(-1);
		}
		try {
			if (!cli.option("until", "").isBlank()) until = LocalDate.parse(cli.option("until", ""));
		} catch (DateTimeParseException e) {
			System.err.println("Failed to parse date input " + cli.option("until", ""));
			System.exit(-1);
		}

		System.out.printf("Mirroring files added since %s until %s to %s with concurrency of %s%n",
						  since, until, mirrorStore, cli.option("concurrency", "3"));

		Mirror mirror = new Mirror(
			contentRepo, contentManager, gameTypeRepo, gameTypeManager, managedRepo, managedManaged,
			mirrorStore,
			Integer.parseInt(cli.option("concurrency", "3")),
			since, until,
			((total, remaining, last) -> {
				System.out.printf("\r[ %-6s / %-6s ] Processed %-40s",
								  total - remaining, total, last.name());
				if (!cli.option("proggers", "").isBlank()) {
					Util.proggers(cli.option("proggers", ""), "sync_mirror", (int)total, (int)(total - remaining));
				}
			})
		);
		mirror.mirror();

		System.out.printf("%nMirror completed%n");

		// cleanup executor
		mirror.cancel();
	}

	private static void localMirror(SimpleAddonRepository contentRepo, CLI cli) throws IOException {
		if (cli.commands().length < 2) {
			System.err.println("An output path should be provided!");
			System.exit(2);
		}

		Path output = Files.createDirectories(Paths.get(cli.commands()[1])).toAbsolutePath();

		System.out.printf("Writing files to %s with concurrency of %s%n", output, cli.option("concurrency", "3"));

		LocalMirrorClient mirror = new LocalMirrorClient(
			contentRepo,
			output,
			Integer.parseInt(cli.option("concurrency", "3")),
			((total, remaining, last) -> System.out.printf("\r[ %-6s / %-6s ] Processed %-40s",
														   total - remaining, total, last.name()))
		);
		mirror.mirror();

		System.out.println("Local mirror completed");

		// cleanup executor
		mirror.cancel();
	}

	private static void www(SimpleAddonRepository contentRepo, GameTypeRepository gameTypeRepo, DocumentRepository documentRepo,
							ManagedContentRepository managedRepo, WikiRepository wikiRepo, CLI cli)
		throws IOException {
		if (cli.commands().length < 2) {
			System.err.println("An output path must be specified!");
			System.exit(2);
		}

		final Path outputPath = Paths.get(cli.commands()[1]).toAbsolutePath();
		if (!Files.exists(outputPath)) {
			System.out.println("Creating directory " + outputPath);
			Files.createDirectories(outputPath);
		} else if (!Files.isDirectory(outputPath)) {
			System.err.println("Output path must be a directory!");
			System.exit(4);
		}

		final boolean withSearch = Boolean.parseBoolean(cli.option("with-search", "false"));
		final boolean withSubmit = Boolean.parseBoolean(cli.option("with-submit", "false"));
		final boolean withLatest = Boolean.parseBoolean(cli.option("with-latest", "false"));
		final boolean withFiles = Boolean.parseBoolean(cli.option("with-files", "true"));
		final boolean withPackages = Boolean.parseBoolean(cli.option("with-packages", "false"));
		final boolean withWikis = Boolean.parseBoolean(cli.option("with-wikis", "false"));
		final boolean localImages = Boolean.parseBoolean(cli.option("local-images", "false"));
		if (localImages) System.out.println("Will download a local copy of content images, this will take additional time.");

		final SiteFeatures features = new SiteFeatures(localImages, withLatest, withSubmit, withSearch, withFiles, withWikis);

		final Path staticOutput = outputPath.resolve("static");

		final long start = System.currentTimeMillis();

		// unpack static content
		Templates.unpackResources("static.list", Files.createDirectories(staticOutput).getParent());

		// prepare author names and aliases
		Path authorPath = contentPath(cli).resolve(AUTHORS_DIR);
		AuthorNames names = new AuthorNames(authorPath);
		contentRepo.all().parallelStream()
				   .filter(c -> !UNKNOWN.equalsIgnoreCase(c.author()))
				   .sorted(Comparator.comparingInt(a -> a.author().length()))
				   .forEachOrdered(c -> names.maybeAutoAlias(c.author));
		AuthorNames.instance = Optional.of(names);
		System.out.printf("Found %d author aliases and names%n", names.aliasCount());

		final Set<SiteMap.Page> allPages = ConcurrentHashMap.newKeySet();

		final Set<PageGenerator> generators = new HashSet<>();
		generators.add(new Index(contentRepo, gameTypeRepo, documentRepo, managedRepo, outputPath, staticOutput, features));

		if (cli.commands().length == 2 || (cli.commands().length > 2 && cli.commands()[2].equalsIgnoreCase("content"))) {
			// generate content pages
			generators.addAll(
				Arrays.asList(
					new Maps(contentRepo, outputPath, staticOutput, features),
					new MapPacks(contentRepo, outputPath, staticOutput, features),
					new Skins(contentRepo, outputPath, staticOutput, features),
					new Models(contentRepo, outputPath, staticOutput, features),
					new Voices(contentRepo, outputPath, staticOutput, features),
					new Mutators(contentRepo, outputPath, staticOutput, features)
				));
			if (withPackages) generators.add(new Packages(contentRepo, gameTypeRepo, managedRepo, outputPath, staticOutput, features));
		}

		if (cli.commands().length == 2 || (cli.commands().length > 2 && cli.commands()[2].equalsIgnoreCase("authors"))) {
			generators.add(new Authors(names, contentRepo, gameTypeRepo, managedRepo, outputPath, staticOutput, features));
		}

		if (cli.commands().length == 2 || (cli.commands().length > 2 && cli.commands()[2].equalsIgnoreCase("docs"))) {
			generators.add(new Documents(documentRepo, outputPath, staticOutput, features));
		}

		if (cli.commands().length == 2 || (cli.commands().length > 2 && cli.commands()[2].equalsIgnoreCase("managed"))) {
			generators.add(new ManagedContent(managedRepo, outputPath, staticOutput, features));
		}

		if (cli.commands().length == 2 || (cli.commands().length > 2 && cli.commands()[2].equalsIgnoreCase("gametypes"))) {
			generators.add(new GameTypes(gameTypeRepo, contentRepo, outputPath, staticOutput, features));
		}

		if (cli.commands().length > 2 && cli.commands()[2].equalsIgnoreCase("packages")) {
			generators.add(new Packages(contentRepo, gameTypeRepo, managedRepo, outputPath, staticOutput, features));
		}

		if (features.wikis || (cli.commands().length > 2 && cli.commands()[2].equalsIgnoreCase("wiki"))) {
			generators.add(new Wiki(outputPath, staticOutput, features, wikiRepo));
		}

		if (features.submit) generators.add(new Submit(outputPath, staticOutput, features));
		if (features.search) generators.add(new Search(outputPath, staticOutput, features));
		if (features.latest) generators.add(new Latest(contentRepo, gameTypeRepo, managedRepo, outputPath, staticOutput, features));
		if (features.files) generators.add(new FileDetails(contentRepo, outputPath, staticOutput, features));

		ForkJoinPool myPool = new ForkJoinPool(Integer.parseInt(cli.option("concurrency", "4")));
		myPool.submit(() -> {
			generators.parallelStream().forEach(g -> {
				System.out.printf("Generating %s pages%n", g.getClass().getSimpleName());
				allPages.addAll(g.generate());
			});
		}).join();

		System.out.println("Generating sitemap");
		allPages.addAll(SiteMap.siteMap(SiteMap.SITE_ROOT, outputPath, allPages, 50000, features).generate());

		System.out.printf("Output %d pages in %.2fs%n", allPages.size(), (System.currentTimeMillis() - start) / 1000f);
	}

	private static void searchSubmit(SimpleAddonRepository contentRepo, DocumentRepository documentRepo,
									 ManagedContentRepository managedContentManager, WikiRepository wikiManager, CLI cli)
		throws IOException {
		// TODO documents, managed content, and gametypes

		// meh
		Path authorPath = contentPath(cli).resolve(AUTHORS_DIR);
		AuthorNames names = new AuthorNames(authorPath);
		contentRepo.all().parallelStream()
				   .filter(c -> !UNKNOWN.equalsIgnoreCase(c.author()))
				   .sorted(Comparator.comparingInt(a -> a.author().length()))
				   .forEachOrdered(c -> names.maybeAutoAlias(c.author));
		AuthorNames.instance = Optional.of(names);

		final long start = System.currentTimeMillis();

		MESSubmitter submitter = new MESSubmitter();

		System.out.printf("Submitting content to search instance at %s%n",
						  System.getenv().getOrDefault("MSE_CONTENT_URL", System.getenv().getOrDefault("MSE_URL", ""))
		);

		submitter.submit(contentRepo,
						 System.getenv().getOrDefault("SITE_URL", ""),
						 System.getenv().getOrDefault("MES_CONTENT_URL", System.getenv().getOrDefault("MES_URL", "")),
						 System.getenv().getOrDefault("MES_CONTENT_TOKEN", System.getenv().getOrDefault("MES_TOKEN", "")), 50,
						 percent -> System.out.printf("\r%.1f%% complete", percent * 100d),
						 done -> System.out.printf("%nSearch submission complete in %.2fs%n",
												   (System.currentTimeMillis() - start) / 1000f));

		System.out.printf("Submitting wikis to search instance at %s%n", System.getenv().getOrDefault("MES_WIKI_URL", ""));
		submitter.submit(wikiManager,
						 System.getenv().getOrDefault("SITE_URL", ""),
						 System.getenv().getOrDefault("MES_WIKI_URL", ""),
						 System.getenv().getOrDefault("MES_WIKI_TOKEN", ""), 5,
						 percent -> System.out.printf("\r%.1f%% complete", percent * 100d),
						 done -> System.out.printf("%nSearch submission complete in %.2fs%n",
												   (System.currentTimeMillis() - start) / 1000f));
	}

	private static void summary(SimpleAddonRepository repository) {
		Map<Class<? extends Addon>, Long> byType = repository.countByType();
		if (byType.size() > 0) {
			System.out.println("Current content by Type:");
			byType.forEach((type, count) -> System.out.printf(" > %s: %d%n", type.getSimpleName(), count));

			System.out.println("Current content by Game:");
			repository.countByGame().forEach((game, count) -> {
				System.out.printf(" > %s: %d%n", game, count);
				repository.countByType(game).forEach(
					(type, typeCount) -> System.out.printf("   > %s: %d%n", type.getSimpleName(), typeCount)
				);
			});
		} else {
			System.out.println("No content stored yet");
		}
	}

	private static void list(SimpleAddonRepository repository, CLI cli) {
		String game = cli.option("game", null);
		String type = cli.option("type", null);
		String author = cli.option("author", null);
		String name = cli.option("name", null);

		if (null == game && type == null && author == null && name == null) {
			System.err.println("Options to search by game, type, author or name are expected");
			System.exit(255);
		}

		Set<Addon> results = new HashSet<>(repository.search(game, type, name, author));

		if (results.isEmpty()) {
			System.out.println("No results found");
		} else {
			System.out.printf("%-22s | %-10s | %-30s | %-20s | %s%n", "Game", "Type", "Name", "Author", "Hash");
			for (Addon result : results) {
				System.out.printf("%-22s | %-10s | %-30s | %-20s | %s%n",
								  result.game, result.contentType,
								  result.name.substring(0, Math.min(20, result.name.length())),
								  result.author.substring(0, Math.min(20, result.author.length())),
								  result.hash);
			}
		}
	}

	private static void show(SimpleAddonRepository repository, CLI cli) throws IOException {
		if (cli.commands().length < 2) {
			System.err.println("List of content hashes or names expected");
			System.exit(255);
		}

		Set<Addon> results = new HashSet<>();

		String[] terms = Arrays.copyOfRange(cli.commands(), 1, cli.commands().length);
		for (String term : terms) {
			if (term.matches("[a-f0-9]{40}")) {
				Addon found = repository.forHash(term);
				if (found != null) results.add(found);
			} else {
				results.addAll(repository.forName(term));
			}
		}

		if (results.isEmpty()) {
			System.out.printf("No results for terms %s found%n", Arrays.toString(terms));
		} else {
			for (Addon result : results) {
				System.out.println(YAML.toString(result));
			}
		}
	}

	private static void unpack(CLI cli) throws IOException {
		if (cli.commands().length < 3) {
			System.err.println("A Umod file and destination directory are required!");
			System.exit(2);
		}

		Path umodFile = Paths.get(cli.commands()[1]);
		if (!Files.exists(umodFile)) {
			System.err.println("Umod file does not exist!");
			System.exit(4);
		}

		Path dest = Paths.get(cli.commands()[2]);
		if (!Files.isDirectory(dest)) {
			System.err.println("Destination directory does not exist!");
			System.exit(4);
		}

		try (Umod umod = new Umod(umodFile)) {
			ByteBuffer buffer = ByteBuffer.allocate(1024 * 8);
			for (Umod.UmodFile f : umod.files) {
				if (f.name.startsWith("System\\Manifest")) continue;

				System.out.printf("Unpacking %s ", f.name);
				Path out = dest.resolve(Util.filePath(f.name));

				if (!Files.exists(out)) Files.createDirectories(out);

				out = out.resolve(Util.fileName(f.name));

				System.out.printf("to %s%n", out);

				try (FileChannel fileChannel = FileChannel.open(out, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
																StandardOpenOption.TRUNCATE_EXISTING);
					 SeekableByteChannel fileData = f.read()) {

					while (fileData.read(buffer) > 0) {
						fileData.read(buffer);
						buffer.flip();
						fileChannel.write(buffer);
						buffer.clear();
					}
				}
			}
		}
	}

	private static void install(SimpleAddonRepository repository, CLI cli) throws IOException {
		if (cli.commands().length < 3) {
			System.err.println("A file path or content hash and destination directory are required!");
			System.exit(2);
		}

		Path dest = Paths.get(cli.commands()[2]).toAbsolutePath();
		if (!Files.isDirectory(dest)) {
			System.err.println("Destination directory does not exist!");
			System.exit(4);
		}

		Path path = localOrRemoteOrHashToPaths(new String[] { cli.commands()[1] }, repository)
			.stream()
			.findFirst()
			.orElseThrow();

		boolean overwrite = Boolean.parseBoolean(cli.option("overwrite", "false"));
		StandardCopyOption[] copyOptions = overwrite
			? new StandardCopyOption[] { StandardCopyOption.REPLACE_EXISTING }
			: new StandardCopyOption[0];

		Map<FileType, String> destinations = new HashMap<>();
		destinations.put(FileType.CODE, "System");
		destinations.put(FileType.INT, "System");
		destinations.put(FileType.INI, "System");
		destinations.put(FileType.UCL, "System");
		destinations.put(FileType.PLAYER, "System");
		destinations.put(FileType.MAP, "Maps");
		destinations.put(FileType.MUSIC, "Music");
		destinations.put(FileType.STATICMESH, "StaticMeshes");
		destinations.put(FileType.SOUNDS, "Sounds");
		destinations.put(FileType.TEXTURE, "Textures");
		destinations.put(FileType.PHYSICS, "KarmaData");
		destinations.put(FileType.ANIMATION, "Animations");

		// create some useless holder things, so we can reuse the Incoming class for unpacking content
		Submission sub = new Submission(path);
		IndexLog log = new IndexLog();
		try (Incoming incoming = new Incoming(sub, log).prepare()) {
			incoming.files(destinations.keySet().toArray(new FileType[0])).forEach(f -> {
				Path destPath = dest.resolve(destinations.get(f.fileType()));
				if (!Files.isDirectory(destPath)) {
					try {
						destPath = Files.createDirectories(destPath);
					} catch (IOException e) {
						throw new RuntimeException("Failed to create output directory " + destPath, e);
					}
				}

				Path destFile = destPath.resolve(f.fileName());
				if (Files.exists(destFile) && !overwrite) {
					System.out.printf("Skipping file %s, use --overwrite=true to force overwrite%n", f.fileName());
					return;
				}
				try {
					System.out.printf("Copying file %s to %s%n", f.fileName(), destFile);
					Files.copy(Channels.newInputStream(f.asChannel()), destFile, copyOptions);
				} catch (IOException e) {
					throw new RuntimeException("Failed to write file to " + destFile, e);
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static Set<Path> cliPaths(CLI cli, int fromOffset, SimpleAddonRepository repository) throws IOException {
		// let's see if there are cli paths which are actually URLs, and download them to local paths
		String[] paths = Arrays.copyOfRange(cli.commands(), fromOffset, cli.commands().length);
		return localOrRemoteOrHashToPaths(paths, repository);
	}

	/**
	 * Given a collection of local file paths, URLs, and content hashes, resolve
	 * everything to an output collection of local files, downloading content where
	 * necessary.
	 */
	private static Set<Path> localOrRemoteOrHashToPaths(String[] paths, SimpleAddonRepository repository) throws IOException {
		// convert hashes to URLs
		String[] contentUrls = Arrays
			.stream(paths)
			.filter(s -> s.matches("^[a-f0-9]{40}$"))
			.map(hash -> {
				Addon content = repository.forHash(hash);
				if (content == null) throw new IllegalArgumentException(String.format("Hash %s does not match any known content!", hash));
				return content.directDownload().url;
			})
			.toArray(String[]::new);

		// get whatever else looks like a URL from the input collection
		String[] urls = Arrays.stream(paths)
							  .filter(s -> s.matches("^https?://.*"))
							  .toArray(String[]::new);

		// convert URLs to local files
		Path dlTemp = Files.createTempDirectory("ua-download");
		Set<Path> dls = Stream.concat(Arrays.stream(contentUrls), Arrays.stream(urls))
							  .map(url -> {
								  System.out.printf("Fetching %s ... ", url);
								  try {
									  return Util.downloadTo(url, dlTemp);
								  } catch (IOException e) {
									  throw new IllegalStateException(String.format("Failed to download from URL %s: %s!", url, e), e);
								  } finally {
									  System.out.println("Done");
								  }
							  }).collect(Collectors.toSet());

		// find local paths
		Set<Path> diskPaths = Arrays.stream(paths)
									.filter(s -> !s.matches("^https?://.*"))
									.filter(s -> !s.matches("^[a-f0-9]{40}$"))
									.map(s -> Paths.get(s).toAbsolutePath())
									.peek(p -> {
										if (!Files.exists(p)) {
											throw new IllegalArgumentException(String.format("Path not found %s!", p));
										}
									})
									.collect(Collectors.toSet());

		return Stream.concat(dls.stream(), diskPaths.stream()).collect(Collectors.toSet());
	}

	private static void usage() {
		System.out.println("Usage: unreal-archive.jar <command> [options]");
		System.out.println();
		System.out.println("Commands:");
		System.out.println("  index <file ...> --content-path=<path> [--force=<true|false>]");
		System.out.println("    Index the contents of files or paths, writing the results to <content-path>.");
		System.out.println("    Optionally force re-indexing of existing content, rather than skipping it.");
		System.out.println("  sync <kind> --content-path=<path>");
		System.out.println("    Sync managed files' local files to remote storage.");
		System.out.println("  scan <file ...> --content-path=<path>");
		System.out.println("    Dry-run scan the contents of files or paths, comparing to known content where possible.");
		System.out.println("  edit <hash> --content-path=<path>");
		System.out.println("    Edit the metadata for the <hash> provided. Relies on `sensible-editor` on Linux.");
		System.out.println("  set <hash> <attribute> <new-value> --content-path=<path>");
		System.out.println("    Set <attribute> to value <new-value> within the metadata of the <hash> provided.");
		System.out.println("  gametype <...>");
		System.out.println("    Utilities for managing gametype content. Run `gametype` with no arguments for help.");
		System.out.println("  local-mirror <output-path> --content-path=<path> [--concurrency=<count>]");
		System.out.println("    Create a local mirror of the content in <content-path> in local directory <output-path>.");
		System.out.println("    Optionally specify the number of concurrent downloads via <count>, defaults to 3.");
		System.out.println("  www <output-path> [docs|content] --content-path=<path>");
		System.out.println("    Generate the HTML website for browsing content.");
		System.out.println("  summary --content-path=<path>");
		System.out.println("    Show stats and counters for the content index in <content-path>");
		System.out.println("  ls [--game=<game>] [--type=<type>] [--author=<author>] --content-path=<path>");
		System.out.println("    List indexed content in <content-path>, filtered by game, type or author");
		System.out.println("  show [name ...] [hash ...] --content-path=<path>");
		System.out.println("    Show data for the content items specified");
		System.out.println("  unpack <umod-file> <destination>");
		System.out.println("    Unpack the contents of <umod-file> to directory <destination>");
		System.out.println("  install <file|hash> <destination>");
		System.out.println("    Extract and place the contents of <file> into the <destination> directory");
		System.out.println("    provided. Files will be placed into appropriate sub-directories by file type,");
		System.out.println("    eg. Maps, System, Textures, etc. If <hash> is provided, content will be downloaded");
		System.out.println("    first and then installed. Supports unpacking of UMOD files");
	}
}
