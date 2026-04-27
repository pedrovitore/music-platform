package com.music.service;

import com.music.model.Song;
import com.music.repository.SongRepository;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Service
public class MusicService {
    
    @Value("${music.folder}")
    private String musicFolderPath;
    
    private final SongRepository songRepository;
    
    public MusicService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }
    
    public List<Song> getAllSongs() {
        return songRepository.findAll();
    }
    
    public Song getSongById(Long id) {
        return songRepository.findById(id).orElse(null);
    }
    
    public Resource getAudioFile(Long id) {
        Song song = getSongById(id);
        if (song != null) {
            File file = new File(song.getFilePath());
            if (file.exists()) {
                return new FileSystemResource(file);
            }
        }
        return null;
    }
    
    public void scanAndImportSongs() {
        Path musicPath = Paths.get(musicFolderPath);
        
        if (!Files.exists(musicPath)) {
            System.err.println("Music folder not found: " + musicFolderPath);
            return;
        }
        
        int importedCount = 0;
        int skippedCount = 0;
        
        try (Stream<Path> walk = Files.walk(musicPath)) {
            List<Path> mp3Files = walk.filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(".mp3"))
                .toList();
            
            System.out.println("Found " + mp3Files.size() + " MP3 files");
            
            for (Path filePath : mp3Files) {
                if (importSongIfNew(filePath)) {
                    importedCount++;
                } else {
                    skippedCount++;
                }
            }
            
            System.out.println("========================================");
            System.out.println("Scan complete!");
            System.out.println("Imported: " + importedCount + " new songs");
            System.out.println("Skipped: " + skippedCount + " existing songs");
            System.out.println("Total in database: " + songRepository.count());
            System.out.println("========================================");
            
        } catch (IOException e) {
            System.err.println("Error scanning music folder: " + e.getMessage());
        }
    }
    
    private boolean importSongIfNew(Path filePath) {
        String absolutePath = filePath.toAbsolutePath().toString();
        
        // Check if song already exists
        boolean exists = songRepository.findAll().stream()
            .anyMatch(song -> song.getFilePath().equals(absolutePath));
        
        if (exists) {
            return false;
        }
        
        try {
            // Extract metadata using Tika
            Mp3Metadata metadata = extractMp3Metadata(filePath);
            
            // Create and save song
            Song song = new Song();
            song.setTitle(metadata.title != null ? metadata.title : 
                         filePath.getFileName().toString().replaceFirst("\\.[^.]*$", ""));
            song.setArtist(metadata.artist != null ? metadata.artist : "Unknown Artist");
            song.setAlbum(metadata.album);
            song.setGenre(metadata.genre);
            song.setReleaseYear(metadata.year);
            song.setTrackNumber(metadata.trackNumber);
            song.setDuration(metadata.duration);
            song.setFilePath(absolutePath);
            song.setFileSize(Files.size(filePath));
            
            songRepository.save(song);
            
            System.out.println("✓ Imported: " + song.getTitle() + 
                             (song.getArtist() != null ? " - " + song.getArtist() : ""));
            if (metadata.album != null) {
                System.out.println("  Album: " + metadata.album);
            }
            return true;
            
        } catch (Exception e) {
            System.err.println("✗ Failed to import: " + filePath.getFileName());
            System.err.println("  Error: " + e.getMessage());
            return false;
        }
    }
    
    private Mp3Metadata extractMp3Metadata(Path filePath) throws IOException, TikaException, SAXException {
        Mp3Metadata metadata = new Mp3Metadata();
        
        try (InputStream input = new FileInputStream(filePath.toFile())) {
            BodyContentHandler handler = new BodyContentHandler();
            Metadata tikaMetadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            
            Mp3Parser parser = new Mp3Parser();
            parser.parse(input, handler, tikaMetadata, parseContext);
            
            // Extract standard MP3 metadata
            metadata.title = tikaMetadata.get("title");
            metadata.artist = tikaMetadata.get("xmpDM:artist");
            if (metadata.artist == null) {
                metadata.artist = tikaMetadata.get("dc:creator");
            }
            if (metadata.artist == null) {
                metadata.artist = tikaMetadata.get("artist");
            }
            
            metadata.album = tikaMetadata.get("xmpDM:album");
            if (metadata.album == null) {
                metadata.album = tikaMetadata.get("album");
            }
            
            metadata.genre = tikaMetadata.get("xmpDM:genre");
            if (metadata.genre == null) {
                metadata.genre = tikaMetadata.get("genre");
            }
            
            // Extract year
            String yearStr = tikaMetadata.get("xmpDM:releaseDate");
            if (yearStr == null) {
                yearStr = tikaMetadata.get("year");
            }
            if (yearStr != null && yearStr.length() >= 4) {
                try {
                    metadata.year = Integer.parseInt(yearStr.substring(0, 4));
                } catch (NumberFormatException e) {
                    // Ignore parsing errors
                }
            }
            
            // Extract track number
            String trackStr = tikaMetadata.get("xmpDM:trackNumber");
            if (trackStr == null) {
                trackStr = tikaMetadata.get("trackNumber");
            }
            if (trackStr != null) {
                // Handle formats like "5/12" or just "5"
                String[] parts = trackStr.split("/");
                try {
                    metadata.trackNumber = Integer.parseInt(parts[0]);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            
            // Extract duration
            String durationStr = tikaMetadata.get("xmpDM:duration");
            if (durationStr == null) {
                durationStr = tikaMetadata.get("duration");
            }
            if (durationStr != null) {
                metadata.duration = formatDuration(durationStr);
            }
            
            // If duration still null, try to get it in seconds
            if (metadata.duration == null) {
                String secondsStr = tikaMetadata.get("xmpDM:durationSeconds");
                if (secondsStr != null) {
                    try {
                        double seconds = Double.parseDouble(secondsStr);
                        metadata.duration = formatDurationFromSeconds(seconds);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }
        }
        
        return metadata;
    }
    
    private String formatDuration(String durationStr) {
        // Tika often returns duration in format "PT3M45S" (ISO 8601)
        if (durationStr != null && durationStr.startsWith("PT")) {
            try {
                String timeStr = durationStr.substring(2);
                int minutes = 0;
                int seconds = 0;
                
                if (timeStr.contains("M")) {
                    String minutesPart = timeStr.substring(0, timeStr.indexOf("M"));
                    minutes = Integer.parseInt(minutesPart);
                    timeStr = timeStr.substring(timeStr.indexOf("M") + 1);
                }
                
                if (timeStr.contains("S")) {
                    String secondsPart = timeStr.substring(0, timeStr.indexOf("S"));
                    seconds = Integer.parseInt(secondsPart);
                }
                
                return String.format("%d:%02d", minutes, seconds);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return durationStr;
    }
    
    private String formatDurationFromSeconds(double seconds) {
        int totalSeconds = (int) seconds;
        int minutes = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }
    
    // Helper class to hold metadata
    private static class Mp3Metadata {
        String title;
        String artist;
        String album;
        String genre;
        Integer year;
        Integer trackNumber;
        String duration;
    }
}