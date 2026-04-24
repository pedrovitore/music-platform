package com.music.controller;

import com.music.model.Song;
import com.music.service.MusicService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")  // Allow frontend access
public class MusicController {
    
    private final MusicService musicService;
    
    public MusicController(MusicService musicService) {
        this.musicService = musicService;
    }
    
    @GetMapping("/songs")
    public List<Song> getSongs() {
        return musicService.getAllSongs();
    }
    
    @GetMapping("/song/{id}")
    public Song getSong(@PathVariable Long id) {
        return musicService.getSongById(id);
    }
    
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
    
    @PostMapping("/scan")
    public String scanMusic() {
        musicService.scanAndImportSongs();
        return "Scan started! Check console for details.";
    }
}