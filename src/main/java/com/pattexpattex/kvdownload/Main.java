package com.pattexpattex.kvdownload;

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormatTools;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats.COMMON_PCM_S16_BE;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String WAV_FILENAME = "output.wav";
    private static final String MP3_FILENAME = "output.mp3";

    private static final AtomicReference<AudioTrack> track = new AtomicReference<>();
    
    public static void main(String[] args) {
        AudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.getConfiguration().setOutputFormat(COMMON_PCM_S16_BE);
        AudioSourceManagers.registerRemoteSources(manager);
        AudioPlayer player = manager.createPlayer();

        log.info("URL to download: ");
        String ITEM = new Scanner(System.in).nextLine();

        log.info("Searching...");
        manager.loadItem(ITEM, new FunctionalResultHandler(
                Main.track::set,
                playlist -> track.set(playlist.getTracks().get(0)),
                () -> {
                    log.error("No results for {}", ITEM);
                    System.exit(0);
                },
                e -> {
                    log.error("Loading threw an exception", e);
                    System.exit(-1);
                }
        ));

        while (track.get() == null) {
            Thread.onSpinWait();
        }

        player.playTrack(track.get());

        AudioFormat format = AudioDataFormatTools.toAudioFormat(manager.getConfiguration().getOutputFormat());
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        final long length = track.get().getDuration();
        AtomicLong progress = new AtomicLong();

        log.info("Starting download...");

        ScheduledFuture<?> future = Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(() -> log.info("Progress: {} ({} MiB)...",
                                calculateProgress(length, progress.get()), bytesToMegaBytes(stream.size())),
                        10L, 5L, TimeUnit.SECONDS);

        try {
            AudioFrame frame;
            while ((frame = player.provide(10000L, TimeUnit.MILLISECONDS)) != null) {
                stream.write(frame.getData());
                progress.set(frame.getTimecode());
            }

            player.stopTrack();
            future.cancel(false);

            log.info("Download completed ({} MiB)", bytesToMegaBytes(stream.size()));
            log.info("Writing to file...");
            File file = new File("output.wav");
            file.createNewFile();

            byte[] bytes = stream.toByteArray();
            AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(bytes), format, bytes.length),
                    AudioFileFormat.Type.WAVE, file);

            log.info("Saved to {}", WAV_FILENAME);
            log.info("Converting to MP3...");

            Path path = Files.find(Path.of("C://Program Files"), 10,
                    (p, att) -> p.endsWith("bin/ffmpeg.exe")).findAny().orElse(null);

            if (path != null) {
                Runtime.getRuntime().exec(String.format("%s -i %s %s", path.toAbsolutePath(), WAV_FILENAME, MP3_FILENAME));
            }
            else {
                log.warn("FFMPEG not found in filesystem, skipping the conversion to MP3...");
            }
        }
        catch (IOException | InterruptedException | TimeoutException e) {
            log.error("Something broke while downloading", e);
            System.exit(-1);
        }

        log.info("Done!");
        System.exit(0);
    }

    private static String calculateProgress(long size, long done) {
        float a = (float) done;
        float b = (float) size;

        long percent = Math.round((a / b) * 100);
        return percent + "%";
    }

    private static float bytesToMegaBytes(long bytes) {
        return ((float) bytes) / (1024 * 1024);
    }
}
