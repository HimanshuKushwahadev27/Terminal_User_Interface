package com.emi.tui.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.emi.tui.modules.DetectedIde;
import com.emi.tui.modules.Environment;
import com.emi.tui.util.OsUtils;

//scans the environment for the ide 
// windows 'where' command is used to find the path of the ide executable
// linux 'which' command is used to find the path of the ide executable
//wsl both of the above commands are used to find the path of the ide executable, since wsl can run both windows and linux commands
// if the ide is found, the path is returned, otherwise an empty list is returned

//for mac which command is used to find the path of the ide executable, since mac is unix based and does not have the where command

public class IdeDetector {

  public Map<Environment, List<DetectedIde>> detect(){
    Map<Environment, List<DetectedIde>> result = new LinkedHashMap<>();

    if(OsUtils.isWindows()){

    }

  }

  private List<DetectedIde> scanWindows(){

    List<DetectedIde> detectedIdes = new ArrayList<>();

    //registry

    detectedIdes.addAll(scanWindowsRegistry());

    String programFiles = System.getenv("ProgramFiles");
    String programFilesX86 = System.getenv("ProgramFiles(x86)");
    String localData = System.getenv("LOCALAPPDATA");
    String appData = System.getenv("APPDATA");

    if(programFiles!=null){
      checkWindowsPath
    }
  }


  public static List<String> runCommand(String... command){
    
    List<String> lines = new ArrayList<>();

    try{
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      try(BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream()))){
            String line;
            while((line = reader.readLine()) != null){
              lines.add(line);
            }
          }
          process.waitFor();
        } catch (Exception e) {
          // return empty list if any error occurs, since this is just for ide detection and not critical;
        }
  
    return lines;
  }
}
