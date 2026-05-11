package com.music.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.id3.ID3v23Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.music.dto.AlbumSummary;
import com.music.dto.ArtistSummary;
import com.music.dto.SongSummary;
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
    
    // NEW: Get random songs - efficient database-level random selection
    public List<Song> getRandomSongs(int limit) {
        // Cap the limit to prevent excessive loading
        int safeLimit = Math.min(limit, 500);
        return songRepository.findRandomSongs(safeLimit);
    }
    
    // Alternative: Get random songs with different random seed each time
    public List<Song> getFreshRandomSongs(int limit) {
        // This will give different results on each call naturally
        // because RANDOM() is evaluated per query
        return getRandomSongs(limit);
    }
    
    // Get total count for pagination
    public long getTotalSongCount() {
        return songRepository.count();
    }
    
    // Get songs with traditional pagination (for when you want sequential browsing)
    public List<Song> getSongsPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return songRepository.findAll(pageable).getContent();
    }
    
    public Song getSongById(Long id) {
        return songRepository.findById(id).orElse(null);
    }
    
    public Optional<Song> getSongByFilePath(String filePath) {
        return songRepository.findByFilePath(filePath);
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
    
    // NEW: Search across multiple fields
    public List<Song> searchSongs(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getRandomSongs(100);
        }
        
        String searchTerm = query.toLowerCase();
        // For large databases, you'd want to use database LIKE queries
        // This is a simple in-memory filter for now
        return songRepository.findAll().stream()
            .filter(song -> 
                (song.getTitle() != null && song.getTitle().toLowerCase().contains(searchTerm)) ||
                (song.getArtist() != null && song.getArtist().toLowerCase().contains(searchTerm)) ||
                (song.getAlbum() != null && song.getAlbum().toLowerCase().contains(searchTerm))
            )
            .limit(100)
            .collect(Collectors.toList());
    }
    
    // NEW: Get songs by artist
    public List<Song> getSongsByArtist(String artist) {
        return songRepository.findByArtistContainingIgnoreCase(artist);
    }
    
    public void scanAndImportSongs() {
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
                    case IMPORTED -> importedCount++;
                    case SKIPPED -> skippedCount++;
                    case ERROR -> errorCount++;
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
    
 // Library service methods
    public List<ArtistSummary> getAllArtistsWithStats() {
        List<String> artists = songRepository.findAllArtists();
        List<ArtistSummary> artistSummaries = new ArrayList<>();
        
        for (String artist : artists) {
            long albumCount = songRepository.countAlbumsByArtist(artist);
            long songCount = songRepository.countSongsByArtist(artist);
            artistSummaries.add(new ArtistSummary(artist, albumCount, songCount));
        }
        
        // Sort by artist name
        artistSummaries.sort(Comparator.comparing(ArtistSummary::getArtistName));
        return artistSummaries;
    }

    public List<AlbumSummary> getAlbumsByArtist(String artistName) {
        List<String> albums = songRepository.findAlbumsByArtist(artistName);
        List<AlbumSummary> albumSummaries = new ArrayList<>();
        
        for (String album : albums) {
            Object[] details = songRepository.findAlbumDetails(artistName, album);
            Integer year = details != null && details.length > 1 && details[1] != null ? 
                          (Integer) details[1] : null;
            
            long songCount = songRepository.countSongsByAlbum(artistName, album);
            AlbumSummary albumSummary = new AlbumSummary(album, artistName, songCount, year);
            
            // Get songs for this album (without loading all at once)
            List<Song> songs = songRepository.findSongsByAlbum(artistName, album);
            List<SongSummary> songSummaries = songs.stream()
                .map(song -> new SongSummary(
                    song.getId(),
                    song.getTitle(),
                    song.getDuration(),
                    song.getTrackNumber()
                ))
                .collect(Collectors.toList());
            
            albumSummary.setSongs(songSummaries);
            albumSummaries.add(albumSummary);
        }
        
        return albumSummaries;
    }

    // Helper method needed for the repository query
    public long countSongsByAlbum(String artist, String album) {
        return songRepository.findSongsByAlbum(artist, album).size();
    }
    
    public List<String> searchArtists(String query) {
        return songRepository.searchArtists(query);
    }
    
    private ImportResult importSongIfNew(Path filePath) {
        String absolutePath = filePath.toAbsolutePath().toString();
        
        try {
            Optional<Song> existingSong = songRepository.findByFilePath(absolutePath);
            
            if (existingSong.isPresent()) {
                return ImportResult.SKIPPED;
            }
            
            // Extract metadata (you'll implement this with jAudiotagger)
            Mp3Metadata metadata = extractMp3Metadata(filePath);
            
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
            return ImportResult.IMPORTED;
            
        } catch (Exception e) {
            System.err.println("✗ Failed to import: " + filePath.getFileName());
            System.err.println("  Error: " + e.getMessage());
            return ImportResult.ERROR;
        }
    }
    
    private Mp3Metadata extractMp3Metadata(Path filePath) throws Exception {
        Mp3Metadata metadata = new Mp3Metadata();
        
        try {
            // Read the MP3 file with JAudioTagger
            AudioFile audioFile = AudioFileIO.read(filePath.toFile());
            Tag tag = audioFile.getTag();
            
            // If no tag exists, create a new one (some MP3s have no metadata)
            if (tag == null) {
                audioFile.setTag(new ID3v23Tag());
                tag = audioFile.getTag();
            }
            
            // Extract standard metadata fields using FieldKey constants[citation:1][citation:2]
            metadata.title = getSafeTagValue(tag, FieldKey.TITLE);
            metadata.artist = getSafeTagValue(tag, FieldKey.ARTIST);
            metadata.album = getSafeTagValue(tag, FieldKey.ALBUM);
            metadata.genre = getSafeTagValue(tag, FieldKey.GENRE);
            
            // Extract year with multiple attempts
            String yearStr = getSafeTagValue(tag, FieldKey.YEAR);
            if (yearStr == null || yearStr.isEmpty()) {
                yearStr = getSafeTagValue(tag, FieldKey.ORIGINAL_YEAR);
            }
            if (yearStr != null && yearStr.length() >= 4) {
                try {
                    metadata.year = Integer.parseInt(yearStr.substring(0, 4));
                } catch (NumberFormatException e) {
                    // Ignore - year stays null
                }
            }
            
            // Extract track number
            String trackStr = getSafeTagValue(tag, FieldKey.TRACK);
            if (trackStr != null && !trackStr.isEmpty()) {
                // Handle formats like "5/12" or just "5"
                String[] parts = trackStr.split("/");
                try {
                    metadata.trackNumber = Integer.parseInt(parts[0]);
                } catch (NumberFormatException e) {
                    // Ignore - trackNumber stays null
                }
            }
            
            // Extract duration from audio header[citation:5]
            if (audioFile.getAudioHeader() != null) {
                int durationSeconds = audioFile.getAudioHeader().getTrackLength();
                metadata.duration = formatDurationFromSeconds(durationSeconds);
            }
            
            // Fallback to filename for title if metadata missing[citation:2]
            if (metadata.title == null || metadata.title.isEmpty()) {
                metadata.title = filePath.getFileName().toString()
                    .replaceFirst("\\.[^.]*$", "");
            }
            
            // Fallback for artist
            if (metadata.artist == null || metadata.artist.isEmpty()) {
                metadata.artist = "Unknown Artist";
            }
            
        } catch (Exception e) {
            System.err.println("Error extracting metadata from: " + filePath.getFileName());
            System.err.println("  Error details: " + e.getMessage());
            
            // Fallback: use filename as title on error
            metadata.title = filePath.getFileName().toString().replaceFirst("\\.[^.]*$", "");
            metadata.artist = "Unknown Artist";
        }
        
        return metadata;
    }
    
	/**
	 * Format duration from seconds to MM:SS format
	 */
	private String formatDurationFromSeconds(int seconds) {
	    int minutes = seconds / 60;
	    int secs = seconds % 60;
	    return String.format("%d:%02d", minutes, secs);
	}

    /**
     * Safely get a tag value, returning null if not found or empty
     */
    private String getSafeTagValue(Tag tag, FieldKey fieldKey) {
        if (tag == null) return null;
        String value = tag.getFirst(fieldKey);
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
    
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