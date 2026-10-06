package com.jada.severe.service;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import java.util.*;
import java.io.*;
import java.time.format.DateTimeFormatter;
@Service
public class ExcelService {
 private final ModuleService modules;private final AuditService audit;
 public ExcelService(ModuleService modules,AuditService audit){this.modules=modules;this.audit=audit;}
 public Map<String,Object> importFile(String module,MultipartFile file){
  if(file.isEmpty()||file.getSize()>5*1024*1024)throw bad("导入文件必须为1字节至5MB");
  String name=Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT);
  try{
   if(name.endsWith(".json")){Object parsed=audit.parse(new String(file.getBytes(),java.nio.charset.StandardCharsets.UTF_8));if(!(parsed instanceof List<?>))throw bad("JSON 必须为记录数组");return modules.importRecords(module,(List<Map<String,Object>>)parsed);}
   if(!name.endsWith(".xlsx")&&!name.endsWith(".xls"))throw bad("仅支持 xlsx/xls/json");
   var records=new ArrayList<Map<String,Object>>();
   try(Workbook book=WorkbookFactory.create(file.getInputStream())){
    Sheet sheet=book.getSheetAt(0);Row header=sheet.getRow(sheet.getFirstRowNum());if(header==null)throw bad("表头不能为空");int size=header.getLastCellNum();if(size<1||size>200)throw bad("列数必须为1至200");
    DataFormatter formatter=new DataFormatter(Locale.ROOT);List<String> keys=new ArrayList<>();Set<String> unique=new HashSet<>();
    for(int c=0;c<size;c++){String key=formatter.formatCellValue(header.getCell(c)).trim();if(key.isEmpty()||!unique.add(key))throw bad("表头不能为空或重复");keys.add(key);}
    for(int i=sheet.getFirstRowNum()+1;i<=sheet.getLastRowNum();i++){
     Row row=sheet.getRow(i);if(row==null)continue;var data=new LinkedHashMap<String,Object>();boolean nonempty=false;
     for(int c=0;c<size;c++){Cell cell=row.getCell(c);if(cell!=null&&cell.getCellType()==CellType.FORMULA)throw bad("第"+(i+1)+"行包含公式，请转换为值");String value=cell==null?"":formatter.formatCellValue(cell);if(cell!=null&&cell.getCellType()==CellType.NUMERIC&&DateUtil.isCellDateFormatted(cell)){var date=cell.getLocalDateTimeCellValue();value=date.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)?date.toLocalDate().toString():date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);}data.put(keys.get(c),value);nonempty|=!value.isBlank();}
     if(nonempty){records.add(data);if(records.size()>500)throw bad("一次最多导入500条");}
    }
   }
   return modules.importRecords(module,records);
  }catch(ResponseStatusException e){throw e;}catch(Exception e){throw bad("导入文件无法解析，请检查格式和数据");}
 }
 public byte[] export(String module,String keyword,String status){
  var result=modules.exportRecords(module,keyword,status);var records=(List<Map<String,Object>>)result.get("records");
  LinkedHashSet<String> keys=new LinkedHashSet<>(List.of("id","recordNo","status"));for(var record:records)keys.addAll(((Map<String,Object>)record.get("payload")).keySet());if(keys.size()>200)throw bad("导出列数超过200");
  try(SXSSFWorkbook book=new SXSSFWorkbook(100);ByteArrayOutputStream out=new ByteArrayOutputStream()){
   Sheet sheet=book.createSheet("业务存档");Row header=sheet.createRow(0);int col=0;for(String key:keys)header.createCell(col++).setCellValue(key);
   int index=1;for(var record:records){Row row=sheet.createRow(index++);col=0;var payload=(Map<String,Object>)record.get("payload");for(String key:keys){Object value=Set.of("id","recordNo","status").contains(key)?record.get(key):payload.get(key);String text=value==null?"":value instanceof Map<?,?>||value instanceof List<?>?audit.json(value):value.toString();if(text.length()>32767)throw bad("字段过长，不能写入Excel");row.createCell(col++).setCellValue(text);}}
   sheet.createFreezePane(0,1);book.write(out);return out.toByteArray();
  }catch(ResponseStatusException e){throw e;}catch(IOException e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Excel导出失败",e);}
 }
 private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
