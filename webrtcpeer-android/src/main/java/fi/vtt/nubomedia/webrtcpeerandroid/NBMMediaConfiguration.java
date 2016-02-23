package fi.vtt.nubomedia.webrtcpeerandroid;

import android.graphics.ImageFormat;

import org.webrtc.VideoRenderer;

/**
 *
 */
public class NBMMediaConfiguration {


    public enum NBMRendererType {
        NATIVE, OPENGLES
    }

    public enum NBMAudioCodec {
        OPUS, ISAC
    }

    public enum NBMVideoCodec {
        VP8, VP9, H264
    }


    public static class NBMVideoFormat {
        public final int heigth;
        public final int width;
        public final int imageFormat;
        public final double frameRate;

        public NBMVideoFormat(int width, int heigth, int imageFormat, double frameRate) {
            this.width = width;
            this.heigth = heigth;

            this.imageFormat = imageFormat;
            this.frameRate = frameRate;
        }
    }

    public enum NBMCameraPosition {
        ANY, BACK, FRONT
    }


    private NBMRendererType rendererType;
    private NBMAudioCodec audioCodec;
    private int audioBandwidth;

    private NBMVideoCodec videoCodec;
    private int videoBandwidth;

    private NBMVideoFormat receiverVideoFormat;

    public NBMCameraPosition getCameraPosition() {
        return cameraPosition;
    }

    public NBMRendererType getRendererType() {
        return rendererType;
    }

    public NBMAudioCodec getAudioCodec() {
        return audioCodec;
    }

    public int getAudioBandwidth() {
        return audioBandwidth;
    }

    public NBMVideoCodec getVideoCodec() {
        return videoCodec;
    }

    public int getVideoBandwidth() {
        return videoBandwidth;
    }

    public NBMVideoFormat getReceiverVideoFormat() {
        return receiverVideoFormat;
    }

    private NBMCameraPosition cameraPosition;

    public NBMMediaConfiguration() {
        rendererType = NBMRendererType.NATIVE
        audioCodec = NBMAudioCodec.OPUS;
        audioBandwidth = 0;

        videoCodec = NBMVideoCodec.VP8;
        videoBandwidth = 0;

        receiverVideoFormat = new NBMVideoFormat(640,480, ImageFormat.NV21 , 30);
        cameraPosition = NBMCameraPosition.FRONT;

    }

    public NBMMediaConfiguration(NBMRendererType rendererType, NBMAudioCodec audioCodec,
                                 int audioBandwidth, NBMVideoCodec videoCodec,
                                 int videoBandwidth, NBMVideoFormat receiverVideoFormat,
                                 NBMCameraPosition cameraPosition) {
        this.rendererType = rendererType;
        this.audioCodec = audioCodec;
        this.audioBandwidth = audioBandwidth;
        this.videoCodec = videoCodec;
        this.videoBandwidth = videoBandwidth;
        this.receiverVideoFormat = receiverVideoFormat;
        this.cameraPosition = cameraPosition;
    }
}
