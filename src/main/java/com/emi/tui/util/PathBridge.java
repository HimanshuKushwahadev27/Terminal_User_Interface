package com.emi.tui.util;

import com.emi.tui.modules.Environment;

import lombok.extern.slf4j.Slf4j;

//converts the windows path to wsl path and vice versa, also can be used to convert between different os paths
@Slf4j
public class PathBridge {
  
////////////
  public static String windowsToWslPath(String windowsPath){
    if(windowsPath==null || windowsPath.isBlank()){
      throw new IllegalArgumentException("Path cannot be null or blank");
    }

    String normalizedPath = windowsPath.replace("\\", "/");

    if(normalizedPath.length() < 3 || normalizedPath.charAt(1) != ':' || normalizedPath.charAt(2) != '/'){
      log.error("Invalid windows path format: {}", windowsPath);
      throw new IllegalArgumentException("Invalid windows path format" + windowsPath);
    }

    char driveLetter = Character.toLowerCase(normalizedPath.charAt(0));
    String rest = normalizedPath.substring(2);

    return "/mnt/" + driveLetter + rest;
  }

  ///////////

  public static String wslToWindowsPath(String wslPath){
    if(wslPath==null || wslPath.isBlank()){
      throw new IllegalArgumentException("Path cannot be null or blank");
    }


    if(!wslPath.startsWith("/mnt/") || wslPath.length() < 7){
      log.error("Invalid wsl path format: {}", wslPath);
      throw new IllegalArgumentException("Invalid wsl path format" + wslPath);
    }

    char driveLetter = Character.toUpperCase(wslPath.charAt(5));
    String rest = wslPath.substring(6);

    return driveLetter + ":" + rest.replace("/", "\\");
  }


  ///////
  public static String convertPath(
   String path,
   Environment projectEnv,
   Environment ideEnv){
    
    if(projectEnv == ideEnv){
      return path;
    }

    return switch(projectEnv){
      case WSL, LINUX -> wslToWindowsPath(path);
      case WINDOWS -> windowsToWslPath(path);
      default -> path;
    };
  }
        
  

}
