// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ListenerThread extends Thread {
  private String root;
  private int port;
  private boolean isRunning = false;
  private ServerSocket sock;
  
  /**
   * Constructor
   * @param port  the port on which the listener will wait for connections
   * @param root  the file system location where the files to be served are rooted
   */
  private ListenerThread(int port, String root) {
    this.root = root;
    this.port = port;
  }
  
  /**
   * This method starts the listener
   * 
   * @param port  the port on which the listener will wait for connections
   * @param root  the file system location where the files to be served are rooted
   * @return the thread started
   */
  public static ListenerThread startListener(int port, String root) {
    ListenerThread listener = new ListenerThread(port, root);
    synchronized(listener){
      try {
        listener.start();
        listener.wait();
      } catch (Exception e){
      }
    }
    return listener;
  }
  
  /**
   * The method which listens for connections
   */
  public void run() {
    synchronized(this){
      try{
        sock = new ServerSocket(port);
        isRunning = true;
      } catch (IOException e){
        System.out.println("Failed to create server on port:" + port);
      }
      this.notify();
    }
    
    while(true){
      try {
        Socket requestSock = sock.accept();
        RequestThread requestThread = new RequestThread(root, requestSock);
        requestThread.start();
      } catch (IOException e) {
        System.out.println("Server socket accept failed, stopping:" + e);
        synchronized(this){
          isRunning = false;
        }
        break;
      }
    }
  }
  
  /**
   * used to check if server is running 
   * 
   * @return true if the server is running, false otherwise
   */
  public boolean isRunning(){
    return isRunning();
  }
}
