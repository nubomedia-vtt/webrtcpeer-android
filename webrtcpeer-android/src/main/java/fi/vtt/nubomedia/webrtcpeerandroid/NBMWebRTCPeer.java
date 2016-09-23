package fi.vtt.nubomedia.webrtcpeerandroid;

import java.util.LinkedList;
import java.util.List;
import android.content.Context;
import android.util.Log;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import fi.vtt.nubomedia.utilitiesandroid.LooperExecutor;

/**
 * Class implements the interface for managing WebRTC connections in harmonious manner with
 * other Kurento APIs (HTML5 and iOs).
 *
 * The implementation is based on PeerConnectionClient.java of package org.appspot.apprtc
 * (please see the copyright notice below)
 */

/*
 * libjingle
 * Copyright 2014 Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Main API class for implementing WebRTC peer on Android
 */
public class NBMWebRTCPeer{
    private static final String TAG = "NBMWebRTCPeer";
    private static final String FIELD_TRIAL_VP9 = "WebRTC-SupportVP9/Enabled/";
    private static final String FIELD_TRIAL_AUTOMATIC_RESIZE = "WebRTC-MediaCodecVideoEncoder-AutomaticResize/Enabled/";
    private final LooperExecutor executor;
    private Context context;
    private NBMPeerConnectionParameters peerConnectionParameters;
    private SignalingParameters signalingParameters = null;
    private VideoRenderer.Callbacks localRender;
    private Observer observer;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnectionResourceManager peerConnectionResourceManager;
    private MediaResourceManager mediaResourceManager;
    private LinkedList<PeerConnection.IceServer> iceServers;
    private boolean initialized = false;

    /**
     * An interface which declares WebRTC callbacks
     * <p>
     * This interface class has to be implemented outside API. NBMWebRTCPeer requires an Observer
     * instance in constructor
     * </p>
     */
    public interface Observer {

        /**
         * WebRTC event which is triggered when local SDP offer has been generated
         * @param localSdpOffer The generated local SDP offer
         * @param connection The connection for which this event takes place
         */
        void onLocalSdpOfferGenerated(SessionDescription localSdpOffer, NBMPeerConnection connection);

        /**
         * WebRTC event which is triggered when local SDP answer has been generated
         * @param localSdpAnswer The generated local SDP answer
         * @param connection The connection for which this event takes place
         */
        void onLocalSdpAnswerGenerated(SessionDescription localSdpAnswer, NBMPeerConnection connection);

        /**
         * WebRTC event which is triggered when new ice candidate is received
         * @param localIceCandidate Ice candidate
         * @param connection The connection for which this event takes place
         */
        void onIceCandidate(IceCandidate localIceCandidate, NBMPeerConnection connection);

        /**
         * WebRTC event which is triggered when ICE status has changed
         * @param state The new ICE connection state
         * @param connection The connection for which this event takes place
         */
        void onIceStatusChanged(IceConnectionState state, NBMPeerConnection connection);

        /**
         * WebRTC event which is triggered when A new remote stream is added to connection
         * @param stream The new remote media stream
         * @param connection The connection for which this event takes place
         */
        void onRemoteStreamAdded(MediaStream stream, NBMPeerConnection connection);

        /**
         * WebRTC event which is triggered when a remote media stream is terminated
         * @param stream The removed remote media stream
         * @param connection The connection for which this event takes place
         */
        void onRemoteStreamRemoved(MediaStream stream, NBMPeerConnection connection);

        /**
         * WebRTC event which is triggered when there is an error with the connection
         * @param error Error string
         */
        void onPeerConnectionError(String error);

        /**
         * WebRTC event which is triggered when peer opens a data channel
         * @param dataChannel The data channel
         * @param connection The connection for which the data channel belongs to
         */
        void onDataChannel(DataChannel dataChannel, NBMPeerConnection connection);

