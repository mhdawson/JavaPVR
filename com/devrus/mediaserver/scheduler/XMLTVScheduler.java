// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.scheduler;

import org.w3c.dom.*;

import javax.swing.text.DateFormatter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

//xmltv tv_grab_na_dd --output sched.xml
public class XMLTVScheduler {
  
  private static final int  OK = 0;
  private static final int  FAILED_TO_GET_SCHED_DATA = -1;
  private static final int  NO_RECORD_TARGET = -2;
  private static final int  NO_RECORD_CLASS = -3;

  private static final String  SHOW_PLACEHOLDER = "place";
  
  private static final String DEFAULT_XMLTV_COMMAND = "xmltv tv_grab_na_dd --output";
  private static final String XMLTV_SCHED_CONFIG_DEFAULT  = "tv_grab_na_dd-ota.conf";
  private static final String XMLTV_SCHED_FILE_DEFAULT = "schednew.xml";
  
  private static final String XMLTV_COMMAND_KEY = "xmltv_command";
  private static final String XMLTV_DIR_KEY = "xmltv_dir";
  private static final String CHANNEL_KEY = "channel";
  private static final String SHOW_KEY = "show";
  private static final String XMLTV_SCHED_CONFIG_KEY = "xmltv_config";
  private static final String RECORD_TARGET_KEY = "target";
  private static final String RECORD_CLASS_KEY = "recordClass";
  private static final String TEMPFILNAME_KEY = "sched_temp_file";
  private static final String RECORD_TARGET_EXTRA_KEY = "target_extra";
  public static final long EXTRA_RECORD_TIME = 1;
  

  Hashtable<String,String> channelList;
  Hashtable<String,String> myChannelList;
  Properties configuration;
  String xmltvDir;
  String xmltvCommand;
  String xmltvSchedFile;
  String xmltvConfig;
  HashSet<String> myPrograms;
  String target;
  String schedTempFile;
  String targetExtra;
  StringBuilder listing = new StringBuilder();
  String recordClass = null;
  
  public static void main(String[] args) {
    
    String listing = "";
    try {
      int current = 1;
      while(current < args.length){
        XMLTVScheduler sched = new XMLTVScheduler(args[current],args[0]);
        listing = listing + sched.generateListing();
        current++;
      }
    } catch (Exception e){
      e.printStackTrace();
    }
    
    System.out.println(listing);
  }
  
  public static String getListing(String[] configs, String getData) throws Exception{
    String listing = "";
    for (int i=0;i<configs.length;i++){
      XMLTVScheduler sched = new XMLTVScheduler(configs[i],getData);
      listing = listing + sched.generateListing();
    }
    return listing;
  }
  
  public XMLTVScheduler(String configFile, String getData) throws Exception{
    if (loadConfiguration(configFile) != OK){
      throw new Exception();
    }
    
    if (getData.equals("yes")){
      if (getSchedData() != OK){
        throw new Exception();
      }
    }
  }
  
