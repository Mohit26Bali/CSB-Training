package com.csb.cinescope.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.csb.cinescope.entity.Quote;
import com.csb.cinescope.entity.QuoteCategory;
import com.csb.cinescope.repository.QuoteRepository;

/**
 * Fills the {@code quotes} table (migration V4) from {@code resources/seed/quotes.psv} on start-up, but only while the table is
 * empty, so lines added or removed later in the database are never overwritten. Unlike {@code DemoDataInitializer} this is
 * reference data, not demo accounts, so it always runs.
 *
 * <p>File format, one line per quote, {@code |}-separated: category, type (movie or tv), TMDB id, title, year, speaker, line.
 * Lines starting with {@code #} are comments. A malformed row stops start-up with a message naming the row number, because
 * silently dropping a quote would be harder to find than a failed start. Tested by {@code QuoteSeederTest}.
 */
@Component
public class QuoteSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QuoteSeeder.class);
    static final String FILE = "seed/quotes.psv";

    private final QuoteRepository quotes;

    public QuoteSeeder(QuoteRepository quotes) {
        this.quotes = quotes;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (quotes.count() > 0) {
            return;
        }
        List<Quote> rows = read();
        quotes.saveAll(rows);
        log.info("Seeded {} quotes", rows.size());
    }

    static List<Quote> read() {
        List<Quote> rows = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new ClassPathResource(FILE).getInputStream(), StandardCharsets.UTF_8))) {
            String text;
            int number = 0;
            while ((text = in.readLine()) != null) {
                number++;
                if (text.isBlank() || text.startsWith("#")) {
                    continue;
                }
                String[] p = text.split("\\|", 7);
                if (p.length != 7) {
                    throw new IllegalStateException(FILE + " row " + number + ": expected 7 fields but found " + p.length);
                }
                try {
                    rows.add(new Quote(QuoteCategory.valueOf(p[0]), p[1], Long.parseLong(p[2]), p[3], Integer.parseInt(p[4]), p[5], p[6]));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalStateException(FILE + " row " + number + ": " + ex.getMessage(), ex);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return rows;
    }
}
