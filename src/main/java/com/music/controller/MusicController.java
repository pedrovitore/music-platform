package com.music.controller;

import com.music.model.Song;
import com.music.service.MusicService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MusicController {
    
    private final MusicService musicService;
    
    public MusicController(MusicService musicService) {
        this.musicService = musicService;
    }
    
    // NEW: Get random songs with pagination
    @GetMapping("/songs")
    public ResponseEntity<Map<String, Object>> getRandomSongs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int limit) {
        
    	System.out.println("Get random songs");
    	
        List<Song> songs = musicService.getRandomSongs(limit);
        long totalSongs = musicService.getTotalSongCount();
        
        Map<String, Object> response = new HashMap<>();
        response.put("songs", songs);
        response.put("currentPage", page);
        response.put("totalItems", totalSongs);
        response.put("totalPages", (int) Math.ceil((double) totalSongs / limit));
        response.put("hasMore", (page + 1) * limit < totalSongs);
        
        return ResponseEntity.ok(response);
    }
    
    // Alternative: Get random songs without pagination metadata (simpler)
    @GetMapping("/songs/random")
    public List<Song> getRandomSongs(@RequestParam(defaultValue = "100") int limit) {
        return musicService.getRandomSongs(limit);
    }
    
    // Get single song by ID
    @GetMapping("/song/{id}")
    public Song getSong(@PathVariable Long id) {
        return musicService.getSongById(id);
    }
    
    // Stream audio
    @GetMapping("/stream/{id}")
    public ResponseEntity<Resource> streamAudio(@PathVariable Long id) {
        Resource audio = musicService.getAudioFile(id);
        
        if (audio == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + audio.getFilename() + "\"")
            .contentType(MediaType.parseMediaType("audio/mpeg"))
            .body(audio);
    }
    
    // Scan for new music
    @PostMapping("/scan")
    public String scanMusic() {
        musicService.scanAndImportSongs();
        return "Scan started! Check console for details.";
    }
    
    // NEW: Search endpoint with pagination
    @GetMapping("/songs/search")
    public List<Song> searchSongs(@RequestParam String query) {
        return musicService.searchSongs(query);
    }
    
    // NEW: Get songs by artist
    @GetMapping("/songs/artist/{artist}")
    public List<Song> getSongsByArtist(@PathVariable String artist) {
        return musicService.getSongsByArtist(artist);
    }
}