  public String generateListing() {
    
    try {
      // we consider a program stale if it was previoulsy shown in the last 30 days 
      // which is 1000 ms * 60 sec * 60 min * 24 hours * 14 days
      Date staleDate = new Date(System.currentTimeMillis() - (1000 * 60 * 60 * 24 * 30));
      
      // format of start time in previoulsy viewed element 
      SimpleDateFormat startPreviouslyViewedFormat = new SimpleDateFormat("yyyyMMddHHmmss");

      DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = builderFactory.newDocumentBuilder();
      
      Document dom = builder.parse(xmltvSchedFile);
      Element root = dom.getDocumentElement();
      buildChannelList(root);
      NodeList list = root.getElementsByTagName("programme");
      for (int i=0;i<list.getLength();i++){
        Element prog = (Element) list.item(i);
        NodeList programNameElement = prog.getElementsByTagName("title");
        String programName = programNameElement.item(0).getTextContent();
        if (myPrograms.contains(programName)){
          String channel = channelList.get(prog.getAttribute("channel"));
          if (channel != null){
            NodeList previouslyViewed = prog.getElementsByTagName("previously-shown");
            boolean repeat = false;
            if (previouslyViewed.getLength() != 0) {
              try { 
                for (int j = 0; j < previouslyViewed.getLength(); j++) {
                  String startString = ((Element) previouslyViewed.item(j)).getAttribute("start");
                  if ((startString != null) && (!startString.equals(""))) {
                    Date previouslyShownTime = startPreviouslyViewedFormat.parse(startString);
                    if (previouslyShownTime.before(staleDate)) { 
                      repeat = true;
                      break;
                    }
                  } else {
                    // if there is no start time then we must just assume it is stale
                    repeat = true;
                    break;
                  }
                }
              } catch (Throwable t) {
                // in case of a format we don't understand just treat as if is did not have a 
                // start date
                repeat = true;
                t.printStackTrace();
              }
            } 
            
            // if this is a repeat don't add it to the list
            if (repeat) {
              continue;
            }
            
            NodeList subtitleList = prog.getElementsByTagName("sub-title");
            String subTitle = "";
            if (subtitleList.getLength() >0){
              subTitle = ((Element)subtitleList.item(0)).getTextContent();
            }
            
            // get start and stop times
            SimpleDateFormat inFormat = new SimpleDateFormat("yyyyMMddHHmmss Z");
            SimpleDateFormat format = new SimpleDateFormat("m H d M *");
            SimpleDateFormat nameFormat = new SimpleDateFormat("MMddHHmm");
            String start = prog.getAttribute("start");
            String stop = prog.getAttribute("stop");
            Date startTime = inFormat.parse(start);
            Date endTime = inFormat.parse(stop);
            long length = EXTRA_RECORD_TIME + (endTime.getTime() - startTime.getTime())/(60*1000);
            
            // create title info
            String title = clean(programName);
            String fullTitle = clean(title + nameFormat.format(startTime) + "-" + subTitle );

            listing.append(format.format(startTime) + " \"java:com.devrus.mediaserver.recorder." + recordClass + "#record\" " + title + " " + fullTitle + target + channel + " " + length + " " + targetExtra);
            listing.append("\n");
          }
        }
      }
    } catch (Exception e){
      e.printStackTrace();
    }
    return (listing.toString());
  }
  
  
  public void buildChannelList(Element root ) throws Exception{
    NodeList list = root.getElementsByTagName("channel");
    for (int i=0;i<list.getLength();i++){
      Element channel = (Element) list.item(i);
      String id = channel.getAttribute("id");
      String name = channel.getElementsByTagName("display-name").item(0).getTextContent();
      Iterator<String> myChannels = myChannelList.keySet().iterator();
      while(myChannels.hasNext()){
        String next = myChannels.next();
        if (name.contains(next)){
          channelList.put(id, myChannelList.get(next));
          System.out.println(myChannelList.get(next));
          break;
        }
      }
    }
  }
  
  public static String clean(String in){
    return in.replace("'","").replace(",","").replace(".","").replace(" ","_").replace(":","").replace("?","").replace("!","").replace("/","").replace(";","").replace("\\","");
  }
  
  /**
   * Executes the command to pull schedule data from Schedules direct using xmltv and
   * stores in local file.
   * @return
   */
  public int getSchedData(){
    System.out.println(xmltvCommand);
    try {
      Process theProcess = Runtime.getRuntime().exec(xmltvCommand);
      theProcess.getOutputStream().close();
      final InputStream theError = theProcess.getErrorStream();
      final InputStream theInput = theProcess.getErrorStream();

      // start thread to print the output
      Thread outputThread = new Thread() {
        public void run() {
          try {
            while(true){
              int nextChar = theInput.read();
              if (nextChar == -1 ){
                break;
              }
              System.out.print((char) nextChar);
            }
            
            while(true){
              int nextChar = theError.read();
              if (nextChar == -1 ){
                break;
              }
              System.out.print((char) nextChar);
            }
          } catch (Exception e) {
          }
        }
      };
      outputThread.start();
        
      // now wait for xmltv to complete
      int result = theProcess.waitFor();

      theInput.close();
      theError.close();
      
      if (result != 0){
        System.out.println("Failed to get schedule data from shedules direct");
        return FAILED_TO_GET_SCHED_DATA;
      }
    } catch (Exception e){
      System.out.println("Exception getting xmltv data:" + e);
      e.printStackTrace();
    }
    return OK;
  }
  
