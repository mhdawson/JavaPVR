// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.recorder;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;

import com.devrus.mediaserver.livecommon.Channel;

public class LocalRecordTask  extends GenericRecordTask {
  public static final String DEFAULT_SERVER_NET_MASK = "10.";
  private static final int MAX_PACKET_SIZE = 2000;
  private static final int RECEIVE_BUFFER_SIZE = 1024*1024 * 10;
  private static final int TAKE_SERVER_MILLIS_BEFORE_DONE = 5000;
  String serverNetMask =  DEFAULT_SERVER_NET_MASK;
  static String serverAddress =  null;
  static InetAddress serverAddressInet = null;
  static ArrayList<HDHomeRunServer> servers = new ArrayList<HDHomeRunServer>();

  /**
   * This method is used to add server available for recording
   * @param base the base command to direct requests to the server
   * @param tuner the tuner associated with the server
   */
  public static void addServer(String base, String tuner){
    servers.add(new HDHomeRunServer(base,tuner));
  }
  
  /**
   * The record task for cron must be static.  This method
   * simply creates an instance of the LocalRecordTask and invokes the method
   * on the parent class to start recording
   * @param extraInfo
   */
  public static void record(String[] extraInfo){
    (new LocalRecordTask()).doRecord(extraInfo);
  }
  
  /**
   * This class implements the InputStream used to read the stream and return it when
   * requested by the parent class.  It simply read the UDP packets received on the string
   * passed in and returns the data through the read() method that must be implemented for
   * InputStreamds
   *
   */
  class RecordInputStream extends InputStream {
    DatagramSocket socket = null;
    HDHomeRunServer server = null;
    byte buffer[] = new byte[MAX_PACKET_SIZE];
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    byte[] lastRead = null;
    int lastLength = 0;
    int lastReadIndex = 0;
    long useSeq = 0;

    /**
     * constructor
     * @param socket the UDP socket from which to read the data to be returned
     * @param the server that is being used to record 
     */
    public RecordInputStream(DatagramSocket socket, HDHomeRunServer server){
      this.socket = socket;
      this.server = server;
      useSeq = server.getUseSeq();
    }

    /**
     * return the next byte of data for the InputStream
     * @returns the next byte of data
     */
    public int read() throws IOException {
      if (lastLength == 0){
        socket.receive(packet);
        lastRead = packet.getData();
        lastLength = packet.getLength();
        lastReadIndex = 0;
      }
      int toReturn = lastRead[lastReadIndex];
      lastReadIndex++;
      if (lastReadIndex == lastLength) {
        lastLength = 0;
      }
      return toReturn;
    }
    
    /**
     * return the next package of data, we assume buffer it big enough for a full packet
     * @returns the next byte of data
     */
    public int read(byte[] buffer, int off, int len) throws IOException {
      if (lastLength == 0){
        socket.receive(packet);
        lastRead = packet.getData();
        lastLength = packet.getLength();
        lastReadIndex = 0;
      }
      System.arraycopy(lastRead, lastReadIndex, buffer,off, lastLength - lastReadIndex );
      int amountReturned = lastLength - lastReadIndex;
      lastLength = 0;
      return amountReturned;
    }
    
    /**
     * We override the base InputStream close method so that we can
     * close the UDP socket when the InputStream is closed.
     */
    public void close() {
      socket.close();
      releaseServer(server, useSeq);
      try {
        super.close();
      } catch (IOException e){};
    }
    
  }
  
  /**
   * get a server for a recording
   * @param the channel that the server will be used to record
   * @return the server to be used for the recording
   */
  protected static synchronized HDHomeRunServer getFreeServer(String channel, long endTime){
    HDHomeRunServer serverToReturn = null;
    
    // first look for a free server
    for (int i=0; i<servers.size(); i++){
      HDHomeRunServer server = servers.get(i);
      if (!server.isInUse()){
         serverToReturn = server;
         break;
      }
    }
    
    // if we got here then all servers were recording, now see if one is recording
    // the same channel.  In this case we will allow it to be switched over to the new recording
    // so that if we don't have enough channels our overlap at the end does not prevent the next
    // recording from starting
    if (serverToReturn == null) {
      for (int i=0; i<servers.size(); i++){
        HDHomeRunServer server = servers.get(i);
        if (server.getCurrentChannel().equals(channel)){
          // ok re-used this server as it is currently recording the same channel and we can't have two 
          // different things on the same channel at the same time
           serverToReturn = server;
           break;
        }
      }
    }
    
    // ok no free server and none are currently recording the same channel.  See if one is in
    // the "extra" we add to the end of a program to ensure we don't cut of due to time skew
    if (serverToReturn == null) {
      for (int i=0; i<servers.size(); i++){
        HDHomeRunServer server = servers.get(i);
        if ((System.currentTimeMillis() + TAKE_SERVER_MILLIS_BEFORE_DONE) >= server.getEndTime()){
           serverToReturn = server;
           break;
  
        }
      }
    }
    
    // setup the server that will be used
    if(serverToReturn != null){
      serverToReturn.setInUse(true);
      serverToReturn.setCurrentChannel(channel);
      serverToReturn.incrementUseSeq();
      serverToReturn.setEndTime(endTime);
    }

    return serverToReturn;
  }
  
