package com.extraction.executor.service;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

@Slf4j
@Service
public class SftpService {

    /**
     * Connect to SFTP server
     */
    public ChannelSftp connect(String host, int port, String username, String password) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(30000); // 30s timeout

        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect(30000);

        log.info("Connected to SFTP: {}@{}:{}", username, host, port);
        return channel;
    }

    /**
     * List files in remote directory
     */
    public List<String> listFiles(ChannelSftp channel, String remoteDir, String pattern) throws SftpException {
        List<String> files = new ArrayList<>();

        @SuppressWarnings("unchecked")
        Vector<ChannelSftp.LsEntry> entries = channel.ls(remoteDir);

        for (ChannelSftp.LsEntry entry : entries) {
            if (!entry.getAttrs().isDir()) {
                String fileName = entry.getFilename();
                if (pattern == null || fileName.matches(pattern)) {
                    files.add(remoteDir + "/" + fileName);
                }
            }
        }

        log.info("Found {} files in {}", files.size(), remoteDir);
        return files;
    }

    /**
     * Download file as InputStream
     */
    public InputStream downloadFile(ChannelSftp channel, String remotePath) throws SftpException {
        log.debug("Downloading file: {}", remotePath);
        return channel.get(remotePath);
    }

    /**
     * Get file size
     */
    public long getFileSize(ChannelSftp channel, String remotePath) throws SftpException {
        SftpATTRS attrs = channel.stat(remotePath);
        return attrs.getSize();
    }

    /**
     * Disconnect from SFTP
     */
    public void disconnect(ChannelSftp channel) {
        if (channel != null) {
            try {
                Session session = channel.getSession();
                channel.disconnect();
                if (session != null) {
                    session.disconnect();
                }
                log.debug("Disconnected from SFTP");
            } catch (Exception e) {
                log.warn("Error disconnecting from SFTP: {}", e.getMessage());
            }
        }
    }
}