        /**
         * WebRTC event which is triggered when a data channel buffer amount has changed
         * @param l The previous amount
         * @param connection The connection for which the data channel belongs to
         * @param channel The data channel which triggered the event
         */
        void onBufferedAmountChange(long l, NBMPeerConnection connection, DataChannel channel);

        /**
         * WebRTC event which is triggered when a data channel state has changed. Possible values:
         * DataChannel.State { CONNECTING, OPEN, CLOSING, CLOSED };
         * @param connection The connection for which the data channel belongs to
         * @param channel The data channel which triggered the event
         */
        void onStateChange(NBMPeerConnection connection, DataChannel channel);

        /**
         * WebRTC event which is triggered when a message is received from a data channel
         * @param buffer The message buffer
         * @param connection The connection for which the data channel belongs to
         * @param channel The data channel which triggered the event
         */
        void onMessage(DataChannel.Buffer buffer, NBMPeerConnection connection, DataChannel channel);
    }

    /**
     * Struct holding the signaling parameters
     */
    public static class SignalingParameters {
        public final List<PeerConnection.IceServer> iceServers;
        public final boolean initiator;
        public final String clientId;
        public final SessionDescription offerSdp;
        public final List<IceCandidate> iceCandidates;

        public SignalingParameters(
                List<PeerConnection.IceServer> iceServers,
                boolean initiator, String clientId,
                SessionDescription offerSdp, List<IceCandidate> iceCandidates) {
            this.iceServers = iceServers;
            this.initiator = initiator;
            this.clientId = clientId;
            this.offerSdp = offerSdp;
            this.iceCandidates = iceCandidates;
        }
    }

    /**
     * Peer connection parameters.
     */
    public static class NBMPeerConnectionParameters {
        public final boolean videoCallEnabled;
        public final boolean loopback;
        public final int videoWidth;
        public final int videoHeight;
        public final int videoFps;
        public final int videoStartBitrate;
        public final String videoCodec;
        public final boolean videoCodecHwAcceleration;
        public final int audioStartBitrate;
        public final String audioCodec;
        public final boolean noAudioProcessing;
        public final boolean cpuOveruseDetection;

        public NBMPeerConnectionParameters(
                boolean videoCallEnabled,
                boolean loopback,
                int videoWidth,
                int videoHeight,
                int videoFps,
                int videoStartBitrate,
                String videoCodec,
                boolean videoCodecHwAcceleration,
                int audioStartBitrate,
                String audioCodec,
                boolean noAudioProcessing,
                boolean cpuOveruseDetection) {
            this.videoCallEnabled = videoCallEnabled;
            this.loopback = loopback;
            this.videoWidth = videoWidth;
            this.videoHeight = videoHeight;
            this.videoFps = videoFps;
            this.videoStartBitrate = videoStartBitrate;
            this.videoCodec = videoCodec;
            this.videoCodecHwAcceleration = videoCodecHwAcceleration;
            this.audioStartBitrate = audioStartBitrate;
            this.audioCodec = audioCodec;
            this.noAudioProcessing = noAudioProcessing;
            this.cpuOveruseDetection = cpuOveruseDetection;
        }
    }


	/**
	* NBMWebRTCPeer constructor
     * <p>
     *     This constructor should always be used in order to properly create a NBMWebRTCPeer instance
     * </p>
	* @param  config			Media configuration instance
	* @param  context			Android context instance
	* @param  localRenderer	    Callback for rendering the locally produced media stream
	* @param  observer			An observer instance which implements WebRTC callback functions
	*/
    public NBMWebRTCPeer(NBMMediaConfiguration config, Context context,
                         VideoRenderer.Callbacks localRenderer, Observer observer) {

        this.context = context;
        this.localRender = localRenderer;
        this.observer = observer;
        executor = new LooperExecutor();

        // Looper thread is started once in private ctor and is used for all
        // peer connection API calls to ensure new peer connection peerConnectionFactory is
        // created on the same thread as previously destroyed peerConnectionFactory.
        executor.requestStart();

        peerConnectionParameters = new NBMWebRTCPeer.NBMPeerConnectionParameters(true, false,
                         config.getReceiverVideoFormat().width, config.getReceiverVideoFormat().heigth,
                        (int)config.getReceiverVideoFormat().frameRate, config.getVideoBandwidth(), config.getVideoCodec().toString(), true,
                        config.getAudioBandwidth(), config.getAudioCodec().toString(),false, true);

        iceServers = new LinkedList<>();
        addIceServer("stun:stun.l.google.com:19302");
    }

