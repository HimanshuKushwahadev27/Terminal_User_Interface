package com.emi.tui.screens;

import java.util.ArrayList;
import java.util.List;

import com.emi.tui.modules.InitilizrMetadata;
import com.emi.tui.modules.ProjectConfig;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;

// multi-select dependency picker 

/*
Dependencies grouped by category (build, test, runtime, etc)
Searchable list with checkboxes
back button returns to metadatascreen
selection preserved in case user goes back and forth between screens
*/
public class DependencyScreen {
  
  private final WindowBasedTextGUI textGUI;
  private final ProjectConfig projectConfig;
  private final InitilizrMetadata metadata;


  private final List<DependencyCheckBox> dependencyCheckboxes = new ArrayList<>();

  private boolean proceed =false;
  private boolean back = false;

  public DependencyScreen(WindowBasedTextGUI textGUI, ProjectConfig projectConfig, InitilizrMetadata metadata) {
    this.textGUI = textGUI;
    this.projectConfig = projectConfig;
    this.metadata = metadata;
  }
}
