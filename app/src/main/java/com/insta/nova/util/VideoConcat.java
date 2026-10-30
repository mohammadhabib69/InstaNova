package com.insta.nova.util;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Joins recorded clips into one MP4.
 *
 * <p>The reel recorder writes one file per take so a take can be deleted and so pausing is
 * just "stop this take and start the next one". The publish path needs a single video, so
 * the takes are copied sample-by-sample into one track set here. Samples are re-timestamped
 * onto a running offset, which is what makes the joined file play as one continuous clip.
 *
 * <p>No re-encode happens: the first take's track formats are reused for every take. That is
 * only valid while all takes come from one camera configuration, so a take whose tracks do
 * not match is rejected instead of producing a broken file.
 */
public final class VideoConcat {

    private static final String TAG = "VideoConcat";
    private static final long FALLBACK_SAMPLE_US = 33_000L;

    private VideoConcat() {
    }

    /** Concatenates {@code parts} in order into {@code out}. */
    public static void concat(List<File> parts, File out) throws IOException {
        if (parts == null || parts.isEmpty()) throw new IOException("no clips to join");
        for (File part : parts) {
            if (part == null || !part.exists() || part.length() == 0) {
                throw new IOException("missing clip " + part);
            }
        }

        MediaMuxer muxer = null;
        List<MediaExtractor> extractors = new ArrayList<>();
        try {
            muxer = new MediaMuxer(out.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            muxer.setOrientationHint(rotationOf(parts.get(0)));

            int videoTrack = -1;
            int audioTrack = -1;
            MediaFormat videoFormat = null;
            MediaFormat audioFormat = null;
            long offsetUs = 0L;

            for (int index = 0; index < parts.size(); index++) {
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(parts.get(index).getAbsolutePath());
                extractors.add(extractor);

                int partVideo = -1;
                int partAudio = -1;
                for (int t = 0; t < extractor.getTrackCount(); t++) {
                    MediaFormat format = extractor.getTrackFormat(t);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime == null) continue;
                    if (mime.startsWith("video/") && partVideo < 0) partVideo = t;
                    else if (mime.startsWith("audio/") && partAudio < 0) partAudio = t;
                }
                if (partVideo < 0) throw new IOException("clip " + index + " has no video track");

                if (index == 0) {
                    videoFormat = extractor.getTrackFormat(partVideo);
                    videoTrack = muxer.addTrack(videoFormat);
                    if (partAudio >= 0) {
                        audioFormat = extractor.getTrackFormat(partAudio);
                        audioTrack = muxer.addTrack(audioFormat);
                    }
                    muxer.start();
                } else {
                    // Reject a take recorded with different encoder settings rather than
                    // writing samples the muxer's single track header cannot describe.
                    MediaFormat current = extractor.getTrackFormat(partVideo);
                    if (!sameVideoShape(videoFormat, current)) {
                        throw new IOException("clip " + index + " does not match the first clip");
                    }
                }

                long endUs = offsetUs;
                ByteBuffer buffer = ByteBuffer.allocate(2 * 1024 * 1024);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                extractor.selectTrack(partVideo);
                while (true) {
                    info.offset = 0;
                    info.size = extractor.readSampleData(buffer, 0);
                    if (info.size < 0) break;
                    info.presentationTimeUs = offsetUs + extractor.getSampleTime();
                    info.flags = extractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC
                            ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                    muxer.writeSampleData(videoTrack, buffer, info);
                    endUs = Math.max(endUs, info.presentationTimeUs + FALLBACK_SAMPLE_US);
                    extractor.advance();
                }
                extractor.unselectTrack(partVideo);

                if (audioTrack >= 0 && partAudio >= 0) {
                    extractor.selectTrack(partAudio);
                    while (true) {
                        info.offset = 0;
                        info.size = extractor.readSampleData(buffer, 0);
                        if (info.size < 0) break;
                        info.presentationTimeUs = offsetUs + extractor.getSampleTime();
                        info.flags = extractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC
                                ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                        muxer.writeSampleData(audioTrack, buffer, info);
                        endUs = Math.max(endUs, info.presentationTimeUs + FALLBACK_SAMPLE_US);
                        extractor.advance();
                    }
                    extractor.unselectTrack(partAudio);
                }

                offsetUs = endUs;
            }

            if (out.length() == 0) throw new IOException("joined clip is empty");
        } finally {
            for (MediaExtractor extractor : extractors) {
                try {
                    extractor.release();
                } catch (Exception ignored) {
                }
            }
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (Exception e) {
                    Log.w(TAG, "muxer stop: " + e.getMessage());
                }
                try {
                    muxer.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** True when two takes can share one track header. */
    private static boolean sameVideoShape(MediaFormat first, MediaFormat other) {
        if (first == null || other == null) return false;
        String mimeA = first.getString(MediaFormat.KEY_MIME);
        String mimeB = other.getString(MediaFormat.KEY_MIME);
        if (mimeA == null || !mimeA.equals(mimeB)) return false;
        if (first.containsKey(MediaFormat.KEY_WIDTH) && other.containsKey(MediaFormat.KEY_WIDTH)) {
            if (first.getInteger(MediaFormat.KEY_WIDTH) != other.getInteger(MediaFormat.KEY_WIDTH)) {
                return false;
            }
            if (first.getInteger(MediaFormat.KEY_HEIGHT)
                    != other.getInteger(MediaFormat.KEY_HEIGHT)) {
                return false;
            }
        }
        return true;
    }

    /** Rotation of the first take, so a portrait recording is not muxed sideways. */
    private static int rotationOf(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            return value == null ? 0 : Integer.parseInt(value);
        } catch (Exception e) {
            Log.w(TAG, "rotation unknown: " + e.getMessage());
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    /** Duration of a clip in milliseconds, 0 when it cannot be read. */
    public static long durationMs(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    /** One frame of a clip, for the timeline strip and the cover. */
    public static android.graphics.Bitmap frameAt(File file, long timeMs) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            return retriever.getFrameAtTime(timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception e) {
            Log.w(TAG, "frame failed: " + e.getMessage());
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }
}
