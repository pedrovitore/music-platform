// API Configuration
const API_BASE = '/api';

// Player State
let currentHowl = null;
let currentSong = null;
let currentSongId = null;
let currentPlaylist = [];
let currentIndex = -1;
let isShuffle = false;
let isRepeat = false;
let currentVolume = 70;

// DOM Elements
let progressInterval = null;

// Initialize Application
document.addEventListener('DOMContentLoaded', () => {
    initializeEventListeners();
    loadSongs();
    setupPlayerControls();
});

function initializeEventListeners() {
    // Navigation
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const view = link.dataset.view;
            switchView(view);
        });
    });
    
    // Search
    document.getElementById('searchInput').addEventListener('input', filterSongs);
    
    // Scan Button
    document.getElementById('scanBtn').addEventListener('click', scanMusic);
    
    // Volume Slider
    const volumeSlider = document.getElementById('volumeSlider');
    volumeSlider.addEventListener('input', (e) => {
        currentVolume = e.target.value;
        if (currentHowl) {
            currentHowl.volume(currentVolume / 100);
        }
        updateVolumeIcon(currentVolume);
    });
    
    // Favorite Button
    document.getElementById('favoriteBtn').addEventListener('click', toggleFavorite);
    
    // Queue Modal
    const modal = document.getElementById('queueModal');
    document.getElementById('queueBtn').addEventListener('click', () => {
        modal.style.display = 'block';
        updateQueueDisplay();
    });
    document.querySelector('.close-modal').addEventListener('click', () => {
        modal.style.display = 'none';
    });
    window.addEventListener('click', (e) => {
        if (e.target === modal) modal.style.display = 'none';
    });
}

function setupPlayerControls() {
    // Previous/Next
    document.getElementById('prevBtn').addEventListener('click', playPrevious);
    document.getElementById('nextBtn').addEventListener('click', playNext);
    
    // Shuffle
    document.getElementById('shuffleBtn').addEventListener('click', toggleShuffle);
    
    // Repeat
    document.getElementById('repeatBtn').addEventListener('click', toggleRepeat);
    
    // Play/Pause (will be shown/hidden as needed)
    document.getElementById('playPauseBtn').addEventListener('click', togglePlayPause);
    
    // Progress bar seeking
    const progressBg = document.getElementById('progressBarBg');
    progressBg.addEventListener('click', seekToPosition);
}

async function loadSongs() {
    const songsList = document.getElementById('songsList');
    songsList.innerHTML = '<tr><td colspan="6" class="loading-state"><i class="fas fa-spinner fa-spin"></i><br>Loading your music...</td></tr>';
    
    try {
        const response = await fetch(`${API_BASE}/songs`);
        if (!response.ok) throw new Error('Failed to load songs');
        
        const songs = await response.json();
        currentPlaylist = songs;
        displaySongs(songs);
        document.getElementById('songCount').textContent = songs.length;
        
    } catch (error) {
        console.error('Error loading songs:', error);
        songsList.innerHTML = '<tr><td colspan="6" class="loading-state"><i class="fas fa-exclamation-triangle"></i><br>Failed to load songs. Make sure the backend is running.</td></tr>';
    }
}

function displaySongs(songs) {
    const tbody = document.getElementById('songsList');
    
    if (songs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="loading-state"><i class="fas fa-music"></i><br>No songs found. Click "Scan Library" to import your MP3 files.</td></tr>';
        return;
    }
    
    tbody.innerHTML = songs.map((song, index) => `
        <tr data-song-id="${song.id}" data-song-index="${index}" class="${currentSongId === song.id ? 'playing' : ''}">
            <td class="play-button-cell">
                <button class="play-btn-mini" onclick="playSongById(${song.id}, ${index})">
                    <i class="fas ${currentSongId === song.id && currentHowl && currentHowl.playing() ? 'fa-pause' : 'fa-play'}"></i>
                </button>
            </td>
            <td class="song-title">${escapeHtml(song.title || 'Unknown Title')}</td>
            <td class="song-artist">${escapeHtml(song.artist || 'Unknown Artist')}</td>
            <td class="song-album">${escapeHtml(song.album || '—')}</td>
            <td>${song.duration || '--:--'}</td>
            <td>
                <button class="play-btn-mini" onclick="addToFavorites(${song.id})">
                    <i class="far fa-heart"></i>
                </button>
            </td>
        </tr>
    `).join('');
}

async function scanMusic() {
    const scanBtn = document.getElementById('scanBtn');
    const originalHtml = scanBtn.innerHTML;
    scanBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Scanning...';
    scanBtn.disabled = true;
    
    try {
        const response = await fetch(`${API_BASE}/scan`, { method: 'POST' });
        if (response.ok) {
            const message = await response.text();
            // Reload songs after scan
            setTimeout(() => {
                loadSongs();
                showNotification('Scan complete! New music added to library.', 'success');
            }, 2000);
        } else {
            throw new Error('Scan failed');
        }
    } catch (error) {
        console.error('Error scanning:', error);
        showNotification('Failed to scan. Check music folder path.', 'error');
    } finally {
        scanBtn.innerHTML = originalHtml;
        scanBtn.disabled = false;
    }
}

