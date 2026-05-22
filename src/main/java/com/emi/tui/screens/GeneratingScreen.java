package com.emi.tui.screens;

import java.io.IOException;
import java.util.List;

import com.emi.tui.modules.InitilizrClient;
import com.emi.tui.modules.ProjectConfig;
import com.emi.tui.service.ZipExtractor;
import com.emi.tui.util.OsUtils;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Separator;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;

//project generating screen

//shows the config made by the user
//background thread runs to generate the project and shows the progress

public class GeneratingScreen {
  
  private final WindowBasedTextGUI gui;
  private final ProjectConfig config;
  private final InitilizrClient initilizrClient;
  private final ZipExtractor zipExtractor;

  private  boolean proceed = false;
  private  boolean back = false;

  public GeneratingScreen(
    WindowBasedTextGUI gui, 
    ProjectConfig config, 
    InitilizrClient initilizrClient, 
    ZipExtractor zipExtractor) {
      this.gui = gui;
      this.config = config;
      this.initilizrClient = initilizrClient;
      this.zipExtractor = zipExtractor;
  }

  public boolean show(){
    BasicWindow window = new BasicWindow(" Spring Initializr — Generating Project ");
    window.setHints(List.of(BasicWindow.Hint.CENTERED));

    Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    //config summary 

    root.addComponent(summaryRow(" Project", config.getArtifactId()));
    root.addComponent(summaryRow("  Group  ", config.getGroupId()));
    root.addComponent(summaryRow("  Java   ", config.getJavaVersion()));
    root.addComponent(summaryRow("  Build  ", config.getBuildTool()));
    root.addComponent(summaryRow("  Deps   ",
            config.getDependencies().isEmpty()
                    ? "(none)"
                    : config.dependenciesAsStrings()));
    root.addComponent(summaryRow("  Output ",
            config.resolvedProjectPath()));

    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
    root.addComponent(new Separator(Direction.HORIZONTAL)
            .setLayoutData(LinearLayout.createLayoutData(
                    LinearLayout.Alignment.Fill)));
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    //step labels
    Label stepCall    = stepLabel("Calling start.spring.io...");
    Label stepDown    = stepLabel("Downloading zip...");
    Label stepExtract = stepLabel("Extracting project...");
    Label stepDone    = stepLabel("Done!");

    root.addComponent(stepCall);
    root.addComponent(stepDown);
    root.addComponent(stepExtract);
    root.addComponent(stepDone);

    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    //error label
    Label errorLabel = new Label("");
    root.addComponent(errorLabel);

    //back button panel
    Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    root.addComponent(buttonPanel);
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    window.setComponent(root);

    //background thread to generate the project
    Thread generationThread = new Thread(() -> {
      try {
          // Step 1 — call Initializr
          gui.getGUIThread().invokeLater(() ->
                  stepCall.setText(" ⟳ Calling start.spring.io..."));

          byte[] zipBytes = initilizrClient.downloadProjectZip(config);

          gui.getGUIThread().invokeLater(() ->
                  stepCall.setText(" ✓ Called start.spring.io"));

          // Step 2 — download complete (downloadZip is synchronous,
          // so by the time we reach here the zip is already in memory)
          gui.getGUIThread().invokeLater(() ->
                  stepDown.setText(" ✓ Downloaded zip ("
                          + formatSize(zipBytes.length) + ")"));

          // Step 3 — extract
          gui.getGUIThread().invokeLater(() ->
                  stepExtract.setText(" ⟳ Extracting project..."));

          String projectPath = resolveOutputPath();

          // use ZipExtractor with progress callback
          // updates the step label with each file extracted
          zipExtractor.extractZip(zipBytes, projectPath,
                  (fileName, current, total) ->
                          gui.getGUIThread().invokeLater(() ->
                                  stepExtract.setText(
                                          "  Extracting... " + current
                                          + "/" + total
                                          + " (" + fileName + ")")));

          gui.getGUIThread().invokeLater(() ->
                  stepExtract.setText(" !! Extracted to " + projectPath));

          // Step 4 — done
          gui.getGUIThread().invokeLater(() -> {
              stepDone.setText(" !! Project ready!");
              proceed = true;
          });

          // short pause so user can read the success state
          Thread.sleep(1200);

          gui.getGUIThread().invokeLater(window::close);

      } catch (IOException e) {
          gui.getGUIThread().invokeLater(() -> {
              stepCall.setText(
                      stepCall.getText().contains("⟳")
                              ? " ✗ " + stepCall.getText().substring(4)
                              : stepCall.getText());
              errorLabel.setText(
                      " Error: " + e.getMessage() + "\n"
                      + " Check your internet connection and try again.");

              // show Back button so user can correct config
              Button backBtn = new Button("  ← Back  ", () -> {
                  back = true;
                  window.close();
              });
              buttonPanel.addComponent(backBtn);
              window.setFocusedInteractable(backBtn);
          });

      } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
      }
    });
    generationThread.setDaemon(true);
    generationThread.start();

    gui.addWindowAndWait(window);

    return proceed;
  }

  public boolean isGoBack() {
    return back;
  }

  // HELPER COMPONENTS -----------------------------------------------------------------------

  //path resolution 
  private String resolveOutputPath(){
    String path = config.resolvedProjectPath();

    // if running in WSL and output dir is a Windows path (C:\...)
    // convert it to the WSL mount path so Java can write to it
    if (OsUtils.isWsl() && path.matches("^[A-Za-z]:\\\\.*")) {
        path = com.emi.tui.util.PathBridge.windowsToWslPath(path);
    }
    return path;
  }

  //creates summary row for the config summary panel
  private Panel summaryRow(String label, String value) {
      Panel row = new Panel(new LinearLayout(Direction.HORIZONTAL));
      row.addComponent(new Label(label + " : "));
      row.addComponent(new Label(value));
      return row;
  }

  /** Creates a step status label with a pending bullet. */
  private Label stepLabel(String text) {
      return new Label("   ○ " + text);
  }

  /** Formats byte count as KB or MB. */
  private String formatSize(long bytes) {
      if (bytes < 1024 * 1024) {
          return (bytes / 1024) + " KB";
      }
      return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
  }

  
}
