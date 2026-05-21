package com.emi.tui.screens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import com.emi.tui.modules.InitilizrMetadata;
import com.emi.tui.modules.InitilizrMetadata.DependencyCategory;
import com.emi.tui.modules.InitilizrMetadata.DependencyItem;
import com.emi.tui.modules.ProjectConfig;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBox;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Separator;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;

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


  private final Map<String, CheckBox> dependencyCheckboxes = new LinkedHashMap<>();

  private boolean proceed =false;
  private boolean back = false;

  public DependencyScreen(WindowBasedTextGUI textGUI, ProjectConfig projectConfig, InitilizrMetadata metadata) {
    this.textGUI = textGUI;
    this.projectConfig = projectConfig;
    this.metadata = metadata;
  }


  public boolean show(){
    BasicWindow window = new BasicWindow(" Spring Initializr — Dependencies ");
    window.setHints(List.of(Window.Hint.CENTERED, Window.Hint.FULL_SCREEN));

    Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    Panel topBar = new Panel(new GridLayout(4));
    topBar.addComponent(new Label("Search: "));

    TextBox searchBox = new TextBox(new TerminalSize(25, 1));
    topBar.addComponent(searchBox);

    topBar.addComponent(new Label(" Selected: ")); 

    Label selectedLabel = new Label(formatSelected());
    topBar.addComponent(selectedLabel);

    root.addComponent(topBar);
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
    root.addComponent(new Separator(Direction.HORIZONTAL))
        .setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

    // two panel body 
    Panel body = new Panel(new LinearLayout(Direction.HORIZONTAL));

    //RIGHT
    Panel rightPanel = new Panel(new LinearLayout(Direction.VERTICAL));
    Panel dependencyPanel   = new Panel(new LinearLayout(Direction.VERTICAL));
    rightPanel.addComponent(dependencyPanel);


    List<DependencyCategory> categories = metadata.dependencyCategories();

    //LEFT
    ActionListBox categoryList = new ActionListBox(new TerminalSize(20, 20)) {
        @Override
        public synchronized Interactable.Result handleKeyStroke(KeyStroke keyStroke) {
            int oldIndex = getSelectedIndex();
            Interactable.Result result = super.handleKeyStroke(keyStroke);
            int newIndex = getSelectedIndex();
            if (oldIndex != newIndex && newIndex >= 0 && newIndex < categories.size()) {
                String query = searchBox.getText().trim().toLowerCase();
                buildDependencyPanel(dependencyPanel, categories.get(newIndex),
                                     query.isEmpty() ? null : query, selectedLabel);
            }
            return result;
        }
        
        @Override
        public ActionListBox setSelectedIndex(int index) {
            int oldIndex = getSelectedIndex();
            super.setSelectedIndex(index);
            if (oldIndex != index && index >= 0 && index < categories.size()) {
                String query = searchBox.getText().trim().toLowerCase();
                buildDependencyPanel(dependencyPanel, categories.get(index),
                                     query.isEmpty() ? null : query, selectedLabel);
            }
            return this;
        }
    };


    //populate category list
    for(DependencyCategory category : categories){
      categoryList.addItem(category.getName(), () -> {});
    }

    //default view first category
    if(!categories.isEmpty()){
      buildDependencyPanel(dependencyPanel, categories.get(0), null , selectedLabel);
    }
    
    //wiresearch on every keystroke
    searchBox.setTextChangeListener((query, userInput) -> {
      if (!userInput) return;
      int selectedIndex = categoryList.getSelectedIndex();
      if (selectedIndex < 0 || selectedIndex >= categories.size()) return;
      DependencyCategory current = categories.get(selectedIndex);
      buildDependencyPanel(dependencyPanel, current,
                    query.trim().toLowerCase().isEmpty()
                            ? null : query.trim().toLowerCase(),
                    selectedLabel);
    });

    //wrap left
    Panel leftWrapper = new Panel(new LinearLayout(Direction.VERTICAL));
    leftWrapper.addComponent(new Label("── Categories ──────"));
    leftWrapper.addComponent(categoryList);

    //wrap right
    Panel rightWrapper = new Panel(new LinearLayout(Direction.VERTICAL));
    rightWrapper.addComponent(new Label("── Dependencies ─────"));
    rightWrapper.addComponent(rightPanel);

    body.addComponent(leftWrapper);
    body.addComponent(new EmptySpace(new TerminalSize(2, 1)));
    body.addComponent(rightWrapper);
    
    root.addComponent(body);
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));
    root.addComponent(new Separator(Direction.HORIZONTAL))
        .setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill)); 
    root.addComponent(new EmptySpace(new TerminalSize(0, 1)));


    //buttons
    Panel buttonBar = new Panel(new LinearLayout(Direction.HORIZONTAL));

    Button backButton = new Button("Back", () -> {
      back = true;
      window.close();
    });

    Button nextButton = new Button (" Next -> ", () -> {
      List<String> selected = dependencyCheckboxes.entrySet().stream()
                              .filter(e -> e.getValue().isChecked())
                              .map(Map.Entry::getKey)
                              .toList();

      if(selected.isEmpty()){
        MessageDialogButton result = MessageDialog.showMessageDialog(textGUI, "No Dependencies",  "No dependencies selected.\nContinue anyway?", MessageDialogButton.Yes, MessageDialogButton.No);
        if (result != MessageDialogButton.Yes) return;
      }

      projectConfig.setDependencies(new ArrayList<>(selected));
      proceed = true;
      window.close();
    });

      Button cancelBtn = new Button("  Cancel  ", window::close);

      buttonBar.addComponent(backButton);
      buttonBar.addComponent(new EmptySpace(new TerminalSize(2, 1)));
      buttonBar.addComponent(nextButton);
      buttonBar.addComponent(new EmptySpace(new TerminalSize(2, 1)));
      buttonBar.addComponent(cancelBtn);

      root.addComponent(buttonBar);
      root.addComponent(new EmptySpace(new TerminalSize(0, 1)));

      window.setComponent(root);
      window.setFocusedInteractable(categoryList);

      textGUI.addWindowAndWait(window);

      return proceed;
  }

    public boolean isGoBack() {
        return back;
    }

  //roght panel builder 
   // called intially on category selection and then on search query change
  private void buildDependencyPanel(Panel dependencyPanel,
                                    DependencyCategory category,
                                    String query,
                                    Label selectedLabel){
    dependencyPanel.removeAllComponents();

    List<DependencyItem> items = filterItems(category.getValues(), query);

    if(items.isEmpty()){
      dependencyPanel.addComponent(new Label(
              "  No matches in " + category.getName()));
      return;
    }

    for(DependencyItem item : items){

      //name and checkbox
      Panel nameBox = new Panel(new LinearLayout(Direction.HORIZONTAL));

      //get or Create checkbox for this dependency
        CheckBox checkBox = dependencyCheckboxes.computeIfAbsent(item.getId(),
                id -> {
                  CheckBox cb = new CheckBox();
                  cb.setChecked(projectConfig.getDependencies().contains(id));
                  return cb;
                }); 
        
        checkBox.addListener(checked -> {
          selectedLabel.setText(buildSelectedText());
        });

        nameBox.addComponent(checkBox);
        nameBox.addComponent(new Label(" " + item.getName()));
        dependencyPanel.addComponent(nameBox);


        //row 2 description if exists

        if (item.getDescription() != null && !item.getDescription().isBlank()) {
          String desc = item.getDescription();
          if (desc.length() > 55) desc = desc.substring(0, 52) + "...";
          dependencyPanel.addComponent(new Label("     " + desc));
        }
 
        dependencyPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
    }
 }

  //helpers

  private List<DependencyItem> filterItems(List<DependencyItem> items,
                                              String query) {
    if (query == null || query.isBlank()) return items;
    return items.stream()
            .filter(item -> {
                String name = item.getName() != null
                        ? item.getName().toLowerCase() : "";
                String desc = item.getDescription() != null
                        ? item.getDescription().toLowerCase() : "";
                return name.contains(query) || desc.contains(query);
            })
            .toList();
  }
 
  private String formatSelected() {
      List<String> deps = projectConfig.getDependencies();
      return deps.isEmpty() ? "(none)" : String.join(", ", deps);
  }

  private String buildSelectedText() {
      List<String> selected = dependencyCheckboxes.entrySet().stream()
              .filter(e -> e.getValue().isChecked())
              .map(Map.Entry::getKey)
              .toList();
      return selected.isEmpty() ? "(none)" : String.join(", ", selected);
  }
}
