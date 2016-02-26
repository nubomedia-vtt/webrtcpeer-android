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
 * The class implements the management of PeerConnection instances.
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


final class PeerConnectionResourceManager {

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



    PeerConnectionResourceManager(NBMWebRTCPeer.NBMPeerConnectionParameters peerConnectionParameters,
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
    NBMPeerConnection createPeerConnection(NBMWebRTCPeer.SignalingParameters signalingParameters,
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
