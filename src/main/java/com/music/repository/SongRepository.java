package com.music.repository;

import com.music.model.Song;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SongRepository extends JpaRepository<Song, Long> {
    
    // Find by file path (for scanning)
    Optional<Song> findByFilePath(String filePath);
    
    // Check existence by file path
    boolean existsByFilePath(String filePath);
    
    // Search methods
    List<Song> findByArtistContainingIgnoreCase(String artist);
    List<Song> findByTitleContainingIgnoreCase(String title);
    
    // Delete by file path
    void deleteByFilePath(String filePath);
    
    // NEW: Get random songs (most efficient - uses database-specific random function)
    @Query(value = "SELECT * FROM songs ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Song> findRandomSongs(int limit);
    
    // Alternative for H2/SQLite compatibility (RANDOM() works in both)
    @Query(value = "SELECT * FROM songs ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Song> findRandomSongsMySQLCompatible(int limit);
    
    // Get total count for pagination info
    long count();
    
    // Optional: Get random songs with pagination offset (for infinite scroll)
    @Query(value = "SELECT * FROM songs ORDER BY RANDOM() LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Song> findRandomSongsWithOffset(int limit, int offset);
    
    // Get songs with pagination (for when you want sequential loading)
    Page<Song> findAll(Pageable pageable);

    
    // NEW: Get all artists with their album and song counts
    @Query("SELECT DISTINCT s.artist FROM Song s WHERE s.artist IS NOT NULL AND s.artist != 'Unknown Artist'")
    List<String> findAllArtists();
    
    // Get album count for an artist
    @Query("SELECT COUNT(DISTINCT s.album) FROM Song s WHERE s.artist = :artist AND s.album IS NOT NULL AND s.album != ''")
    long countAlbumsByArtist(@Param("artist") String artist);
    
    // Get song count for an artist
    @Query("SELECT COUNT(s) FROM Song s WHERE s.artist = :artist")
    long countSongsByArtist(@Param("artist") String artist);
    
    // Get all albums for an artist
    @Query("SELECT DISTINCT s.album FROM Song s WHERE s.artist = :artist AND s.album IS NOT NULL AND s.album != '' ORDER BY s.album")
    List<String> findAlbumsByArtist(@Param("artist") String artist);
    
    // Get album details including year
    @Query("SELECT s.album, MAX(s.releaseYear) FROM Song s WHERE s.artist = :artist AND s.album = :album GROUP BY s.album")
    Object[] findAlbumDetails(@Param("artist") String artist, @Param("album") String album);
    
    // Get songs for a specific album
    @Query("SELECT s FROM Song s WHERE s.artist = :artist AND s.album = :album ORDER BY COALESCE(s.trackNumber, 999), s.title")
    List<Song> findSongsByAlbum(@Param("artist") String artist, @Param("album") String album);
    
    // Get songs by artist (all albums)
    @Query("SELECT s FROM Song s WHERE s.artist = :artist ORDER BY COALESCE(s.album, ''), COALESCE(s.trackNumber, 999), s.title")
    List<Song> findSongsByArtist(@Param("artist") String artist);
    
    // Search artists
    @Query("SELECT DISTINCT s.artist FROM Song s WHERE LOWER(s.artist) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<String> searchArtists(@Param("query") String query);
    
    @Query("SELECT COUNT(s) FROM Song s WHERE s.artist = :artist AND s.album = :album")
    long countSongsByAlbum(@Param("artist") String artist, @Param("album") String album);
}