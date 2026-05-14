package com.emi.tui.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.emi.tui.modules.Environment;
import com.emi.tui.service.IdeDetector;

public class OsUtils {
  
  //cahe it so that dont have to read file every time, since it wont change during runtime

  private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

  //cache wsl detection result since it involves file reading and wont change during runtime

  private static boolean IS_WSL = detectWsl();



  public static boolean isWindows() {
    return OS_NAME.contains("win");
  }

  public static boolean isLinux() {
    return OS_NAME.contains("nux") || OS_NAME.contains("nix");
  }

  public static boolean isMac() {
    return OS_NAME.contains("mac");
  }

  public static boolean isWsl() {
    return  IS_WSL;
  }

  //current environment detection

  public static Environment getCurrentEnvironment() {
    if(isWindows()){
      return Environment.WINDOWS;
    } else if(isMac()){
      return Environment.MACOS;
    } else if(isWsl()){
      return Environment.WSL;
    } 
    return Environment.LINUX;
  }

  public static String userHome(){
    return System.getProperty("user.home");
  }

  //windows user profile detection for wsl, if not wsl just return system user name

  public static String windowsUserProfile() throws IOException{
    if(!IS_WSL)return System.getProperty("user.name");

    String winMountPath = getWindowsMountPath();
    Path userProfile = Path.of(winMountPath + "/Users");

    if(!Files.exists(userProfile)){
      return System.getProperty("user.name");
    }

    try{
      return Files.list(userProfile)
        .filter(Files::isDirectory)
        .map(path -> path.getFileName().toString())
                         .filter(name -> !name.equalsIgnoreCase("Public")
                                 && !name.equalsIgnoreCase("Default")
                                 && !name.equalsIgnoreCase("Default User")
                                 && !name.equalsIgnoreCase("All Users"))
                    .findFirst()
                    .orElse(System.getProperty("user.name"));
    } catch (IOException e) {
      return System.getProperty("user.name");
    }
  }




  public static String getWindowsMountPath() throws IOException{

    //checking the wsl for custom mount root

    String mountRoot = "/mnt/";
    Path wslConf = Path.of("/etc/wsl.conf");

    if(Files.exists(wslConf)){
       for(String line : Files.readAllLines(wslConf)){
          if(line.trim().startsWith("root=")) {
            mountRoot = line.split("=")[1].trim();

            mountRoot = mountRoot.replaceAll("/$", ""); // remove trailing slash if exists
          }
       }
    }

    //finding actual windows drive

    String systemDrive = "c" ; // default to c if not found
    List<String> output = IdeDetector.runCommand("cmd.exe", "/c", "echo %SystemDrive%");

    if(!output.isEmpty()){
      String raw = output.get(0).trim();
      if(raw.length() >= 1){
        systemDrive = String.valueOf(raw.charAt(0)).toLowerCase();
      }
    }

    return mountRoot + "/" + systemDrive;
  }


  //wsl detections

  private static boolean detectWsl() {
    if(!OS_NAME.contains("nux") && OS_NAME.contains("nix")){ 
      return false;
    }

    Path wslPath = Path.of("/proc/version");

    if(!Files.exists(wslPath)){
      return false;
    }

    try{
      String content = Files.readString(wslPath).toLowerCase();
      return content.contains("microsoft") || content.contains("wsl");
    } catch (IOException e) {
      return false; 
    }
  }
}