  /**
   * loads the configuration from the properties file in args[0];
   * 
   * @param args 
   */
  public int loadConfiguration(String configFile){
    channelList = new Hashtable<String,String>();
    myChannelList = new Hashtable<String,String>();
    configuration = new Properties();
    xmltvDir = null;
    xmltvCommand = DEFAULT_XMLTV_COMMAND;
    xmltvSchedFile = null;
    xmltvConfig = XMLTV_SCHED_CONFIG_DEFAULT;
    myPrograms = new HashSet<String>();
    target = null;
    schedTempFile = XMLTV_SCHED_FILE_DEFAULT;
    targetExtra = "";
    
    
    try{
      configuration.load(new FileInputStream(new File(configFile)));
    } catch (Exception e){
      System.out.println("Failed to load configuration file:" + configFile);
    }
    
    if (configuration.getProperty(RECORD_TARGET_KEY) != null){
      target = " " + configuration.getProperty(RECORD_TARGET_KEY);
    } else {
      System.out.println("Configuration file does not contain record target, aborting !");
      return NO_RECORD_TARGET;
    }
    
    if (configuration.getProperty(RECORD_CLASS_KEY) != null){
      recordClass = configuration.getProperty(RECORD_CLASS_KEY);
    } else {
      System.out.println("Configuration file does not contain record Class, aborting !");
      return NO_RECORD_CLASS;
    }
    
    if (configuration.getProperty(RECORD_TARGET_EXTRA_KEY) != null){
      targetExtra = configuration.getProperty(RECORD_TARGET_EXTRA_KEY);
    }
    
    if (configuration.getProperty(TEMPFILNAME_KEY) != null){
      schedTempFile = configuration.getProperty(TEMPFILNAME_KEY);
    } 
    
    if (configuration.getProperty(XMLTV_SCHED_CONFIG_KEY) != null){
      xmltvConfig = configuration.getProperty(XMLTV_SCHED_CONFIG_KEY);
    }
      
    if (configuration.getProperty(XMLTV_DIR_KEY) != null){
      xmltvDir = configuration.getProperty(XMLTV_DIR_KEY);
    }
    xmltvSchedFile = xmltvDir + File.separator + schedTempFile;
  
    if (configuration.getProperty(XMLTV_COMMAND_KEY) != null){
      xmltvCommand = configuration.getProperty(XMLTV_COMMAND_KEY);
    }
    xmltvCommand = xmltvDir + File.separator + xmltvCommand + " " + xmltvSchedFile + " --share " + xmltvDir + " --config " + xmltvDir + File.separator + xmltvConfig;
    
    
    // get the channels that we support, this maps the id we get from schedules direct back to the
    // id that we have to pass to the recorder
    int channel = 1;
    while(true) {
      String nextChannel = configuration.getProperty(CHANNEL_KEY + channel);
      if (nextChannel == null){
        break;
      }
      String channelId = nextChannel.substring(0,nextChannel.indexOf(":"));
      String channelTag = nextChannel.substring(nextChannel.indexOf(":")+1);
      myChannelList.put(channelId,channelTag);
      channel++;
    }
    
    // get the list of shows we are interested in 
    int show = 1;
    while(true) {
      String nextShow = configuration.getProperty(SHOW_KEY + show);
      if (nextShow == null){
        break;
      }
      if (!nextShow.equals(SHOW_PLACEHOLDER)){
        myPrograms.add(nextShow);
      }
      show++;
    }
    
    return OK;
  }
}
