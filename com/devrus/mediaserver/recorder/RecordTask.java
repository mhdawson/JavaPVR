// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.recorder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.text.*;

import com.devrus.mediaserver.clean.Cleaner;

public class RecordTask {
  private static final int VIDEO_READ_BUFFER_SIZE = 1024*1024;
  private static final String FILE_TRAILER = ".ts";
  private static final String MOVE_TAG = "move";

  // set when the properties file is read 
  public static String recordDir = null;
  public static String comskipBase = null;
  public static String completeDir = null;
  public static boolean runComskip = true;
  
  public static void record(String[] extraInfo){
    try {
      boolean move = false;
      byte[] buffer = new byte[VIDEO_READ_BUFFER_SIZE];
      String showName = extraInfo[0];
      String episodeName = extraInfo[1];
      String recordTarget = extraInfo[2];
      int duration = Integer.parseInt(extraInfo[3]);
      if (extraInfo.length >4){
        if ((extraInfo[4] != null)&&(extraInfo[4].compareTo(MOVE_TAG)==0)){
          move = true;
        }
      }
      SimpleDateFormat dateFormat = new SimpleDateFormat("dd_MMM_yyyy_HH_mm");
      long endTime = System.currentTimeMillis() + (duration * 60 * 1000);

      // make sure we start from the beginning
      try {
        String cleanRecordTarget = recordTarget + "restart";  
        URL connection = new URL(cleanRecordTarget);
        URLConnection urlConnection = connection.openConnection();
        urlConnection.getInputStream().close();
        System.out.println("closed clean connection");
      } catch (Exception e){
        e.printStackTrace();
      };
      
      URL connection = new URL(recordTarget);
      URLConnection urlConnection = connection.openConnection();
      InputStream in = urlConnection.getInputStream();
      File directory = new File(recordDir + File.separator + showName);
      try {directory.mkdirs();} catch (Exception e){};
      File outputFile = new File(directory.getAbsolutePath() + File.separator + episodeName /*+ "_" + dateFormat.format(new Date()) */+ FILE_TRAILER);
      System.out.println("Recording: " + outputFile.getAbsolutePath());
      FileOutputStream out = new FileOutputStream(outputFile);
      while (true){
        try {
          int numRead = in.read(buffer);
          if (numRead >0){
            out.write(buffer, 0, numRead);
            out.flush();
          }
        } catch (Exception e){
          System.out.println("Exception received in input stream receive");
          e.printStackTrace();
          break;
        }
        if (System.currentTimeMillis() > endTime){
          break;
        }
      }
      try {
        out.close();
        in.close();
      } catch (Exception e ){
        System.out.println("Exception closing input/output file");
      }
      
      if (runComskip) {
        // temporary delay, too much was going on for the atom to handle, seeing if delaying until
        // after recordings complete helps avoid affecting the recordgins.
        System.out.println("Recording complete, delaying for 4 hours to start comskip: " + outputFile.getAbsolutePath());
        try {
          Thread.sleep(1000*60*60*4);
        } catch (InterruptedException e) {};
        
        // now at this point we want to start comskip, we need to wait for it to finish
        System.out.println("Detecting Commercials: " + outputFile.getAbsolutePath());
        Process theProcess = Runtime.getRuntime().exec(comskipBase + " \"" + outputFile.getAbsolutePath() + "\"");
        theProcess.getInputStream().close();
        theProcess.getErrorStream().close();
        theProcess.getOutputStream().close();
        theProcess.waitFor();
        
        String[] cleanerArgs = new String[1];
        cleanerArgs[0] = outputFile.getAbsolutePath();
        System.out.println("Cleaning Commercials: " + outputFile.getAbsolutePath());
        Cleaner.main(cleanerArgs);
        if (move){
          File currentName = new File(Cleaner.newFileName(outputFile.getAbsolutePath()));
          currentName.renameTo(new File(completeDir + File.separator + currentName.getName()));
        }
      }
      System.out.println("Done: " + outputFile.getAbsolutePath());
    } catch (Exception e){
      System.out.println("Exception during recording:" + e);
      e.printStackTrace();
    }
  
  }
}
