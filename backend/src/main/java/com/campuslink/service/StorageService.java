package com.campuslink.service;

import com.campuslink.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class StorageService {
    private static final Set<String> EXTENSIONS=Set.of("jpg","jpeg","png","gif","webp");
    @Value("${campuslink.upload-dir:./uploads}") private String uploadDir;
    public Map<String,Object> storeImage(MultipartFile file){
        if(file==null||file.isEmpty())throw new BusinessException("请选择要上传的图片");
        if(file.getSize()>10*1024*1024)throw new BusinessException("图片不能超过10MB");
        String contentType=Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        String original=Optional.ofNullable(file.getOriginalFilename()).orElse("");int dot=original.lastIndexOf('.');String ext=dot<0?"":original.substring(dot+1).toLowerCase(Locale.ROOT);
        if(!contentType.startsWith("image/")||!EXTENSIONS.contains(ext))throw new BusinessException("仅支持 jpg、png、gif、webp 图片");
        String day=LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),name=UUID.randomUUID()+"."+ext;Path root=Path.of(uploadDir).toAbsolutePath().normalize(),dir=root.resolve(day).normalize();if(!dir.startsWith(root))throw new BusinessException("存储路径无效");
        try{Files.createDirectories(dir);Path target=dir.resolve(name).normalize();try(var input=file.getInputStream()){Files.copy(input,target,StandardCopyOption.REPLACE_EXISTING);}Map<String,Object> result=new LinkedHashMap<>();result.put("url","/uploads/"+day+"/"+name);result.put("originalName",original);result.put("size",file.getSize());return result;}catch(IOException e){throw new BusinessException(500,"图片保存失败");}
    }
}
