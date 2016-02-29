package fi.vtt.nubomedia.webrtcpeerandroid;

import android.content.Context;
import android.util.Log;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

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


public class NBMWebRTCPeer{

    NBMMediaConfiguration config;
    Context context;

    NBMWebRTCPeer.NBMPeerConnectionParameters peerConnectionParameters;

    NBMWebRTCPeer.SignalingParameters signalingParameters;

    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;

    private Observer observer;
    private static final String TAG = "NBMWebRTCPeer";

    private final LooperExecutor executor;
    // PeerConnectionFactory internals. Move to separate static class?
    private static final String FIELD_TRIAL_VP9 = "WebRTC-SupportVP9/Enabled/";
    private static final String FIELD_TRIAL_AUTOMATIC_RESIZE = "WebRTC-MediaCodecVideoEncoder-AutomaticResize/Enabled/";

    private PeerConnectionFactory factory;
    private PeerConnectionResourceManager connectionManager;

    private MediaResourceManager mediaManager;


    public interface Observer {
        void onLocalSdpOfferGenerated(SessionDescription localSdpOffer, NBMPeerConnection connection);
        /* Not implemented yet  */
        void onLocalSdpAnswerGenerated(SessionDescription localSdpAnswer, NBMPeerConnection connection);

        void onIceCandidate(IceCandidate localIceCandidate, NBMPeerConnection connection);

        void onIceStatusChanged(IceConnectionState state, NBMPeerConnection connection);

        void onRemoteStreamAdded(MediaStream stream, NBMPeerConnection connection);

        void onRemoteStreamRemoved(MediaStream stream, NBMPeerConnection connection);

        void onPeerConnectionError(String error);
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


    public NBMWebRTCPeer(NBMMediaConfiguration config, Context context,
                         VideoRenderer.Callbacks localRenderer, Observer observer) {
        this.config = config;
        this.context = context;

        localRender = localRenderer;

        executor = new LooperExecutor();
        // Looper thread is started once in private ctor and is used for all
        // peer connection API calls to ensure new peer connection factory is
        // created on the same thread as previously destroyed factory.
        executor.requestStart();


        peerConnectionParameters = new NBMWebRTCPeer.NBMPeerConnectionParameters(true, true,
                         config.getReceiverVideoFormat().width, config.getReceiverVideoFormat().heigth,
                        (int)config.getReceiverVideoFormat().frameRate, config.getVideoBandwidth(), config.getVideoCodec().toString(), true,
                        config.getAudioBandwidth(), config.getAudioCodec().toString(),false, true);


        LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));




        signalingParameters = new NBMWebRTCPeer.SignalingParameters(iceServers,true,"",null,null);

