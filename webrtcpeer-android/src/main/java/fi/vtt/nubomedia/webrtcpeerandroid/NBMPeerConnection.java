package fi.vtt.nubomedia.webrtcpeerandroid;

import android.util.Log;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.vtt.nubomedia.utilitiesandroid.LooperExecutor;

/**
 * A peer connection wrapper which is used by NBMWebRTCPeer to support multiple connectivity.
 *
 */
public class NBMPeerConnection implements PeerConnection.Observer, SdpObserver {

    private static final String TAG = "NBMPeerConnection";
    private static final String VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate";
    private static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";

    private PeerConnection pc;
    private String connectionId;
    private LooperExecutor executor;
    private SessionDescription localSdp; // either offer or answer SDP
    private boolean preferIsac;
    private boolean videoCallEnabled;
    private boolean preferH264;
    private boolean isInitiator;
    private HashMap<String, ObservedDataChannel> observedDataChannels;
    private LinkedList<IceCandidate> queuedRemoteCandidates;
    MediaConstraints sdpMediaConstraints = null;
    Vector<NBMWebRTCPeer.Observer> observers;
    NBMWebRTCPeer.NBMPeerConnectionParameters peerConnectionParameters;

    /* This private class exists to receive per-channel events and forward them to upper layers
       with the channel instance
      */
    private class ObservedDataChannel implements DataChannel.Observer {
        private DataChannel channel;

        public ObservedDataChannel(String label, DataChannel.Init init) {
            channel = pc.createDataChannel(label, init);
            if (channel != null) {
                channel.registerObserver(this);
                Log.i(TAG, "Created data channel with Id: " + label);
            }
            else {
                Log.e(TAG, "Failed to create data channel with Id: " + label);
            }
        }

        public DataChannel getChannel() {
            return channel;
        }

        @Override
        public void onBufferedAmountChange(long l) {
            Log.i(TAG, "[ObservedDataChannel] NBMPeerConnection onBufferedAmountChange");
            for (NBMWebRTCPeer.Observer observer : observers) {
                observer.onBufferedAmountChange(l, NBMPeerConnection.this, channel);
            }
        }

        @Override
        public void onStateChange(){
            for (NBMWebRTCPeer.Observer observer : observers) {
                observer.onStateChange(NBMPeerConnection.this, channel);
            }
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            Log.i(TAG, "[ObservedDataChannel] NBMPeerConnection onMessage");
            for (NBMWebRTCPeer.Observer observer : observers) {
                observer.onMessage(buffer, NBMPeerConnection.this, channel);
            }
        }
    }

    public NBMPeerConnection(String connectionId, boolean preferIsac, boolean videoCallEnabled, boolean preferH264,
                             LooperExecutor executor, NBMWebRTCPeer.NBMPeerConnectionParameters params) {

        this.connectionId = connectionId;
        observers = new Vector<>();
        this.preferIsac = preferIsac;
        this.videoCallEnabled = videoCallEnabled;
        this.preferH264 = preferH264;
        this.executor = executor;
        isInitiator = false;
        peerConnectionParameters = params;
        queuedRemoteCandidates = new LinkedList<>();
        observedDataChannels = new HashMap<>();
    }

    public DataChannel createDataChannel(String label, DataChannel.Init init)
    {
        ObservedDataChannel dataChannel = new ObservedDataChannel(label, init);
        observedDataChannels.put(label, dataChannel);
        return dataChannel.getChannel();
    }

    @SuppressWarnings("unused")
    public HashMap<String, DataChannel> getDataChannels(){
        HashMap<String, DataChannel> channels = new HashMap<>();
        for (HashMap.Entry<String, ObservedDataChannel> entry : observedDataChannels.entrySet()) {
            String key = entry.getKey();
            ObservedDataChannel value = entry.getValue();
            channels.put(key, value.getChannel());
        }
        return channels;
    }

    @SuppressWarnings("unused")
    public String getConnectionId() {
        return connectionId;
    }

