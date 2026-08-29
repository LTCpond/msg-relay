package com.ltcpond.msgrelay.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件服务接口 — 支持多种存储实现
 *
 * 实现类:
 * - OssFileServiceImpl: 阿里云 OSS（默认，生产环境）
 */
public interface FileService {

    String upload(MultipartFile file) throws IOException;

    byte[] download(String filename) throws IOException;
}
