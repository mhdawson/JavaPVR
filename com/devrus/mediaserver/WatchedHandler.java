// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver;

import java.io.File;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Enumeration;

/**
 * This class handles the logic for determining when files have been watched and then
 * marking them as watched
 */
public class WatchedHandler extends Thread {
  
  // Default times for when we deem a vile watched and how often we clean up
  public static long CLEANUP_INTERVAL = 60 * 60 * 24 * 1000; // do cleanup once a day
  public static long DEFAULT_VIEWING_THRESHHOLD = 120 * 1000;
  
  // used to maintain the state 
  static boolean cleanupThreadRunning = false;
  static HashSet<String> markedWatched = new HashSet<String>();
  static Hashtable<String,Long> currentlyWatching = new Hashtable<String,Long>();
  static long viewingThreshhold = DEFAULT_VIEWING_THRESHHOLD;
  static long recordTimeout = 2* viewingThreshhold;
  static MichaelsMediaServer directoryService;

  /**
   * Used to set the time used to determine if a show has been watched
   * @param seconds time in seconds after which we deem the show viewed
   */
  public static void setViewingThreshhold(long seconds){
    viewingThreshhold = seconds * 1000;
    recordTimeout = 2* viewingThreshhold;
  }
  
  /**
   * Set the Content directory service that can be used to notify when content changes were made
   * because files were deemed watched
   * 
   * @param service the content directory service that can be used
   */
  public static void setContentDirectoryService(MichaelsMediaServer service){
    directoryService = service;
  }
  
  /**
   * This method is called by the engine which serves files.  It is called each time another section 
   * of the file is served. (for example every 1MB)
   * 
   * @param theFile  File representing the file being served
   * @returns true if the file has been deemed watched 
   */
  public static  boolean watching(File theFile){
    
    String id = theFile.getPath();
    if (markedWatched.contains(id)){
      return true;
    }
    
    if (currentlyWatching.containsKey(id)){
      Long lastWatched = currentlyWatching.get(id);
      if ((System.currentTimeMillis() - lastWatched.longValue()) > recordTimeout ){
        currentlyWatching.remove(id);
        currentlyWatching.put(id,new Long(System.currentTimeMillis()));
        return false;
      } else {
        if ((System.currentTimeMillis() - lastWatched.longValue()) > viewingThreshhold ){
          markFileAsWatched(theFile);
          return true;
        }
      }
    } else {
      currentlyWatching.put(id,new Long(System.currentTimeMillis()));
    }
    return false;
  }
  
  /**
   * This method is called when the file is deemed watched to record this in the file system
   * 
   * @param theFile the File object representing the file deemed watched
   */
  protected static void markFileAsWatched(File theFile) {
    try {
      File markedFile = new File(ContentTypes.replaceExtension(theFile.getPath(),MichaelsMediaServer.watchedExtension));
      markedFile.createNewFile();
      markedWatched.add(theFile.getPath());
      directoryService.contentUpdated();
      System.out.println("Marked as viewed:" + theFile.getPath());
      if (!cleanupThreadRunning){
        startCleanupThread();
      }
    } catch (Exception e){
      System.out.println("Exception while marking as viewed:" + theFile.getPath());
    }  
  }
  
  /**
   * This method starts the thread which does the periodic cleanup of the watching records as well as the
   * marked watched records
   */
  public static void startCleanupThread() {
    synchronized(WatchedHandler.class){
      if (!cleanupThreadRunning){
        Thread theThread = new WatchedHandler();
        theThread.start();
      }
    }
  }
  
  /**
   * The method which does the period cleanup
   */
  public void run(){
    // run every so often and clean out the list of things being watched and the cache of what has already
    // been watched   
    while(true){
      try {
        Thread.sleep(CLEANUP_INTERVAL);
        Enumeration<String> elems = currentlyWatching.keys();
        while(elems.hasMoreElements()){
          String nextElem = elems.nextElement();
          Long lastTime = currentlyWatching.get(nextElem);
          if ((System.currentTimeMillis() - lastTime.longValue()) > recordTimeout ){
            currentlyWatching.remove(nextElem);
            markedWatched.remove(nextElem);
          } 
        }
      } catch (InterruptedException e){
        
      }
    }
  }
}