    public DataChannel getDataChannel(String dataChannelId){
        ObservedDataChannel channel = this.observedDataChannels.get(dataChannelId);
        if (channel == null) {
            return null;
        }
        else {
            return channel.getChannel();
        }
    }

    public void setPc(PeerConnection pc) {
        this.pc = pc;
    }

    public PeerConnection getPc(){
        return pc;
    }

    public void addObserver(NBMWebRTCPeer.Observer observer){
        observers.add(observer);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        Log.i(TAG, "[datachannel] Peer opened data channel");
        for (NBMWebRTCPeer.Observer observer : observers) {
            observer.onDataChannel(dataChannel, NBMPeerConnection.this);
        }
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        Log.d(TAG, "SignalingState: " + signalingState);
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
        Log.d(TAG, "IceConnectionState: " + newState);

        for (NBMWebRTCPeer.Observer o : observers) {
            o.onIceStatusChanged(newState, this);
        }
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
        Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        Log.d(TAG, "IceGatheringState: " + iceGatheringState);
    }

    @Override
    public void onIceCandidate(final IceCandidate iceCandidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (NBMWebRTCPeer.Observer observer : observers) {
                    observer.onIceCandidate(iceCandidate, NBMPeerConnection.this);
                }
            }
        });
    }

    @Override
    public void onAddStream(final MediaStream mediaStream) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (pc == null) {
                    return;
                }
                if (mediaStream.audioTracks.size() > 1 || mediaStream.videoTracks.size() > 1) {
                    for (NBMWebRTCPeer.Observer observer : observers) {
                        observer.onPeerConnectionError("Weird-looking stream: " + mediaStream);
                    }
                    return;
                }
                for (NBMWebRTCPeer.Observer observer : observers) {
                    observer.onRemoteStreamAdded(mediaStream, NBMPeerConnection.this);
                }
            }
        });
    }

    @Override
    public void onRemoveStream(final MediaStream mediaStream) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (pc == null) {
                    return;
                }
                if (mediaStream.audioTracks.size() > 1 || mediaStream.videoTracks.size() > 1) {
                    for (NBMWebRTCPeer.Observer observer : observers) {
                        observer.onPeerConnectionError("Weird-looking stream: " + mediaStream);
                    }
                    return;
                }
                for (NBMWebRTCPeer.Observer observer : observers) {
                    observer.onRemoteStreamRemoved(mediaStream, NBMPeerConnection.this);
                }
            }
        });
    }

    @Override
    public void onRenegotiationNeeded() {
        Log.d(TAG, "[datachannel] OnRenegotiationNeeded called.");
        /*if (sdpMediaConstraints != null) {
            Log.d(TAG, sdpMediaConstraints.toString());
            pc.createOffer(NBMPeerConnection.this, sdpMediaConstraints);
        }*/
    }

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        assert(localSdp != null);
        String sdpDescription = sessionDescription.description;
        if (preferIsac) {
            sdpDescription = preferCodec(sdpDescription, NBMMediaConfiguration.NBMAudioCodec.ISAC.toString(), true);
        }
        if (videoCallEnabled && preferH264) {
            sdpDescription = preferCodec(sdpDescription, NBMMediaConfiguration.NBMVideoCodec.H264.toString(), false);
        }
        final SessionDescription sdp = new SessionDescription(sessionDescription.type, sdpDescription);
        localSdp = sdp;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (pc != null) {// && !isError) {
                    Log.d(TAG, "Set local SDP from " + sdp.type);
                    pc.setLocalDescription(NBMPeerConnection.this, sdp);
                }
            }
        });
    }

    @Override
    public void onSetSuccess() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (pc == null) {// || isError) {
                    return;
                }
                if (isInitiator) {
                    // For offering peer connection we first create offer and set
                    // local SDP, then after receiving answer set remote SDP.
                    if (pc.getRemoteDescription() == null) {
                        // We've just set our local SDP so time to send it.
                        Log.d(TAG, "Local SDP set succesfully");
                        for (NBMWebRTCPeer.Observer observer : observers) {
                            observer.onLocalSdpOfferGenerated(localSdp, NBMPeerConnection.this);
                        }
                    } else {
                        // We've just set remote description, so drain remote
                        // and send local ICE candidates.
                        Log.d(TAG, "Remote SDP set succesfully");
                        drainCandidates();
                    }
                } else {
                    // For answering peer connection we set remote SDP and then
                    // create answer and set local SDP.
                    if (pc.getLocalDescription() != null) {
                        // We've just set our local SDP so time to send it, drain
                        // remote and send local ICE candidates.
                        Log.d(TAG, "Local SDP set succesfully");
                        for (NBMWebRTCPeer.Observer observer : observers) {
                            observer.onLocalSdpAnswerGenerated(localSdp, NBMPeerConnection.this);
                        }
                        drainCandidates();
                    } else {
                        // We've just set remote SDP - do nothing for now -
                        // answer will be created soon.
                        Log.d(TAG, "Remote SDP set succesfully");
                    }
                }
            }
        });
    }

    @Override
    public void onCreateFailure(String s) {
        for (NBMWebRTCPeer.Observer observer : observers) {
            observer.onPeerConnectionError(s);
        }
    }

    @Override
    public void onSetFailure(String s) {
        for (NBMWebRTCPeer.Observer observer : observers) {
            observer.onPeerConnectionError(s);
        }
    }

    private void drainCandidates() {
        if (queuedRemoteCandidates != null) {
            Log.d(TAG, "Add " + queuedRemoteCandidates.size() + " remote candidates");
            for (IceCandidate candidate : queuedRemoteCandidates) {
                pc.addIceCandidate(candidate);
            }
            queuedRemoteCandidates = null;
        }
    }

    public void createOffer(MediaConstraints sdpMediaConstraints) {
        this.sdpMediaConstraints = sdpMediaConstraints;
        if (pc != null){// && !isError) {
            Log.d(TAG, "PC Create OFFER");
            isInitiator = true;
            pc.createOffer(this,this.sdpMediaConstraints);
        }
    }

    public void createAnswer(final MediaConstraints sdpMediaConstraints) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (pc != null){// && !isError) {
                    Log.d(TAG, "PC create ANSWER");
                    isInitiator = false;
                    pc.createAnswer(NBMPeerConnection.this, sdpMediaConstraints);
                }
            }
        });
    }

    protected void setRemoteDescriptionSync(SessionDescription sdp) {
        if (pc == null){// || isError) {
            return;
        }
        String sdpDescription = sdp.description;
        if (preferIsac) {
            sdpDescription = preferCodec(sdpDescription, NBMMediaConfiguration.NBMAudioCodec.ISAC.toString(), true);
        }
        if (videoCallEnabled && preferH264) {
            sdpDescription = preferCodec(sdpDescription, NBMMediaConfiguration.NBMVideoCodec.H264.toString(), false);
        }
        if (videoCallEnabled && peerConnectionParameters.videoStartBitrate > 0) {
            sdpDescription = setStartBitrate(NBMMediaConfiguration.NBMVideoCodec.VP8.toString(), true, sdpDescription, peerConnectionParameters.videoStartBitrate);
            sdpDescription = setStartBitrate(NBMMediaConfiguration.NBMVideoCodec.VP9.toString(), true, sdpDescription, peerConnectionParameters.videoStartBitrate);
            sdpDescription = setStartBitrate(NBMMediaConfiguration.NBMVideoCodec.H264.toString(), true, sdpDescription, peerConnectionParameters.videoStartBitrate);
        }
        if (peerConnectionParameters.audioStartBitrate > 0) {
            sdpDescription = setStartBitrate(NBMMediaConfiguration.NBMAudioCodec.OPUS.toString(), false, sdpDescription, peerConnectionParameters.audioStartBitrate);
        }
        Log.d(TAG, "Set remote SDP.");
        SessionDescription sdpRemote = new SessionDescription(sdp.type, sdpDescription);
        pc.setRemoteDescription(NBMPeerConnection.this, sdpRemote);
    }

    protected void setRemoteDescription(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                setRemoteDescriptionSync(sdp);
            }
        });
    }

    public void addRemoteIceCandidate(final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (pc != null){// && !isError) {
                    if (queuedRemoteCandidates != null) {
                        queuedRemoteCandidates.add(candidate);
                    } else {
                        pc.addIceCandidate(candidate);
                    }
                }
            }
        });
    }

    public void close(){
        Log.d(TAG, "Closing peer connection.");
        if (pc != null) {
            pc.dispose();
            pc = null;
        }
        Log.d(TAG, "Closing peer connection done.");
    }

    /**
     *
     * @param codec
     * @param isVideoCodec
     * @param sdpDescription
     * @param bitrateKbps
     * @return
     */
    private static String setStartBitrate(String codec, boolean isVideoCodec, String sdpDescription, int bitrateKbps) {
        String[] lines = sdpDescription.split("\r\n");
        int rtpmapLineIndex = -1;
        boolean sdpFormatUpdated = false;
        String codecRtpMap = null;
        // Search for codec rtpmap in format
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                rtpmapLineIndex = i;
                break;
            }
        }
        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for " + codec + " codec");
            return sdpDescription;
        }
        Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]);
        // Check if a=fmtp string already exist in remote SDP for this codec and
        // update it with new bitrate parameter.
        regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
        codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                Log.d(TAG, "Found " + codec + " " + lines[i]);
                if (isVideoCodec) {
                    lines[i] += "; " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
                }
                Log.d(TAG, "Update remote SDP line: " + lines[i]);
                sdpFormatUpdated = true;
                break;
            }
        }
        StringBuilder newSdpDescription = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            newSdpDescription.append(lines[i]).append("\r\n");
            // Append new a=fmtp line if no such line exist for a codec.
            if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                String bitrateSet;
                if (isVideoCodec) {
                    bitrateSet = "a=fmtp:" + codecRtpMap + " " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    bitrateSet = "a=fmtp:" + codecRtpMap + " " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
                }
                Log.d(TAG, "Add remote SDP line: " + bitrateSet);
                newSdpDescription.append(bitrateSet).append("\r\n");
            }
        }
        return newSdpDescription.toString();
    }


    /**
     *
     * @param sdpDescription
     * @param codec
     * @param isAudio
     * @return
     */
    private static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
        String[] lines = sdpDescription.split("\r\n");
        int mLineIndex = -1;
        String codecRtpMap = null;
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);
        String mediaDescription = "m=video ";
        if (isAudio) {
            mediaDescription = "m=audio ";
        }
        for (int i = 0; (i < lines.length) && (mLineIndex == -1 || codecRtpMap == null); i++) {
            if (lines[i].startsWith(mediaDescription)) {
                mLineIndex = i;
                continue;
            }
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                continue;
            }
        }
        if (mLineIndex == -1) {
            Log.w(TAG, "No " + mediaDescription + " line, so can't prefer " + codec);
            return sdpDescription;
        }
        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for " + codec);
            return sdpDescription;
        }
        Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + ", prefer at " + lines[mLineIndex]);
        String[] origMLineParts = lines[mLineIndex].split(" ");
        if (origMLineParts.length > 3) {
            StringBuilder newMLine = new StringBuilder();
            int origPartIndex = 0;
            // Format is: m=<media> <port> <proto> <fmt> ...
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(codecRtpMap);
            for (; origPartIndex < origMLineParts.length; origPartIndex++) {
                if (!origMLineParts[origPartIndex].equals(codecRtpMap)) {
                    newMLine.append(" ").append(origMLineParts[origPartIndex]);
                }
            }
            lines[mLineIndex] = newMLine.toString();
            Log.d(TAG, "Change media description: " + lines[mLineIndex]);
        } else {
            Log.e(TAG, "Wrong SDP media description format: " + lines[mLineIndex]);
        }
        StringBuilder newSdpDescription = new StringBuilder();
        for (String line : lines) {
            newSdpDescription.append(line).append("\r\n");
        }
        return newSdpDescription.toString();
    }
}
