package fi.vtt.nubomedia.webrtcpeerandroid;

import android.content.Context;
import android.util.Log;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
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

    private Observer observer;


    public interface Observer {
        void onLocalSdpOfferGenerated(SessionDescription localSdpOffer);
        /* Not implemented yet  */
        void onLocalSdpAnswerGenerated(SessionDescription localSdpAnswer);

        void onIceCandicate(IceCandidate localIceCandidate);

        void onIceStatusChanged(IceConnectionState state);

        void onRemoteStreamAdded(MediaStream stream);

        void onRemoteStreamRemoved(MediaStream stream);
    }



    public NBMWebRTCPeer(NBMMediaConfiguration config, Context context,
                         VideoRenderer.Callbacks localRenderer, VideoRenderer.Callbacks remoteRenderer, Observer observer) {
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

        // Create connection and initialise local stream
        createPeerConnection();

        // Create offer. Offer SDP will be sent to answering client in
        // PeerConnectionEvents.onLocalDescription event.
        connection.createOffer();
    }

    public void processOffer(SessionDescription remoteOffer) {
        throw new UnsupportedOperationException();
    }

    public void processAnswer(SessionDescription remoteAnswer) {



    }

    public void addRemoteIceCandidate(IceCandidate remoteIceCandidate) {

    }

    public boolean startLocalMedia() {
        connection.startVideoSource();
        return true;
    }

    public void stopLocalMedia() {
        connection.stopVideoSource();
    }

    public void selectCameraPosition(NBMMediaConfiguration.NBMCameraPosition position){

    }

    public boolean hasCameraPosition(NBMMediaConfiguration.NBMCameraPosition position){
        return false;
    }

    public boolean videoEnabled(){
        return false;
    }

    public void enableVideo(boolean enable){

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

    private void createPeerConnection() {
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
        observer.onLocalSdpAnswerGenerated(sdp);
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




