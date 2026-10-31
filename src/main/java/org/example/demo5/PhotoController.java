package org.example.demo5;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final Path uploadDir =
            Paths.get("uploads").toAbsolutePath().normalize();

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadPhoto(
            @RequestParam("photo") MultipartFile photo) {

        try {
            if (photo == null || photo.isEmpty()
                    || photo.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "Invalid image or size exceeds 5 MB")
                );
            }

            if (!"image/jpeg".equalsIgnoreCase(photo.getContentType())) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "JPEG images only")
                );
            }

            Files.createDirectories(uploadDir);

            String filename = UUID.randomUUID() + ".jpg";
            Path destination = uploadDir.resolve(filename).normalize();

            if (!destination.startsWith(uploadDir)) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "Invalid filename")
                );
            }

            Files.copy(photo.getInputStream(), destination);

            return ResponseEntity.ok(
                    Map.of("message", "Photo saved successfully")
            );

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Could not save photo"));
        }
    }

    @GetMapping(value = "/latest", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> latestPhoto(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {

        String expectedKey = System.getenv("ADMIN_VIEW_KEY");

        if (expectedKey == null || expectedKey.isBlank()
                || key == null
                || !MessageDigest.isEqual(
                expectedKey.getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            if (!Files.isDirectory(uploadDir)) {
                return ResponseEntity.notFound().build();
            }

            Path latest;
            try (Stream<Path> files = Files.list(uploadDir)) {
                latest = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString()
                                .toLowerCase().endsWith(".jpg"))
                        .max(Comparator.comparingLong(
                                path -> path.toFile().lastModified()))
                        .orElse(null);
            }

            if (latest == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header("X-Photo-Name", latest.getFileName().toString())
                    .cacheControl(CacheControl.noStore())
                    .body(Files.readAllBytes(latest));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}