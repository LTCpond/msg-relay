package com.ltcpond.msgrelay.file.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.ltcpond.msgrelay.file.config.OssProperties;
import com.ltcpond.msgrelay.file.service.FileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * 阿里云 OSS 文件存储实现 — 默认激活
 *
 * 去重策略: 上传前计算 MD5，同名文件跳过（OSS 上相同 key 即为同一文件）
 * 配置: 需在 application.yml 中配置 oss.endpoint / accessKeyId / accessKeySecret / bucketName
 */
@Slf4j
@Service
public class OssFileServiceImpl implements FileService {

    @Resource
    private OSS ossClient;

    @Resource
    private OssProperties ossProperties;

    @Override
    public String upload(MultipartFile file) throws IOException {
        // 1. 只读取一次流，避免重复读取
        byte[] bytes = file.getBytes();
        String md5 = computeMd5(bytes);
        String ext = getExtension(file.getOriginalFilename());
        String objectName = md5 + "." + ext;

        // 2. 去重：OSS 已存在直接返回
        if (ossClient.doesObjectExist(ossProperties.getBucketName(), objectName)) {
            log.info("OSS文件已存在(去重): {}", objectName);
            return objectName;
        }

        // 3. 上传（复用 bytes，不再读流）
        ossClient.putObject(
                ossProperties.getBucketName(),
                objectName,
                new ByteArrayInputStream(bytes)
        );

        log.info("OSS上传成功: fileName={}, size={}", objectName, file.getSize());
        return objectName;
    }

    @Override
    public byte[] download(String filename) throws IOException {
        if (!ossClient.doesObjectExist(ossProperties.getBucketName(), filename)) {
            throw new RuntimeException("文件不存在: " + filename);
        }

        // 自动关闭 OSSObject，防止连接泄漏
        try (OSSObject object = ossClient.getObject(ossProperties.getBucketName(), filename);
             InputStream in = object.getObjectContent();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        }
    }

    // MD5 不捕获异常，因为不可能异常
    private String computeMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不存在", e);
        }
    }

    // 获取扩展名
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}