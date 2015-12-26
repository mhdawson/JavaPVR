// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver;

import java.util.HashMap;
import org.teleal.common.util.MimeType;

public class ContentTypes {
  
  public static String WATCHED_EXTENSION   = "watched";
  
  private static HashMap<String,MimeType> supportedTypes = new HashMap<String,MimeType>();
  
  static {
    supportedTypes.put("avi", new MimeType("video","avi"));
    supportedTypes.put("mpg", new MimeType("video","mpeg"));
    supportedTypes.put("mpe", new MimeType("video","mpeg"));
    supportedTypes.put("mpeg",new MimeType("video","mpeg"));
    supportedTypes.put("ts",  new MimeType("video","mp2t"));
    supportedTypes.put("mp4",  new MimeType("video","mp4"));
    supportedTypes.put("m4v",  new MimeType("video","mp4"));
    supportedTypes.put("mkv",  new MimeType("video","x-mkv"));
  };

  /**
   * returns a MimeType object for the given extension 
   * @param extension the extension for which the MimeType is requested
   * @return the requested MimeType object or null if it is not supported
   */
  public static MimeType getMimeType(String extension) {
    return supportedTypes.get(extension);
  }
  
  
  /**
   * returns a MimeType object for the given file
   * @param filename the file for which the MimeType is requested
   * @return the requested MimeType object or null if the file has a type which is not supported
   */
  public static MimeType getMimeTypeForFile(String filename) {
    return supportedTypes.get(getExtension(filename));
  }

  /**
   * Helper function to get the extension from a filename
   * @param filename the file name to get the extension from
   * @return the extension
   */
  public static String getExtension(String filename){
    if (filename.lastIndexOf(".") != -1){
      return filename.substring(filename.lastIndexOf(".") + 1).trim();
    } else {
      return null;
    }
  }
  
  /**
   * Helper function to get the extension from a filename
   * @param filename the file name to get the extension from
   * @return the extension
   */
  public static String replaceExtension(String filename, String newExtension){
    if (filename.lastIndexOf(".") != -1){
      return filename.substring(0,filename.lastIndexOf(".") + 1) + newExtension;
    } else {
      return null;
    }
  }
  
  /**
   * Helper function to get the extension from a filename
   * @param filename the file name to get the extension from
   * @return the extension
   */
  public static String removeExtension(String filename){
    if (filename.lastIndexOf(".") != -1){
      return filename.substring(0,filename.lastIndexOf("."));
    } else {
      return null;
    }
  }
  
}
