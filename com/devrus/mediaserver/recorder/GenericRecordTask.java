// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.recorder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.*;
import java.util.HashMap;

import com.devrus.mediaserver.clean.Cleaner;
import com.devrus.mediaserver.livecommon.Channel;
import com.devrus.mediaserver.scheduler.XMLTVScheduler;

public abstract class GenericRecordTask {
  private static final int VIDEO_READ_BUFFER_SIZE = 1024*1024;
  private static final String FILE_TRAILER = ".ts";
  private static final String MOVE_TAG = "move";

  // set when the properties file is read 
  public static String recordDir = null;
  public static String comskipBase = null;
  public static String completeDir = null;
  public static boolean runComskip = true;
  
  protected static HashMap<String,Channel> channels = new HashMap<String,Channel>();
  
  /** 
   * This method is used to add channels available for recording
   * @param name the name of the channel
   * @param channel the channel number
   */
  public static void addChannel(String name, String channel){
    channels.put(name, new Channel(name,channel));
  }
  
  class WatchDog extends Thread {
    long endTime = 0;
    InputStream input = null;
    public WatchDog(long endTime, InputStream input){
      this.endTime = endTime;
      this.input = input;
    }
    
    public void run(){
      while(true){
        if (System.currentTimeMillis() > endTime) {
          try {
            input.close();
          } catch (Exception e) {};
          break;
        }
        try {
          Thread.sleep(1000 * 30);
        } catch (Exception e) {};
      }
    }
  }
  
  public void doRecord(String[] extraInfo){
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

      // get the input stream used to get the content for the show, provided by subclasses
      InputStream in = null;
      FileOutputStream out = null;
      File outputFile = null;
      try {
        in = getInputStream(recordTarget, endTime - (XMLTVScheduler.EXTRA_RECORD_TIME)*60*1000);
        
        if (in != null){
          // start watchdog that will kill recording at end recording  
          (new WatchDog(endTime, in)).start();
    
          // generate the OutputStream that we will use to write the file 
          File directory = new File(recordDir + File.separator + showName);
          try {directory.mkdirs();} catch (Exception e){};
          outputFile = new File(directory.getAbsolutePath() + File.separator + episodeName /*+ "_" + dateFormat.format(new Date()) */+ FILE_TRAILER);
          System.out.println("Recording: " + outputFile.getAbsolutePath());
          out = new FileOutputStream(outputFile);
          
          int buffered = 0;
          int maxBuffer = buffer.length;
          while (true) {
            try {
              int numRead = in.read(buffer,buffered,maxBuffer-buffered);
              buffered = buffered + numRead;
              if (buffered > 128000) {
                out.write(buffer, 0, buffered);
                buffered = 0;
                out.flush();
              }
            } catch (Exception e) {
              out.write(buffer, 0, buffered);
              out.flush();
              if (System.currentTimeMillis() < endTime) {
                // this can be ok in some cases as we may have an overlap and the
                // next recording started, causing this exception
                System.out.println("Early termination of recording");
              }
              break;
            }
          }
        } else {
          System.out.println("Failed to record:" + episodeName);
        }
      } finally {
        try {
          if (out != null){
            out.close();
          }
        } catch (Exception e ){
          System.out.println("Exception closing output file");
        }
        
        try {
          if (in != null){
            in.close();
          }
        } catch (Exception e ){
          System.out.println("Exception closing input");
        }
      }
      
      if (runComskip) {
        // temporary delay, too much was going on for the atom to handle, seeing if delaying until
        // after recordings complete helps avoid affecting the recordgins.
        System.out.println("Recording complete, delaying for 4 hours to start comskip: " + outputFile.getAbsolutePath());
        try {
          Thread.sleep(1000*60*60*4);
        } catch (InterruptedException e) {};
        
        // now at this point we want to start comskip, we don't need to wait for it to finish
        if (outputFile != null) {
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
          System.out.println("Done: " + outputFile.getAbsolutePath());
        }
      }
    } catch (Exception e){
      System.out.println("Exception during recording:" + e);
      e.printStackTrace();
    }
  
  }
  
  /**
   * returns the InputStream to be used to read the content for the program.  
   * @param recordTarget string with the information for what should be recorded
   * @param endTime the time the program actually ends (which may be before the recording will stop
   *                as we all an "extra" to allow for clock skew 
   * @return the InputStream that can be used to read the content for the program being recorded
   */
  abstract InputStream getInputStream(String recordTarget, long endTime) throws Exception;
}
