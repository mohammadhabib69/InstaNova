package com.insta.nova.util;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a clip down to a time range without re-encoding.
 *
 * <p>Samples are copied straight from the source container into a new one: the range is
 * honoured by skipping samples outside it and re-basing the presentation timestamps so the
 * output starts at zero. That keeps the first frame and the exact pixels of the original,
 * which a decode/encode pass would not.
 */
public final class VideoTrim {

    private static final String TAG = "VideoTrim";
    private static final long TIMEOUT_US = 10_000L;

    /** Last failure seen by {@link #trim}, for the caller's diagnostics. */
    public static String lastError = "";
    /** Shape of the last attempt: track counts, chosen cut point, first sample seen. */
    public static String lastInfo = "";
    /** Key frame the last cut actually started on, in microseconds. */
    public static long lastCutUs = 0L;

    private VideoTrim() {
    }

    private static int indexOfMux(int[] muxTrack, int muxIndex) {
        for (int i = 0; i < muxTrack.length; i++) {
            if (muxTrack[i] == muxIndex) return i;
        }
        return 0;
    }

    /** Duration of the first video track in milliseconds, or 0 when it cannot be read. */
    public static long durationMs(File file) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        return format.getLong(MediaFormat.KEY_DURATION) / 1000L;
                    }
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "duration failed: " + e.getMessage());
        } finally {
            extractor.release();
        }
        return 0L;
    }

    /**
     * Writes {@code [startUs, endUs)} of {@code in} to {@code out}.
     *
     * @return true when at least one sample of the video track was written.
     */
    public static boolean trim(File in, File out, long startUs, long endUs) {
        return trim(in, out, startUs, endUs, false);
    }

    /**
     * Same cut, optionally without the audio track: a muted post is the copy of the video
     * samples alone, so no sound reaches the uploaded file.
     */
    public static boolean trim(File in, File out, long startUs, long endUs, boolean dropAudio) {
        lastError = "";
        lastInfo = "";
        if (out.exists() && !out.delete()) {
            Log.w(TAG, "cannot replace " + out);
            return false;
        }
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            extractor.setDataSource(in.getAbsolutePath());

            muxer = new MediaMuxer(out.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int[] muxTrack = new int[extractor.getTrackCount()];
            int[] sourceTrack = new int[extractor.getTrackCount()];
            for (int i = 0; i < muxTrack.length; i++) muxTrack[i] = -1;
            int videoMuxIndex = -1;
            long endLimitUs = endUs > startUs ? endUs : Long.MAX_VALUE;

            // Every media track is added to the muxer and read in one pass. Reading the
            // tracks one after another let the audio timestamps restart at zero while the
            // video kept its offset, and MediaMuxer silently dropped the out-of-order
            // samples — the cut came out as a 3 KB stub.
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue;
                if (dropAudio && mime.startsWith("audio/")) continue;
                muxTrack[i] = muxer.addTrack(format);
                sourceTrack[i] = i;
                // Nothing is selected by default, so a seek + read with no selectTrack calls
                // returns -1 for every sample and the muxer finalises an empty 3 KB file.
                extractor.selectTrack(i);
                if (videoMuxIndex < 0 && mime.startsWith("video/")) {
                    videoMuxIndex = muxTrack[i];
                    int rotation = format.containsKey(MediaFormat.KEY_ROTATION)
                            ? format.getInteger(MediaFormat.KEY_ROTATION) : 0;
                    if (rotation != 0) muxer.setOrientationHint(rotation);
                }
            }
            if (videoMuxIndex < 0) {
                lastError = "no-video-track tracks=" + extractor.getTrackCount();
                Log.w(TAG, "no video track in " + in.getName());
                return false;
            }
            muxer.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            ByteBuffer buffer = ByteBuffer.allocateDirect(1 << 20);
            int writtenVideoSamples = 0;
            long lastUs = 0L;

            // The cut has to land on a key frame or the muxed stream opens on a P frame and
            // will not decode. seekTo(SEEK_TO_PREVIOUS_SYNC) returned -1 straight away on this
            // device, so the cut point is located by scanning the sample table instead.
            long cutUs = -1L;
            long firstSyncAfter = -1L;
            int videoSource = sourceTrack[videoMuxIndex >= 0 ? indexOfMux(muxTrack, videoMuxIndex) : 0];
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            int scanned = 0;
            while (true) {
                long t0 = extractor.getSampleTime();
                if (t0 < 0) break;
                int idx = extractor.getSampleTrackIndex();
                boolean sync = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
                scanned++;
                if (idx == videoSource && sync) {
                    if (t0 <= startUs) {
                        cutUs = t0;
                    } else {
                        firstSyncAfter = t0;
                        break;
                    }
                }
                if (t0 > startUs) break;
                extractor.advance();
            }
            lastCutUs = Math.max(0L, cutUs);
            if (cutUs < 0) cutUs = firstSyncAfter;
            if (cutUs < 0) cutUs = startUs;
            lastInfo = "tracks=" + muxTrack.length + " scanned=" + scanned
                    + " cutUs=" + cutUs + " reqUs=" + startUs;
            extractor.seekTo(cutUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while (true) {
                long sampleUs = extractor.getSampleTime();
                if (sampleUs < 0 || sampleUs >= endLimitUs) break;
                int track = extractor.getSampleTrackIndex();
                if (track < 0 || track >= muxTrack.length || muxTrack[track] < 0) {
                    extractor.advance();
                    continue;
                }
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                if (sampleUs >= cutUs) {
                    info.offset = 0;
                    info.size = size;
                    info.presentationTimeUs = Math.max(0L, sampleUs - cutUs);
                    info.flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                            ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                    // writeSampleData honours the buffer's own window, so it has to be reset
                    // after every read or the muxer slices past the sample and drops it.
                    buffer.position(0);
                    buffer.limit(size);
                    muxer.writeSampleData(muxTrack[track], buffer, info);
                    lastUs = Math.max(lastUs, info.presentationTimeUs);
                    if (muxTrack[track] == videoMuxIndex) writtenVideoSamples++;
                }
                extractor.advance();
            }

            if (writtenVideoSamples == 0) {
                lastError = "no-samples " + lastInfo;
                Log.w(TAG, "range produced no video samples (" + startUs + "-" + endUs + ")");
                return false;
            }
            Log.d(TAG, "trimmed " + writtenVideoSamples + " samples, " + lastUs / 1000 + " ms");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "trim failed: " + e.getMessage(), e);
            lastError = "exc:" + e.getMessage();
            return false;
        } finally {
            extractor.release();
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (Exception e) {
                    // A failed stop leaves an unfinalised container: the file is written but
                    // unreadable, which is exactly the 3 KB stub this used to produce.
                    lastError = "stop:" + e.getMessage();
                }
                try {
                    muxer.release();
                } catch (Exception e) {
                    lastError = lastError + " release:" + e.getMessage();
                }
            }
        }
    }
}
