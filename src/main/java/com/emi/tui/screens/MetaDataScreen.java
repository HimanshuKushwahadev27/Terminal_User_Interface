package com.emi.tui.screens;

import java.util.List;

import com.emi.tui.modules.InitilizrClient;
import com.emi.tui.modules.InitilizrMetadata;
import com.emi.tui.modules.InitilizrMetadata.SelectItem;
import com.emi.tui.modules.ProjectConfig;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.ComboBox;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.RadioBoxList;
import com.googlecode.lanterna.gui2.Separator;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.screen.TerminalScreen;

/*
 Screen 1/4 - Metadata collection

 collect the basic metadata about the project - group, artifact, name, description, java version, boot version, build tool, packaging etc

  This is the first screen in the flow, so it also fetches the metadata from initializr and passes it to the next screens via ProjectConfig object

  having 

    ["Next"] button move to next screen - dependency selection

    ["Back"] button is disabled on this screen as it's the first screen in the flow (will be used in next xcreens not this one)

    ["Quit"] button exits the TUI 
*/
public class MetaDataScreen {
  
  private final WindowBasedTextGUI gui ; 
  private final ProjectConfig config ; 
  private final InitilizrMetadata initializrClient;

  private boolean proceed = false;

  public MetaDataScreen(WindowBasedTextGUI gui , 
                        ProjectConfig config , 
                        InitilizrMetadata initializrClient
  ){
      this.gui = gui ; 
      this.config = config ;
      this.initializrClient = initializrClient;

      //override the harcoded default values in the config with the ones from the metadata, so the user can just press next if they are fine with the defaults
      
      if(config.getBootVersion().equals("3.4.5")){
        config.setBootVersion(initializrClient.defaultBootVersion());
      }

      if(config.getJavaVersion().equals("21")){
        config.setJavaVersion(initializrClient.defaultJavaVersion());
      }

      if(config.getBuildTool().equals("maven-project")){
        config.setBuildTool(initializrClient.defaultBuildTool());
      }
  }

  //renders the screen and handles the user input for this screen, then sets proceed to true if the user wants to move to the next screen, otherwise it keeps it false and the main loop will decide what to do based on that

