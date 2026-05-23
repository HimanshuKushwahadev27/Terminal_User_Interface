package com.emi.tui.screens;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import com.emi.tui.modules.DetectedIde;
import com.emi.tui.modules.Environment;
import com.emi.tui.modules.ProjectConfig;
import com.emi.tui.util.PathBridge;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.RadioBoxList;
import com.googlecode.lanterna.gui2.Separator;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;


//IDE selection screen
//grouped by environment (windows, linux, mac) and shows the compatible IDEs for each environment
//path conversion is handled by the PathBridge class, which converts the paths between different os formats
//if no ide is selected, the project will be generated without opening it in an ide, and the user can open it manually later  

public class IdeSelection {
  
  private final WindowBasedTextGUI gui;
  private final ProjectConfig config;
  private final Map<Environment, List<DetectedIde>> detectedIdes;

  public IdeSelection(
    WindowBasedTextGUI gui, 
    ProjectConfig config, 
    Map<Environment, List<DetectedIde>> detectedIdes) {
      this.gui = gui;
      this.config = config;
      this.detectedIdes = detectedIdes;
  }

  //show IDE selection
  //block until user selects an IDE or proceeds without selecting
  public void show(){
    BasicWindow window = new BasicWindow(" IDE Selection ");
    window.setHints(List.of(BasicWindow.Hint.CENTERED));

    Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    //project path summary
    root.addComponent(new Label(" Project generated at: "));
    root.addComponent(new Label(" " + config.resolvedProjectPath()));
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
    root.addComponent(new Separator(Direction.HORIZONTAL)
      .setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill)));
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    //IDE radio list grouped by environment

    RadioBoxList<String> ideRadio = new RadioBoxList<>();

    List<DetectedIde> ideEntries = new ArrayList<>();

    boolean anyIdesFound = false;

    for(Environment env : Environment.values()){
      List<DetectedIde> ides = detectedIdes.get(env);

      if(ides==null || ides.isEmpty()){
        continue;
      }

      anyIdesFound = true;
      root.addComponent(new Label(" " + env.displayName()));

      for(DetectedIde ide : ides){
        String displayPath = ide.getExecutablePath();

        //truncate long path for better display
        if(displayPath.length() > 45){
          displayPath = "..." + displayPath.substring(displayPath.length() - 42);
        }
        
        ideRadio.addItem(" " + ide.getName() + "  " + displayPath);
        ideEntries.add(ide);
      }
      root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
    }

    if(!anyIdesFound){
      root.addComponent(new Label(
              " No IDEs detected on this machine."));
      root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
    }

    //skip option at the bottom

    ideRadio.addItem("  Skip — I'll open the project manually");
    ideEntries.add(null);

    //default to first IDE if found any 
    ideRadio.setCheckedItemIndex(0);

    root.addComponent(ideRadio);
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
    root.addComponent(new Separator(Direction.HORIZONTAL)
      .setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill)));
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    //buttons

    Panel buttons = new Panel(new LinearLayout(Direction.HORIZONTAL));
    Button openButton = new Button(" Open ", () -> {
      int selectedIndex = ideRadio.getCheckedItemIndex();
      DetectedIde chosen = ideEntries.get(selectedIndex);

      if (chosen == null) {
          // user picked Skip
          window.close();
          return;
      }

      //resolving projects path for the choosen IDE
      String projectPath = resolvePathForIde(chosen);

       
      try {
          launchIde(chosen, projectPath);
          window.close();
      } catch (IOException e) {
          MessageDialog.showMessageDialog(
                  gui,
                  " Failed to Open IDE ",
                  "Could not launch " + chosen.getName() + ":\n"
                  + e.getMessage() + "\n\n"
                  + "Try opening the project manually at:\n"
                  + config.resolvedProjectPath(),
                  MessageDialogButton.OK
          );
      }
    });

    Button exitBtn = new Button("  Exit  ", window::close);

    buttons.addComponent(openButton);
    buttons.addComponent(new EmptySpace(new TerminalSize(2, 1)));
    buttons.addComponent(exitBtn);

    root.addComponent(buttons);
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    window.setComponent(root);
    window.setFocusedInteractable(openButton);

    gui.addWindowAndWait(window);

  }

  //IDE launching logic

  //4 posssible scenarios:
     //Case 1: Windows IDE, TUI on Windows CMD  → direct ProcessBuilder
     // Case 2: Windows IDE, TUI inside WSL      → cmd.exe /c start "" <exe> <path>
     // Case 3: WSL IDE,     TUI on Windows CMD  → wsl.exe -e <exe> <wsl-path>
     // Case 4: WSL/Linux/Mac IDE, TUI native    → direct ProcessBuilder


  private void launchIde(DetectedIde ide, String projectPath)
            throws IOException {
 
        String[] command;
 
        boolean tuiOnWindows = com.emi.tui.util.OsUtils.isWindows();
        boolean tuiInWsl     = com.emi.tui.util.OsUtils.isWsl();
        boolean ideIsWindows = ide.getEnvironment() == Environment.WINDOWS;
        boolean ideIsWsl     = ide.getEnvironment() == Environment.WSL
                            || ide.getEnvironment() == Environment.LINUX;
 
        if (ideIsWindows && tuiInWsl) {
            // Case 2: Windows IDE launched from inside WSL
            // cmd.exe /c start "" launches the process detached from WSL terminal
            // the "" is mandatory — it's the window title argument for start
            // without it, start treats the exe path as the title and fails
            command = buildCmdExeLaunch(ide, projectPath);
 
        } else if (ideIsWsl && tuiOnWindows) {
            // Case 3: WSL IDE launched from Windows CMD
            // wsl.exe -e runs a single command inside the default WSL distro
            // project path must be converted to WSL format first
            command = buildWslLaunch(ide, projectPath);
 
        } else {
            // Case 1 & 4: same environment — direct native launch
            // Works for: Windows→Windows, WSL→WSL, Linux→Linux, Mac→Mac
            command = ide.launchCommand(projectPath);
        }
 
        new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        // not waiting for process — IDE opens and stays open while TUI exits
    }
 
    
     // Builds a cmd.exe /c start launch command.
     //Used when launching a Windows IDE from inside WSL.
     // Format: cmd.exe /c start "" <exe> [openFlag] <projectPath>
    
    private String[] buildCmdExeLaunch(DetectedIde ide, String projectPath) {
        if (ide.getOpenFlags() != null) {
            return new String[]{
                    "cmd.exe", "/c", "start", "",
                    ide.getExecutablePath(),
                    ide.getOpenFlags(),
                    projectPath
            };
        }
        return new String[]{
                "cmd.exe", "/c", "start", "",
                ide.getExecutablePath(),
                projectPath
        };
    }
 
    
      // Builds a wsl.exe -e launch command.
      // Used when launching a WSL IDE from Windows CMD.
      // Format: wsl.exe -e <exe> [openFlag] <wsl-path>
    private String[] buildWslLaunch(DetectedIde ide, String projectPath) {
        // projectPath at this point is already converted by resolvePathForIde()
        // via PathBridge — it will be in WSL format if project was on Windows,
        // or already correct if project was saved in WSL
        if (ide.getOpenFlags() != null) {
            return new String[]{
                    "wsl.exe", "-e",
                    ide.getExecutablePath(),
                    ide.getOpenFlags(),
                    projectPath
            };
        }
        return new String[]{
                "wsl.exe", "-e",
                ide.getExecutablePath(),
                projectPath
        };
    }
 

  //Path resolution 
  //converts the project path to the format compatible with the chosen IDE, especially important when running in WSL and launching a Windows IDE, or vice versa

  //EX -   Project saved in WSL, IDE is Windows → wslToWindows()
    //   Project saved in Windows, IDE is WSL → windowsToWsl()

  private String resolvePathForIde(DetectedIde ide) {
      return PathBridge.convertPath(
              config.resolvedProjectPath(),
              config.getProjectEnvironment(),
              ide.getEnvironment()
      );
    }

}
