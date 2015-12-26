// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.net.*;
import java.util.*;
import java.io.*;

import org.teleal.cling.model.*;
import org.teleal.cling.model.meta.*;
import org.teleal.cling.model.types.*;
import org.teleal.cling.support.model.*;
import org.teleal.cling.support.contentdirectory.*;
import org.teleal.cling.binding.annotations.*;
import org.teleal.cling.support.connectionmanager.*;
import org.teleal.cling.UpnpServiceImpl;

/**
 * This class is the main entry point for the media server.  
 *
 */
public class MichaelsMediaServer extends AbstractContentDirectoryService {
  // externally configurable properties
  public static final String SERVER_NAME = "server_name";
  public static final String UNIQUE_ID = "unique_id";
  public static final String SERVER_PORT = "server_port";
  public static final String NETWORK_MASK = "network_mask";
  public static final String ENABLED_ROOTS = "enabled_roots";
  public static final String VIEWING_THRESHHOLD = "viewing_threshhold";
  public static final String FILE_SERVER_ROOT = "file_server_root";
  public static final String WATCHED_EXTENTION_KEY = "watched_extension";
  
  // defaults
  public static final String DEFAULT_SERVER_NAME = "MichaelsMediaServer";
  public static final String DEFAULT_UNIQUE_ID = "284FDFA0-3FEA-11E1-BD89-A82C4924019B";
  public static final long DEVAULT_VIEWING_THRESHOLD_IN_SECONDS = 120;
  public static final int DEFAULT_SERVER_PORT = 25000;
  public static final String DEFAULT_SERVER_NET_MASK = "10.";
  public static final String DEFAULT_FILE_SERVER_ROOT = "t:";
  public static final String DEFAULT_ROOTS = "New Video New(auto)" + BuildContent.CABLE_ROOT + " " + BuildContent.OTA_ROOT;

  // constants
  static  final ProtocolInfos sourceProtocols =  new ProtocolInfos(new ProtocolInfo("http-get:*:*:*"));   
    
  // static variables used by  the server
  static public int serverPort = DEFAULT_SERVER_PORT;
  static private String fileServerRoot = DEFAULT_FILE_SERVER_ROOT;
  static public String watchedExtension = ContentTypes.WATCHED_EXTENSION;

  // object variables
  private BuildContent contentBuilder = null;
  private String serverAddress;
  

  /**
   * Constructor for the media server object 
   * 
   * @param configuration Properties object with the configuration entries for the server
   */
  public MichaelsMediaServer(Properties configuration) {
    // setup the roots
    String enabledRoots = "";
    if (configuration.getProperty(ENABLED_ROOTS)!= null){
      enabledRoots = configuration.getProperty(ENABLED_ROOTS);
    }
        
    // figure out the address for the server
    String serverNetMask = DEFAULT_SERVER_NET_MASK;
    if (configuration.getProperty(NETWORK_MASK) != null){
      serverNetMask =configuration.getProperty(NETWORK_MASK);
    }
    serverAddress = getServerAddress(serverNetMask);
    
    // ok create the object that will generate the content for the server
    contentBuilder = new BuildContent(serverAddress,serverPort, fileServerRoot, getEnabledRoots(enabledRoots),configuration);
  }

