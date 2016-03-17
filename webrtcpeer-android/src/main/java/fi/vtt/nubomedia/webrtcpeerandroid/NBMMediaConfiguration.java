package fi.vtt.nubomedia.webrtcpeerandroid;

import android.graphics.ImageFormat;

import org.webrtc.VideoRenderer;

/**
 * Media configuration object used in construction of NBMWebRTCPeer
 */
public class NBMMediaConfiguration {

    /**
     * Renderer type
     */
    public enum NBMRendererType {
        NATIVE, OPENGLES
    }

    /**
     * Audio codec
     */
    public enum NBMAudioCodec {
        OPUS, ISAC
    }

    /**
     * Video codec
     */
    public enum NBMVideoCodec {
        VP8, VP9, H264
    }

    /**
     * Camera position
     * <p>
     * Synonymous to active camera. Currently supports back, front and any cameras
     * </p>
     */
    public enum NBMCameraPosition {
        ANY, BACK, FRONT
    }

    /**
     * Video format struct
     */
    public static class NBMVideoFormat {
        /**
         * Video frame height in pixels
         */
        public final int heigth;
        /**
         * Video frame width in pixels
         */
        public final int width;
        /**
         * Video image format.
         * <p>Values are in android.graphics.PixelFormat and android.graphics.ImageFormat. See
         * documentation at <br>
         * http://developer.android.com/reference/android/graphics/ImageFormat.html <br>
         * http://developer.android.com/reference/android/graphics/PixelFormat.html
         * </p>
         */
        public final int imageFormat;
        /**
         * Video frames per second
         */
        public final double frameRate;

        public NBMVideoFormat(int width, int heigth, int imageFormat, double frameRate) {
            this.width = width;
            this.heigth = heigth;

            this.imageFormat = imageFormat;
            this.frameRate = frameRate;
        }
    }

    private NBMRendererType rendererType;
    private NBMAudioCodec audioCodec;
    private int audioBandwidth;
    private NBMVideoCodec videoCodec;
    private int videoBandwidth;
    private NBMCameraPosition cameraPosition;
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

    /**
     * Default constructor
     * <p>
     * Default values: <br>
     * rendererType NATIVE <br>
     * audioCodec OPUS <br>
     * audioBandwidth unlimited <br>
     * videoCodec VP8 <br>
     * videoBandwidth unlimited <br>
     * receiverVideoFormat <br>
     * width 640 <br>
     * height 480 <br>
     * ImageFormat.NV21 <br>
     * fram rate 30 <br>
     * cameraPosition FRONT
     * </p>
     */
    public NBMMediaConfiguration() {
        rendererType = NBMRendererType.NATIVE;
        audioCodec = NBMAudioCodec.OPUS;
        audioBandwidth = 0;

        videoCodec = NBMVideoCodec.VP8;
        videoBandwidth = 0;

        receiverVideoFormat = new NBMVideoFormat(640, 480, ImageFormat.NV21, 30);
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
