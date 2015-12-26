// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.livecommon;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import org.teleal.cling.support.contentdirectory.DIDLParser;
import org.teleal.cling.support.model.BrowseFlag;
import org.teleal.cling.support.model.BrowseResult;
import org.teleal.cling.support.model.DIDLContent;
import org.teleal.cling.support.model.DIDLObject;
import org.teleal.cling.support.model.Res;
import org.teleal.cling.support.model.WriteStatus;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.VideoItem;
import org.teleal.common.util.MimeType;

import com.devrus.mediaserver.BuildContent;

public abstract class LiveBase extends Thread {
  
  // externally configurable properties
  public static final String WATCH_TIMEOUT = "watch_timeout";
  
  // defaults
  private static final int DEFAULT_WATCH_TIMEOUT = 120; /* in seconds */
  
  // other constants
  public static final long NO_SEQ = -1;

  // values to be filled in by the subclass
  protected HashMap<String,Channel> channels = new HashMap<String,Channel>();
  protected ArrayList<Channel> channelList = new ArrayList<Channel>();
  protected MimeType mimeType = null;
  protected String rootName = null;
  protected String fileNameTrailer;
  protected String fileNameBase;
  
  // local vars
  protected File theFile = null;
  protected String lastChannel = null;
  protected long lastWatched = 0;
  protected boolean capturehreadRunning = false;
  protected long lastTuneSeq = 0;
  private Object tunedSync = new Object();
  protected long bufferTime = 0;
  private int watchTimeout = DEFAULT_WATCH_TIMEOUT;
  protected String serverAddress;
  protected int serverPort;
  
  public LiveBase(String serverAddress, int serverPort, Properties configuration){
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
    
    if (configuration.getProperty(WATCH_TIMEOUT) != null) {
      try {
        watchTimeout = Integer.parseInt(configuration.getProperty(WATCH_TIMEOUT));
      } catch (NumberFormatException e) {
        
      }
    }
  }
  
  /**
   * Given an id, this builds the content for an request
   * 
   * @param id the id to build the content for
   * @return a BrowseResult with the content for the id passed in
   * 
   * @throws Exception if there is a problem building the content
   */
  public BrowseResult buildContentForID(String id, BrowseFlag browseFlag, long firstResult, long maxResults, boolean nested) throws Exception{
    DIDLContent didl = new DIDLContent();
    int count = 0;
    if (browseFlag.equals(BrowseFlag.DIRECT_CHILDREN)) {
      if ((id.equals(rootName))||(id.equals(rootName + BuildContent.NESTED))) {
        // create the content listing the channels which are supported
        // use use the ArrayList for display so that we always get the same order
        for (int i = 0; i<Math.min(channelList.size(),(int)maxResults);i++) {
          Channel channelInfo = channelList.get(i+(int)firstResult);
          if (!nested) {
            Res res = new Res(mimeType,Long.MAX_VALUE,"http://" + serverAddress + ":" + serverPort + "/" + URLEncoder.encode(rootName + "/" + channelInfo.name));
            VideoItem newItem = new VideoItem(id + "/" + channelInfo.name,id,channelInfo.name,null,res);
            didl.addItem(newItem);
          } else {
            Container newContainer = new Container(id + "/" + channelInfo.name,id,channelInfo.name,null,new DIDLObject.Class("object.container.storageFolder"),1);
            newContainer.setWriteStatus(WriteStatus.UNKNOWN);
            newContainer.setSearchable(true);
            didl.addContainer(newContainer);
          }
          count++;
        }
      } else {
        String channelKey = id.substring(id.indexOf("/") + 1);
        Channel channelInfo = channels.get(channelKey);
        Res res = new Res(mimeType,Long.MAX_VALUE,"http://" + serverAddress + ":" + serverPort  + "/" + URLEncoder.encode(rootName + "/" + channelInfo.name));
        VideoItem newItem = new VideoItem(id + "/" + channelInfo.name,id,channelInfo.name,null,res);
        didl.addItem(newItem);
        count++;
      }
    } else {
      // request is for info about a specific channel
      if (id.indexOf("/") != -1) {
        // request was for a channel
        String channelKey = id.substring(id.indexOf("/") + 1);
        if ((!nested) ||(channelKey.indexOf("/") != -1)) {
          if (channelKey.indexOf("/") != -1) {
            channelKey = channelKey.substring(channelKey.indexOf("/") + 1);
          }
          Channel channelInfo = channels.get(channelKey);
          Res res = new Res(mimeType,Long.MAX_VALUE,"http://" + serverAddress + ":" + serverPort  + "/" + URLEncoder.encode(rootName + "/" + channelInfo.name));
          VideoItem newItem = new VideoItem(id,id.substring(0,id.indexOf("/")),channelInfo.name,null,res);
          didl.addItem(newItem);
        } else {
          Container newContainer = new Container(id,id.substring(0,id.indexOf("/")),channelKey,null,new DIDLObject.Class("object.container.storageFolder"),1);
          newContainer.setWriteStatus(WriteStatus.UNKNOWN);
          newContainer.setSearchable(true);
          didl.addContainer(newContainer);
        }
        count++;
      } else {
        // request was for the root
        Container newContainer = new Container(id,BuildContent.ROOT_ID,id,null,new DIDLObject.Class("object.container.storageFolder"),channels.size());
        newContainer.setWriteStatus(WriteStatus.UNKNOWN);
        newContainer.setSearchable(true);
        didl.addContainer(newContainer);
        count++;
      }
    }
    return new BrowseResult(new DIDLParser().generate(didl,true), count, count, 1);
  }
  