	/**
	 * Initializes NBMWebRTCPeer
	 * <p>
	 * NBMWebRTCPeer must be initialized before use. This function can be called immediately after constructor
	 * <p>
	 */
    @SuppressWarnings("unused")
    public void initialize() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                signalingParameters = new NBMWebRTCPeer.SignalingParameters(iceServers, true, "", null, null);
                createPeerConnectionFactoryInternal(context);
                peerConnectionResourceManager = new PeerConnectionResourceManager(peerConnectionParameters, executor, peerConnectionFactory);
                mediaResourceManager = new MediaResourceManager(peerConnectionParameters, executor, peerConnectionFactory);
                initialized = true;
            }
        });
    }

    @SuppressWarnings("unused")
    public boolean isInitialized() {
        return initialized;
    }

    private class GenerateOfferTask implements Runnable {

        String connectionId;
        boolean includeLocalMedia;

        private GenerateOfferTask(String connectionId, boolean includeLocalMedia){
            this.connectionId = connectionId;
            this.includeLocalMedia = includeLocalMedia;
        }

        public void run() {
            if (mediaResourceManager.getLocalMediaStream() == null) {
                mediaResourceManager.createMediaConstraints();
                startLocalMediaSync();
            }

            NBMPeerConnection connection = peerConnectionResourceManager.getConnection(connectionId);

            if (connection == null) {
                if (signalingParameters != null) {

                    connection = peerConnectionResourceManager.createPeerConnection(
                                                                signalingParameters,
                                                                mediaResourceManager.getPcConstraints(),
                                                                connectionId);
                    connection.addObserver(observer);
                    connection.addObserver(mediaResourceManager);
                    if (includeLocalMedia) {
                        connection.getPc().addStream(mediaResourceManager.getLocalMediaStream());
                    }

                    DataChannel.Init init =  new DataChannel.Init();
                    createDataChannel(this.connectionId, "default", init);

                    // Create offer. Offer SDP will be sent to answering client in
                    // PeerConnectionEvents.onLocalDescription event.
                    connection.createOffer(mediaResourceManager.getSdpMediaConstraints());
                }
            }
        }
    }

	/**
	* Generate SDP offer
	*
	* @param  connectionId		A unique identifier for the connection
	*/
    @SuppressWarnings("unused")
    public void generateOffer(String connectionId, boolean includeLocalMedia){
        executor.execute(new GenerateOfferTask(connectionId, includeLocalMedia));
    }

    @SuppressWarnings("unused")
    public LinkedList<PeerConnection.IceServer> getIceServers() {
        return iceServers;
    }

    @SuppressWarnings("unused")
    public void setIceServers(LinkedList<PeerConnection.IceServer> iceServers) {
        if (!initialized) {
            this.iceServers = iceServers;
        } else {
            throw new RuntimeException("Cannot set ICE servers after NBMWebRTCPeer has been initialized");
        }
    }

    public void addIceServer(String serverURI) {
        if (!initialized) {
            iceServers.add(new PeerConnection.IceServer(serverURI));
        } else {
            throw new RuntimeException("Cannot set ICE servers after NBMWebRTCPeer has been initialized");
        }
    }

    private class ProcessOfferTask implements Runnable {

        SessionDescription remoteOffer;
        String connectionId;

        private ProcessOfferTask(SessionDescription remoteOffer, String connectionId){
            this.remoteOffer = remoteOffer;
            this.connectionId = connectionId;
        }

        public void run() {

            NBMPeerConnection connection = peerConnectionResourceManager.getConnection(connectionId);

            if (connection == null) {
                if (signalingParameters != null) {
                    connection = peerConnectionResourceManager.createPeerConnection(signalingParameters,
                            mediaResourceManager.getPcConstraints(), connectionId);
                    connection.addObserver(NBMWebRTCPeer.this.observer);
                    connection.setRemoteDescriptionSync(remoteOffer);
                    // Create offer. Offer SDP will be sent to answering client in
                    // PeerConnectionEvents.onLocalDescription event.
                    connection.createAnswer(mediaResourceManager.getSdpMediaConstraints());
                }
            }
        }
    }

    /**
     * Processes received SDP offer
     * <p>
     *
     * <p>
     * @param remoteOffer The received offer
     * @param connectionId A unique identifier for the connection
     */
    @SuppressWarnings("unused")
    public void processOffer(SessionDescription remoteOffer, String connectionId) {
        executor.execute(new ProcessOfferTask(remoteOffer, connectionId));
    }

    /**
     * Processes received SDP answer
     * @param remoteAnswer The received answer
     * @param connectionId A unique identifier for the connection
     */
    @SuppressWarnings("unused")
    public void processAnswer(SessionDescription remoteAnswer, String connectionId) {
        NBMPeerConnection connection = peerConnectionResourceManager.getConnection(connectionId);

        if (connection != null) {
            connection.setRemoteDescription(remoteAnswer);
        } else {
            observer.onPeerConnectionError("Connection for id " + connectionId + " cannot be found!");
        }
    }

    /**
     * Adds remote ice candidate for connection
     * @param remoteIceCandidate The received ICE candidate
     * @param connectionId A unique identifier for the connection
     */
    @SuppressWarnings("unused")
    public void addRemoteIceCandidate(IceCandidate remoteIceCandidate, String connectionId) {
        NBMPeerConnection connection = peerConnectionResourceManager.getConnection(connectionId);

        if (connection != null) {
            connection.addRemoteIceCandidate(remoteIceCandidate);
        } else {
            observer.onPeerConnectionError("Connection for id " + connectionId + " cannot be found!");
        }
    }

    /**
     * Closes specific connection
     * @param connectionId A unique identifier for the connection
     */
    @SuppressWarnings("unused")
    public void closeConnection(String connectionId){
        peerConnectionResourceManager.closeConnection(connectionId);
    }

    @SuppressWarnings("unused")
    public DataChannel getDataChannel(String connectionId, String dataChannelId) {
        return peerConnectionResourceManager.getConnection(connectionId).getDataChannel(dataChannelId);
    }

    public DataChannel createDataChannel(String connectionId, String dataChannelId, DataChannel.Init init) {
        NBMPeerConnection connection = peerConnectionResourceManager.getConnection(connectionId);
        if (connection!=null) {
            return connection.createDataChannel(dataChannelId, init);
        }
        else {
            Log.e(TAG, "Cannot find connection by id: " + connectionId);
        }
        return null;
    }

    /**
     * Closes all connections
     */
    @SuppressWarnings("unused")
    public void close(){
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for(NBMPeerConnection c : peerConnectionResourceManager.getConnections()){
                    c.getPc().removeStream(mediaResourceManager.getLocalMediaStream());
                }
                peerConnectionResourceManager.closeAllConnections();
                mediaResourceManager.close();
                peerConnectionFactory.dispose();
                peerConnectionResourceManager = null;
                mediaResourceManager = null;
                peerConnectionFactory = null;
            }
        });
    }

    private boolean startLocalMediaSync() {
        if (mediaResourceManager != null && mediaResourceManager.getLocalMediaStream() == null) {
            mediaResourceManager.createLocalMediaStream(VideoRendererGui.getEglBaseContext(), localRender);
            mediaResourceManager.startVideoSource();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Starts local media playback
     * @return true if local media video source was successfully initiated, otherwise false
     */
    @SuppressWarnings("unused")
    public boolean startLocalMedia() {
        if (mediaResourceManager != null && mediaResourceManager.getLocalMediaStream() == null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    startLocalMediaSync();
                }
            });
            return true;
        } else {
            return false;
        }
    }

    /**
     * Stops local media playback
     */
    @SuppressWarnings("unused")
    public void stopLocalMedia() {
        mediaResourceManager.stopVideoSource();
    }

    /**
     * Attaches remote stream to renderer
     * @param remoteRender A render callback for rendering the remote media
     * @param remoteStream The remote media stream
     */
    @SuppressWarnings("unused")
    public void attachRendererToRemoteStream(VideoRenderer.Callbacks remoteRender, MediaStream remoteStream){
        mediaResourceManager.attachRendererToRemoteStream(remoteRender, remoteStream);
    }

    /**
     * Select active camera for local media
     * @param position The camera identifier (usually either back or front camera e.g. in camera)
     */
    @SuppressWarnings("unused")
    public void selectCameraPosition(NBMMediaConfiguration.NBMCameraPosition position){
        mediaResourceManager.selectCameraPosition(position);
    }

    /**
     * Check if a specific camera is available on the device
     * @param position The camera position to query
     * @return true if position is available on the device, otherwise false
     */
    @SuppressWarnings("unused")
    public boolean hasCameraPosition(NBMMediaConfiguration.NBMCameraPosition position){
        return mediaResourceManager.hasCameraPosition(position);
    }

    /**
     * Check if video is enabled
     * @return true if video is enabled, otherwise false
     */
    @SuppressWarnings("unused")
    public boolean videoEnabled(){
        return mediaResourceManager.getVideoEnabled();
    }

    /**
     * Enable or disable video
     * @param enable If true then video will be enabled, if false then video will be disabled
     */
    @SuppressWarnings("unused")
    public void enableVideo(boolean enable){
        mediaResourceManager.setVideoEnabled(enable);
    }

    /**
     * Check if audio is enabled
     * @return true if audio is enabled, otherwise false
     */
    @SuppressWarnings("unused")
    public boolean audioEnabled(){
        return false;
    }

    /**
     * Enable or disable audio
     * @param enable If true then audio will be enabled, if false then audio will be disabled
     */
    @SuppressWarnings("unused")
    public void enableAudio(boolean enable){

    }

    /**
     * Check if video is authorized
     * @return true if video is authorized, otherwise false
     */
    @SuppressWarnings("unused")
    public boolean videoAuthorized(){
        return false;
    }

    /**
     * Check if audio is authorized
     * @return true if audio is authorized, otherwise false
     */
    @SuppressWarnings("unused")
    public boolean audioAuthorized(){
        return false;
    }

    private void createPeerConnectionFactoryInternal(Context context) {
        Log.d(TAG, "Create peer connection peerConnectionFactory. Use video: " + peerConnectionParameters.videoCallEnabled);
//        isError = false;
        // Initialize field trials.
        String field_trials = FIELD_TRIAL_AUTOMATIC_RESIZE;
        // Check if VP9 is used by default.
        if (peerConnectionParameters.videoCallEnabled && peerConnectionParameters.videoCodec != null &&
                peerConnectionParameters.videoCodec.equals(NBMMediaConfiguration.NBMVideoCodec.VP9.toString())) {
            field_trials += FIELD_TRIAL_VP9;
        }
        PeerConnectionFactory.initializeFieldTrials(field_trials);

        if (!PeerConnectionFactory.initializeAndroidGlobals(context, true, true, peerConnectionParameters.videoCodecHwAcceleration)) {
            observer.onPeerConnectionError("Failed to initializeAndroidGlobals");
        }
        peerConnectionFactory = new PeerConnectionFactory();
        // ToDo: What about these options?
//        if (options != null) {
//            Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
//            peerConnectionFactory.setOptions(options);
//        }
        Log.d(TAG, "Peer connection peerConnectionFactory created.");
    }

}