function playSongById(id, index) {
    const song = currentPlaylist.find(s => s.id === id);
    if (!song) return;
    
    currentSong = song;
    currentSongId = id;
    currentIndex = index;
    
    // Stop current playback
    if (currentHowl) {
        currentHowl.stop();
        if (progressInterval) clearInterval(progressInterval);
    }
    
    // Show loading indicator
    document.getElementById('playPauseBtn').style.display = 'none';
    document.getElementById('loadingIndicator').style.display = 'flex';
    
    // Create new Howl instance
    currentHowl = new Howl({
        src: [`${API_BASE}/stream/${id}`],
        html5: true,
        format: ['mp3'],
        volume: currentVolume / 100,
        onload: () => {
            document.getElementById('loadingIndicator').style.display = 'none';
            document.getElementById('playPauseBtn').style.display = 'flex';
            document.getElementById('playPauseBtn').innerHTML = '<i class="fas fa-pause"></i>';
            
            // Update UI
            updateNowPlayingDisplay(song);
            startProgressUpdater();
            
            // Highlight playing row
            highlightPlayingRow(id);
        },
        onplay: () => {
            console.log('Playing:', song.title);
        },
        onpause: () => {
            document.getElementById('playPauseBtn').innerHTML = '<i class="fas fa-play"></i>';
        },
        onend: () => {
            handleSongEnd();
        },
        onloaderror: (_, error) => {
            console.error('Error loading song:', error);
            document.getElementById('loadingIndicator').style.display = 'none';
            showNotification('Error loading song. File may be corrupted.', 'error');
        }
    });
    
    currentHowl.play();
}

function updateNowPlayingDisplay(song) {
    document.getElementById('currentSongTitle').textContent = song.title || 'Unknown Title';
    document.getElementById('currentSongArtist').textContent = song.artist || 'Unknown Artist';
    document.getElementById('favoriteBtn').style.display = 'flex';
}

function startProgressUpdater() {
    if (progressInterval) clearInterval(progressInterval);
    
    progressInterval = setInterval(() => {
        if (currentHowl && currentHowl.playing()) {
            const seek = currentHowl.seek() || 0;
            const duration = currentHowl.duration() || 0;
            const progress = (seek / duration) * 100;
            
            document.getElementById('currentTime').textContent = formatTime(seek);
            document.getElementById('totalTime').textContent = formatTime(duration);
            document.getElementById('progressBarFill').style.width = `${progress}%`;
            document.getElementById('progressHandle').style.left = `${progress}%`;
        }
    }, 500);
}

function togglePlayPause() {
    if (!currentHowl) return;
    
    if (currentHowl.playing()) {
        currentHowl.pause();
        document.getElementById('playPauseBtn').innerHTML = '<i class="fas fa-play"></i>';
        if (progressInterval) clearInterval(progressInterval);
    } else {
        currentHowl.play();
        document.getElementById('playPauseBtn').innerHTML = '<i class="fas fa-pause"></i>';
        startProgressUpdater();
    }
}

function playNext() {
    if (currentPlaylist.length === 0) return;
    
    let nextIndex = currentIndex + 1;
    if (nextIndex >= currentPlaylist.length) {
        if (isRepeat) {
            nextIndex = 0;
        } else {
            return;
        }
    }
    
    const nextSong = currentPlaylist[nextIndex];
    playSongById(nextSong.id, nextIndex);
}

function playPrevious() {
    if (currentPlaylist.length === 0) return;
    
    let prevIndex = currentIndex - 1;
    if (prevIndex < 0) {
        if (isRepeat) {
            prevIndex = currentPlaylist.length - 1;
        } else {
            return;
        }
    }
    
    const prevSong = currentPlaylist[prevIndex];
    playSongById(prevSong.id, prevIndex);
}

function handleSongEnd() {
    if (isRepeat && currentPlaylist.length > 0) {
        // Repeat current song
        playSongById(currentSong.id, currentIndex);
    } else {
        playNext();
    }
}

function toggleShuffle() {
    isShuffle = !isShuffle;
    const shuffleBtn = document.getElementById('shuffleBtn');
    
    if (isShuffle) {
        shuffleBtn.classList.add('active');
        // Shuffle the playlist
        currentPlaylist = shuffleArray([...currentPlaylist]);
        displaySongs(currentPlaylist);
        // Update current index
        if (currentSong) {
            currentIndex = currentPlaylist.findIndex(s => s.id === currentSong.id);
        }
    } else {
        shuffleBtn.classList.remove('active');
        // Restore original order
        loadSongs();
    }
}

function toggleRepeat() {
    isRepeat = !isRepeat;
    const repeatBtn = document.getElementById('repeatBtn');
    
    if (isRepeat) {
        repeatBtn.classList.add('active');
    } else {
        repeatBtn.classList.remove('active');
    }
}

