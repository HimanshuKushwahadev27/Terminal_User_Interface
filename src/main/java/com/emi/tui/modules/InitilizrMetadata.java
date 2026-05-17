package com.emi.tui.modules;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/*  
  Maps the JSON response from:
  GET https://start.spring.io/metadata/client
 
  We only map the fields we actually need.
  @JsonIgnoreProperties(ignoreUnknown = true) silently drops
  everything else in the JSON so we don't need to model the full response. 
*/

// will be utilizing the nested classes as for representing each level of the JSONI response got from the initializr metadata api, for example the dependencies are grouped by type in the json response, so we will have a DependencyGroup class which will have a map of dependency type to list of dependencies, and each dependency will be represented by a Dependency class with fields like name, id, etc.


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InitilizrMetadata {
  
  private VersionGroup type;
  private VersionGroup JavaVersion;
  private VersionGroup bootVersion;
  private DependencyGroup dependencies;


  // ---- Inner classes representing the nested structure of the JSON response ----


  //represent any value that has a default as well as the list of the values 

  /*Json shape 
  
   {
      "default": "4.0.6",
      "values": [ { "id": "4.0.6", "name": "4.0.6" }, ... ]
   }

  */

   @Data
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class VersionGroup{
            // since "default" is a reserved word in Java — mapping  it via @JsonProperty
        @JsonProperty("default")
        private String defaultValue;
        private List<SelectItem> values;
   }


   /*
    the dependency block 

    JSON SHAPE 

    {
           "values": [
          {
            "name": "Web",
            "values": [
             { "id": "web",      "name": "Spring Web",      "description": "..." },
              { "id": "webflux",  "name": "Spring Reactive Web", "description": "..." }
            ]
          },
          {
            "name": "SQL",
            "values": [ ... ]
          }
        ]
      }
   */

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class DependencyGroup{
    private List<DependencyCategory> values ;
  }


  // single category of dependencies, e.g. "Web", "SQL", "Cloud", etc

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class DependencyCategory{
    private String name; // e.g. "Web"
    private List<DependencyItem> values; // list of dependencies in this category
  }


  // single dependency item, e.g. "Spring Web", "Spring Reactive Web", etc
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class DependencyItem{
    private String id; // e.g. "web"
    private String name; // e.g. "Spring Web"
    private String description; // e.g. "Build web, including RESTful, applications using Spring MVC. Uses Apache Tomcat as the default embedded container."
  }


  // simple id + name pair used for representing boot versions, java versions, etc

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SelectItem{
    private String id; // e.g. "4.0.6"
    private String name; // e.g. "4.0.6"  
  }

  // ------- HELPER METHODS USED BY SCREENS ---------------

  public String defaultBootVersion(){
    if(bootVersion==null || bootVersion.getDefaultValue()==null){
      return "3.4.0"; // fallback to a hardcoded default if metadata is missing or malformed
    }

    if(bootVersion.getDefaultValue()!=null){
      return bootVersion.getDefaultValue();
    }

    List<SelectItem> versions = bootVersion.getValues();

    return (versions != null && !versions.isEmpty())
            ? versions.get(0).getId()
            : "3.4.0";  
  }

  //return all boot version ids in order 

  public List<String> bootVersionIds(){
    if (bootVersion == null || bootVersion.getValues() == null) {
        return List.of("3.4.0");
    }
    return bootVersion.getValues().stream()
            .map(SelectItem::getId)
            .toList();
  }



  //returns all dependency category

  public List<DependencyCategory> dependencyCategories(){
    if(dependencies==null || dependencies.getValues()==null){
      return List.of();
    }

    return dependencies.getValues();
  } 


  public String defaultJavaVersion(){
    if(JavaVersion==null){
      return "21"; // fallback to a hardcoded default if metadata is missing or malformed
    }
    if(JavaVersion.getDefaultValue()!=null){
       return JavaVersion.getDefaultValue();
    }
    List<SelectItem> versions = JavaVersion.getValues();

    return (versions != null && !versions.isEmpty())
            ? versions.get(0).getId()
            : "21";  
  }


  public List<String> JavaVersionIds(){
    if(JavaVersion == null || JavaVersion.getValues() == null){
      return List.of("21", "17", "11");
    }

    return JavaVersion.getValues().stream()
        .map(SelectItem::getId)
        .toList();
  }


  public String defaultBuildTool(){
    if(type==null || type.getDefaultValue()==null){
      return "maven-project"; // fallback to a hardcoded default if metadata is missing or malformed
    }

    if(type.getDefaultValue()!=null){
      return type.getDefaultValue();
    }

    List<SelectItem> buildTools = type.getValues();
    return (buildTools!=null && !buildTools.isEmpty())
            ? buildTools.get(0).getId()
            : "maven-project";

  }

  public List<SelectItem> buildToolsOptions(){
    if(type ==null || type.getValues() == null){
      return List.of(new SelectItem());
    }
    return type.getValues();
  }
}
