package com.jada.severe.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

@Service
public class FileService {
 private final JdbcTemplate db;private final AccessService access;private final AuditService audit;private final TransactionTemplate tx;
 @Value("${app.attachments.directory}")private String directory;
 public FileService(JdbcTemplate db,AccessService access,AuditService audit,PlatformTransactionManager manager){this.db=db;this.access=access;this.audit=audit;tx=new TransactionTemplate(manager);}
 public Map<String,Object> upload(String module,UUID record,MultipartFile file){
  access.require(module,"edit");access.record(module,record);if(file.isEmpty()||file.getSize()>5*1024*1024)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"附件大小必须为1字节至5MB");
  String name=Objects.toString(file.getOriginalFilename(),"attachment").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);if(name.isBlank()||name.length()>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件名无效");
  UUID id=UUID.randomUUID();Path root=Path.of(directory).toAbsolutePath().normalize(),target=root.resolve(id.toString());
  try{byte[] content=file.getBytes();String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));Files.createDirectories(root);Files.write(target,content,StandardOpenOption.CREATE_NEW);String filename=name;
   return tx.execute(status->{db.update("insert into app_attachments(id,record_id,name,storage_key,created_by,content_type,file_size,sha256) values(?,?,?,?,?,?,?,?)",id,record,filename,id.toString(),access.user(),contentType(content,filename),content.length,digest);audit.log(module,record,"uploadAttachment",null,Map.of("id",id,"name",filename,"size",content.length,"sha256",digest),null,null);return Map.of("id",id,"name",filename,"size",content.length);});
  }catch(Exception e){try{Files.deleteIfExists(target);}catch(Exception cleanup){e.addSuppressed(cleanup);}if(e instanceof ResponseStatusException ex)throw ex;throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"附件保存失败",e);}
 }
 private String contentType(byte[] bytes,String name){
  if(bytes.length>=5&&new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))return "application/pdf";
  if(bytes.length>=8&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71)return "image/png";
  if(bytes.length>=3&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255)return "image/jpeg";
  if(name.toLowerCase(Locale.ROOT).endsWith(".txt"))return "text/plain";
  return "application/octet-stream";
 }
 public Map<String,Object> metadata(UUID id,String action){var rows=db.queryForList("select a.*,r.module_id from app_attachments a join app_records r on r.id=a.record_id where a.id=? and a.deleted_at is null",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"附件不存在");var meta=rows.get(0);String module=meta.get("module_id").toString();access.require(module,action);access.record(module,(UUID)meta.get("record_id"));return meta;}
 public Resource download(UUID id){var meta=metadata(id,"attachments");String key=meta.get("storage_key").toString();if(!key.equals(id.toString()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"附件文件尚未上传");Path file=Path.of(directory).toAbsolutePath().normalize().resolve(key);if(!Files.isRegularFile(file))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"附件文件不存在");audit.log(meta.get("module_id").toString(),(UUID)meta.get("record_id"),"downloadAttachment",null,Map.of("id",id),null,null);return new FileSystemResource(file);}
 public void delete(UUID id){tx.executeWithoutResult(status->{var meta=metadata(id,"edit");db.update("update app_attachments set deleted_at=now() where id=?",id);audit.log(meta.get("module_id").toString(),(UUID)meta.get("record_id"),"deleteAttachment",Map.of("id",id),null,null,null);});/* Retain bytes for audit/backup retention; never serve deleted metadata. */}
}
