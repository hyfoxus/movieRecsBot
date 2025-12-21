package com.gnemirko.movieRecsBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

@Slf4j
@Service
public class VersionService {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_INSTANT;

    private final ObjectProvider<GitProperties> gitPropertiesProvider;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;
    private final VersionInfo versionInfo;

    public VersionService(ObjectProvider<GitProperties> gitPropertiesProvider,
                          ObjectProvider<BuildProperties> buildPropertiesProvider,
                          ResourceLoader resourceLoader) {
        this.gitPropertiesProvider = gitPropertiesProvider;
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.versionInfo = loadVersionInfo(resourceLoader);
    }

    public String describe() {
        List<String> lines = new ArrayList<>();
        String safeVersion = versionInfo.safeVersion();
        if ("unknown".equals(safeVersion)) {
            lines.add("movieRecsBot version unknown");
        } else {
            lines.add("movieRecsBot v" + safeVersion);
        }

        if (versionInfo.sourceBranch() != null) {
            lines.add("Branch: " + versionInfo.sourceBranch());
        }

        gitPropertiesProvider.ifAvailable(git -> {
            StringBuilder gitLine = new StringBuilder("Commit: ").append(git.getShortCommitId());
            if (git.getCommitTime() != null) {
                gitLine.append(" (").append(ISO_FMT.format(git.getCommitTime())).append(")");
            }
            lines.add(gitLine.toString());
        });

        buildPropertiesProvider.ifAvailable(build -> {
            StringBuilder buildLine = new StringBuilder("Build: ").append(build.getVersion());
            if (build.getTime() != null) {
                buildLine.append(" @ ").append(ISO_FMT.format(build.getTime()));
            }
            lines.add(buildLine.toString());
        });

        if (versionInfo.generatedAt() != null) {
            lines.add("Version generated at: " + versionInfo.generatedAt());
        }

        if (versionInfo.baseCommit() != null) {
            lines.add("Base commit: " + versionInfo.baseCommit());
        }

        return String.join("\n", lines);
    }

    private VersionInfo loadVersionInfo(ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource("classpath:version-info.properties");
        if (!resource.exists()) {
            log.warn("version-info.properties is missing; runtime version will report as unknown");
            return VersionInfo.empty();
        }
        try {
            Properties properties = PropertiesLoaderUtils.loadProperties(resource);
            return new VersionInfo(
                    properties.getProperty("bot.version"),
                    properties.getProperty("bot.version.generatedAt"),
                    properties.getProperty("bot.version.sourceBranch"),
                    properties.getProperty("bot.version.baseCommit")
            );
        } catch (IOException e) {
            log.warn("Failed to load version-info.properties: {}", e.getMessage());
            return VersionInfo.empty();
        }
    }

    private record VersionInfo(String version,
                               String generatedAt,
                               String sourceBranch,
                               String baseCommit) {

        static VersionInfo empty() {
            return new VersionInfo(null, null, null, null);
        }

        String safeVersion() {
            return Objects.requireNonNullElse(version, "unknown");
        }
    }
}
