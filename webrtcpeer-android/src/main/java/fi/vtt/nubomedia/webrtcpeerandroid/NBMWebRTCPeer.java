package fi.vtt.nubomedia.webrtcpeerandroid;

import android.content.Context;
import android.util.Log;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import java.util.HashMap;
import java.util.LinkedList;

/**
 *
 */
public class NBMWebRTCPeer implements PeerConnectionClient.PeerConnectionEvents{

    NBMMediaConfiguration config;
    Context context;

    PeerConnectionClient connection;
    PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;

    PeerConnectionClient.SignalingParameters signalingParameters;

    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;


    public interface Observer {
        void onLocalSdpOffer(SessionDescription localSdp);

        void onIceCandicate(IceCandidate localIceCandidate);

    }


    public NBMWebRTCPeer(NBMMediaConfiguration config, Context context,
                         VideoRenderer.Callbacks localRenderer, VideoRenderer.Callbacks remoteRenderer) {
        this.config = config;
        this.context = context;

        localRenderer = localRenderer;
        remoteRender = remoteRenderer;

        peerConnectionParameters = new PeerConnectionClient.PeerConnectionParameters(true, true,
                         config.getReceiverVideoFormat().width, config.getReceiverVideoFormat().heigth,
                        (int)config.getReceiverVideoFormat().frameRate, config.getVideoBandwidth(), config.getVideoCodec().toString(), true,
                        config.getAudioBandwidth(), config.getAudioCodec().toString(),false, true);


        LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

        signalingParameters = new PeerConnectionClient.SignalingParameters(iceServers,true,"",null,null);

        initialize();

    }


    public void initialize() {
        //            Log.d(TAG, "Creating peer connection factory");
        connection = PeerConnectionClient.getInstance();

        connection.createPeerConnectionFactory(context,
                peerConnectionParameters, this);

    }




    public void generateOffer(String connectionId){

        // Start local stream
        createPeerConnectionAndOffer();

        // Create offer. Offer SDP will be sent to answering client in
        // PeerConnectionEvents.onLocalDescription event.
        connection.createOffer();
    }

    public void processAnswer(SessionDescription remoteAnswer) {

    }

    public void addRemoteIceCandidate(IceCandidate remoteIceCandidate) {

    }


    private void createPeerConnectionAndOffer() {
        if (signalingParameters != null) {

        //                  Log.w(TAG, "EGL context is ready after room connection.");
//                    onConnectedToServerInternal(signalingParameters);
//                }
            connection.createPeerConnection(VideoRendererGui.getEGLContext(),
                    localRender, remoteRender, signalingParameters);


        }
    }

    @Override
    public void onLocalDescription(SessionDescription sdp) {

    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {

    }

    @Override
    public void onIceConnected() {

    }

    @Override
    public void onIceDisconnected() {

    }

    @Override
    public void onPeerConnectionClosed() {

    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {

    }

    @Override
    public void onPeerConnectionError(String description) {

    }
}





}