  /** 
   * release a server for use for another recording
   * @param server the server being released
   * @param the sequence number associated with the server when it was obtained
   */
  protected static synchronized void releaseServer(HDHomeRunServer server, long useSeq){
    if (server.getUseSeq() == useSeq){
      server.setCurrentChannel(null);
      server.setInUse(false);
      
      // now stop the streaming by setting the channel to none
      String homerunBaseCommand = server.getHomerunBaseCommand();
      String tuneCommand = " set /" + server.getTuner() + "/channel none ";
      String command = homerunBaseCommand + " " + tuneCommand;
      System.out.println(command);
      try {
        Process theProcess = Runtime.getRuntime().exec(command);
        theProcess.waitFor();
      } catch (Exception e) {
        System.out.println(e);
      }
    };
  }

  /**
   * returns the InputStream to be used to read the content for the program.  
   * @param recordTarget string with the information for what should be recorded
   * @param endTime the time the program actually ends (which may be before the recording will stop
   *                as we all an "extra" to allow for clock skew 
   * @return the InputStream that can be used to read the content for the program being recorded
   */
  InputStream getInputStream(String recordTarget, long endTime) throws Exception {
    DatagramSocket socket = null;
    
    // get the server address if it has not yet be obtained. We do this lazily so that the mask can be
    // set after the LocalRecordTask is created
    if (serverAddress == null){
      serverAddressInet = getServerAddress();
      serverAddress = serverAddressInet.getHostAddress();
    }
    
    // create the socket that will be used to receive the stream
    // let the OS chose the local socket so that we get a different one for each stream
    socket = new DatagramSocket();
    socket = new DatagramSocket(new InetSocketAddress(serverAddressInet,0));
    socket.setReceiveBufferSize(RECEIVE_BUFFER_SIZE);
    
    // extract the channel from the record target and determine the homerun instance and tunner that should be used
    HDHomeRunServer server = getFreeServer(recordTarget,endTime);
    if (!(null == server)){
      String homerunBaseCommand = server.getHomerunBaseCommand();
      String tuner = server.getTuner();
      String tuneCommand = " set /" + tuner + "/channel auto:";
      String programCommand = " set /" + tuner + "/program ";
      String streamCommand = " set /" + tuner + "/target udp://";
      String channel = channels.get(recordTarget).number;
      String program = null;
      
      // if a channel support multiple programs the specific program may be indicated with an
      // addition of ".X" to the channel were X is the program number. Here we separate
      // the channel and program if necessary
      if (channel.contains(".")) {
        try { 
          program = channel.substring(channel.indexOf(".") + 1);
          channel = channel.substring(0, channel.indexOf("."));
        } catch (Exception e) {
        }
      }
      
      // tune to the right channel
      String command = homerunBaseCommand + " " + tuneCommand + channel;
      System.out.println(command);
      Process theProcess = Runtime.getRuntime().exec(command);
      theProcess.waitFor();
      
      // if there was a program set it
      if (null != program) {
        command = homerunBaseCommand + " " + programCommand + program;
        System.out.println(command);
        theProcess = Runtime.getRuntime().exec(command);
        theProcess.waitFor();
      }
      
      Thread.sleep(1000);
      
      // direct the stream to the appropriate server address/port
      command = homerunBaseCommand + streamCommand + serverAddress + ":" + socket.getLocalPort();
      System.out.println(command);
      theProcess = Runtime.getRuntime().exec(command);
      theProcess.waitFor();
      
      // ok create the InputStream from which the data from the turner will be returned
      return new RecordInputStream(socket,server);
    } else {
      System.out.println("No available server");
      return null;
    }
  }
  
  /**
   * This method gets the fist local address which is the default address we use for the server
   * @return string representing the address of the server the LocalRecordTask is running on
   */
  public InetAddress getServerAddress() {
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()){
        // use the first one which is not localhost
        NetworkInterface next = interfaces.nextElement();
        Enumeration<InetAddress> addresses = next.getInetAddresses();
        while(addresses.hasMoreElements()){
          InetAddress address = addresses.nextElement();
          // use the first one that is not localhost
          if (!address.isLoopbackAddress()){
            if (address.getHostAddress().startsWith(serverNetMask)){
              return address;
            }
          }
        }
      }
    } catch (SocketException e){
      System.out.println("Exception obtaining server address:" + e );
    }
    return null;
  }
}
