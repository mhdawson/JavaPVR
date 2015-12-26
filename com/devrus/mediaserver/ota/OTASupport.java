// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.ota;

// includes
import java.io.*;
import java.util.Properties;
import java.util.Iterator;
import java.net.*;

import org.teleal.common.util.MimeType;

import com.devrus.mediaserver.*;
import com.devrus.mediaserver.livecommon.*;


public class OTASupport extends LiveBase {
  // externally configurable properties
  public static final String HOMERUN_BASE_COMMAND_KEY = "homerun_base_command";
  public static final String OTA_FILENAME_BASE_KEY = "ota_filename_base";
  
  // defaults 
  private static final int OTA_IN_PORT = 25001;
  private static final String HOMERUN_BASE_COMMAND_DEFAULT = "C:\\Program Files\\Silicondust\\HDHomeRun\\hdhomerun_config 10.1.1.33";
  public static final String OTA_FILE_NAME_BASE_DEFAULT = "D:\\tv\\";
  
  // other constants
  public static final String OTA_FILE_NAME_TRAILER = ".ts";
  private static final int RECEIVE_BUFFER_SIZE = 1024*1024;
  private static final int MAX_PACKET_SIZE = 2000;
  private static final int START_BUFFER_TIME = 2000;

  // local variables
  DatagramSocket socket = null;
  OutputStream outputStream = null;
  byte buffer[] = new byte[MAX_PACKET_SIZE];
  DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
  
  // values that can be overridden in the configuration file
  public String homerunBaseCommand =  HOMERUN_BASE_COMMAND_DEFAULT;

  // holds the instance of this class used by other components
  static OTASupport instance;
  
  /**
   * Method used by other comments that need to interact with the HDPVR
   * @param hostInfo host information for the host that will server the files
   * @param root path to root where video files are located
   * @return an instance of OTASupport that can be used by other components
   */
  public static synchronized OTASupport getInstance(String serverAddress, int serverPort, Properties configuration) {
    if (instance == null) {
      instance = new OTASupport(serverAddress,serverPort, configuration);
    }
    return instance;
  }
  
  /**
   * Method used by other components that need to interact with the HDPVR
   * Only to be called when the single instance has already been created by the call 
   * to getInstance which passes in the initialization info
   * 
   * @return an instance of HDPVRSupport that can be used by other components
   */
  public static synchronized OTASupport getInstance() throws Exception {
    if (instance == null) {
      throw new Exception("OTASupport Instance not created yet");
    }
    return instance;
  }
  
  /** 
   * Constructor
   *  
   * @param hostInfo host information for the host that will server the files
   * @param root path to root where video files are located
   * @param configuration properties object with the configuration for the server
   */
  private OTASupport(String serverAddress, int serverPort, Properties configuration) {
    super(serverAddress,serverPort,configuration);
    
    // specifics for OTA 
    mimeType = new MimeType("video","mp2t");
    rootName = BuildContent.OTA_ROOT;
    fileNameTrailer = OTA_FILE_NAME_TRAILER;
    fileNameBase = OTA_FILE_NAME_BASE_DEFAULT;
    bufferTime = START_BUFFER_TIME;
    
    // Channels for OTA
    channels.put("CBOT", new Channel("CBOT","25"));
//    Channels.put("CBOFT", new Channel("CBOFT","9"));
    channels.put("CHCH", new Channel("CHCH","22"));
    channels.put("CJOH", new Channel("CJOH","13"));
    channels.put("OMNI2", new Channel("OMNI2","20"));
    channels.put("TVO", new Channel("TVO","24"));
//    Channels.put("CIVO", new Channel("CIVO","30"));
//    Channels.put("CFGS", new Channel("CFGS","34"));
//    Channels.put("CHOT", new Channel("CHOT","40"));
    channels.put("CTS", new Channel("CTS","42"));
    channels.put("CHRO", new Channel("CHRO","43"));
    channels.put("OMNI1", new Channel("OMNI1","27"));
    channels.put("CITY", new Channel("CITY","17"));
    
    Iterator<String> theChannels = channels.keySet().iterator();
    while(theChannels.hasNext()){
      String nextChannel = theChannels.next();
      channelList.add(channels.get(nextChannel));
    }
    
    if (configuration.getProperty(HOMERUN_BASE_COMMAND_KEY) != null){
      homerunBaseCommand = configuration.getProperty(HOMERUN_BASE_COMMAND_KEY);
    }
    
    if (configuration.getProperty(OTA_FILENAME_BASE_KEY) != null){
      fileNameBase = configuration.getProperty(OTA_FILENAME_BASE_KEY);
    }
  }
  
  /**
   * {@inheritDoc}
   */
  public void stopCapture(){
    // no channel to tune to simply stop capture
    if (socket != null){
      try {
        socket.close();
        outputStream.close();
        try{theFile.delete();} catch (Exception e){/* just ignore*/ }
      } catch (Exception e) {};
      socket = null;
    }
  }
  
  /**
   * {@inheritDoc}
   */
  public void startCapture() throws Exception {
    if (socket != null) {
      socket.close();
      outputStream.close();
      try{theFile.delete();} catch (Exception e) {/* just ignore*/ }
    } 
    
    socket = new DatagramSocket(OTA_IN_PORT);
    socket.setSoTimeout(100);
    socket.setReceiveBufferSize(RECEIVE_BUFFER_SIZE);
    System.out.println("Creating socket");

    // delete any old files to keep total buffer size down. 
    File directory = new File(fileNameBase);
    File files[] = directory.listFiles();
    for (int i=0;i<files.length;i++) {
      if (files[i].getName().endsWith(OTA_FILE_NAME_TRAILER)) {
        try{files[i].delete();} catch (Exception e){/* just ignore*/ }
      }
    }
    
    theFile = new File(getFileName(null,lastTuneSeq));
    outputStream = new FileOutputStream(theFile);
    
    String command = homerunBaseCommand + " set /tuner1/channel auto:" + channels.get(lastChannel).number;
    System.out.println(command);
    Process theProcess = Runtime.getRuntime().exec(command);
    theProcess.waitFor();
    Thread.sleep(1000);
    
    command = homerunBaseCommand + " set /tuner1/target udp://" + serverAddress + ":" + OTA_IN_PORT;
    System.out.println(command);
    theProcess = Runtime.getRuntime().exec(command);
    theProcess.waitFor();  
  }
  
  /**
   * {@inheritDoc}
   */
  public  void doCapture() {
    for(int i=0;i<10;i++){
      try {
        for (int j=0;j<1000;j++) {
          socket.receive(packet);
          outputStream.write(packet.getData(), 0, packet.getLength());
          outputStream.flush();
        } 
      } catch (SocketTimeoutException e) {
        // this is fine just go to the top of the loop
        break;
      } catch (Exception e) {
        System.out.println("Exception received in datagram socket receive");
        e.printStackTrace();
      }
    }
  }
  
}
