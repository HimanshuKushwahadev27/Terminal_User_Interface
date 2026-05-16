package com.emi.tui.modules;


import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.Minimal;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

//handles the http call with the stirng initializr api 

// fetchmatadata from initializr - list of boot versions, java versions, dependencies etc

//download the zip file from initializr and pass it to zip extractor after all the user input is collected and the url is formed

public class InitilizrClient {
  
  private static final String BASE_URL = "https://start.spring.io";
  private static final String METADATA_URL = BASE_URL + "/metadata/client";
  private static final String STARTER_URL = BASE_URL + "/starter.zip";


  private final OkHttpClient httpClient;

  private final ObjectMapper objectMapper;


  public InitilizrClient() {
    this.httpClient = new OkHttpClient().newBuilder()
            .callTimeout(java.time.Duration.ofSeconds(30))
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .readTimeout(java.time.Duration.ofSeconds(30))
            .writeTimeout(java.time.Duration.ofSeconds(30))
            .build();

    this.objectMapper = new ObjectMapper();
  }

  // PUBLIC APIs -----------------------------------------------------------------------


  // fetch metadata from initializr - list of boot versions, java versions, dependencies etc
  public InitilizrMetadata fetchMetadata() throws IOException {
    Request request = new Request.Builder()
            .url(METADATA_URL)
            .header("Accept", "application/json")
            .get()
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException(
           "Metadata fetch failed — HTTP "+ response.code() + ": " + response.message()
        );
      }

      String json = response.body().string();

      return objectMapper.readValue(json, InitilizrMetadata.class);

    }
  }


  // download the zip file from initializr and pass it to zip extractor after all the user input is collected and the url is formed ---------

  public byte[] downloadProjectZip(ProjectConfig config) throws IOException {
    String url = buildZipUrl(config);

    Request request = new Request.Builder()
            .url(url)
            .header("Accept", "application/zip")
            .get()
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException(
           "Project download failed — HTTP "+ response.code() + ": " + response.message()
        );
      }

      return response.body().bytes();
    }
  }

  // PRIVATE HELPERS -----------------------------------------------------------------------

  //URL BUILDER

  private String buildZipUrl(ProjectConfig config){
    StringBuilder url = new StringBuilder(STARTER_URL).append("?");

        append(url, "type",        config.getBuildTool());
        append(url, "language",    "java");
        append(url, "bootVersion", config.getBootVersion());
        append(url, "groupId",     config.getGroupId());
        append(url, "artifactId",  config.getArtifactId());
        append(url, "name",        config.getName());
        append(url, "description", config.getDescription());
        append(url, "packageName", config.getGroupId()
                                   + "." + config.getArtifactId());
        append(url, "packaging",   config.getPackaging());
        append(url, "javaVersion", config.getJavaVersion());

    if(!config.getDependencies().isEmpty()){
      append(url, "dependencies", config.dependenciesAsStrings());
    }


    //clean up trailing '&' if present
    if (url.charAt(url.length() - 1) == '&') {
        url.deleteCharAt(url.length() - 1);
    }
 
    return url.toString();
  }


  private void append(StringBuilder sb, String key, String value) {
    if (value == null || value.isBlank()) return;
    sb.append(key)
      .append("=")
      .append(urlEncode(value))
      .append("&");
  }


  // Minimal URL encoder — only encodes characters that break URLs.

  private String urlEncode(String value) {
    return value
            .replace(" ", "%20")
            .replace(",", "%2C")
            .replace("#", "%23")
            .replace("&", "%26");
  }

}
