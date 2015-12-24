// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.recorder;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.StringTokenizer;
import com.devrus.mediaserver.scheduler.XMLTVScheduler;

import it.sauronsoftware.cron4j.Scheduler;

public class ScheduledRecorder {
  
  private static final String RECORD_DIRECTORY_KEY = "record_dir";
  private static final String COMSKIP_BASE_KEY = "comskip_base";
  private static final String CRON_CONFIG_KEY = "cron_config";
  private static final String COMPLETE_DIR_KEY = "complete_dir";
  private static final String SCHEDULE_CONFIG_KEY = "schedule_configs";
  private static final String UPDATE_KEY = "update_on_start";
  private static final String RUNCOMSKIP_KEY = "run_comskip";
  private static final String SERVER_FOR_LOCAL_RECORDER = "server";
  private static final String CHANNEL_FOR_LOCAL_RECORDER = "channel";
  
  private static final String[] INITIAL_UPDATE_ARGS = {"no","no"};
  
  public static String[] scheduleConfigs;
  public static String updateEntry = "0 4 * * *  \"java:com.devrus.mediaserver.recorder.UpdateTask#update\" yes";
  public static String cronConfig = null;
  public static Scheduler mySched;
  
  public static void main(String[] args) {
    boolean update = false;
    try {
      mySched = new Scheduler();
      
      // read in the configuration for the server
      final Properties configuration = new Properties();
      if ((args.length >0)&&(args[0] != null)&&(!args[0].equals(""))){
        try{
          configuration.load(new FileInputStream(new File(args[0])));
        } catch (Exception e){
          System.out.println("Failed to load configuration file:" + args[0]);
        }
      }

      if (configuration.getProperty(SCHEDULE_CONFIG_KEY) != null){
        StringTokenizer splitter = new StringTokenizer(configuration.getProperty(SCHEDULE_CONFIG_KEY),",");
        int tokens = splitter.countTokens();
        scheduleConfigs = new String[tokens];
        for(int i=0;i<tokens;i++){
          scheduleConfigs[i] = splitter.nextToken();
        }
      }
      
      if (configuration.getProperty(RECORD_DIRECTORY_KEY) != null){
        RecordTask.recordDir = configuration.getProperty(RECORD_DIRECTORY_KEY);
        GenericRecordTask.recordDir = configuration.getProperty(RECORD_DIRECTORY_KEY);
      }
      
      if (configuration.getProperty(COMSKIP_BASE_KEY) != null){
        RecordTask.comskipBase = configuration.getProperty(COMSKIP_BASE_KEY);
        GenericRecordTask.comskipBase = configuration.getProperty(COMSKIP_BASE_KEY);
      }
      
      if (configuration.getProperty(CRON_CONFIG_KEY) != null){
        cronConfig = configuration.getProperty(CRON_CONFIG_KEY);
      }
      
      if (configuration.getProperty(COMPLETE_DIR_KEY) != null){
        RecordTask.completeDir = configuration.getProperty(COMPLETE_DIR_KEY);
        GenericRecordTask.completeDir = configuration.getProperty(COMPLETE_DIR_KEY);
      }
      
      if (configuration.getProperty(UPDATE_KEY) != null){
        if (configuration.getProperty(UPDATE_KEY).equals("yes")){
          update = true;
        }
      }
      
      if (configuration.getProperty(RUNCOMSKIP_KEY) != null){
        if (configuration.getProperty(RUNCOMSKIP_KEY).equals("no")) {
          GenericRecordTask.runComskip = false;
          RecordTask.runComskip = false;
        }
      }
      
      // get the available server for use with the LocalRecordTask
      int index = 0;
      while(true){
        String nextServer = configuration.getProperty(SERVER_FOR_LOCAL_RECORDER + index);
        if (nextServer == null) {
          break;
        }
        int sepLocation = nextServer.indexOf('|');
        if (sepLocation != -1){
          String baseCommand = nextServer.substring(0,sepLocation);
          String tuner = nextServer.substring(sepLocation + 1);
          LocalRecordTask.addServer(baseCommand, tuner);
        }
        index++;
      }
      
      // get the channels available for the LocalRecordTask
      index = 0;
      while(true){
        String nextServer = configuration.getProperty(CHANNEL_FOR_LOCAL_RECORDER + index);
        if (nextServer == null) {
          break;
        }
        int sepLocation = nextServer.indexOf('|');
        if (sepLocation != -1){
          String name = nextServer.substring(0,sepLocation);
          String channel = nextServer.substring(sepLocation + 1);
          GenericRecordTask.addChannel(name, channel);
        }
        index++;
      }
      
      // get the schedule data and write it out to the first config variation
      if (update){
        UpdateTask.update(INITIAL_UPDATE_ARGS);
      }
      mySched.scheduleFile(new File(cronConfig));
      mySched.start();
      System.out.println("Scheduler started");
      while(true){
        Thread.sleep(1000000);
      }
    } catch (Exception e) {
      System.out.println("Failed to initialize crontab");
      e.printStackTrace();
    }
  }
}
