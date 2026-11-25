package demo.service;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async                                                       // runs on another thread
    public CompletableFuture<String> buildReport(String name) {
        return CompletableFuture.completedFuture(name + " on " + Thread.currentThread().getName().replaceAll("[0-9]+$", ""));
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")     // every day at 02:00 (6 fields: sec min hour day month weekday)
    public void nightlyCleanup() { }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT30S")   // 5 minutes after the last run finished
    public void refresh() { }
}
