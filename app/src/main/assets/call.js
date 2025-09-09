let localVideo = document.getElementById("local-video")
let remoteVideo = document.getElementById("remote-video")

localVideo.style.opacity = 0
remoteVideo.style.opacity = 0

localVideo.onplaying = () => { localVideo.style.opacity = 1 }
remoteVideo.onplaying = () => { remoteVideo.style.opacity = 1 }

let peer
let localStream
let currentCall = null
let isConnected = false
let qualityMonitor = null

// Connection status indicators
function updateConnectionStatus(status) {
    if (window.Android) {
        Android.onConnectionStatus(status)
    }
}

// Error handling
function handleError(error) {
    console.error('WebRTC Error:', error)
    if (window.Android) {
        Android.onError(error.message || 'Connection failed')
    }
}

function init(userId) {
    try {
        // Try multiple PeerJS servers for better connectivity
        const peerConfigs = [
            {
                host: 'peerjs-server.herokuapp.com',
                port: 443,
                path: '/',
                secure: true
            },
            {
                host: '0.peerjs.com',
                port: 443,
                path: '/',
                secure: true
            },
            {
                host: 'peerjs.com',
                port: 443,
                path: '/',
                secure: true
            }
        ];

        let currentConfigIndex = 0;
        
        function tryNextConfig() {
            if (currentConfigIndex >= peerConfigs.length) {
                handleError(new Error('All PeerJS servers failed to connect'));
                return;
            }

            const config = peerConfigs[currentConfigIndex];
            console.log('Trying PeerJS config:', config);

            peer = new Peer(userId, {
                ...config,
                config: {
                    iceServers: [
                        { urls: 'stun:stun.l.google.com:19302' },
                        { urls: 'stun:stun1.l.google.com:19302' },
                        { urls: 'stun:stun2.l.google.com:19302' },
                        { urls: 'stun:stun3.l.google.com:19302' },
                        { urls: 'stun:stun4.l.google.com:19302' },
                        { 
                            urls: 'turn:numb.viagenie.ca',
                            credential: 'muazkh',
                            username: 'webrtc@live.com'
                        }
                    ],
                    iceCandidatePoolSize: 10
                },
                debug: 2
            })

            peer.on('open', (id) => {
                console.log('Peer connected with ID:', id)
                updateConnectionStatus('connected')
                if (window.Android) {
                    Android.onPeerConnected()
                }
            })

            peer.on('error', (error) => {
                console.error('Peer error with config', currentConfigIndex, ':', error)
                
                // Try next configuration on connection errors
                if (error.type === 'server-error' || error.type === 'network') {
                    currentConfigIndex++;
                    setTimeout(() => {
                        if (peer) {
                            peer.destroy();
                        }
                        tryNextConfig();
                    }, 1000);
                } else {
                    handleError(error)
                }
            })

            peer.on('disconnected', () => {
                console.log('Peer disconnected')
                updateConnectionStatus('disconnected')
                isConnected = false
                
                // Try to reconnect
                setTimeout(() => {
                    if (peer && peer.destroyed) {
                        currentConfigIndex = 0;
                        tryNextConfig();
                    }
                }, 2000);
            })

            peer.on('close', () => {
                console.log('Peer connection closed')
                updateConnectionStatus('closed')
                isConnected = false
            })

            listen()
        }

        // Start with first configuration
        tryNextConfig();
    } catch (error) {
        handleError(error)
    }
}

function listen() {
    peer.on('call', (call) => {
        console.log('Incoming call from:', call.peer)
        currentCall = call
        
        // Request media with better error handling
        navigator.mediaDevices.getUserMedia({
            audio: {
                echoCancellation: true,
                noiseSuppression: true,
                autoGainControl: true
            },
            video: {
                width: { ideal: 1280 },
                height: { ideal: 720 },
                frameRate: { ideal: 30 }
            }
        }).then((stream) => {
            localVideo.srcObject = stream
            localStream = stream

            call.answer(stream)
            
            call.on('stream', (remoteStream) => {
                console.log('Received remote stream')
                remoteVideo.srcObject = remoteStream
                remoteVideo.className = "primary-video"
                localVideo.className = "secondary-video"
                isConnected = true
                updateConnectionStatus('streaming')
                startQualityMonitoring()
            })

            call.on('close', () => {
                console.log('Call ended')
                isConnected = false
                updateConnectionStatus('call_ended')
            })

            call.on('error', (error) => {
                console.error('Call error:', error)
                handleError(error)
            })

        }).catch((error) => {
            console.error('Error accessing media devices:', error)
            handleError(error)
        })
    })
}

