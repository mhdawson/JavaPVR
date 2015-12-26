// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import org.teleal.common.util.MimeType;

import com.devrus.mediaserver.ota.OTASupport;
import com.devrus.mediaserver.hdpvr.HDPVRSupport;

/**
 * This class implements the thread run to handle each request for a portion of a file
 */
public class RequestThread extends Thread {
  //constants
  private static final String GET_LINE                    = "GET ";
  private static final String HEAD_LINE                   = "HEAD ";
  private static final String HTTP_VERSION_PREFIX         = "HTTP";
  private static final String CONTENT_FEATURES_DLNA_ORG   = "getcontentFeatures.dlna.org: 1";
  private static final String WDTV_USER_AGENT             = "User-Agent: INTEL_NMPR";
  private static final int FILE_SERVE_INCREMENT           = 1024*1024;
  private static final int GROWING_FILE_RETRY_WAIT_MS     = 100;
  
  String root = null;
  Socket requestSock = null;
  long startPosition = 0;
  long requestedStartPosition = 0;
  long endPosition = 0;
  boolean samsung = false;

  /**
   * Constructor
   * @param root  filesystem root where files to serve are located
   * @param sock  socket from which to read the contents of the request
   */
  public RequestThread(String root, Socket sock){
    this.root = root;
    requestSock = sock;
  }
  
