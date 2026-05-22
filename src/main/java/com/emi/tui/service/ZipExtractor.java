package com.emi.tui.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

//extract zip files  in target directory after downloading from initializr, with added zipSlip support to avoid the extra parent directory in the zip file

public class ZipExtractor {
  
  //progress reporting callback interface to update the UI about the extraction progress
  @FunctionalInterface
  public interface progressCallBack{
    void onFileExtracted(String fileName, int current, int total);
  }

  //public API to extract zip file from byte array, with progress callback
  public void extractZip(byte[] zipBytes, String targetDir)throws IOException{
    extractZip(zipBytes, targetDir, null);
  }

  public void extractZip(byte[] zipBytes, String targetDir, progressCallBack callBack)throws IOException{
    
    if(zipBytes == null || zipBytes.length == 0){
      throw new IllegalArgumentException("Zip file is empty");
    }

    Path target = Path.of(targetDir);

    Files.createDirectories(target);
    int totalEntries = countZipEntries(zipBytes);
    int current = 0;

    try(ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))){
      ZipEntry entry;
      
      while((entry = zis.getNextEntry()) !=null){
        Path resolved = target.resolve(entry.getName()).normalize();

        //zipSlip
        if(!resolved.startsWith(target.normalize())){
          throw new IOException(
            "Zip slip detected — entry escapes target directory: "
            + entry.getName());
        }

        if(entry.isDirectory()){
          Files.createDirectories(resolved);
        }else{
          //parent dir exists ensure
          if(resolved.getParent() != null){
            Files.createDirectories(resolved.getParent());
          }

          Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING);

          current++;

          if (callBack != null) {
              String fileName = resolved.getFileName().toString();
              callBack.onFileExtracted(fileName, current, totalEntries);
          }
        }
        zis.closeEntry();
      }
    }
  }
  
  //helper
  //count total entries in the zip for progress reporting
  private int countZipEntries(byte[] zipBytes) {
    int count = 0;
    try(ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))){
      ZipEntry entry;

      while((entry = zis.getNextEntry()) !=null){
        if(!entry.isDirectory()){
          count++;
        }
        zis.closeEntry();
      }
    }catch(IOException e){
      // if counting fails, just return 0 — progress % will show 0
    }
    return count;
  }
}