  public static void main(String[] args) {
    String serverName = DEFAULT_SERVER_NAME;
    String uniqueId =   DEFAULT_UNIQUE_ID;
    long viewingThreshhold = DEVAULT_VIEWING_THRESHOLD_IN_SECONDS;
    
    // read in the configuration for the server
    final Properties configuration = new Properties();
    if ((args.length >0)&&(args[0] != null)&&(!args[0].equals(""))){
      try{
        configuration.load(new FileInputStream(new File(args[0])));
      } catch (Exception e){
        System.out.println("Failed to load configuration file:" + args[0]);
      }
    }
    
    if (configuration.getProperty(UNIQUE_ID) != null){
      uniqueId = configuration.getProperty(UNIQUE_ID);
    }
    
    if (configuration.getProperty(SERVER_NAME) != null){
      serverName = configuration.getProperty(SERVER_NAME);
    }
    
    if (configuration.getProperty(VIEWING_THRESHHOLD) != null){
      try {
        viewingThreshhold = Long.parseLong(configuration.getProperty(VIEWING_THRESHHOLD));
      } catch (NumberFormatException e){
        System.out.println("Invalid " + VIEWING_THRESHHOLD + " in configuration file");
      }
    }
    
    if (configuration.getProperty(SERVER_PORT) != null){
      try {
        serverPort = Integer.parseInt(configuration.getProperty(SERVER_PORT));
      } catch (NumberFormatException e){
        System.out.println("Invalid " + SERVER_PORT + " in configuration file");
      }
    }
    
    if (configuration.getProperty(FILE_SERVER_ROOT) != null){
      fileServerRoot = configuration.getProperty(FILE_SERVER_ROOT);
    }
    
    if (configuration.getProperty(WATCHED_EXTENTION_KEY) != null){
      watchedExtension = configuration.getProperty(WATCHED_EXTENTION_KEY);
    }
    
    try {
      final MichaelsMediaServer myserver = new MichaelsMediaServer(configuration);
      LocalService<AbstractContentDirectoryService> service =  new AnnotationLocalServiceBinder().read(AbstractContentDirectoryService.class);
       service.setManager(new DefaultServiceManager<AbstractContentDirectoryService>(service, null) {
         @Override
         protected AbstractContentDirectoryService createServiceInstance() throws Exception {
           return myserver;
         }
       });
  
  
      LocalService<ConnectionManagerService> connService = new AnnotationLocalServiceBinder().read(ConnectionManagerService.class);
      connService.setManager(new DefaultServiceManager<ConnectionManagerService>(connService,null) {
        @Override
        protected ConnectionManagerService createServiceInstance() throws Exception {
          return new ConnectionManagerService(sourceProtocols,null);
        }
      });
  
              
      LocalService[] services = {service, connService};
      LocalDevice device = new LocalDevice(new DeviceIdentity(new UDN(uniqueId)),
                                           new UDADeviceType("MediaServer", 1),
                                           new DeviceDetails(serverName, new ManufacturerDetails("MHD Industries"), new ModelDetails("must be here")),      
                                           services); 
  
      UpnpServiceImpl stack = new UpnpServiceImpl();
      stack.getRegistry().addDevice(device);
  
      // ok start the threads which accept connection and server up video files
      WatchedHandler.setViewingThreshhold(viewingThreshhold);
      ListenerThread listener = ListenerThread.startListener(serverPort, fileServerRoot);
    } catch (Throwable t){
      System.out.println("Exception:" + t);
    } 

  };

  public BrowseResult browse(String objectID, BrowseFlag browseFlag,
                             String filter,
                             long firstResult, long maxResults,
                             SortCriterion[] orderby) throws ContentDirectoryException {
        try {
            System.out.println("BrowseRequest Object ID[" + objectID + "] browseFlag ["  + browseFlag + "] filter [" + 
                    filter + "] firstResult[" + firstResult + "] maxResults[" + maxResults + "] orderby[" + orderby +"]");
            return contentBuilder.buildContentForID(objectID,browseFlag, firstResult, maxResults);

        } catch (Exception ex) {
          System.out.println("Exception occured:" + ex);
          ex.printStackTrace();
            throw new ContentDirectoryException(
                    ContentDirectoryErrorCode.CANNOT_PROCESS,
                    ex.toString()
            );
        }

    }
  
  public void contentUpdated(){
    super.changeSystemUpdateID();
  }
  
  /**
   * This method gets the fist local address which is the default address we use for the server
   * @return
   */
  public String getServerAddress(String mask) {
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
            if (address.getHostAddress().startsWith(mask)){
              return address.getHostAddress();
            }
          }
        }
      }
    } catch (SocketException e){
      System.out.println("Exception obtaining server address:" + e );
    }
    return null;
  }
  
  /**
   * This method returns a HashSet with the roots that will be enabled, given a string
   * listing the roots to be included. If the string is null then the default set of roots
   * is used
   * 
   * @param roots string listing roots to be enabled
   * @return HashSet with roots that are enabled
   */
  public HashSet<String> getEnabledRoots(String roots){
    HashSet<String> enabledRoots = new HashSet<String>();
    if ((roots == null)||(roots.equals(""))){
      roots = DEFAULT_ROOTS;
    }
    String[] rootStrings = roots.split("\\s+");
    for (int i=0; i < rootStrings.length; i++){
      enabledRoots.add(rootStrings[i]);
    }
    
    return enabledRoots;
  }

}
