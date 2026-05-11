package com.music.dto;

import java.util.List;

public class AlbumSummary {
    private String albumName;
    private String artistName;
    private long songCount;
    private Integer year;
    private List<SongSummary> songs;
    
    public AlbumSummary(String albumName, String artistName, long songCount, Integer year) {
        this.albumName = albumName;
        this.artistName = artistName;
        this.songCount = songCount;
        this.year = year;
    }
    
    // Getters and setters
    public String getAlbumName() { return albumName; }
    public void setAlbumName(String albumName) { this.albumName = albumName; }
    
    public String getArtistName() { return artistName; }
    public void setArtistName(String artistName) { this.artistName = artistName; }
    
    public long getSongCount() { return songCount; }
    public void setSongCount(long songCount) { this.songCount = songCount; }
    
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    
    public List<SongSummary> getSongs() { return songs; }
    public void setSongs(List<SongSummary> songs) { this.songs = songs; }
}