function seekToPosition(e) {
    if (!currentHowl) return;
    
    const progressBar = e.currentTarget;
    const rect = progressBar.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const width = rect.width;
    const percentage = x / width;
    const duration = currentHowl.duration();
    
    if (duration) {
        const seekTime = percentage * duration;
        currentHowl.seek(seekTime);
    }
}

function setVolume(value) {
    currentVolume = value;
    if (currentHowl) {
        currentHowl.volume(value / 100);
    }
    updateVolumeIcon(value);
}

function updateVolumeIcon(volume) {
    const icon = document.getElementById('volumeIcon');
    if (volume === 0) {
        icon.className = 'fas fa-volume-mute';
    } else if (volume < 50) {
        icon.className = 'fas fa-volume-down';
    } else {
        icon.className = 'fas fa-volume-up';
    }
}

function filterSongs() {
    const searchTerm = document.getElementById('searchInput').value.toLowerCase();
    const filtered = currentPlaylist.filter(song => 
        (song.title && song.title.toLowerCase().includes(searchTerm)) ||
        (song.artist && song.artist.toLowerCase().includes(searchTerm)) ||
        (song.album && song.album.toLowerCase().includes(searchTerm))
    );
    displaySongs(filtered);
}

function highlightPlayingRow(songId) {
    document.querySelectorAll('#songsTable tbody tr').forEach(row => {
        row.classList.remove('playing');
        if (row.dataset.songId == songId) {
            row.classList.add('playing');
            // Update play button icon
            const playBtn = row.querySelector('.play-btn-mini i');
            if (playBtn) {
                playBtn.className = 'fas fa-pause';
            }
        } else {
            const playBtn = row.querySelector('.play-btn-mini i');
            if (playBtn && playBtn.className !== 'fas fa-play') {
                playBtn.className = 'fas fa-play';
            }
        }
    });
}

function toggleFavorite() {
    // This would integrate with a favorites backend endpoint
    showNotification('Added to favorites!', 'success');
}

function addToFavorites(songId) {
    showNotification('Added to favorites!', 'success');
}

function updateQueueDisplay() {
    const queueList = document.getElementById('queueList');
    if (!currentPlaylist.length) {
        queueList.innerHTML = '<p style="text-align:center; color: var(--text-secondary);">Queue is empty</p>';
        return;
    }
    
    queueList.innerHTML = currentPlaylist.map((song, idx) => `
        <div style="padding: 12px; border-bottom: 1px solid var(--border-color); cursor: pointer; ${idx === currentIndex ? 'color: var(--primary-color);' : ''}"
             onclick="playSongById(${song.id}, ${idx})">
            <strong>${escapeHtml(song.title || 'Unknown')}</strong><br>
            <small style="color: var(--text-secondary);">${escapeHtml(song.artist || 'Unknown Artist')}</small>
        </div>
    `).join('');
}

function switchView(viewId) {
    // Update active nav link
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
        if (link.dataset.view === viewId) {
            link.classList.add('active');
        }
    });
    
    // Update active view
    document.querySelectorAll('.view').forEach(view => {
        view.classList.remove('active');
    });
    document.getElementById(`${viewId}View`).classList.add('active');
    
    // Update header title
    const titles = {
        library: 'My Library',
        playlists: 'Playlists',
        favorites: 'Favorites'
    };
    document.getElementById('viewTitle').textContent = titles[viewId] || 'My Library';
}

function createPlaylist() {
    const name = prompt('Enter playlist name:');
    if (name) {
        showNotification(`Playlist "${name}" created!`, 'success');
    }
}

function showNotification(message, type = 'info') {
    // Create notification element
    const notification = document.createElement('div');
    notification.className = `notification notification-${type}`;
    notification.textContent = message;
    notification.style.cssText = `
        position: fixed;
        bottom: 100px;
        right: 20px;
        background: ${type === 'success' ? '#1db954' : '#e91e63'};
        color: white;
        padding: 12px 24px;
        border-radius: 8px;
        z-index: 2000;
        animation: slideIn 0.3s ease;
        box-shadow: 0 4px 12px rgba(0,0,0,0.3);
    `;
    
    document.body.appendChild(notification);
    
    setTimeout(() => {
        notification.style.animation = 'fadeOut 0.3s ease';
        setTimeout(() => notification.remove(), 300);
    }, 3000);
}

// Helper Functions
function formatTime(seconds) {
    if (isNaN(seconds)) return '0:00';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
}

function shuffleArray(array) {
    for (let i = array.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [array[i], array[j]] = [array[j], array[i]];
    }
    return array;
}

function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// Add CSS animations
const style = document.createElement('style');
style.textContent = `
    @keyframes slideIn {
        from { transform: translateX(100%); opacity: 0; }
        to { transform: translateX(0); opacity: 1; }
    }
    @keyframes fadeOut {
        from { opacity: 1; }
        to { opacity: 0; }
    }
`;
document.head.appendChild(style);

// Make functions global for onclick handlers
window.playSongById = playSongById;
window.addToFavorites = addToFavorites;
window.createPlaylist = createPlaylist;