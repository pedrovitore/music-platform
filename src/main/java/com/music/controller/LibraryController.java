package com.music.controller;

import com.music.dto.AlbumSummary;
import com.music.dto.ArtistSummary;
import com.music.service.MusicService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/library")
@CrossOrigin(origins = "*")
public class LibraryController {
    
    private final MusicService musicService;
    
    public LibraryController(MusicService musicService) {
        this.musicService = musicService;
    }
    
    @GetMapping("/artists")
    public List<ArtistSummary> getAllArtists() {
        return musicService.getAllArtistsWithStats();
    }
    
    @GetMapping("/artists/{artistName}/albums")
    public List<AlbumSummary> getArtistAlbums(@PathVariable String artistName) {
        return musicService.getAlbumsByArtist(artistName);
    }
    
    @GetMapping("/search")
    public List<String> searchArtists(@RequestParam String query) {
        return musicService.searchArtists(query);
    }
}