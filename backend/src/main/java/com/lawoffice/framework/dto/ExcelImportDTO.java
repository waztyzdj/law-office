package com.lawoffice.framework.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ExcelImportDTO {
    
    private MultipartFile file;
    
    private RequestContext context;
}
