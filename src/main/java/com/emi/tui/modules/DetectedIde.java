package com.emi.tui.modules;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class DetectedIde {
  
  //name shown in TUI ui, e.g. "IntelliJ IDEA", "Visual Studio Code", "Eclipse", etc.
  private final String name;

  //path to the ide executable, e.g. "C:\Program Files\JetBrains\IntelliJ IDEA 2021.2\bin\idea64.exe" or "/usr/bin/code" or "/usr/bin/eclipse"
  private final String executablePath;

  //environment in which the ide was detected, e.g. "windows", "linux", "wsl", "mac"
  private final Environment environment;

  //flags to open the ide with, e.g. "--new-window" for vscode, "" for intellij and eclipse
  private final String openFlags;


  //returns the command to launch the ide with the given project path, e.g. ["C:\Program Files\JetBrains\IntelliJ IDEA 2021.2\bin\idea64.exe", "C:\path\to\project"] or ["/usr/bin/code", "--new-window", "/path/to/project"] or ["/usr/bin/eclipse", "/path/to/project"]
  //buiuld the full launch command for the processbuilder to launch the ide with the given project path, using the executable path and open flags

  public String[] launchCommand(String pathProject){

    if(openFlags == null || openFlags.isBlank()){
      return new String[]{executablePath, pathProject};
    } else {
      return new String[]{executablePath, openFlags, pathProject};
    }
  }

}
