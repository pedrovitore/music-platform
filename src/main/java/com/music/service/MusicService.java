package com.music.service;

import com.music.model.Song;
import com.music.repository.SongRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
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
        
        try (Stream<Path> walk = Files.walk(musicPath)) {
            walk.filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.toString().toLowerCase();
                    return fileName.endsWith(".mp3") || fileName.endsWith(".m4a") || fileName.endsWith(".wav");
                })
                .forEach(this::importSongIfNew);
                
            System.out.println("Scan complete! Found " + songRepository.count() + " songs in database");
        } catch (IOException e) {
            System.err.println("Error scanning music folder: " + e.getMessage());
        }
    }
    
    private void importSongIfNew(Path filePath) {
        String absolutePath = filePath.toAbsolutePath().toString();
        
        // Check if song already exists
        boolean exists = songRepository.findAll().stream()
            .anyMatch(song -> song.getFilePath().equals(absolutePath));
            
        if (!exists) {
            String fileName = filePath.getFileName().toString();
            String title = fileName.replaceFirst("\\.[^.]*$", ""); // Remove extension
            
            // Simple artist extraction (optional - you can enhance this)
            String artist = "Unknown Artist";
            
            Song song = new Song(title, artist, absolutePath);
            
            try {
                song.setFileSize(Files.size(filePath));
                // Duration extraction is complex; you can add a library later
                song.setDuration("--:--");
                songRepository.save(song);
                System.out.println("Imported: " + title);
            } catch (IOException e) {
                System.err.println("Error importing: " + fileName);
            }
        }
    }
}