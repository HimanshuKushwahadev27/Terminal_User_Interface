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
import java.util.stream.Collectors;

import com.emi.tui.modules.DetectedIde;
import com.emi.tui.modules.Environment;
import com.emi.tui.util.OsUtils;
import com.emi.tui.util.PathBridge;

//scans the environment for the ide 
// windows 'where' command is used to find the path of the ide executable
// linux 'which' command is used to find the path of the ide executable
//wsl both of the above commands are used to find the path of the ide executable, since wsl can run both windows and linux commands
// if the ide is found, the path is returned, otherwise an empty list is returned

//for mac which command is used to find the path of the ide executable, since mac is unix based and does not have the where command

public class IdeDetector {

  public Map<Environment, List<DetectedIde>> detect() throws IOException{
  Map<Environment, List<DetectedIde>> result = new LinkedHashMap<>();

    if (OsUtils.isWsl()) {
        // PATH scan catches everything — Windows and WSL IDEs both
        List<DetectedIde> fromPath = scanFromPath(Environment.WSL);

        // split by environment
        List<DetectedIde> wslIdes = fromPath.stream()
                .filter(ide -> ide.getEnvironment() == Environment.WSL)
                .collect(Collectors.toList());

        List<DetectedIde> windowsIdes = fromPath.stream()
                .filter(ide -> ide.getEnvironment() == Environment.WINDOWS)
                .collect(Collectors.toList());

        // also run targeted scans as fallback
        wslIdes.addAll(scanLinux());
        windowsIdes.addAll(scanWindowsFromWsl());

        // also check VS Code server
        scanVsCodeServer(wslIdes);

        if (!wslIdes.isEmpty())
            result.put(Environment.WSL, depulicate(wslIdes));
        if (!windowsIdes.isEmpty())
            result.put(Environment.WINDOWS, depulicate(windowsIdes));

    } else if (OsUtils.isWindows()) {
        result.put(Environment.WINDOWS, scanWindows());
    } else if (OsUtils.isMac()) {
        result.put(Environment.MACOS, scanMac());
    } else {
        result.put(Environment.LINUX, scanLinux());
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

  //scan linux 
  private List<DetectedIde> scanLinux(){
    List<DetectedIde> found = new ArrayList<>();

          String[][] knownPaths = {
            // { executable path,                         display name,    open flag }
            {"/usr/local/bin/idea",                      "IntelliJ IDEA", null     },
            {"/opt/idea/bin/idea.sh",                    "IntelliJ IDEA", null     },
            {"/snap/bin/idea-ultimate",                  "IntelliJ IDEA", null     },
            {"/snap/bin/intellij-idea-community",        "IntelliJ IDEA", null     },
            {"/usr/bin/code",                            "VS Code",       null     },
            {"/snap/bin/code",                           "VS Code",       null     },
            {"/usr/bin/codium",                          "VSCodium",      null     },
            {"/opt/eclipse/eclipse",                     "Eclipse IDE",   "-data"  },
            {"/usr/bin/eclipse",                         "Eclipse IDE",   "-data"  },
            {"/snap/bin/eclipse",                        "Eclipse IDE",   "-data"  },
        };

    for(String[] entry : knownPaths){
        if (Files.exists(Path.of(entry[0]))) {
          found.add(DetectedIde.builder()
                  .name(entry[1])
                  .executablePath(entry[0])
                  .environment(OsUtils.isWsl() ? Environment.WSL : Environment.LINUX)
                  .openFlags(entry[2])
                  .build());
      }
    }

        tryWhichCommand(found, "idea",    "IntelliJ IDEA", null);
        tryWhichCommand(found, "code",    "VS Code",       null);
        tryWhichCommand(found, "codium",  "VSCodium",      null);
        tryWhichCommand(found, "eclipse", "Eclipse IDE",   "-data");


    found.addAll(scanDesktopFiles()); // if we are in wsl, we can also try to find windows ides, since wsl can run windows executables, we can use the same method as windows to find the ides, but we need to convert the windows path to wsl path

    if (OsUtils.isWsl()) {
        try{
          scanVsCodeServer(found);
        } catch(IOException e){}         // VS Code Remote Server
    }
    return depulicate(found);
  }


  //scan mac

  private List<DetectedIde> scanMac(){
    List<DetectedIde> found = new ArrayList<>();

    String[][] apps = {
            {"/Applications/IntelliJ IDEA.app/Contents/MacOS/idea", "IntelliJ IDEA", null  },
            {"/Applications/IntelliJ IDEA CE.app/Contents/MacOS/idea","IntelliJ IDEA Community",null},
            {"/Applications/Visual Studio Code.app/Contents/MacOS/Electron","VS Code",null},
            {"/Applications/VSCodium.app/Contents/MacOS/VSCodium",   "VSCodium",      null  },
            {"/Applications/Eclipse.app/Contents/MacOS/eclipse",     "Eclipse IDE",   "-data"},
        };

    for(String [] entry : apps){
      if(Files.exists(Path.of(entry[0]))){
        found.add(DetectedIde.builder()
          .name(entry[1])
          .executablePath(entry[0])
          .environment(Environment.MACOS)
          .openFlags(entry[2])
          .build());
      }
    }

        tryWhichCommand(found, "idea",    "IntelliJ IDEA", null);
        tryWhichCommand(found, "code",    "VS Code",       null);
        tryWhichCommand(found, "eclipse", "Eclipse IDE",   "-data");    


        return depulicate(found);
  }


  // Scans /usr/share/applications/ for IDE .desktop files.
  // Useful for IDEs installed via package managers that don't
  // land in standard /usr/bin or /usr/local/bin paths.

  private List<DetectedIde> scanDesktopFiles(){
     List<DetectedIde> found = new ArrayList<>();

    Path desktopDir =  Path.of("/usr/share/applications");

    Map<String, String> ideNameMap = Map.of(
      "jetbrains-idea",  "IntelliJ IDEA",
      "code",            "VS Code",
      "codium",          "VSCodium",
      "eclipse",         "Eclipse IDE"
    );

    try{
      Files.list(desktopDir)
        .filter(path -> path.toString().endsWith(".desktop"))
        .forEach(desktopfile -> {
          String fileName = desktopfile.getFileName().toString().replace(".desktop", "");
            for(Map.Entry<String,String> entry : ideNameMap.entrySet()){
              if(fileName.startsWith(entry.getKey())){
                String exec = extractExecFromDesktopFile(desktopfile); 
                if(exec!=null){
                  found.add(DetectedIde.builder()
                              .name(entry.getValue())
                              .executablePath(exec)
                              .environment(OsUtils.isWsl()
                                      ? Environment.WSL
                                      : Environment.LINUX)
                              .openFlags(null)
                              .build());
                }
              }
            }
        });

    }catch(IOException e){
  // do nothing, since this is just for ide detection and not critical
    }

    return found ;
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


  //windows scan from inside wsl 
  private List<DetectedIde> scanWindowsFromWsl() throws IOException{
    List<DetectedIde> found = new ArrayList<>();

    String winUser  = OsUtils.windowsUserProfile();

    String programFiles    = "/mnt/c/Program Files";
    String localAppData    = "/mnt/c/Users/" + winUser + "/AppData/Local";

    //intellij
    checkWslWindowsPath(found, programFiles + "/JetBrains",
            "IntelliJ IDEA", "bin/idea64.exe", null);


     //vscode
    checkWslWindowsPath(found, localAppData + "/Programs/Microsoft VS Code",
            "VS Code", "Code.exe", null);

    //vcsodium
    checkWslWindowsPath(found, localAppData + "/Programs/VSCodium",
            "VSCodium", "VSCodium.exe", null);  

            // Eclipse
    checkWslWindowsPath(found,
            programFiles + "/Eclipse Foundation",
            "Eclipse IDE", "eclipse.exe", "-data");

    // also try Program Files (x86)
    checkWslWindowsPath(found,
            "/mnt/c/Program Files (x86)/Eclipse Foundation",
            "Eclipse IDE", "eclipse.exe", "-data");

    
    //convert /mnt/c back to windows path 
    //as we will launce via cmd whihc will need c:/... paths
        List<DetectedIde> windowsFormatted = new ArrayList<>();
        for (DetectedIde ide : found) {
          String winPath = PathBridge
                  .wslToWindowsPath(ide.getExecutablePath());
          windowsFormatted.add(DetectedIde.builder()
                  .name(ide.getName())
                  .executablePath(winPath)
                  .environment(Environment.WINDOWS)
                  .openFlags(ide.getOpenFlags())
                  .build());
        }
 
        return depulicate(windowsFormatted);
    
  }

  //scans the directory in the currnt path for the known IDE 
  //works regardless of drive letter

  private List<DetectedIde> scanFromPath(Environment env){
    List<DetectedIde> found = new ArrayList<>();

        // get PATH via bash so we see the full shell PATH
    // not the bare environment ProcessBuilder would normally see
    List<String> output = runCommand(
            "bash", "-c", "echo $PATH");

    if (output.isEmpty()) return found;

    String pathVar = output.get(0);
    String[] pathDirs = pathVar.split(":");

    // known IDE executable names to look for in each PATH dir
    // format - { executableName, displayName, openFlag }
    String[][] ideExecutables = {
        {"idea",          "IntelliJ IDEA",   null   },
        {"idea.sh",       "IntelliJ IDEA",   null   },
        {"idea64.exe",    "IntelliJ IDEA",   null   },
        {"code",          "VS Code",         null   },
        {"Code.exe",      "VS Code",         null   },
        {"codium",        "VSCodium",        null   },
        {"eclipse",       "Eclipse IDE",     "-data"},
        {"eclipse.exe",   "Eclipse IDE",     "-data"},
    };

    for (String dir : pathDirs) {
        dir = dir.trim().replace("'", ""); // strip stray quotes like the ' in your PATH
        if (dir.isBlank()) continue;

        for (String[] ide : ideExecutables) {
            Path exePath = Path.of(dir, ide[0]);
            if (Files.exists(exePath)
                    && Files.isExecutable(exePath)
                    && notAlreadyFound(found, exePath.toString())) {

                // determine environment from path
                Environment ideEnv = exePath.toString().startsWith("/mnt/")
                        ? Environment.WINDOWS  // it's a Windows binary via WSL mount
                        : env;                 // native Linux/WSL binary

                found.add(DetectedIde.builder()
                        .name(ide[1])
                        .executablePath(exePath.toString())
                        .environment(ideEnv)
                        .openFlags(ide[2])
                        .build());
            }
        }
    }

    return found;

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


  private void checkWslWindowsPath(List<DetectedIde> found,
                                  String baseDir,
                                  String ideName,
                                  String exeName,
                                  String openFlag) {
    // reuse the same logic — paths are still just paths
       checkWindowsPath(found, baseDir, ideName, exeName, openFlag);
  }

  private void scanVsCodeServer(List<DetectedIde> found) throws IOException {
    Path vscodeServer = Path.of(
            System.getProperty("user.home"), ".vscode-server", "bin");

    if (!Files.exists(vscodeServer)) return;

    // each subdirectory is a commit hash — VS Code updates create new ones
    // e.g. someID
    Files.list(vscodeServer)
         .filter(Files::isDirectory)
         .forEach(hashDir -> {
             Path cliPath = hashDir
                     .resolve("bin")
                     .resolve("remote-cli")
                     .resolve("code");

             if (Files.exists(cliPath)) {
                 found.add(DetectedIde.builder()
                         .name("VS Code (WSL Server)")
                         .executablePath(cliPath.toString())
                         .environment(Environment.WSL)
                         .openFlags(null)
                         .build());
             }
         });
  }

  //HELPER COMMAND RUNNERS 

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


  private void tryWhichCommand(List<DetectedIde> found, String command, String ideName, String openFlag){
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

  private String extractExecFromDesktopFile(Path desktopFile){
    try{
      for(String line : Files.readAllLines(desktopFile)){
        if(line.startsWith("Exec=")){
          return line.substring(5).split(" ")[0].trim(); // get the part after 'Exec=' and before any space, since the Exec line can contain flags after the executable path
        }
      }
    }catch(IOException e){}
      return null ;
  } 

  //check windows path for the ide, since windows does not have a standard location for ides, we need to check multiple locations and also check the registry for installed programs, this method checks a specific directory for the ide executable, if the executable is found, it is added to the list of found ides, if the directory does not exist, it is ignored, since this is just for ide detection and not critical    

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
