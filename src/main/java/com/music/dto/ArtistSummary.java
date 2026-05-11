package com.music.dto;

public class ArtistSummary {
    private String artistName;
    private long albumCount;
    private long songCount;
    
    public ArtistSummary(String artistName, long albumCount, long songCount) {
        this.artistName = artistName;
        this.albumCount = albumCount;
        this.songCount = songCount;
    }
    
    // Getters and setters
    public String getArtistName() { return artistName; }
    public void setArtistName(String artistName) { this.artistName = artistName; }
    
    public long getAlbumCount() { return albumCount; }
    public void setAlbumCount(long albumCount) { this.albumCount = albumCount; }
    
    public long getSongCount() { return songCount; }
    public void setSongCount(long songCount) { this.songCount = songCount; }
}