  public boolean show(){
    BasicWindow window = new BasicWindow(" Spring Initializr — Project Metadata ");
    window.setHints(List.of(Window.Hint.CENTERED));


    // outer panel - holds everything
    Panel root = new Panel(new LinearLayout(Direction.VERTICAL)); 
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));


    //---------------- form panel ------------------------------

    Panel formPanel = new Panel(new GridLayout(2));
    GridLayout gridLayout = (GridLayout) formPanel.getLayoutManager();
    gridLayout.setHorizontalSpacing(2);
    gridLayout.setVerticalSpacing(1);

    // GroupId ----------------------

    formPanel.addComponent(label("Group ID:"));
    TextBox groupIdBox = new TextBox(new TerminalSize(35, 1)).setText(config.getGroupId());
    formPanel.addComponent(groupIdBox);

    // ArtifactId ----------------------
    formPanel.addComponent(label("Artifact ID:"));
    TextBox artifactIdBox = new TextBox(new TerminalSize(35, 1)).setText(config.getArtifactId());
    formPanel.addComponent(artifactIdBox);  

    //Name ----------------------
    formPanel.addComponent(label("Project Name:"));
    TextBox nameBox = new TextBox(new TerminalSize(35, 1)).setText(config.getName());
    formPanel.addComponent(nameBox);

    //autofill name when the artifact id is changed.
    artifactIdBox.setTextChangeListener((text, user) -> {
        if(user)nameBox.setText(text);
    });

    //Description ----------------------
    formPanel.addComponent(label("Description:"));
    TextBox descriptionBox = new TextBox(new TerminalSize(35, 3)).setText(config.getDescription());
    formPanel.addComponent(descriptionBox);

    //spaceRow ----------------

    formPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
    formPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    //Java Version ---------------------- radio broup

    formPanel.addComponent(label("Java Version:"));
    Panel javaVersionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    RadioBoxList<String> javaVersionRadio = new RadioBoxList<>();

    List<String> javaVersions = initializrClient.JavaVersionIds();
    javaVersions.forEach(javaVersionRadio::addItem);
    javaVersionRadio.setCheckedItem(config.getJavaVersion());
    javaVersionPanel.addComponent(javaVersionRadio);
    formPanel.addComponent(javaVersionPanel);

    // Build tool ------------------------------ radio group
    
    formPanel.addComponent(label("Build Tool:"));
    Panel buildToolPanel = new Panel(new LinearLayout(Direction.HORIZONTAL)); 
    RadioBoxList<String> buildToolRadio = new RadioBoxList<>();

    List<InitilizrMetadata.SelectItem> buildTools = initializrClient.buildToolsOptions();
    buildTools.forEach(bt -> buildToolRadio.addItem(bt.getName()));

    String currentBuildTool = config.getBuildTool();
    buildTools.stream()
              .filter(bt -> bt.getId().equals(currentBuildTool))
              .findFirst()
              .ifPresent(bt -> buildToolRadio.setCheckedItem(bt.getName()));

    // Packaging ------------------------------ radio group
    //using hardcoded values for packaging as initializr metadata doesn't provide options for that, and the defaults are usually the same (jar) and the only other option is war which is also well known, so we can just hardcode those values without fetching them from the metadata.
    formPanel.addComponent(label("Packaging:"));
    Panel packagingPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    RadioBoxList<String> packagingRadio = new RadioBoxList<>();
    packagingRadio.addItem("JAR");
    packagingRadio.addItem("WAR");
    packagingRadio.setCheckedItem(config.getPackaging().equalsIgnoreCase("war") ? "War" : "Jar");
    packagingPanel.addComponent(packagingRadio);
    formPanel.addComponent(packagingPanel);


    //Spring boot version ------------------------------ live meta data from initializr

    formPanel.addComponent(label("Boot Version:"));
    List<String> bootVersions = initializrClient.bootVersionIds();
    ComboBox<String> bootVersionCombo = new ComboBox<>(bootVersions);

    bootVersionCombo.setSelectedItem(config.getBootVersion());
    formPanel.addComponent(bootVersionCombo);


    root.addComponent(formPanel);
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));


    // Separator ---------------------------------

    root.addComponent(new Separator(Direction.HORIZONTAL))
        .setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    // Buttons panel ---------------------------------

    Panel ButtonsPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));

    Button nextButton = new Button(" Next -> " , () -> {

            config.setGroupId(groupIdBox.getText().trim());
            config.setArtifactId(artifactIdBox.getText().trim());
            config.setName(nameBox.getText().trim());
            config.setDescription(descriptionBox.getText().trim());
            config.setJavaVersion(javaVersionRadio.getCheckedItem());
            config.setBootVersion(bootVersionCombo.getSelectedItem());

      String selectedItem = buildToolRadio.getCheckedItem();
      String buildToolId = buildTools.stream()
                              .filter(bt -> bt.getName().equals(selectedItem))
                              .map(SelectItem::getId)
                              .findFirst()
                              .orElse("maven-project"); // default fallback
      config.setBuildTool(buildToolId);
      config.setPackaging(
        packagingRadio.getCheckedItem().equalsIgnoreCase("War") ? "war" : "jar"
      );

      //validation 
      String error = config.validate();
      if(error!=null){
        MessageDialog.showMessageDialog(
                gui,
                " Validation Error ",
                error,
                MessageDialogButton.OK
        );
         return;
      }

      proceed = true;
      window.close();
    });


    //cancel button

    Button cancelBtn = new Button("  Cancel  ", window::close);

    ButtonsPanel.addComponent(nextButton);
    ButtonsPanel.addComponent(new EmptySpace(new TerminalSize(2, 1)));
    ButtonsPanel.addComponent(cancelBtn);

    root.addComponent(ButtonsPanel);
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    window.setComponent(root);

    // set focus to Next button by default so Enter works naturally
    window.setFocusedInteractable(nextButton);

    gui.addWindowAndWait(window);

    return proceed;

  }

  // HELPER-------------------------------------------------
  private Label label(String text){
    return new Label(text).setLayoutData(
      GridLayout.createLayoutData(
        GridLayout.Alignment.END, // right align labels
        GridLayout.Alignment.CENTER, // vertically center labels
        false, // don't grab extra horizontal space
        false  // don't grab extra vertical space
      )
    );
  }
}
