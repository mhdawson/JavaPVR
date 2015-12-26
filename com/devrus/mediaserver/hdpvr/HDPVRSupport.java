// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.hdpvr;

import com.devrus.mediaserver.*;
import com.devrus.mediaserver.livecommon.*;

import java.io.*;
import java.util.Properties;

import org.teleal.common.util.MimeType;

public class HDPVRSupport extends LiveBase {
  // externally configurable properties
  public static final String HDPVR_FILE_NAME_BASE_KEY = "hdpvr_video_file_base_name";
  public static final String HDPVR_VIDEO_DEVICE     = "hdpvr_video_device";
  
  // default values
  private static final String VIDEO_DEVICE = "/dev/video0";
  
  // other constants
  public static final String HDPVR_FILE_NAME_TRAILER = ".ts";
  private static final int VIDEO_READ_BUFFER_SIZE = 1024*1024;
  
  // values that can be set or overridden via the configuration properties
  private String videoDevice = VIDEO_DEVICE;
  
  // locals
  byte[] buffer = new byte[VIDEO_READ_BUFFER_SIZE];
  File theInputDeviceFile = new File(videoDevice);
  FileInputStream inputStream = null;
  OutputStream outputStream = null;
  
  // holds the instance of this class used by other components
  static HDPVRSupport instance;
  
  /**
   * Method used by other comments that need to interact with the HDPVR
   * @param hostInfo host information for the host that will server the files
   * @param root path to root where video files are located
   * @return an instance of HDPVRSupport that can be used by other components
   */
  public static synchronized HDPVRSupport getInstance(String serverAddress, int serverPort, Properties configuration){
    if (instance == null){
      instance = new HDPVRSupport(serverAddress,serverPort,configuration);
    }
    return instance;
  }
  
  /**
   * Method used by other comments that need to interact with the HDPVR
   * Only to be called when the single instance has already been created by the call 
   * to getInstance which passes in the initialization info
   * 
   * @return an instance of HDPVRSupport that can be used by other components
   */
  public static synchronized HDPVRSupport getInstance() throws Exception {
    if (instance == null){
      throw new Exception("HDPVR Instance not create yet");
    }
    return instance;
  }
  
  /** 
   * Constructor
   *  
   * @param hostInfo host information for the host that will server the files
   * @param root path to root where video files are located
   */
  private HDPVRSupport(String serverAddress, int serverPort, Properties configuration){
    super(serverAddress,serverPort,configuration);
    
    // specifics for HDPVr 
    mimeType = new MimeType("video","mp2t");
    rootName = BuildContent.CABLE_ROOT;
    fileNameTrailer = HDPVR_FILE_NAME_TRAILER;
    fileNameBase = configuration.getProperty(HDPVR_FILE_NAME_BASE_KEY);

    // setup the channels
    channels.put("TSN", new Channel("TSN","30"));
    channels.put("Fireplace", new Channel("Fireplace","574"));
    channels.put("GlobalHD", new Channel("GlobalHD","517"));
    channels.put("CityHD", new Channel("CityHD","519"));
    channels.put("TVMixKIds", new Channel("TVMixKIds","219"));
    channels.put("CBOT", new Channel("CBOT","8"));
    channelList.add(channels.get("TSN"));
    channelList.add(channels.get("Fireplace"));
    channelList.add(channels.get("GlobalHD"));
    channelList.add(channels.get("CityHD"));
    channelList.add(channels.get("TVMixKIds"));
    channelList.add(channels.get("CBOT"));
    
    if (configuration.getProperty(HDPVR_VIDEO_DEVICE) != null){
      videoDevice = configuration.getProperty(HDPVR_VIDEO_DEVICE);
    }
  }
  
  /**
   * This method returns the number of HDPVR channels supported 
   * 
   * @return
   */
  public int numberChannels(){
    return channels.size();
  }
  
  /**
   * {@inheritDoc}
   */
  public void stopCapture(){
    if (inputStream != null){
      try {inputStream.close();} catch (Exception e){/* just ignore*/ }
      try {outputStream.close();} catch (Exception e){/* just ignore*/ }
      try{theFile.delete();} catch (Exception e){/* just ignore*/ }
      inputStream = null;
      try {togglePower(false);} catch (Exception e){System.out.println("Failed to turn power off"); }
      System.out.println("turned power off");
    } 
  }
  
  /**
   * {@inheritDoc}
   */
  public void startCapture() throws Exception {
    togglePower(true);
    System.out.println("turned power on");
    if (inputStream != null){
      inputStream.close();
      outputStream.close();
      try{theFile.delete();} catch (Exception e){/* just ignore*/ }
    } 
    
    inputStream = new FileInputStream(theInputDeviceFile);

    // delete any old files to keep total buffer size down. 
    File directory = new File(fileNameBase);
    File files[] = directory.listFiles();
    for (int i=0;i<files.length;i++){
      if (files[i].getName().endsWith(HDPVR_FILE_NAME_TRAILER)){
        try{files[i].delete();} catch (Exception e){/* just ignore*/ }
      }
    }
    
    setChannel(channels.get(lastChannel).number);
    theFile = new File(getFileName(null,lastTuneSeq));
    outputStream = new FileOutputStream(theFile);
  }
  
  /**
   * {@inheritDoc}
   */
  public  void doCapture(){
    for(int i=0;i<2;i++){
      try {
        int numRead = inputStream.read(buffer);
        outputStream.write(buffer, 0, numRead);
        outputStream.flush();
      } catch (Exception e){
        System.out.println("Exception received in input stream receive");
        e.printStackTrace();
      }
    }
  }
  
  
  /**
   * Tunes the the cable box to the requested channel using lirc 
   * 
   * @param channel the channel to that the cable box should be tuned to
   * @throws Exception thrown if something goes wrong
   */
  public void setChannel(String channel) throws Exception {
    String command = "/usr/bin/irsend SEND_ONCE blaster ";
    for(int i =0;i<channel.length();i++){
      command = command + "0_78_KEY_" + channel.charAt(i) + " ";
    }
    System.out.println(command);
    Process theProcess = Runtime.getRuntime().exec(command);
    theProcess.waitFor();
  }
  
  /**
   * toggles the power on the cable box
   * @throws Exception if something goes wrong
   */
  public boolean powerOn = false;
  public void togglePower(boolean on) throws Exception {
    if (powerOn != on){
      String command = "/usr/bin/irsend SEND_ONCE blaster 0_78_KEY_POWER";
      System.out.println(command);
      Process theProcess = Runtime.getRuntime().exec(command);
      theProcess.waitFor();
    }
    powerOn = on;
  }
  
}
