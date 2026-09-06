package com.campuslink.service;

import com.campuslink.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service public class StorageService {
 @Value("${campuslink.upload-dir:./uploads}") private String uploadDir;
 public Map<String,Object> storeImage(MultipartFile file){return store(file,false);}
 public Map<String,Object> storeProof(MultipartFile file){return store(file,true);}
 public void requireImageReference(String url){requireReference(url,Set.of("jpg","jpeg","png"),"头像");}
 public void requireProofReference(String url){requireReference(url,Set.of("jpg","jpeg","png","pdf"),"完成凭证");}
 private void requireReference(String url,Set<String> allowed,String label){
  if(url==null||url.isBlank())return;
  if(!url.matches("^/uploads/[0-9]{8}/[A-Za-z0-9-]+\\.(?i:jpg|jpeg|png|pdf)$"))throw new BusinessException(label+"地址无效");
  String ext=url.substring(url.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);if(!allowed.contains(ext))throw new BusinessException(label+"格式无效");
  Path root=Path.of(uploadDir).toAbsolutePath().normalize(),file=root.resolve(url.substring("/uploads/".length())).normalize();
  if(!file.startsWith(root)||!Files.isRegularFile(file))throw new BusinessException(label+"不存在或已失效，请重新上传");
 }
 private Map<String,Object> store(MultipartFile file,boolean proof){
  long max=(proof?10L:5L)*1024*1024;
  if(file==null||file.isEmpty())throw new BusinessException("请选择文件");
  if(file.getSize()>max)throw new BusinessException(proof?"凭证不能超过10MB":"头像不能超过5MB");
  String original=Optional.ofNullable(file.getOriginalFilename()).orElse("");int dot=original.lastIndexOf('.');String ext=dot<0?"":original.substring(dot+1).toLowerCase(Locale.ROOT);
  if(!(proof?Set.of("jpg","jpeg","png","pdf"):Set.of("jpg","jpeg","png")).contains(ext))throw new BusinessException(proof?"仅支持JPG、PNG、PDF凭证":"头像仅支持JPG、PNG");
  try{
   byte[] bytes=file.getBytes();boolean pdf=ext.equals("pdf");
   if(pdf){if(bytes.length<8||!new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new BusinessException("PDF内容无效");}
   else{
    boolean png=bytes.length>=8&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71;
    boolean jpeg=bytes.length>=3&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255;
    if((ext.equals("png")&&!png)||(!ext.equals("png")&&!jpeg))throw new BusinessException("图片内容与扩展名不一致");
    try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new BusinessException("图片内容无效");var reader=readers.next();try{reader.setInput(input);if((long)reader.getWidth(0)*reader.getHeight(0)>25000000)throw new BusinessException("图片像素过大");if(reader.read(0)==null)throw new BusinessException("图片内容无效");}finally{reader.dispose();}}
   }
   String day=LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),name=UUID.randomUUID()+"."+ext;
   Path root=Path.of(uploadDir).toAbsolutePath().normalize(),dir=root.resolve(day).normalize();if(!dir.startsWith(root))throw new BusinessException("存储路径无效");Files.createDirectories(dir);Files.write(dir.resolve(name),bytes,StandardOpenOption.CREATE_NEW);
   return Map.of("url","/uploads/"+day+"/"+name,"originalName",original,"size",file.getSize());
  }catch(IOException e){throw new BusinessException(400,"文件内容无效或保存失败");}
 }
}
