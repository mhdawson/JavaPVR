// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.clean;

import java.io.*;
import java.util.*;

public class Cleaner {
  final static int BUFFER_SIZE = 1024*1024*4;
  final static String LINE_START  = "<region start=\"";
  final static String LINE_MIDDLE = "\" end=\"";


  /**
   * @param args
   */
  public static void main(String[] args) {
    
    String baseFileName = removeExtension(args[0]);
    
    // First read in the comskip markers
    try {
      ArrayList<Commercial> commercials = new ArrayList<Commercial>();
      File theFile = new File(baseFileName + ".edlx");
      FileInputStream input = new FileInputStream(theFile);
      InputStreamReader theInput = new InputStreamReader(input);
      BufferedReader theReader = new BufferedReader(theInput);
      
      String nextLine = theReader.readLine();
      while(nextLine != null){
        if (nextLine.startsWith(LINE_START)){
          nextLine = nextLine.substring(LINE_START.length());
          Long start = Long.parseLong(nextLine.substring(0,nextLine.indexOf("\"")));
          nextLine = nextLine.substring(nextLine.indexOf("\"")+ LINE_MIDDLE.length());
          Long end = Long.parseLong(nextLine.substring(0,nextLine.indexOf("\"")));
          // we need to check end > start as comskip has a bug where the last commercial ends having
          // 0 for the end
          if (end > start) {
            commercials.add(new Commercial(start,end));
          }
        }
        nextLine = theReader.readLine();
      }
      theReader.close();

      
      // ok now create file without commercials
      File inputFile = new File(baseFileName + ".ts");
      File outputFile = new File(baseFileName + ".clean.ts");
      System.out.println("Output file name:" + outputFile.getAbsolutePath());
      
      commercials.add(new Commercial(inputFile.length(),inputFile.length()));
      
      FileInputStream inputStream = new FileInputStream(inputFile);
      FileOutputStream outputStream = new FileOutputStream(outputFile);
      
      byte[] buffer = new byte[BUFFER_SIZE];
      long totalBytesRead = 0;
      int currentCommercialIndex = 0;
      Commercial currentCommercial = commercials.get(currentCommercialIndex);
      while (inputStream.available() !=  0){
        int bytesRead = inputStream.read(buffer,0,(int)(Math.min(currentCommercial.start-totalBytesRead,BUFFER_SIZE)));
        outputStream.write(buffer,0,bytesRead);
        totalBytesRead = totalBytesRead + bytesRead;
        if (inputStream.available() != 0){
          if (totalBytesRead == currentCommercial.start){
            long skipped = inputStream.skip(currentCommercial.end-currentCommercial.start);
            totalBytesRead = totalBytesRead + (currentCommercial.end-currentCommercial.start);
            currentCommercialIndex++;
            currentCommercial = commercials.get(currentCommercialIndex);
          }
        }
      }
      inputStream.close();
      outputStream.close();
      inputFile.delete();
    } catch (Exception e) {
      try {
        System.out.println("Unexpected exception:" + e);
        e.printStackTrace();
        // we failed to clean the file,setup fileaname so that at least we compress the unclean version
        File inputFile = new File(baseFileName + ".ts");
        inputFile.renameTo(new File(baseFileName + ".clean.ts"));
      } catch (Exception t) {
        System.out.println("Unexpected exception:" + e);
        e.printStackTrace();
      }
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
  
  public static String newFileName(String filename){
    return removeExtension(filename) + ".clean.ts";
  }

}
