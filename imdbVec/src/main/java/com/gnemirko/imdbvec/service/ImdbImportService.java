package com.gnemirko.imdbvec.service;

import com.gnemirko.imdbvec.model.Movie;
import com.gnemirko.imdbvec.repo.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Service
@RequiredArgsConstructor
public class ImdbImportService {
    private final MovieRepository repo;

    @Value("${app.imdb.dir}")
    private String imdbDir;

    @Value("${app.imdb.files.basics}")
    private String basicsFile;
    @Value("${app.imdb.files.ratings}")
    private String ratingsFile;

    private static final String BASE_URL = "https://datasets.imdbws.com/";

    public void importImdb() throws Exception {
        Path dir = Path.of(imdbDir);
        Files.createDirectories(dir);

        Path basicsGz = dir.resolve(basicsFile);
        Path ratingsGz = dir.resolve(ratingsFile);

        downloadIfMissing(BASE_URL + basicsFile, basicsGz);
        downloadIfMissing(BASE_URL + ratingsFile, ratingsGz);

        Map<String, double[]> ratings = loadRatings(ratingsGz);
        importBasics(basicsGz, ratings);
    }

    private void downloadIfMissing(String url, Path target) throws IOException {
        if (Files.exists(target)) return;
        try (InputStream in = new URL(url).openStream();
             OutputStream out = Files.newOutputStream(target)) {
            in.transferTo(out);
        }
    }

    private Map<String, double[]> loadRatings(Path gz) throws IOException {
        Map<String, double[]> map = new HashMap<>();
        try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(gz)), StandardCharsets.UTF_8)) {
            Iterable<CSVRecord> records = CSVFormat.TDF.builder().setHeader().setSkipHeaderRecord(true).build().parse(r);
            for (CSVRecord rec : records) {
                String tconst = rec.get("tconst");
                String avg = rec.get("averageRating");
                String num = rec.get("numVotes");
                try {
                    map.put(tconst, new double[]{Double.parseDouble(avg), Double.parseDouble(num)});
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return map;
    }

    @Transactional
    protected void importBasics(Path gz, Map<String, double[]> ratings) throws IOException {
        try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(gz)), StandardCharsets.UTF_8)) {
            Iterable<CSVRecord> records = CSVFormat.TDF.builder().setHeader().setSkipHeaderRecord(true).build().parse(r);
            for (CSVRecord rec : records) {
                if (!"movie".equals(rec.get("titleType"))) continue;
                String tconst = rec.get("tconst");
                String title = rec.get("primaryTitle");
                String startYear = rec.get("startYear");
                String genres = rec.get("genres");

                Movie m = new Movie();
                m.setTconst(tconst);
                m.setTitle(title);
                try {
                    m.setYear(Integer.parseInt(startYear));
                } catch (Exception ignore) {
                }
                m.setGenres(List.of("\\N".equals(genres) ? new String[]{} : genres.split(",")));

                double[] rvals = ratings.get(tconst);
                if (rvals != null) {
                    m.setRating(rvals[0]);
                    m.setVotes((int) rvals[1]);
                }

                try {
                    repo.save(m);
                } catch (Exception e) { /* duplicates etc. */ }
            }
        }
    }
}
