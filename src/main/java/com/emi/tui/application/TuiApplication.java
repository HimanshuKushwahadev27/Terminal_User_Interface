package com.emi.tui.application;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.emi.tui.modules.DetectedIde;
import com.emi.tui.modules.Environment;
import com.emi.tui.modules.InitilizrClient;
import com.emi.tui.modules.InitilizrMetadata;
import com.emi.tui.modules.ProjectConfig;
import com.emi.tui.screens.DependencyScreen;
import com.emi.tui.screens.GeneratingScreen;
import com.emi.tui.screens.IdeSelection;
import com.emi.tui.screens.MetaDataScreen;
import com.emi.tui.screens.OutputDirScreen;
import com.emi.tui.service.IdeDetector;
import com.emi.tui.service.ZipExtractor;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

//main orchestration of the application, initializes the modules and starts the tui
//handes the flow of the screens and the data passing between them


//responsibilities
 //  1. Initialize Lanterna terminal + screen + GUI
  // 2. Fetch Initializr metadata (boot versions, deps) at startup
 //  3. Run IDE detection in parallel with metadata fetch
 //  4. Orchestrate screen flow with back navigation support
 //  5. Clean up terminal on exit
public class TuiApplication {
  
  private final InitilizrClient initilizrClient;
  private final IdeDetector ideDetector;
  private final ZipExtractor zipExtractor;

  public TuiApplication() {
    this.initilizrClient = new InitilizrClient();
    this.ideDetector = new IdeDetector();
    this.zipExtractor = new ZipExtractor();
  }

  //called from main method, starts the application
  public void start() {
    Terminal terminal = null;
    Screen screen = null;

    try{
      //terminal setup 

      terminal  = new DefaultTerminalFactory().createTerminal();
      screen = new TerminalScreen(terminal);
      screen.startScreen();

      MultiWindowTextGUI gui = new MultiWindowTextGUI(screen,
           new DefaultWindowManager(),
           new EmptySpace(TextColor.ANSI.BLUE));
      
      //fetch metadata and detect ides in parallel

      InitilizrMetadata metadata = null;
      Map<Environment, List<DetectedIde>> detectedIdes = null;

      showLoadingScreen(gui, screen);

      try{
        metadata = initilizrClient.fetchMetadata();
      } catch (Exception e){
          showFatalError(gui,
                        "Failed to reach start.spring.io\n\n"
                        + e.getMessage() + "\n\n"
                        + "Check your internet connection and try again.");
          return;
      }

      detectedIdes = ideDetector.detect();

      gui.getWindows().forEach(Window::close); //close loading screen

      ProjectConfig config = new ProjectConfig();

      runScreenFlow(gui, config, metadata, detectedIdes);


    }catch(IOException e){
      System.err.println("Terminal initialization failed: " + e.getMessage());
    }finally{
        //cleanup
           // Always restore the terminal to its original state
            // even if something threw — without this the terminal
            // stays in raw mode and the shell becomes unusable
      try{
        if (screen   != null) screen.stopScreen();
        if (terminal != null) terminal.close();
      }catch(IOException ignored){ }
    }
  }

  //screen flow orchestration, shows the screens in order and passes the data between them
  //0 → MetaDataScreen
  //1 → DependencyScreen
  //2 → OutputDirScreen
  //3 → GeneratingScreen
  //4 → IdeSelectionScreen

  private void runScreenFlow(
          MultiWindowTextGUI gui,
          ProjectConfig config,
          InitilizrMetadata metadata,
          Map<Environment, List<DetectedIde>> detectedIdes){
    
    int screen = 0;
    
    while(screen >= 0 && screen <= 4){
      switch(screen){
        case 0 -> {
          MetaDataScreen metaScreen = new MetaDataScreen(
                  gui, config, metadata);
          boolean next = metaScreen.show();
          if (!next) {
              // Cancel on first screen = exit
              return;
          }
          screen = 1;
        }

        case 1 -> {
          DependencyScreen depScreen = new DependencyScreen(
                            gui, config, metadata);
                    boolean next = depScreen.show();
          if (depScreen.isGoBack()) {
              screen = 0;
          } else if (!next) {
              // Cancel
              return;
          } else {
              screen = 2;
          }
        }

        case 2 -> {
          OutputDirScreen outScreen = new OutputDirScreen(
                  gui, config);
          boolean next = outScreen.show();
          if (outScreen.isBack()) {
              screen = 1;
          } else if (!next) {
              return;
          } else {
              screen = 3;
          }         
        }

        case 3 -> {
          GeneratingScreen genScreen = new GeneratingScreen(
                  gui, config, initilizrClient, zipExtractor);
          boolean success = genScreen.show();
          if (genScreen.isGoBack()) {
              screen = 2; // go back to OutputDirScreen
          } else if (!success) {
              return;
          } else {
              screen = 4;
          }
        }

        case 4 -> {
            IdeSelection ideScreen = new IdeSelection(
                    gui, config, detectedIdes);
            ideScreen.show(); // no back from last screen
            return; // always exit after IDE selection
        }

        default -> { return; }
      }
    }
  }

  //loading screen 

  private void showLoadingScreen(MultiWindowTextGUI gui, Screen screen) {
        BasicWindow loadingWindow = new BasicWindow();
        loadingWindow.setHints(List.of(Window.Hint.CENTERED));
 
        Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
        panel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        panel.addComponent(new Label("  Spring TUI  "));
        panel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        panel.addComponent(new Label("  Fetching metadata from start.spring.io...  "));
        panel.addComponent(new Label("  Detecting installed IDEs...                "));
        panel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
 
        loadingWindow.setComponent(panel);
        gui.addWindow(loadingWindow);
 
        // force a render so the loading screen actually appears
        // before we block on the network call
        try {
            screen.refresh();
        } catch (IOException ignored) {}
  }

  //error dialog screen

  private void showFatalError(MultiWindowTextGUI gui, String message) {
    MessageDialog.showMessageDialog(
            gui,
            " Startup Error ",
            message,
            MessageDialogButton.OK
    );
  }
}
