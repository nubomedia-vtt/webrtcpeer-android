package fi.vtt.nubomedia.webrtcpeerandroid;

import android.content.Context;
import android.opengl.EGLContext;
import android.util.Log;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoRenderer;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;

import fi.vtt.nubomedia.utilitiesandroid.LooperExecutor;

/**
 * Created by tmtoni on 24.2.2016.
 */
public class PeerConnectionResourceManager {

    private static final String TAG = "PCResourceManager";

    private boolean preferIsac;
    private boolean preferH264;
    private boolean videoCallEnabled;

    private VideoRenderer.Callbacks remoteRender;

    private LooperExecutor executor;
    // PeerConnectionFactory internals. Move to separate static class?
    private static final String FIELD_TRIAL_VP9 = "WebRTC-SupportVP9/Enabled/";
    private static final String FIELD_TRIAL_AUTOMATIC_RESIZE = "WebRTC-MediaCodecVideoEncoder-AutomaticResize/Enabled/";

    private PeerConnectionFactory factory;

    private HashMap<String,NBMPeerConnection> connections;

    private boolean isInitiator;
    private MediaConstraints pcConstraints;

    NBMWebRTCPeer.NBMPeerConnectionParameters peerConnectionParameters;
    NBMWebRTCPeer.SignalingParameters signalingParameters;



    public PeerConnectionResourceManager(NBMWebRTCPeer.NBMPeerConnectionParameters peerConnectionParameters,
                                         LooperExecutor executor, PeerConnectionFactory factory) {

        this.peerConnectionParameters = peerConnectionParameters;
        this.executor = executor;
        this.factory = factory;

        videoCallEnabled = peerConnectionParameters.videoCallEnabled;

        // Check if H.264 is used by default.
        preferH264 = false;
        if ( videoCallEnabled && peerConnectionParameters.videoCodec != null &&
                peerConnectionParameters.videoCodec.equals(NBMMediaConfiguration.NBMVideoCodec.H264.toString())) {
            preferH264 = true;
        }
        // Check if ISAC is used by default.
        preferIsac = false;
        if (peerConnectionParameters.audioCodec != null &&
                peerConnectionParameters.audioCodec.equals(NBMMediaConfiguration.NBMAudioCodec.ISAC.toString())) {
            preferIsac = true;
        }

        connections = new HashMap<String,NBMPeerConnection>();

    }


    /**
     *
     * @param signalingParameters
     */
    public NBMPeerConnection createPeerConnection(NBMWebRTCPeer.SignalingParameters signalingParameters,
                                     MediaConstraints pcConstraints,
                                     String connectionId) {
        if (peerConnectionParameters == null) {
            Log.e(TAG, "Creating peer connection without initializing factory.");
            return null;
        }
        this.signalingParameters = signalingParameters;
        this.pcConstraints = pcConstraints;

        return createPeerConnectionInternal(connectionId);
//        executor.execute(new Runnable() {
//            @Override
//            public void run() {
//                createPeerConnectionInternal();
//            }
//        });
    }

    /**
     *
     *
     */
    private NBMPeerConnection createPeerConnectionInternal(String connectionId) {
        if (factory == null){ // || isError) {
            Log.e(TAG, "Peerconnection factory is not created");
            return null;
        }
        Log.d(TAG, "Create peer connection.");
        Log.d(TAG, "PCConstraints: " + pcConstraints.toString());



        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(signalingParameters.iceServers);
        // TCP candidates are only useful when connecting to a server that supports ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        NBMPeerConnection connectionWrapper = new NBMPeerConnection(connectionId, preferIsac, videoCallEnabled, preferH264, executor, peerConnectionParameters);

        PeerConnection peerConnection = factory.createPeerConnection(rtcConfig, pcConstraints, connectionWrapper);

        connectionWrapper.setPc(peerConnection);
        connections.put(connectionId, connectionWrapper);

        isInitiator = false;
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
        if (connection != null){
            connection.close();
        }

    }

    void close(){
        for(NBMPeerConnection c : connections.values()){
            c.close();
        }
        connections.clear();
    }


}