  /**
   * There run method that does the actual work
   */
  public void run() {
    boolean growingFile = false;
    String fileName = null;
    FileInputStream filein = null;
    File theFile = null;
    String otaChannel = null;
    String cableChannel = null;
    long tuneSeq = OTASupport.NO_SEQ;
    try {
      
      // parse the headers coming in, we need to know the file requested and if a range of bytes was requested
      BufferedReader in = new BufferedReader(new InputStreamReader(requestSock.getInputStream()));
      String nextLine = in.readLine();
      while((nextLine != null)&&(!(nextLine.equals("")))){
        System.out.println(nextLine);
        if (nextLine.startsWith(GET_LINE)||nextLine.startsWith(HEAD_LINE)){
          try {
            if (nextLine.startsWith(GET_LINE)){
              fileName = URLDecoder.decode(nextLine.substring(GET_LINE.length(), nextLine.indexOf(HTTP_VERSION_PREFIX)));
            } else {
              fileName = URLDecoder.decode(nextLine.substring(HEAD_LINE.length() + 1, nextLine.indexOf(HTTP_VERSION_PREFIX)));
            }
            System.out.println("Request for:" + fileName);
            
            if ((fileName.startsWith("/" + BuildContent.OTA_ROOT))||(fileName.startsWith(BuildContent.OTA_ROOT))){
              fileName = fileName.trim();
              // remove front / if is was there
              if (fileName.startsWith("/")){
                fileName = fileName.substring(1);
              }
              otaChannel = fileName.substring(BuildContent.OTA_ROOT.length() + 1);
              tuneSeq = OTASupport.getInstance().watchChannel(otaChannel,tuneSeq);
              fileName = OTASupport.getInstance().getFileName(otaChannel,tuneSeq);
              theFile = new File(fileName);
              growingFile = true;
            } else if ((fileName.startsWith("/" + BuildContent.CABLE_ROOT))||(fileName.startsWith(BuildContent.CABLE_ROOT))){
              fileName = fileName.trim();
              // remove front / if is was there
              if (fileName.startsWith("/")){
                fileName = fileName.substring(1);
              }
              cableChannel = fileName.substring(BuildContent.CABLE_ROOT.length() + 1);
              tuneSeq = HDPVRSupport.getInstance().watchChannel(cableChannel,tuneSeq);
              fileName = HDPVRSupport.getInstance().getFileName(cableChannel,tuneSeq);
              theFile = new File(fileName);
              growingFile = true;
            
            } else {
              theFile = new File(root + File.separator + fileName.trim());
            }
          } catch (Exception e ){
            e.printStackTrace();
            System.out.println("Failed to open file for request:" + theFile.getPath());
          }
          
        } else if (nextLine.startsWith("Range: bytes=")){
          startPosition = Long.parseLong(nextLine.substring(nextLine.indexOf("=")+1,nextLine.indexOf("-")));
          requestedStartPosition = startPosition;
          
          // if we are asked for data which is beyond the end of the file just start sending from the beginning
          // some clients seem to poll multiple places into the file, possibly to validate it. For live tv we 
          // lie about the length so just return the start of the file if we are being asked for something past
          // the current end.
          if (startPosition > theFile.length()){
            startPosition = 0;
          }
          
          if (nextLine.indexOf("-")!= -1){
            try{
              endPosition = Long.parseLong(nextLine.substring(nextLine.indexOf("-")+1));
            } catch (NumberFormatException e){
              // just ignore, request likely was just in form of xxxx- as opposed to specifying an end 
            }
          }
        } else if (nextLine.startsWith(CONTENT_FEATURES_DLNA_ORG)){
          samsung = true;
          System.out.println("Samsung TV");
        } else if (nextLine.startsWith(WDTV_USER_AGENT)){
          // we need the same headers as for samsung otherwise ts etc. does not work properly
          samsung = true;
          System.out.println("WDTV Live");
        }
        nextLine = in.readLine();
      }  
      System.out.println("Finished processing input headers");
      System.out.flush();
  
      /////////////////////////////////
      // ok now send the response 
      //////////////////////////////////
      OutputStream fileOut = requestSock.getOutputStream();
      PrintStream out = new PrintStream(fileOut);
      
      
      // Output the HTTP OK header along with the headers related to the data we are returning 
      long fileLength = 0;
    
      if (!growingFile){
        fileLength = theFile.length();
      }  else {
        fileLength = ((long)Integer.MAX_VALUE)*64;
      }
      
      if (startPosition != 0){
        out.print("HTTP/1.0 206 Partial Content\r\n");
        out.print("Content-Length: " + (fileLength - requestedStartPosition) + "\r\n");
        out.print("CONTENT-RANGE: bytes " + requestedStartPosition + "-" + fileLength + "/" + fileLength + "\r\n");
      } else {
        out.print("HTTP/1.0 200 OK\r\n");
        out.print("Content-Length: " + fileLength + "\r\n");
      }
      
      System.out.println("Sent HTTP response");

      // output the content type
      if (!samsung){
        MimeType mimeType = ContentTypes.getMimeType(ContentTypes.getExtension(fileName));
        out.print("Content-Type: " + mimeType.toString() + "\r\n");
      } else {
        out.print("Content-Type: video/mpeg\r\n");
      }
      
      // standard static headers
      out.print("Accept-Ranges: bytes\r\n");
      out.print("Pragma: no-cache\r\n");
      out.print("Expires:0\r\n");
      out.print("Cache-Control: no-cache, no-store, must-revalidate, max-age=0, proxy-revalidate, no-transform, private\r\n");
      out.print("Server: Michaels Media Sever 0.1\r\n");
      out.print("Connection: keep-alive\r\n");
      
      // Date headers
      Calendar now = Calendar.getInstance();
      String dateString = (new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z")).format(now.getTime());
      out.print("Date: "+ dateString + "\r\n");
      out.print("LAST-MODIFIED: " + dateString + "\r\n");
      System.out.println("Date: "+ dateString);
      System.out.println("LAST-MODIFIED: " + dateString);
            
      // extra headers needed by samsung tvs
      if (samsung) {
        out.print("contentFeatures.dlna.org: DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000\r\n");
        out.print("transferMode.dlna.org: Streaming\r\n");
      }

      // add blank line and make sure everything is flushed out
      out.print("\r\n");
      out.flush();
      
      System.out.println("Finished sending headers");
      System.out.println("Start Position:" + startPosition);
      
      // now actually open the file 
      filein = new FileInputStream(theFile);
      System.out.println("Opened file:" + theFile.getPath());

      // now send the requested bytes themselves.
      boolean markedViewed = false;
      byte[] buf = new byte[FILE_SERVE_INCREMENT];
      long totalBytesRead = 0;
      if (startPosition != 0){
        totalBytesRead = filein.skip(startPosition);
      }

      while(true){
        while(filein.available() >0 ){
          int amountToRead = buf.length;
          if (otaChannel != null){
            tuneSeq = OTASupport.getInstance().watchChannel(otaChannel,tuneSeq);
          }
          if (cableChannel != null){
            tuneSeq = HDPVRSupport.getInstance().watchChannel(cableChannel,tuneSeq);
          }
          int amountRead = filein.read(buf,0,amountToRead);
          if (amountRead >0){
            totalBytesRead = totalBytesRead + amountRead;
            fileOut.write(buf,0,amountRead);
          }
  
          // now check if we should mark this file as viewed.
          if ((!markedViewed)&&(!growingFile)){
            markedViewed = WatchedHandler.watching(theFile);
          }
          fileOut.flush();
        }
        if (!growingFile){
          break;
        }
        Thread.sleep(GROWING_FILE_RETRY_WAIT_MS);
        filein.close();
        // now reopen the file so that we can get what more is now available
        filein = new FileInputStream(theFile);
        filein.skip(totalBytesRead);
      }
      System.out.println("Finished sending file");
      filein.close();
      requestSock.close();
    } catch (Exception e) {
      // this occurs every time the client aborts watching something
      System.out.println("Exception occured while handling request:" + e);
      e.printStackTrace();
    }
  }
}

