// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.recorder;

/**
 * This class represents one of the available HDHomeRun servers
 */
public class HDHomeRunServer {
  
  String base = null;
  String tuner  = null;
  String currentChannel = null;
  boolean inUse = false;
  private long useSeq = 0;
  private long endTime = 0;
  
  /**
   * returns the time this sever should become free
   *
   * @return time the server should become free
   */
  public long getEndTime() {
    return endTime;
  }

  /**
   * set the end time associated with the recording this server is currently being used for
   * @param endTime the time the ending finishes
   */
  public void setEndTime(long endTime) {
    this.endTime = endTime;
  }

  /**
   * return if the server is in use
   * @return true if the server is in use
   */
  public boolean isInUse() {
    return inUse;
  }
  
  /**
   * sets if the server is in use
   */
  public void setInUse(boolean inUse) {
    this.inUse = inUse;
  }

  public HDHomeRunServer(String base, String tuner) {
    this.base = base;
    this.tuner = tuner;
  }
  
  /**
   * return the base command to direct requests to this server
   * @return the based command for this server
   */
  public String getHomerunBaseCommand(){
    return base;
  }
    
  /**
   * return the tuner for this server
   * @return the tuner associated with this server
   */
  public String getTuner(){
    return tuner;
  }
  
  /** 
   * set the channel the server is currently being used to record
   * @param channel the channel the server is currently being used to record
   */
  public void setCurrentChannel(String channel) {
    currentChannel = channel;
  }
  
  /**
   * return the channel the server is currently being used to record
   * @return the channel the server is currently being used to record
   */
  public String getCurrentChannel() {
    return currentChannel;
  }
  
  /**
   * increment the sequence counter used to make sure releases match up with 
   * the in use count
   */
  public void incrementUseSeq(){
    useSeq++;
  }
  
  /**
   * get the sequence counter used to make sure releases match up with 
   * the in use count
   * @return the current sequence number
   */
  public long getUseSeq(){
    return useSeq;
  }
}
