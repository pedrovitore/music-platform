const API_BASE = '/api';

// Load songs on page load
document.addEventListener('DOMContentLoaded', () => {
    loadSongs();
    document.getElementById('scanBtn').addEventListener('click', scanMusic);
});

async function loadSongs() {
    const songsList = document.getElementById('songsList');
    songsList.innerHTML = '<div class="loading">Loading your music...</div>';
    
    try {
        const response = await fetch(`${API_BASE}/songs`);
        if (!response.ok) throw new Error('Failed to load songs');
        
        const songs = await response.json();
        displaySongs(songs);
    } catch (error) {
        console.error('Error loading songs:', error);
        songsList.innerHTML = '<div class="error">Failed to load songs. Make sure the backend is running.</div>';
    }
}

function displaySongs(songs) {
    const songsList = document.getElementById('songsList');
    
    if (songs.length === 0) {
        songsList.innerHTML = '<div class="loading">No songs found. Click "Scan for New Music" to import your MP3 files.</div>';
        return;
    }
    
    songsList.innerHTML = songs.map(song => `
        <div class="song-card" onclick="playSong(${song.id}, '${escapeHtml(song.title)}', '${escapeHtml(song.artist)}')">
            <h3>${escapeHtml(song.title)}</h3>
            <p>${escapeHtml(song.artist)}</p>
            <div class="metadata">
                ${song.album ? `<span class="album">💿 ${escapeHtml(song.album)}</span>` : ''}
                ${song.year ? `<span class="year">📅 ${song.year}</span>` : ''}
                ${song.genre ? `<span class="genre">🎸 ${escapeHtml(song.genre)}</span>` : ''}
                ${song.duration ? `<span class="duration">⏱️ ${song.duration}</span>` : ''}
            </div>
        </div>
    `).join('');
}

function playSong(id, title, artist) {
    const audioPlayer = document.getElementById('audioPlayer');
    const streamUrl = `${API_BASE}/stream/${id}`;
    
    audioPlayer.src = streamUrl;
    audioPlayer.play();
    
    // Update now playing display
    document.getElementById('nowPlayingTitle').textContent = title;
    document.getElementById('nowPlayingArtist').textContent = artist;
    
    // Add visual feedback
    document.querySelectorAll('.song-card').forEach(card => {
        card.style.background = '#f7fafc';
    });
    event.currentTarget.style.background = '#c6f6d5';
}

async function scanMusic() {
    const scanBtn = document.getElementById('scanBtn');
    const originalText = scanBtn.textContent;
    scanBtn.textContent = '🔄 Scanning...';
    scanBtn.disabled = true;
    
    try {
        const response = await fetch(`${API_BASE}/scan`, { method: 'POST' });
        if (response.ok) {
            const message = await response.text();
            alert('Scan started! Check the server console for details.\n\n' + message);
            setTimeout(() => loadSongs(), 2000);
        } else {
            throw new Error('Scan failed');
        }
    } catch (error) {
        console.error('Error scanning:', error);
        alert('Failed to scan. Check if music folder path is correct in application.properties');
    } finally {
        scanBtn.textContent = originalText;
        scanBtn.disabled = false;
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>]/g, function(m) {
        if (m === '&') return '&amp;';
        if (m === '<') return '&lt;';
        if (m === '>') return '&gt;';
        return m;
    });
}

function formatFileSize(bytes) {
    if (!bytes) return 'Unknown size';
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return Math.round(bytes / Math.pow(1024, i)) + ' ' + sizes[i];
}