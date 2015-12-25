// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.clean;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

public class DetectAndClean {
  
  public static final String VIDEO_FILE_NAME_TRAILER = ".ts";
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    String handbrakeExe = args[0];
    String comskipCommand = args[1];
    String outputDir = args[2];
    File rootDirectory = new File(args[3]);
    System.out.println("Processing Recordings");
    handleDirectory(handbrakeExe, comskipCommand, outputDir, rootDirectory);
  }
  
  public static void handleDirectory(String handbrakeExe, String comskipCommand, String outputDir, File directory) {
    // delete any old files to keep total buffer size down. 
    File files[] = directory.listFiles();
    for (int i=0;i<files.length;i++){
      if (files[i].isDirectory()) {
        handleDirectory(handbrakeExe, comskipCommand, outputDir, files[i]);
      } else if (files[i].getName().endsWith(VIDEO_FILE_NAME_TRAILER)) {
        String fileName = files[i].getAbsolutePath();
        // rename the file to remove spaces as we can see to get process.exec to work with that
        String newFileName = fileName.replace(' ', '_');
        (new File(fileName)).renameTo(new File(newFileName));
        process(handbrakeExe, comskipCommand, outputDir, newFileName, true);
      }
    }
  }

  /**
   * @param args
   */
  public static void process(String handbrakeExe, String comskipCommand, String outputDir, String fileName, boolean compress) {
      
    try {
      // first detect commercials
      System.out.println("Detecting commercials file:(" + new Date() + ")" + fileName);
      String command = comskipCommand + " " + fileName;
      System.out.println(command);
      
      Process theProcess = Runtime.getRuntime().exec(command);
      InputStream theInput = theProcess.getInputStream();
      theProcess.getErrorStream().close();
      OutputStream theOutput = theProcess.getOutputStream();
      
      theOutput.close();
      theInput.close();

      theProcess.waitFor();
      System.out.println("commercial detection done");
      
      // now strip out the commercials
      String[] cleanerArgs = new String[1];
      cleanerArgs[0] = fileName;
      Cleaner.main(cleanerArgs);

      // now compress the file
      if (compress) {
        compress(handbrakeExe,Cleaner.newFileName(fileName), outputDir);
      } else {
        // just move to the output directory
        File theFile = new File(fileName);
        String outputFile = outputDir + theFile.getName();
        theFile.renameTo(new File(outputFile));
      }
      
      
    } catch (Exception e){
      System.out.println("Exception doing detection or cleaning:" + e);
      e.printStackTrace();
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
  
  public static String buildCommand(String handbrakeExe, String inFile, String outFile){
    return handbrakeExe + " -i " + inFile + " -t 1 -c 1 -o " + outFile + " -f mp4 -4 --strict-anamorphic --crop 0:0:0:0 -e x264 -q 24 -a 1 -E faac -6 dpl2 -R 48 -B 160 -D 0.0 -x ref=2:bframes=2:subq=6:mixed-refs=0:weightb=0:8x8dct=0:trellis=0 -v 1";
  }
  
  public static void compress(String handbrakeExe, String fileName, String outputDir ) {
    System.out.println("Compressing file:" + fileName);
    File theFile = new File(fileName);
    String outputFile = outputDir + removeExtension(theFile.getName()) + ".m4v" ;
    String command = buildCommand(handbrakeExe, fileName,outputFile);
    System.out.println(command);
    try {
      Process theProcess = Runtime.getRuntime().exec(command);
      final InputStream theInput = theProcess.getInputStream();
      final InputStream theError = theProcess.getErrorStream();
      OutputStream theOutput = theProcess.getOutputStream();
      
      theOutput.close();
      theInput.close();
      theError.close();

      theProcess.waitFor();
      System.out.println("compression done:(" + new Date() + ")");
      theFile.renameTo(new File(theFile.getAbsolutePath() + ".compressed"));
    } catch (Exception e){
      System.out.println("Exception doing compression:" + e);
      e.printStackTrace();
    }
  }
  
}
