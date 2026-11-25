package demo.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class UploadController {
    private static final Set<String> ALLOWED = Set.of("png", "jpg", "pdf");
    private final Path uploadDir;

    public UploadController() throws IOException {
        this.uploadDir = Files.createTempDirectory("uploads");   // in real life: a configured folder OUTSIDE the web root
    }

    @PostMapping("/files")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty file");
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED.contains(ext)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "type not allowed");
        String stored = UUID.randomUUID() + "." + ext;           // never trust or reuse the client's file name
        file.transferTo(uploadDir.resolve(stored));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", stored));
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        Path file = uploadDir.resolve(id).normalize();
        if (!file.startsWith(uploadDir) || !Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .body(new PathResource(file));
    }
}
