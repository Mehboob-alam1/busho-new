let localVideo = document.getElementById("local-video");
let remoteVideo = document.getElementById("remote-video");

localVideo.style.opacity = 0;
remoteVideo.style.opacity = 0;

localVideo.onplaying = () => { localVideo.style.opacity = 1; };
remoteVideo.onplaying = () => { remoteVideo.style.opacity = 1; };

const PEER_OPTS = {
    host: "0.peerjs.com",
    port: 443,
    path: "/",
    secure: true,
    debug: 1
};

let peer;
let localStream;
let wantVideo = true;
let peerReady = false;

function notifyRemote() {
    if (window.Android && Android.onRemoteConnected) {
        Android.onRemoteConnected();
    }
}

function init(userId) {
    peerReady = false;
    if (peer) {
        try { peer.destroy(); } catch (e) { /* ignore */ }
    }
    peer = new Peer(userId, PEER_OPTS);

    peer.on("open", () => {
        peerReady = true;
        if (window.Android && Android.onPeerConnected) {
            Android.onPeerConnected();
        }
    });

    peer.on("error", (err) => {
        console.error("Peer error", err);
        if (window.Android && Android.onPeerError) {
            Android.onPeerError(String(err));
        }
    });

    listen();
}

function mediaConstraints(video) {
    return {
        audio: true,
        video: video ? { facingMode: "user" } : false
    };
}

function getStream(video) {
    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        return navigator.mediaDevices.getUserMedia(mediaConstraints(video));
    }
    return new Promise((resolve, reject) => {
        const legacy = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;
        if (!legacy) {
            reject(new Error("getUserMedia not supported"));
            return;
        }
        legacy.call(navigator, mediaConstraints(video), resolve, reject);
    });
}

function listen() {
    peer.on("call", (call) => {
        getStream(wantVideo).then((stream) => {
            localVideo.srcObject = stream;
            localStream = stream;
            call.answer(stream);
            call.on("stream", (remoteStream) => {
                remoteVideo.srcObject = remoteStream;
                remoteVideo.className = "primary-video";
                localVideo.className = "secondary-video";
                notifyRemote();
            });
        }).catch((e) => {
            if (window.Android && Android.onPeerError) {
                Android.onPeerError(String(e));
            }
        });
    });
}

function waitForPeer(triesLeft, onReady) {
    if (peer && peerReady && peer.id && peer.disconnected !== true) {
        onReady();
        return;
    }
    if (triesLeft <= 0) {
        if (window.Android && Android.onPeerError) {
            Android.onPeerError("Call room not ready. Check internet and try again.");
        }
        return;
    }
    setTimeout(() => waitForPeer(triesLeft - 1, onReady), 400);
}

function startCall(otherUserId, videoEnabled) {
    wantVideo = videoEnabled !== "false" && videoEnabled !== false;
    waitForPeer(20, () => {
        getStream(wantVideo).then((stream) => {
            localVideo.srcObject = stream;
            localStream = stream;
            const call = peer.call(otherUserId, stream);
            if (!call) {
                throw new Error("Could not start call");
            }
            call.on("stream", (remoteStream) => {
                remoteVideo.srcObject = remoteStream;
                remoteVideo.className = "primary-video";
                localVideo.className = "secondary-video";
                notifyRemote();
            });
            call.on("error", (err) => {
                if (window.Android && Android.onPeerError) {
                    Android.onPeerError(String(err));
                }
            });
        }).catch((e) => {
            if (window.Android && Android.onPeerError) {
                Android.onPeerError(String(e));
            }
        });
    });
}

function toggleVideo(b) {
    if (!localStream) return;
    const tracks = localStream.getVideoTracks();
    if (tracks.length === 0) return;
    tracks[0].enabled = b === "true";
}

function toggleAudio(b) {
    if (!localStream) return;
    const tracks = localStream.getAudioTracks();
    if (tracks.length === 0) return;
    tracks[0].enabled = b === "true";
}

function hangup() {
    peerReady = false;
    if (localStream) {
        try {
            localStream.getTracks().forEach((t) => t.stop());
        } catch (e) { /* ignore */ }
        localStream = null;
    }
    if (localVideo) localVideo.srcObject = null;
    if (remoteVideo) remoteVideo.srcObject = null;
    if (peer) {
        try { peer.destroy(); } catch (e) { /* ignore */ }
        peer = null;
    }
}

function swapVideos() {
    const rv = document.getElementById("remote-video");
    const lv = document.getElementById("local-video");
    if (rv.classList.contains("primary-video")) {
        rv.classList.remove("primary-video");
        rv.classList.add("secondary-video");
        lv.classList.remove("secondary-video");
        lv.classList.add("primary-video");
    } else {
        rv.classList.remove("secondary-video");
        rv.classList.add("primary-video");
        lv.classList.remove("primary-video");
        lv.classList.add("secondary-video");
    }
}
