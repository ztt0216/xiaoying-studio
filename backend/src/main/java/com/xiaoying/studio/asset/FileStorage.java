package com.xiaoying.studio.asset;

import com.xiaoying.studio.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 本地文件存储。路径均为相对存储根目录的形式（如 uploads/xxx.png），对外地址为 /files/ + 相对路径。
 */
@Component
public class FileStorage {

    private final Path root;

    @Autowired
    public FileStorage(AppProperties props) {
        this(Path.of(props.storageDir()));
    }

    public FileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root.resolve("uploads"));
            Files.createDirectories(this.root.resolve("work"));
            Files.createDirectories(this.root.resolve("results"));
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建存储目录 " + this.root, e);
        }
    }

    public String saveUpload(InputStream in, String extension) throws IOException {
        String relative = "uploads/" + UUID.randomUUID() + "." + extension;
        Files.copy(in, resolve(relative), StandardCopyOption.REPLACE_EXISTING);
        return relative;
    }

    public Path resolve(String relative) {
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("非法路径: " + relative);
        }
        return p;
    }

    public static String urlOf(String relative) {
        return "/files/" + relative;
    }
}
