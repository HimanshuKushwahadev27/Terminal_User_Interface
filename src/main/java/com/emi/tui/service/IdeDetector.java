package com.emi.tui.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
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
      result.put(Environment.WINDOWS, scanWindows());
    }
    
    else if (OsUtils.isMac()){
      result.put(Environment.MACOS, scanMac());
    }

    else if(OsUtils.isLinux() || OsUtils.isWsl()){
     
      List<DetectedIde> linuxIdes = scanLinux();

      if(linuxIdes.isEmpty()){
        result.put(Environment.WSL, linuxIdes); 
      }

      if(OsUtils.isWsl()){
        List<DetectedIde> windowsIdes = scanWindowsFromWsl();;
        if(windowsIdes.isEmpty()){
          result.put(Environment.WINDOWS, windowsIdes);
        }
      }
    }

    return result;
  }

  private List<DetectedIde> scanWindows(){

    List<DetectedIde> detectedIdes = new ArrayList<>();

    //registry

    detectedIdes.addAll(scanWindowsRegistry());

    //known install paths
    String programFiles = System.getenv("ProgramFiles");
    String localData = System.getenv("LOCALAPPDATA");
    String appData = System.getenv("APPDATA");

    if(programFiles!=null){
          checkWindowsPath(detectedIdes, programFiles + "\\JetBrains",
                    "IntelliJ IDEA", "idea64.exe", null);
          checkWindowsPath(detectedIdes, programFiles + "\\Eclipse Foundation",
                    "Eclipse IDE", "eclipse.exe", "-data"); 
   }
   if (localData != null) {
      checkWindowsPath(detectedIdes,
              localData + "\\Programs\\Microsoft VS Code",
              "VS Code", "Code.exe", null);
   }
   if (appData != null) {
      // VSCodium
      checkWindowsPath(detectedIdes,
              appData + "\\Local\\Programs\\VSCodium",
              "VSCodium", "VSCodium.exe", null);
    }

    //path ('WHERE' command)

        tryWhereCommand(detectedIdes, "idea",    "IntelliJ IDEA", Environment.WINDOWS, null);
        tryWhereCommand(detectedIdes, "code",    "VS Code",       Environment.WINDOWS, null);
        tryWhereCommand(detectedIdes, "eclipse", "Eclipse IDE",   Environment.WINDOWS, "-data");

    return depulicate(detectedIdes);
  }


  // Query windows registry for installed ides

  private List<DetectedIde> scanWindowsRegistry(){
    List<DetectedIde> found = new ArrayList<>();
    
    //jetBrains installations
    String jetBrainsKey = "HKEY_LOCAL_MACHINE\\SOFTWARE\\JetBrains";
    List<String> regOutput = runCommand("reg", "query", jetBrainsKey,  "/s", "/v", "ShellPath");

    for(String line : regOutput){
      line = line.trim();
      if(line.contains("idea") && line.endsWith(".exe")){
        String exePath = extractRegValue(line);

        if(exePath!=null){
          found.add(DetectedIde.builder()
            .name("IntelliJ IDEA")
            .executablePath(exePath)
            .environment(Environment.WINDOWS)
            .openFlags(null)
            .build());
        }
      }
    }


    //vs code 

    String vscodeKey = "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows" +
            "\\CurrentVersion\\Uninstall\\{771FD6B0-FA20-440A-A002-3B3BAC16DC50}_is1";
    
    List<String> vsCodeOutput = runCommand("reg", "query", vscodeKey, "/v", "InstallLocation");


    for(String line : vsCodeOutput){
      line = line.trim();
      if(line.startsWith("InstallLocation")){
        String installPath = extractRegValue(line.trim());
        if(installPath!=null){
            found.add(DetectedIde.builder()
          .name("VS Code")
          .executablePath(installPath + "\\Code.exe")
          .environment(Environment.WINDOWS)
          .openFlags(null)
          .build());
        }
      }
    }

    return found ;
  }

  //runs the command and returns the output as a list of strings, each string is a line of the output, if any error occurs, an empty list is returned, since this is just for ide detection and not critical
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

  //HELPER COMMANDE RUNNERS 

  private void tryWhereCommand(List<DetectedIde> found, String command, String ideName, Environment env, String openFlag){
    List<String> output = runCommand("where", command);
    for(String line : output){
      line = line.trim();
      if((line.toLowerCase().endsWith(".exe") && notAlreadyFound(found, line)) && !line.isBlank()){
        found.add(DetectedIde.builder()
          .name(ideName)
          .executablePath(line)
          .environment(env)
          .openFlags(openFlag)
          .build());
      }
    }
  }


  private void tryWhichCommand(List<DetectedIde> found, String command, String ideName, Environment env, String openFlag){
    List<String> output = runCommand("which", command);
    for(String line : output){
      line = line.trim();
      if((notAlreadyFound(found, line) && !line.isBlank()) && !line.contains("not found")){
        found.add(DetectedIde.builder()
          .name(ideName)
          .executablePath(line)
          .environment(OsUtils.isWsl() ? Environment.WSL : Environment.LINUX)
          .openFlags(openFlag)
          .build());
      }
    }
  }

  //path checker for windows, since windows does not have a standard location for ides, we need to check multiple locations and also check the registry for installed programs

  // HELPER METHODS

  private void checkWindowsPath(
    List<DetectedIde> found,
    String baseDir,
    String ideName,
    String exeName,
    String openFlag
  ){

    Path base = Path.of(baseDir);

    if(!Files.exists(base))return ;

    try{
      Files.list(base)
        .filter(Files::isDirectory)
        .forEach(Subdir -> {
          Path exe = Subdir.resolve("bin").resolve(exeName);
          if(!Files.exists(exe)){
            exe = Subdir.resolve(exeName);   //trying directly in the subdir, since some ides like intellij have the exe directly in the subdir and not in the bin folder
          }

          if(Files.exists(exe) && notAlreadyFound(found, exe.toString())){
            found.add(DetectedIde.builder()
              .name(ideName)
              .executablePath(exe.toString())
              .environment(Environment.WINDOWS)
              .openFlags(openFlag)
              .build());
          }
        });
    }catch(IOException e){}
   
    //checking directory at the base dir

    Path directExe = base.resolve(exeName);
    if(Files.exists(directExe) && notAlreadyFound(found, directExe.toString())){
      found.add(DetectedIde.builder()
        .name(ideName)
        .executablePath(directExe.toString())
        .environment(Environment.WINDOWS)
        .openFlags(openFlag)
        .build());
    }
  }


  //helper method to check if the ide is already found, since some ides can be found in multiple locations, we want to avoid duplicates

  private boolean notAlreadyFound(List<DetectedIde> found, String path){
    return found.stream().noneMatch(ide -> ide.getExecutablePath().equalsIgnoreCase(path));
  }


  //remove duplicates if any 

  private List<DetectedIde> depulicate(List<DetectedIde> ides){
    Map<String, DetectedIde> uniqueMap = new LinkedHashMap<>();
    for(DetectedIde ide : ides){
      uniqueMap.putIfAbsent(ide.getExecutablePath().toLowerCase(), ide);
    }
    return new ArrayList<>(uniqueMap.values());
  }

  //extracts the value from the 'reg' command output line, the value is the last part of the line after the last '    ' (4 spaces), since the output format is 'key    type    value', we can split by 4 spaces and get the last part as the value, if the line does not contain 4 spaces, null is returned

  private String extractRegValue(String line){
    String[] parts = line.trim().split("\\s{4,}"); // split by 4 spaces
    
    if(parts.length >=3){
      return parts[parts.length-1].trim(); // the value is the last part, trim it to remove any extra spaces
    }

    if(line.contains("REG_SZ")){
      return line.substring(line.indexOf("REG_SZ") +6).trim(); // if the line contains REG_SZ, we can extract the value after it, since the output format is 'key    type    value', we can split by REG_SZ and get the last part as the value, if the line does not contain 4 spaces but contains REG_SZ, we can use this method to extract the value
    }
    return null ;
  }
}
