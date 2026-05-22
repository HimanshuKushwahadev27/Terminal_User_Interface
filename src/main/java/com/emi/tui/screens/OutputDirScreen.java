package com.emi.tui.screens;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

import com.emi.tui.modules.Environment;
import com.emi.tui.modules.ProjectConfig;
import com.emi.tui.util.OsUtils;
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
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;

import lombok.extern.slf4j.Slf4j;

//selection of output directory where the project will be stored
@Slf4j
public class OutputDirScreen {
  
  private final WindowBasedTextGUI gui;
  private final ProjectConfig config;

  private boolean proceed = false;
  private boolean back = false;

  public OutputDirScreen(WindowBasedTextGUI gui, ProjectConfig config) {
    this.gui = gui;
    this.config = config;
  }

  public boolean show(){
    BasicWindow window = new BasicWindow(" Spring Initializr — Output Directory ");
    window.setHints(List.of(BasicWindow.Hint.CENTERED));

    Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    //instruction label 
    root.addComponent(new Label(" Where do u wanna save ur project"));
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    //path input field with label
    Panel pathRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
    pathRow.addComponent(new Label(" Save to: "));

    TextBox pathBox = new TextBox(new TerminalSize(50, 1))
      .setText(config.getOutputDirectory());
    pathRow.addComponent(pathBox);

    root.addComponent(pathRow);
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    //radio buttons shown only when the system have window and the wsl
    RadioBoxList<String> envRadio = null;

    if(OsUtils.isWsl()){
      root.addComponent(new Label(" Save to environment: "));
      root.addComponent(new EmptySpace(new TerminalSize(0, 0)));

      envRadio = new RadioBoxList<>();
      envRadio.addItem("WSL (Linux)  — e.g. /home/Username/dir");
      envRadio.addItem("Windows      — e.g. C:\\Users\\Username\\dir");

      envRadio.setCheckedItemIndex(0);

      //if the environment switches
      envRadio.addListener((selectedIndex, previousIndex) -> {
        if (selectedIndex == previousIndex) return;
        if (selectedIndex == 0) {
            // switched to WSL
            pathBox.setText(System.getProperty("user.home")
                    + "/projects");
        } else {
            // switched to Windows 
            String winUser = "";
            try {
              winUser = OsUtils.windowsUserProfile();
            } catch (IOException e) {
              log.info("Failed to detect windows user profile, defaulting to system user name , from dirSreen", e);
            }
            pathBox.setText("C:\\Users\\" + winUser + "\\Projects");
        }
      });

      root.addComponent(envRadio);
      root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
    }
    //preview label 

    root.addComponent(new Label(" Project will be created at: "));
    Label previewLabel = new Label(" " + resolveProjectPath(pathBox.getText()));
    root.addComponent(previewLabel);

    pathBox.setTextChangeListener((text, userInput) -> {
      if(!userInput) return;
      previewLabel.setText(" " + resolveProjectPath(text));
    });

    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
    root.addComponent(new Separator(Direction.HORIZONTAL)
            .setLayoutData(LinearLayout.createLayoutData(
                    LinearLayout.Alignment.Fill)));
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    //buttons
    Panel buttonRow = new Panel(new LinearLayout(Direction.HORIZONTAL));

    final RadioBoxList<String> finalEnvRadio1 = envRadio;

    Button backButton = new Button(" Back ", () -> {
      back = true;
      window.close();
    });

    Button nextButton = new Button (" Next -> ", () -> {
      String path  = pathBox.getText().trim();

      //validate pathway
      String error = validatePath(path);
      if(error!=null){
        MessageDialog.showMessageDialog(
              gui,
              " Invalid Path ",
              error,
              MessageDialogButton.OK
        );
        return; 
      }

      //save the path to config
      config.setOutputDirectory(path);

      //resolve environment if the radio button is shown

      if(OsUtils.isWsl() && finalEnvRadio1 != null){

        config.setProjectEnvironment(
                finalEnvRadio1.getCheckedItemIndex() == 0 ?
                        Environment.WSL :
                        Environment.WINDOWS

        );
      } else {

        config.setProjectEnvironment(OsUtils.getCurrentEnvironment());

      }

      proceed = true;
      window.close();
    });

    Button cancelBtn = new Button("  Cancel  ", window::close);

    buttonRow.addComponent(backButton);
    buttonRow.addComponent(new EmptySpace(new TerminalSize(2, 1)));
    buttonRow.addComponent(nextButton);
    buttonRow.addComponent(new EmptySpace(new TerminalSize(2, 1)));
    buttonRow.addComponent(cancelBtn);

    root.addComponent(buttonRow);
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    window.setComponent(root);
    window.setFocusedInteractable(pathBox);

    gui.addWindowAndWait(window);

    return proceed;
  }

  public boolean isBack() {
    return back;
  }

  //helpers 

  private String resolveProjectPath(String outputDir) {
    if (outputDir == null || outputDir.isBlank()) {
        return "(enter a directory above)";
    }
    // detect separator from the path itself
    String sep = outputDir.contains("\\") ? "\\" : "/";
    String base = outputDir.endsWith(sep)
            ? outputDir
            : outputDir + sep;
    return base + config.getArtifactId();
  }

  //validating the typed path 

  //doesnt require the dir to already exist, just checking if the path is valid for the current os and environment
  //will be created in generating screen
  private String validatePath(String path) {
      if (path == null || path.isBlank()) {
          return "Output directory cannot be empty.";
      }

      // check it's a valid path syntax 
      try {
          // for Windows paths starting with 
          // from WSL — just check it looks reasonable
          if (path.matches("^[A-Za-z]:\\\\.*")) {
              // accept it
              return null;
          }
          Path p = Path.of(path);
          if (!p.isAbsolute()) {
              return "Path must be absolute.\n"
                    + "Examples:\n"
                    + "  /home/himanshu/projects\n"
                    + "  C:\\Users\\Himanshu\\Projects";
          }
      } catch (InvalidPathException e) {
          return "Invalid path: " + e.getReason();
      }

      // if directory exists, check it's actually a directory not a file
      try {
          Path p = Path.of(path);
          if (Files.exists(p) && !Files.isDirectory(p)) {
              return "Path exists but is a file, not a directory:\n" + path;
          }
      } catch (InvalidPathException ignored) {
          // already handled above
      }

      return null; // valid
  }
}
