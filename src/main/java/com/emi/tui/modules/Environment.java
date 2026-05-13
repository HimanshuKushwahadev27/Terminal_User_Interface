package com.emi.tui.modules;

public enum Environment {
  WINDOWS,
  LINUX,
  MACOS,
  WSL ;

  public String displayName(){
    return switch(this){
      case WINDOWS -> "Windows";
      case LINUX -> "Linux";
      case MACOS -> "macOS";
      case WSL -> "WSL";
    };
  }
}
