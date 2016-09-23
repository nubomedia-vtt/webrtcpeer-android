package fi.vtt.nubomedia.webrtcpeerandroid;

import android.util.Log;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import fi.vtt.nubomedia.utilitiesandroid.LooperExecutor;
import fi.vtt.nubomedia.webrtcpeerandroid.NBMWebRTCPeer.NBMPeerConnectionParameters;
import fi.vtt.nubomedia.webrtcpeerandroid.NBMWebRTCPeer.SignalingParameters;

/**
 * The class implements the management of PeerConnection instances.
 *
 * The implementation is based on PeerConnectionClient.java of package org.appspot.apprtc
 * (please see the copyright notice below)
 */
final class PeerConnectionResourceManager {
    private static final String TAG = "PCResourceManager";

    private boolean preferIsac;
    private boolean preferH264;
    private boolean videoCallEnabled;
    private LooperExecutor executor;
    private PeerConnectionFactory factory;
    private HashMap<String,NBMPeerConnection> connections;
    private NBMPeerConnectionParameters peerConnectionParameters;

    PeerConnectionResourceManager(NBMPeerConnectionParameters peerConnectionParameters,
                                         LooperExecutor executor, PeerConnectionFactory factory) {

        this.peerConnectionParameters = peerConnectionParameters;
        this.executor = executor;
        this.factory = factory;
        videoCallEnabled = peerConnectionParameters.videoCallEnabled;

        // Check if H.264 is used by default.
        preferH264 = videoCallEnabled && peerConnectionParameters.videoCodec != null && peerConnectionParameters.videoCodec.equals(NBMMediaConfiguration.NBMVideoCodec.H264.toString());
        // Check if ISAC is used by default.
        preferIsac = peerConnectionParameters.audioCodec != null && peerConnectionParameters.audioCodec.equals(NBMMediaConfiguration.NBMAudioCodec.ISAC.toString());
        connections = new HashMap<>();
    }

    NBMPeerConnection createPeerConnection( SignalingParameters signalingParameters,
                                            MediaConstraints pcConstraints,
                                            String connectionId) {

        Log.d(TAG, "Create peer connection.");
        Log.d(TAG, "PCConstraints: " + pcConstraints.toString());

        // TCP candidates are only useful when connecting to a server that supports ICE-TCP.
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(signalingParameters.iceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        //rtcConfig.iceServers IceServer
        NBMPeerConnection connectionWrapper = new NBMPeerConnection(connectionId, preferIsac, videoCallEnabled, preferH264, executor, peerConnectionParameters);
        PeerConnection peerConnection = factory.createPeerConnection(rtcConfig, pcConstraints, connectionWrapper);

        connectionWrapper.setPc(peerConnection);
        connections.put(connectionId, connectionWrapper);

        // Set default WebRTC tracing and INFO libjingle logging.
        // NOTE: this _must_ happen while |factory| is alive!
        Logging.enableTracing("logcat:", EnumSet.of(Logging.TraceLevel.TRACE_DEFAULT), Logging.Severity.LS_INFO);

        Log.d(TAG, "Peer connection created.");
        return connectionWrapper;
    }

    NBMPeerConnection getConnection(String connectionId){
        return connections.get(connectionId);
    }

    Collection<NBMPeerConnection> getConnections(){
        return connections.values();
    }

    void closeConnection(String connectionId){
        NBMPeerConnection connection = connections.remove(connectionId);
        connection.close();
    }

    void closeAllConnections(){
        for(NBMPeerConnection connection : connections.values()){
            connection.close();
        }
        connections.clear();
    }

}
