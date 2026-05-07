package com.lawoffice.util;

import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 工具类
 */
@Slf4j
@Component
public class MinioUtils {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private com.lawoffice.framework.config.MinioConfig minioConfig;

    /**
     * 检查存储桶是否存在，如果不存在则创建
     */
    public void ensureBucketExists() {
        try {
            String bucketName = minioConfig.getBucketName();
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("创建存储桶成功: {}", bucketName);
            } else {
                log.debug("存储桶已存在: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("检查或创建存储桶失败", e);
            throw new RuntimeException("检查或创建存储桶失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件
     *
     * @param file 文件
     * @return 文件访问路径
     */
    public String uploadFile(MultipartFile file) {
        try {
            // 确保存储桶存在
            ensureBucketExists();

            String bucketName = minioConfig.getBucketName();
            String objectName = generateObjectName(file.getOriginalFilename());

            // 上传文件
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("文件上传成功: {}", objectName);
            return getObjectUrl(objectName);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件（通过输入流）
     *
     * @param inputStream 输入流
     * @param fileName    文件名
     * @param contentType 内容类型
     * @return 文件访问路径
     */
    public String uploadFile(InputStream inputStream, String fileName, String contentType) {
        try {
            // 确保存储桶存在
            ensureBucketExists();

            String bucketName = minioConfig.getBucketName();
            String objectName = generateObjectName(fileName);

            // 上传文件
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, -1, 10485760) // 10MB part size
                            .contentType(contentType)
                            .build()
            );

            log.info("文件上传成功: {}", objectName);
            return getObjectUrl(objectName);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     *
     * @param objectName 对象名称
     */
    public void deleteFile(String objectName) {
        try {
            String bucketName = minioConfig.getBucketName();
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(extractObjectName(objectName))
                            .build()
            );
            log.info("文件删除成功: {}", objectName);
        } catch (Exception e) {
            log.error("文件删除失败", e);
            throw new RuntimeException("文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件访问URL
     *
     * @param objectName 对象名称
     * @return 文件访问URL
     */
    public String getObjectUrl(String objectName) {
        try {
            String bucketName = minioConfig.getBucketName();
            String actualObjectName = extractObjectName(objectName);
            
            // 生成预签名URL（有效期7天）
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(actualObjectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build()
            );
            
            // 如果使用公开访问，可以直接返回完整路径
            return minioConfig.getEndpoint() + "/" + bucketName + "/" + actualObjectName;
        } catch (Exception e) {
            log.error("获取文件URL失败", e);
            throw new RuntimeException("获取文件URL失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件
     *
     * @param objectName 对象名称
     * @return 文件输入流
     */
    public InputStream downloadFile(String objectName) {
        try {
            String bucketName = minioConfig.getBucketName();
            String actualObjectName = extractObjectName(objectName);
            
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(actualObjectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("文件下载失败", e);
            throw new RuntimeException("文件下载失败: " + e.getMessage());
        }
    }

    /**
     * 生成对象名称（避免文件名冲突）
     *
     * @param originalFilename 原始文件名
     * @return 生成的对象名称
     */
    private String generateObjectName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        // 使用 UUID 生成唯一文件名
        String uuid = java.util.UUID.randomUUID().toString().replace("-", "");
        long timestamp = System.currentTimeMillis();
        
        return timestamp + "_" + uuid + extension;
    }

    /**
     * 从完整URL中提取对象名称
     *
     * @param objectNameOrUrl 对象名称或URL
     * @return 纯对象名称
     */
    private String extractObjectName(String objectNameOrUrl) {
        if (objectNameOrUrl == null) {
            return null;
        }
        
        // 如果是完整URL，提取对象名称
        if (objectNameOrUrl.startsWith("http")) {
            String bucketName = minioConfig.getBucketName();
            int index = objectNameOrUrl.indexOf("/" + bucketName + "/");
            if (index != -1) {
                return objectNameOrUrl.substring(index + bucketName.length() + 2);
            }
        }
        
        return objectNameOrUrl;
    }

    /**
     * 获取存储桶名称
     *
     * @return 存储桶名称
     */
    public String getBucketName() {
        return minioConfig.getBucketName();
    }

    /**
     * 获取MinIO服务端点
     *
     * @return 服务端点
     */
    public String getEndpoint() {
        return minioConfig.getEndpoint();
    }
}
