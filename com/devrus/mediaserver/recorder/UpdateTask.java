// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.recorder;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import com.devrus.mediaserver.scheduler.XMLTVScheduler;

public class UpdateTask {
  public static void update(String[] extraInfo){
    try {
      String updatedListing = XMLTVScheduler.getListing(ScheduledRecorder.scheduleConfigs, extraInfo[0]);
      Date today = new Date();
      SimpleDateFormat dateFormat = new SimpleDateFormat("dd_MMM_yyyy");
      File origFile = new File(ScheduledRecorder.cronConfig);
      File newFile = new File(ScheduledRecorder.cronConfig + ".tmp");
      File baseFile = new File(ScheduledRecorder.cronConfig + ".base");
      FileOutputStream outfile = new FileOutputStream(newFile);
      PrintStream printout = new PrintStream(outfile);
      System.out.println("Updated schedule:" + new Date());
      
      // copy over the base shows that are not automatically scheduled
      try {
        FileInputStream base = new FileInputStream(baseFile);
        while(base.available() > 0 ) {
          char nextChar = (char) base.read();
          if (nextChar == '$'){
            printout.append(dateFormat.format(today));
          } else {
            printout.append(nextChar);
          }
        }
        base.close();
      } catch (IOException f){
        System.out.println(f);
      }
      
      // now add the schedule itself

      printout.println(updatedListing);
      printout.println(ScheduledRecorder.updateEntry);
      System.out.println(updatedListing);
      System.out.println(ScheduledRecorder.updateEntry);
      System.out.println("");
      printout.flush();
      printout.close();
      ScheduledRecorder.mySched.descheduleFile(origFile);
      try {
        origFile.delete();
      } catch (Exception e) {};
      newFile.renameTo(origFile);
      if (extraInfo.length <= 1){
        ScheduledRecorder.mySched.scheduleFile(origFile);
      }
    } catch (Exception e){
      System.out.println("Failed to update listing");
      e.printStackTrace();
    }
    
  }
}