        this.observer = observer;



    }


    public void initialize() {

        executor.execute(new Runnable() {
            @Override
            public void run() {


                createPeerConnectionFactoryInternal(context);
                connectionManager = new PeerConnectionResourceManager(peerConnectionParameters, executor, factory);

                mediaManager = new MediaResourceManager(peerConnectionParameters, executor, factory);
                mediaManager.createMediaConstraints();

            }
        });


    }



    private class GenerateOfferTask implements Runnable {


        String connectionId;

        private GenerateOfferTask(String connectionId){
            this.connectionId = connectionId;
        }


        public void run() {
            if (mediaManager.getLocalMediaStream() == null) {
                startLocalMediaSync();
            }

            NBMPeerConnection connection = connectionManager.getConnection(connectionId);

            if (connection == null) {
                if (signalingParameters != null) {
                    connection = connectionManager.createPeerConnection(signalingParameters,
                            mediaManager.getPcConstraints(), connectionId);

                    connection.addObserver(mediaManager);
                    connection.getPc().addStream(mediaManager.getLocalMediaStream());

                    // Create offer. Offer SDP will be sent to answering client in
                    // PeerConnectionEvents.onLocalDescription event.
                    connection.createOffer(mediaManager.getSdpMediaConstraints());
                }

            }

        }

    }

    public void generateOffer(String connectionId){

        executor.execute(new GenerateOfferTask(connectionId));

    }

    private class ProcessOfferTask implements Runnable {


        SessionDescription remoteOffer;
        String connectionId;


        private ProcessOfferTask(SessionDescription remoteOffer, String connectionId){
            this.remoteOffer = remoteOffer;
            this.connectionId = connectionId;
        }


        public void run() {
//            if (mediaManager.getLocalMediaStream() == null) {
//                startLocalMediaSync();
//            }

            NBMPeerConnection connection = connectionManager.getConnection(connectionId);

            if (connection == null) {
                if (signalingParameters != null) {
                    connection = connectionManager.createPeerConnection(signalingParameters,
                            mediaManager.getPcConstraints(), connectionId);

                    connection.setRemoteDescriptionSync(remoteOffer);
                    // Create offer. Offer SDP will be sent to answering client in
                    // PeerConnectionEvents.onLocalDescription event.
                    connection.createAnswer(mediaManager.getSdpMediaConstraints());
                }

            }

        }

    }


    public void processOffer(SessionDescription remoteOffer, String connectionId) {
        executor.execute(new ProcessOfferTask(remoteOffer, connectionId));
    }

    public void processAnswer(SessionDescription remoteAnswer, String connectionId) {
        NBMPeerConnection connection = connectionManager.getConnection(connectionId);

        if (connection != null) {
            connection.setRemoteDescription(remoteAnswer);
        } else {
            observer.onPeerConnectionError("Connection for id " + connectionId + " cannot be found!");
        }
    }

    public void addRemoteIceCandidate(IceCandidate remoteIceCandidate, String connectionId) {
        NBMPeerConnection connection = connectionManager.getConnection(connectionId);

        if (connection != null) {
            connection.addRemoteIceCandidate(remoteIceCandidate);
        } else {
            observer.onPeerConnectionError("Connection for id " + connectionId + " cannot be found!");
        }
    }

    public void closeConnection(String connectionId){
        connectionManager.closeConnection(connectionId);
    }

    public void close(){
        connectionManager.close();
        connectionManager = null;
        mediaManager.close();
        mediaManager = null;
        factory.dispose();
        factory = null;
    }

    private boolean startLocalMediaSync() {
        if (mediaManager != null && mediaManager.getLocalMediaStream() == null) {
            mediaManager.createLocalMediaStream(VideoRendererGui.getEGLContext(), localRender);
            mediaManager.startVideoSource();
            return true;
        } else {
            return false;
        }
    }

    public boolean startLocalMedia() {
        if (mediaManager != null && mediaManager.getLocalMediaStream() == null) {
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

    public void stopLocalMedia() {
        mediaManager.stopVideoSource();
    }

    public void attachRendererToRemoteStream(VideoRenderer.Callbacks remoteRender, MediaStream remoteStream){
        mediaManager.attachRendererToRemoteStream(remoteRender, remoteStream);
    }


    public void selectCameraPosition(NBMMediaConfiguration.NBMCameraPosition position){
        mediaManager.selectCameraPosition(position);
    }

    public boolean hasCameraPosition(NBMMediaConfiguration.NBMCameraPosition position){
        return mediaManager.hasCameraPosition(position);
    }

    public boolean videoEnabled(){
        return mediaManager.getVideoEnabled();
    }

    public void enableVideo(boolean enable){
        mediaManager.setVideoEnabled(enable);
    }

    public boolean audioEnabled(){
        return false;
    }

    public void enableAudio(boolean enable){

    }

    public boolean videoAuthorized(){
        return false;
    }

    public boolean audioAuthorized(){
        return false;
    }



    /**
     *
     * @param context
     */
    private void createPeerConnectionFactoryInternal(Context context) {
        Log.d(TAG, "Create peer connection factory. Use video: " + peerConnectionParameters.videoCallEnabled);
//        isError = false;
        // Initialize field trials.
        String field_trials = FIELD_TRIAL_AUTOMATIC_RESIZE;
        // Check if VP9 is used by default.
        if (peerConnectionParameters.videoCallEnabled && peerConnectionParameters.videoCodec != null &&
                peerConnectionParameters.videoCodec.equals(NBMMediaConfiguration.NBMVideoCodec.VP9.toString())) {
            field_trials += FIELD_TRIAL_VP9;
        }
        if (!PeerConnectionFactory.initializeAndroidGlobals(context, true, true, peerConnectionParameters.videoCodecHwAcceleration)) {
            observer.onPeerConnectionError("Failed to initializeAndroidGlobals");
        }
        factory = new PeerConnectionFactory();
        // ToDo: What about these options?
//        if (options != null) {
//            Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
//            factory.setOptions(options);
//        }
        Log.d(TAG, "Peer connection factory created.");
    }



}




