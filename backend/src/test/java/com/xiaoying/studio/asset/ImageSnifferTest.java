package com.xiaoying.studio.asset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageSnifferTest {

    @Test
    void detectsByMagicBytesNotExtension() {
        assertThat(ImageSniffer.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).contains("image/jpeg");
        assertThat(ImageSniffer.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}))
                .contains("image/png");
        assertThat(ImageSniffer.detect("<html>not an image".getBytes())).isEmpty();
        assertThat(ImageSniffer.detect(new byte[0])).isEmpty();
    }
}
