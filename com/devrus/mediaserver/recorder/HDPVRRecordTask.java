// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.recorder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class HDPVRRecordTask extends GenericRecordTask {
  
  private static final String VIDEO_DEVICE = "/dev/video0";
  
  // values that can be set or overridden via the configuration properties
  private String videoDevice = VIDEO_DEVICE;
  
  /**
   * This class implements the InputStream used to read the stream and return it when
   * requested by the parent class.  It simply read the UDP packets received on the string
   * passed in and returns the data through the read() method that must be implemented for
   * InputStreamds
   *
   */
  class RecordInputStream extends InputStream {
    
    File theInputDeviceFile = null;
    InputStream directInputStream = null;

    /**
     * constructor
     * @param socket the UDP socket from which to read the data to be returned
     * @param the server that is being used to record 
     */
    public RecordInputStream(String channel) throws Exception {
      // power on the cable box if it is not already on
      recordingStartStop(true);
      
      // set the cable box to the correct channel
      setChannel(channel);
      
      // ok create the inputStream that will be used to capture from the HDPVR
      directInputStream = new FileInputStream(new File(videoDevice));
    }

    /**
     * return the next byte of data for the InputStream
     * @returns the next byte of data
     */
    public int read() throws IOException {
      return directInputStream.read();
    }
    
    /**
     * return the next package of data, we assume buffer it big enough for a full packet
     * @returns the next byte of data
     */
    public int read(byte[] buffer, int off, int len) throws IOException {
      return directInputStream.read(buffer, off, len);
    }
    
    /**
     * We override the base InputStream close method so that we can
     * close the UDP socket when the InputStream is closed.
     */
    public void close() {
      try {
        recordingStartStop(false);
      } catch (Exception e) {
        System.out.println("Exception while handling recording stop");
        e.printStackTrace();
      }
      
      try {
        directInputStream.close();
      } catch (IOException e) {
      }
    }
    
  }

  /* (non-Javadoc)
   * @see com.devrus.mediaserver.recorder.GenericRecordTask#getInputStream(java.lang.String, long)
   */
  InputStream getInputStream(String recordTarget, long endTime) throws Exception {
    return new RecordInputStream(channels.get(recordTarget).number);
  }
  
  /**
   * toggles the power on the cable box
   * @throws Exception if something goes wrong
   */
  private int recordings = 0 ;
  private synchronized void recordingStartStop(boolean starting) throws Exception {
    if (!starting) {
      recordings--;
    }
    
    if (recordings == 0 ) {
      String command = "/usr/bin/irsend SEND_ONCE blaster 0_78_KEY_POWER";
      System.out.println(command);
      Process theProcess = Runtime.getRuntime().exec(command);
      theProcess.waitFor();
    }
    
    if (starting) {
      recordings++;
    }
  }
  
  /**
   * Tunes the the cable box to the requested channel using lirc 
   * 
   * @param channel the channel to that the cable box should be tuned to
   * @throws Exception thrown if something goes wrong
   */
  private void setChannel(String channel) throws Exception {
    String command = "/usr/bin/irsend SEND_ONCE blaster ";
    for(int i =0;i<channel.length();i++){
      command = command + "0_78_KEY_" + channel.charAt(i) + " ";
    }
    System.out.println(command);
    Process theProcess = Runtime.getRuntime().exec(command);
    theProcess.waitFor();
  }
}
