package com.gnemirko.movieRecsBot.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class VersionService {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_INSTANT;

    private final ObjectProvider<GitProperties> gitPropertiesProvider;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;

    public VersionService(ObjectProvider<GitProperties> gitPropertiesProvider,
                          ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.gitPropertiesProvider = gitPropertiesProvider;
        this.buildPropertiesProvider = buildPropertiesProvider;
    }

    public String describe() {
        List<String> lines = new ArrayList<>();
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        GitProperties gitProperties = gitPropertiesProvider.getIfAvailable();

        String version = (buildProperties != null && buildProperties.getVersion() != null)
                ? buildProperties.getVersion()
                : "unknown";
        lines.add("movieRecsBot v" + version);

        if (gitProperties != null && gitProperties.getBranch() != null) {
            lines.add("Branch: " + gitProperties.getBranch());
        }

        if (gitProperties != null) {
            StringBuilder gitLine = new StringBuilder("Commit: ").append(gitProperties.getShortCommitId());
            if (gitProperties.getCommitTime() != null) {
                gitLine.append(" (").append(ISO_FMT.format(gitProperties.getCommitTime())).append(")");
            }
            lines.add(gitLine.toString());
        }

        if (buildProperties != null) {
            StringBuilder buildLine = new StringBuilder("Build: ").append(buildProperties.getVersion());
            if (buildProperties.getTime() != null) {
                buildLine.append(" @ ").append(ISO_FMT.format(buildProperties.getTime()));
            }
            lines.add(buildLine.toString());
        }

        return String.join("\n", lines);
    }
}
