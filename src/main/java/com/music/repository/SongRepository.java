package com.music.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.music.model.Song;

@Repository
public interface SongRepository extends JpaRepository<Song, Long> {
    
    // PERFORMANCE IMPROVEMENT: Direct query by file path using database index
    Optional<Song> findByFilePath(String filePath);
    
    // Additional useful methods for future optimizations
    boolean existsByFilePath(String filePath);
    
    List<Song> findByArtistContainingIgnoreCase(String artist);
    
    List<Song> findByTitleContainingIgnoreCase(String title);
    
    void deleteByFilePath(String filePath);
}