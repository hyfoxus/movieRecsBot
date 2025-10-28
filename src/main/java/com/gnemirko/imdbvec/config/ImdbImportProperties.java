package com.gnemirko.imdbvec.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.imdb")
public class ImdbImportProperties {


    private String baseUrl = "https://datasets.imdbws.com";


    private List<String> files = new ArrayList<>();


    private Path dataDir = Path.of("./data/imdb");

    private Integer maxTitles = 10_000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<String> getFiles() {
        return files;
    }

    public void setFiles(List<String> files) {
        this.files = files == null ? new ArrayList<>() : new ArrayList<>(files);
    }

    public Path getDataDir() {
        return dataDir;
    }

    public void setDataDir(Path dataDir) {
        this.dataDir = dataDir;
    }

    public Integer getMaxTitles() {
        return maxTitles;
    }

    public void setMaxTitles(Integer maxTitles) {
        this.maxTitles = maxTitles;
    }

    public URI resolveDownloadUri(String fileName) {
        Objects.requireNonNull(fileName, "fileName must not be null");
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return URI.create(normalizedBase + fileName);
    }

    public Path resolveDataPath(String fileName) {
        Objects.requireNonNull(fileName, "fileName must not be null");
        return dataDir.resolve(fileName);
    }
}