  /**
   * returns the name to be used for the growing file containing the live stream
   * @param channel channel being watched
   * @param tuneSeq sequence associated with the current watch session
   * @return the filename for the file containing the streaming data 
   */
  public synchronized String getFileName(String channel, long tuneSeq) {
    if (channel != null) {
      return fileNameBase + File.separator + channel + tuneSeq + fileNameTrailer;
    } else {
      return fileNameBase + File.separator + lastChannel + lastTuneSeq + fileNameTrailer;
    }
  }
  
  /**
   * This method returns the number of channels supported 
   * 
   * @return the number of channels supported
   */
  public int numberChannels() {
    return channels.size();
  }
  
  /**
   * This method is called both the the RequestThread gets the initial request for 
   * a channel and each time is sends a block of data.  This allows the OTASupport logic
   * to know when a channel is no longer being watched
   * 
   * @param name name of the channel being watched
   * @param tuneSeq sequence number associated with the watch session, or NO_SEQ if this the initial request for the session
   * @return the sequence number associated with the watch session
   * @throws Exception if the channel has been changed and the sequence/channel passed in don't match the current values
   */
  public long watchChannel(String name,long tuneSeq) throws Exception {
    long retVal = 0;
    long mySeq = 0;
    boolean newChannelTuned = false;
    synchronized(this){
      if (!capturehreadRunning){
        this.start();
      }
      if((tuneSeq != NO_SEQ)&&(tuneSeq != lastTuneSeq)) {
        throw new Exception("Channel Changed");
      }
      
      lastWatched = System.currentTimeMillis();
      
      if ((lastChannel == null)||(!lastChannel.equals(name))) {
        System.out.println("New channel tuned:" + name);
        lastChannel = name;
        lastTuneSeq++;
        if (lastTuneSeq <0) {
          lastTuneSeq = 0;
        }
        
        this.notifyAll();
        newChannelTuned = true;
      }
      retVal = lastTuneSeq;
    }
  
    mySeq = lastTuneSeq;
    if (newChannelTuned){
      synchronized(tunedSync) { 
        tunedSync.wait();
      }
    }

    synchronized(this) {
      if (mySeq != lastTuneSeq) {
        throw new Exception("Channel Changed");
      }
    }

    return retVal;
  }
  
  /** 
   * The method handles capturing the live stream
   */
  public void run() {
    String myLastChannel = null;
    synchronized(this) {
      capturehreadRunning = true;
    }

    while(true) {
      try { 
        boolean newChannel = false;
        boolean doCapture = true;
        synchronized(this) {
          if (lastChannel != null) {
            if (!lastChannel.equals(myLastChannel)) {
              newChannel = true;
            }
          } else {
            // no channel to tune to simply stop capture
            stopCapture();
            myLastChannel = null;
            this.wait();
            newChannel = true;
          }
          
          if (newChannel) {
            try {
              startCapture();
              myLastChannel = lastChannel;
            } catch (Exception e) {
              // failed to start capture
              doCapture = false;
              newChannel = false;
              lastChannel = null;
              myLastChannel = null;
              synchronized(tunedSync) {
                tunedSync.notifyAll();
              }
            }
          
          } else {
            if ((System.currentTimeMillis() - lastWatched) > (watchTimeout*1000)) {
              lastChannel = null;
              myLastChannel = null;
              doCapture = false;
            }
          }
        }
          
        // do the next set of packets or until we time out
        if (doCapture == true) {
          doCapture();
        }
          
        if (newChannel) {
          // build up a bit of a buffer
          try { Thread.sleep(bufferTime); } catch (Exception e) {};
          
          synchronized(tunedSync) {
            tunedSync.notifyAll();
          }
        }

      } catch (Exception e) {
        lastChannel = null;
        stopCapture();
      }
    }
  }
  
  /** 
   * called when capture for a channel should stop
   */
  public abstract void stopCapture();

  /**
   * called when the capture for a channel should start
   * @throws Exception
   */
  public abstract void startCapture() throws Exception;
  
  /** 
   * called when more data for a channel should be captured
   * @throws Exception
   */
  public abstract void doCapture() throws Exception;

}
