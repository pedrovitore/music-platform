package com.music.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.music.model.Song;
import com.music.repository.SongRepository;

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
    	System.out.println("Starting song scan...");
    	
        Path musicPath = Paths.get(musicFolderPath);
        
        if (!Files.exists(musicPath)) {
            System.err.println("Music folder not found: " + musicFolderPath);
            return;
        }
        
        int importedCount = 0;
        int skippedCount = 0;
        int errorCount = 0;
        
        try (Stream<Path> walk = Files.walk(musicPath)) {
            List<Path> mp3Files = walk.filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(".mp3"))
                .toList();
            
            System.out.println("Found " + mp3Files.size() + " MP3 files");
            
            for (Path filePath : mp3Files) {
                ImportResult result = importSongIfNew(filePath);
                switch (result) {
                    case IMPORTED:
                        importedCount++;
                        break;
                    case SKIPPED:
                        skippedCount++;
                        break;
                    case ERROR:
                        errorCount++;
                        break;
                }
            }
            
            System.out.println("========================================");
            System.out.println("Scan complete!");
            System.out.println("Imported: " + importedCount + " new songs");
            System.out.println("Skipped: " + skippedCount + " existing songs");
            System.out.println("Errors: " + errorCount);
            System.out.println("Total in database: " + songRepository.count());
            System.out.println("========================================");
            
        } catch (IOException e) {
            System.err.println("Error scanning music folder: " + e.getMessage());
        }
    }
    
    private ImportResult importSongIfNew(Path filePath) {
        String absolutePath = filePath.toAbsolutePath().toString();
        
        try {
            // PERFORMANCE IMPROVEMENT: Direct database query by file path
            // Instead of loading all songs into memory and filtering
            Optional<Song> existingSong = songRepository.findByFilePath(absolutePath);
            
            if (existingSong.isPresent()) {
                return ImportResult.SKIPPED;
            }
            
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
            return ImportResult.IMPORTED;
            
        } catch (Exception e) {
            System.err.println("✗ Failed to import: " + filePath.getFileName());
            System.err.println("  Error: " + e.getMessage());
            return ImportResult.ERROR;
        }
    }
    
    private Mp3Metadata extractMp3Metadata(Path filePath) throws Exception {
        Mp3Metadata metadata = new Mp3Metadata();
        
        // JAudiotagger only reads the tag sections (usually first/last few KB of the file)
        MP3File mp3File = (MP3File) AudioFileIO.read(filePath.toFile());
        Tag tag = mp3File.getTag();
        
        if (tag != null) {
            // Extract all available metadata - these operations don't read the audio data
            metadata.title = tag.getFirst(FieldKey.TITLE);
            metadata.artist = tag.getFirst(FieldKey.ARTIST);
            metadata.album = tag.getFirst(FieldKey.ALBUM);
            metadata.genre = tag.getFirst(FieldKey.GENRE);
            metadata.year = parseYear(tag.getFirst(FieldKey.YEAR));
            metadata.trackNumber = parseTrackNumber(tag.getFirst(FieldKey.TRACK));
            metadata.duration = formatDurationFromSeconds(mp3File.getAudioHeader().getTrackLength());
        }
        
        // Fallback to filename for title if metadata missing
        if (metadata.title == null || metadata.title.isEmpty()) {
            metadata.title = filePath.getFileName().toString().replaceFirst("\\.[^.]*$", "");
        }
        
        return metadata;
    }
    
    private Integer parseYear(String yearStr) {
        if (yearStr != null && yearStr.length() >= 4) {
            try {
                return Integer.parseInt(yearStr.substring(0, 4));
            } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private Integer parseTrackNumber(String trackStr) {
        if (trackStr != null && trackStr.matches("\\d+.*")) {
            String[] parts = trackStr.split("/");
            try {
                return Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private String formatDurationFromSeconds(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
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
    
    private enum ImportResult {
        IMPORTED, SKIPPED, ERROR
    }
}