function startCall(otherUserId) {
    console.log('Starting call to:', otherUserId)
    
    navigator.mediaDevices.getUserMedia({
        audio: {
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
        },
        video: {
            width: { ideal: 1280 },
            height: { ideal: 720 },
            frameRate: { ideal: 30 }
        }
    }).then((stream) => {
        localVideo.srcObject = stream
        localStream = stream

        const call = peer.call(otherUserId, stream)
        currentCall = call
        
        call.on('stream', (remoteStream) => {
            console.log('Received remote stream')
            remoteVideo.srcObject = remoteStream
            remoteVideo.className = "primary-video"
            localVideo.className = "secondary-video"
            isConnected = true
            updateConnectionStatus('streaming')
            startQualityMonitoring()
        })

        call.on('close', () => {
            console.log('Call ended')
            isConnected = false
            updateConnectionStatus('call_ended')
        })

        call.on('error', (error) => {
            console.error('Call error:', error)
            handleError(error)
        })

    }).catch((error) => {
        console.error('Error accessing media devices:', error)
        handleError(error)
    })
}

function toggleVideo(enabled) {
    if (localStream && localStream.getVideoTracks().length > 0) {
        localStream.getVideoTracks()[0].enabled = enabled === "true"
        console.log('Video toggled:', enabled)
    }
} 

function toggleAudio(enabled) {
    if (localStream && localStream.getAudioTracks().length > 0) {
        localStream.getAudioTracks()[0].enabled = enabled === "true"
        console.log('Audio toggled:', enabled)
    }
}

function endCall() {
    if (currentCall) {
        currentCall.close()
        currentCall = null
    }
    if (localStream) {
        localStream.getTracks().forEach(track => track.stop())
        localStream = null
    }
    isConnected = false
    stopQualityMonitoring()
    updateConnectionStatus('call_ended')
}

function swapVideos() {
    const remoteVideo = document.getElementById('remote-video');
    const localVideo = document.getElementById('local-video');

    if (remoteVideo.classList.contains('primary-video')) {
        remoteVideo.classList.remove('primary-video');
        remoteVideo.classList.add('secondary-video');
        localVideo.classList.remove('secondary-video');
        localVideo.classList.add('primary-video');
    } else {
        remoteVideo.classList.remove('secondary-video');
        remoteVideo.classList.add('primary-video');
        localVideo.classList.remove('primary-video');
        localVideo.classList.add('secondary-video');
    }
}

// Call quality monitoring
function startQualityMonitoring() {
    if (qualityMonitor) {
        clearInterval(qualityMonitor);
    }
    
    qualityMonitor = setInterval(() => {
        if (currentCall && currentCall.peerConnection) {
            const stats = currentCall.peerConnection.getStats();
            stats.then(report => {
                let audioQuality = 'unknown';
                let videoQuality = 'unknown';
                let connectionType = 'unknown';
                
                report.forEach(stat => {
                    if (stat.type === 'inbound-rtp' && stat.mediaType === 'audio') {
                        const audioLevel = stat.audioLevel || 0;
                        if (audioLevel > 0.7) audioQuality = 'excellent';
                        else if (audioLevel > 0.4) audioQuality = 'good';
                        else if (audioLevel > 0.1) audioQuality = 'fair';
                        else audioQuality = 'poor';
                    }
                    
                    if (stat.type === 'inbound-rtp' && stat.mediaType === 'video') {
                        const frameRate = stat.framesPerSecond || 0;
                        const resolution = stat.frameWidth * stat.frameHeight || 0;
                        
                        if (frameRate >= 25 && resolution >= 1280*720) videoQuality = 'excellent';
                        else if (frameRate >= 20 && resolution >= 640*480) videoQuality = 'good';
                        else if (frameRate >= 15 && resolution >= 320*240) videoQuality = 'fair';
                        else videoQuality = 'poor';
                    }
                    
                    if (stat.type === 'candidate-pair' && stat.state === 'succeeded') {
                        connectionType = stat.localCandidateType || 'unknown';
                    }
                });
                
                updateQualityStatus(audioQuality, videoQuality, connectionType);
            });
        }
    }, 5000); // Check every 5 seconds
}

function updateQualityStatus(audioQuality, videoQuality, connectionType) {
    if (window.Android) {
        Android.onQualityUpdate(audioQuality, videoQuality, connectionType);
    }
}

function stopQualityMonitoring() {
    if (qualityMonitor) {
        clearInterval(qualityMonitor);
        qualityMonitor = null;
    }
}

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    endCall()
    stopQualityMonitoring()
    if (peer) {
        peer.destroy()
    }
}) 