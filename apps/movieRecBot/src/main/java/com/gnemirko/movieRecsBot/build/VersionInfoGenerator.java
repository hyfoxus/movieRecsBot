package com.gnemirko.movieRecsBot.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generates version-info.properties without relying on external shell scripts.
 */
public final class VersionInfoGenerator {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_INSTANT;
    private static final String MERGE_PATTERN = "^Merge pull request";

    private final Path repoRoot;
    private final boolean gitAvailable;

    private VersionInfoGenerator(Path repoRoot, boolean gitAvailable) {
        this.repoRoot = repoRoot;
        this.gitAvailable = gitAvailable;
    }

    public static void main(String[] args) throws Exception {
        String outputArg = args.length > 0 ? args[0] : "target/generated-resources";
        Path moduleDir = Path.of(System.getProperty("user.dir"));
        Path outputDir = moduleDir.resolve(outputArg).normalize();

        Path repoRoot;
        boolean gitAvailable = true;
        try {
            repoRoot = Path.of(runGit(moduleDir, "rev-parse", "--show-toplevel").trim());
        } catch (RuntimeException ex) {
            System.err.println("WARN: Git repository not detected; falling back to module directory. Version will be marked unknown.");
            repoRoot = moduleDir;
            gitAvailable = false;
        }

        VersionInfoGenerator generator = new VersionInfoGenerator(repoRoot, gitAvailable);
        generator.generate(outputDir);
    }

    private void generate(Path outputDir) throws IOException, InterruptedException {
        Files.createDirectories(outputDir);
        if (!gitAvailable) {
            writeUnknownVersion(outputDir);
            return;
        }
        String mainBranch = Optional.ofNullable(System.getenv("MAIN_BRANCH")).filter(s -> !s.isBlank()).orElse("main");

        String mainRef = resolveBranchRef(mainBranch).orElseGet(() -> {
            System.err.printf("WARN: Main branch '%s' not found. Falling back to HEAD for version calculation.%n", mainBranch);
            try {
                return runGit(repoRoot, "rev-parse", "--abbrev-ref", "HEAD").trim();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to resolve current branch", e);
            }
        });

        long mergeCount = countMerges(mainRef);
        Optional<String> lastMerge = findLastMerge(mainRef);
        Optional<String> mergeBase = mergeBase(mainRef);

        Optional<String> baseForPatch = lastMerge.filter(hash -> isAncestor(hash, "HEAD"))
                .or(() -> mergeBase);

        long patchCount = baseForPatch
                .map(base -> countCommits(base + "..HEAD"))
                .orElseGet(() -> countCommits("HEAD"));

        String timestamp = ISO_FMT.format(Instant.now());
        String currentBranch = runGit(repoRoot, "rev-parse", "--abbrev-ref", "HEAD").trim();

        List<String> lines = new ArrayList<>();
        lines.add("bot.version=1." + mergeCount + "." + patchCount);
        lines.add("bot.version.generatedAt=" + timestamp);
        lines.add("bot.version.sourceBranch=" + currentBranch);
        baseForPatch.ifPresent(base -> lines.add("bot.version.baseCommit=" + base));

        Path file = outputDir.resolve("version-info.properties");
        Files.write(file, lines, StandardCharsets.UTF_8);
        System.out.println("Version info written to " + file);
    }

    private void writeUnknownVersion(Path outputDir) throws IOException {
        Path file = outputDir.resolve("version-info.properties");
        List<String> lines = List.of(
                "bot.version=1.0.0",
                "bot.version.generatedAt=" + ISO_FMT.format(Instant.now()),
                "bot.version.sourceBranch=unknown"
        );
        Files.write(file, lines, StandardCharsets.UTF_8);
        System.out.println("Version info written with fallback data to " + file);
    }

    private long countMerges(String mainRef) {
        String result = runGitOrEmpty("rev-list", "--merges", "--first-parent", mainRef,
                "--grep", MERGE_PATTERN, "--count");
        return parseLongOrZero(result);
    }

    private Optional<String> findLastMerge(String mainRef) {
        String hash = runGitOrEmpty("log", mainRef, "--merges", "--first-parent",
                "--grep", MERGE_PATTERN, "-1", "--pretty=%H").trim();
        return hash.isEmpty() ? Optional.empty() : Optional.of(hash);
    }

    private Optional<String> mergeBase(String mainRef) {
        String base = runGitOrEmpty("merge-base", "HEAD", mainRef).trim();
        return base.isEmpty() ? Optional.empty() : Optional.of(base);
    }

    private long countCommits(String range) {
        String result = runGitOrEmpty("rev-list", "--count", range);
        return parseLongOrZero(result);
    }

    private Optional<String> resolveBranchRef(String branch) {
        String localRef = "refs/heads/" + branch;
        if (refExists(localRef)) {
            return Optional.of(branch);
        }
        String remoteRef = "refs/remotes/origin/" + branch;
        if (refExists(remoteRef)) {
            return Optional.of("origin/" + branch);
        }
        return Optional.empty();
    }

    private boolean refExists(String fullRef) {
        try {
            Process process = new ProcessBuilder("git", "show-ref", "--verify", "--quiet", fullRef)
                    .directory(repoRoot.toFile())
                    .start();
            int exit = process.waitFor();
            return exit == 0;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to check ref: " + fullRef, e);
        }
    }

    private boolean isAncestor(String ancestor, String head) {
        try {
            Process process = new ProcessBuilder("git", "merge-base", "--is-ancestor", ancestor, head)
                    .directory(repoRoot.toFile())
                    .start();
            int exit = process.waitFor();
            return exit == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static long parseLongOrZero(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String runGitOrEmpty(String... args) {
        try {
            return runGit(repoRoot, args);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String runGit(Path workingDir, String... args) {
        if (!Files.exists(workingDir)) {
            throw new RuntimeException("Working directory does not exist: " + workingDir);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(workingDir.toFile());
        try {
            Process process = pb.start();
            byte[] stdout = process.getInputStream().readAllBytes();
            byte[] stderr = process.getErrorStream().readAllBytes();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new RuntimeException("git " + String.join(" ", args) + " failed with exit " + exit + ": " + new String(stderr, StandardCharsets.UTF_8));
            }
            return new String(stdout, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
