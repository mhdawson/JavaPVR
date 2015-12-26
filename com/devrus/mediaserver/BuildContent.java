// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver;

import java.io.*;
import java.util.*;
import java.net.URLEncoder;
import org.teleal.cling.support.model.*;
import org.teleal.cling.support.contentdirectory.*;
import org.teleal.cling.support.model.item.*;
import org.teleal.common.util.MimeType;
import org.teleal.cling.support.model.container.*;
import com.devrus.mediaserver.ota.*;
import com.devrus.mediaserver.hdpvr.*;

public class BuildContent {
  public static final String NESTED = "_NESTED";
  
  public static final String ROOT_ID = "0";
  public static final String OTA_ROOT = "OTA";
  public static final String OTA_ROOT_NESTED = "OTA" + NESTED;
  public static final String CABLE_ROOT = "Cable";
  public static final String CABLE_ROOT_NESTED = "Cable" + NESTED;
  
  // Constants used in this class
  private static final long  MAX_FILES_IN_DIRECTORY = 1000;
  private static final String WATCHED_TAG = "[watched]";
  
  public enum RootTypes {
    FILE_SYSTEM,
    OTA,
    CABLE
  }
  
  // for handling roots
  private Hashtable<String,RootInfo> rootEntries = new Hashtable<String,RootInfo>();
  
  /**
   * Objects of this type are used to store information about
   * roots 
   */
  private static class RootInfo {
    public RootTypes type;
    public String pathPrefix;
    public boolean nested;
    public RootInfo(RootTypes type, String pathPrefix, boolean nested){
      this.type = type;
      this.pathPrefix = pathPrefix;
      this.nested = nested;
    }
    
    public RootInfo(RootTypes type, String pathPrefix){
      this.type = type;
      this.pathPrefix = pathPrefix;
      this.nested = false;
    }
  }
  
  // used to generate unique update ids
  private static int lastUpdateId = 0;
  
  // holds the information about the host were the files will be served in the form host:port
  private String serverAddress;
  private int serverPort;
  
  // holds root of where video files are stored
  private String root;
  
  // holds the known file types
  private HashMap<String,MimeType> videoTypes = new HashMap<String,MimeType> ();
  
  // used for OTA support
  OTASupport otaSupport;
  
  // used for hdpvr support
  HDPVRSupport hdpvrSupport;
  
  /**
   * This class is used to filter directories so we only return types that
   * we support
   */
  static class DirectoryFilter implements FileFilter {
    
    /**
     * This method determines if a file should be shown or not
     * @param File the file being checked
     * @returns true if the file should be included, false otherwise
     */
    public boolean accept(File dir){
      if (dir.isDirectory()){
        return true;
      }
      if (ContentTypes.getMimeTypeForFile(dir.getPath()) == null){
        return false;
      }
      return true;
    }
  }

  // single directory filter object used every time we need to filter the 
  // contents of a directory
  public static DirectoryFilter dirFilter = new DirectoryFilter();

  
  /** 
   * Constructor
   *  
   * @param hostInfo host information for the host that will server the files
   * @param root path to root where video files are located
   */
  public BuildContent(String serverAddress, int serverPort, String root, HashSet<String> enabledRoots, Properties configuration){
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
    this.root = root;
    createSupportedVideoTypes();
    otaSupport = OTASupport.getInstance(serverAddress,serverPort,configuration);
    hdpvrSupport = HDPVRSupport.getInstance(serverAddress, serverPort,configuration);
    buildRootEntries(enabledRoots);
  }
  
  /**
   * Creates the map used to store the video types that we know about/support
   */
  public void  createSupportedVideoTypes(){
    videoTypes.put("avi", new MimeType("video","divx"));
    videoTypes.put("mpg", new MimeType("video","mpeg"));
    videoTypes.put("mpe", new MimeType("video","mpeg"));
    videoTypes.put("mpeg",new MimeType("video","mpeg"));
    videoTypes.put("ts",  new MimeType("video","mp2t"));
  };
  
  /**
   * Given an id, this builds the content that will be shown for that id
   * 
   * @param id the id to build the content for
   * @return a BrowseResult with the content for the id passed in
   * 
   * @throws Exception if there is a problem building the content
   */
  public BrowseResult buildContentForID(String id, BrowseFlag browseFlag, long firstResult, long maxResults) throws Exception{
    File theFile = null;
    BrowseResult result = null;
    if ((maxResults ==0)||(maxResults != 1)){
      maxResults = MAX_FILES_IN_DIRECTORY;
    }
  
    // first check if this is the root, if so return the contents for the root directories
    if (id.equals(ROOT_ID)){
      if (browseFlag.equals(BrowseFlag.DIRECT_CHILDREN)){
        return BuildRoots();
      } else {
        return buildContentForVideoFile(id, null, browseFlag,firstResult, maxResults);
      }
    }
    
    if (!rootEntries.containsKey(id)){
      String rootName = id.substring(0,id.indexOf("/"));
      RootInfo info = rootEntries.get(rootName);
      if (RootTypes.FILE_SYSTEM == info.type){
        theFile  = new File(root + info.pathPrefix + File.separator + id.substring(id.indexOf("/")+1));
        result =  buildContentForVideoFile(id, theFile, browseFlag, firstResult, maxResults);
      } else if (RootTypes.OTA == info.type) {
        result = otaSupport.buildContentForID(id, browseFlag, firstResult, maxResults,info.nested);
      } else if (RootTypes.CABLE == info.type) {
        result = hdpvrSupport.buildContentForID(id, browseFlag, firstResult, maxResults,info.nested);
      }
    } else {
      // the request was for one of the roots 
      RootInfo info = rootEntries.get(id);
      if (info.type == RootTypes.FILE_SYSTEM){
        theFile = new File(root + info.pathPrefix);
        result = buildContentForVideoFile(id,theFile, browseFlag, firstResult, maxResults);
      } else if (RootTypes.OTA == info.type) {
        result = otaSupport.buildContentForID(id, browseFlag, firstResult, maxResults,info.nested);
      } else if (RootTypes.CABLE == info.type) {
        result = hdpvrSupport.buildContentForID(id, browseFlag, firstResult, maxResults,info.nested);
      }
    }
    return result;
  }

  /**
   * Builds the content requested for a file
   * 
   * @param id the id for the content requested
   * @param directory on disk corresponding to the id requested
   * 
   * @return a BrowseResult with the content for the id passed in
   * 
   * @throws Exception if there is a problem building the content
   */
  public BrowseResult buildContentForVideoFile(String id, File theFile, BrowseFlag browseFlag, long firstResult, long maxResults) throws Exception{
    DIDLContent didl = new DIDLContent();
    HashSet<String> watchedFiles = new HashSet<String>();
    File[]  contents = null;
    String parent = null;
    long count = 0;
    
    if (browseFlag.equals(BrowseFlag.DIRECT_CHILDREN)){
      parent = id;
      // find all of the already watched files in the directory 
      contents = theFile.listFiles();
      for (int i=(int) firstResult;i < contents.length;i++){
        if (!contents[i].isDirectory()){
          if (ContentTypes.getExtension(contents[i].getName()).equals(MichaelsMediaServer.watchedExtension)){
            watchedFiles.add(ContentTypes.removeExtension(contents[i].getName()));
          }
        }
      }
      contents = theFile.listFiles(dirFilter);
    } else {
      /* we are being asked for the meta data about the file itself so simply return the info for the specific file */
      if (!id.equals(ROOT_ID)){
        if (rootEntries.containsKey(id)){
          parent = ROOT_ID;
        } else {
          if (id.lastIndexOf("/") != -1) {
            parent = id.substring(0,id.lastIndexOf("/"));
          }  else {
          }
          contents = new File[1];
          contents[0] = theFile;
          
          // now check if the file has been watched and if so add to watchedFiles
          File watchedFile = new File(ContentTypes.removeExtension(theFile.getAbsolutePath()) + "." + MichaelsMediaServer.watchedExtension);
          if (watchedFile.exists()){
            watchedFiles.add(ContentTypes.removeExtension(contents[0].getName()));
          }
        }
      } 
    }
    
    // list the files in the directory and build the appropriate content, either a container for directories
    // or the link to the file itself for files
    if (contents != null) {
      for (int i=(int) firstResult;i< Math.min(contents.length,maxResults + firstResult);i++){
        if (contents[i].isDirectory()){
          Container newContainer = new Container(parent + "/" + contents[i].getName(),parent,contents[i].getName(),null,new DIDLObject.Class("object.container.storageFolder"),countFiles(new File(contents[i].getPath())));
          newContainer.setWriteStatus(WriteStatus.UNKNOWN);
          newContainer.setSearchable(true);
          didl.addContainer(newContainer);
          count++;
        } else {
          try {
            MimeType mimeType = null;
            String itemName = ContentTypes.removeExtension(contents[i].getName());
            String itemDisplayName = itemName;
            if (watchedFiles.contains(itemDisplayName)){
              if (id.contains("(auto)")){
                continue;
              } else {
                itemDisplayName = itemDisplayName + WATCHED_TAG;
              }
            }
            
            mimeType = ContentTypes.getMimeTypeForFile(contents[i].getName());
            if (mimeType != null){
              String filePath = null;
              if (rootEntries.containsKey(id)){
                filePath = rootEntries.get(id).pathPrefix;
              } else {
                filePath = rootEntries.get(id.substring(0,id.indexOf("/"))).pathPrefix + id.substring(id.indexOf("/")+1);
              }

              Res res = new Res(mimeType,contents[i].length(),"http://" + serverAddress +":" + serverPort + "/" + URLEncoder.encode(filePath.replace(File.separator,"/") + "/" + contents[i].getName()));
              VideoItem newItem = new VideoItem(id + "/" + itemName,parent,itemDisplayName,null,res);
              didl.addItem(newItem);
              count++;
            }
          } catch (Exception e){
            e.printStackTrace();
          }
        } 
      } 
    } else {
      Container newContainer = null;
      if (!id.equals(ROOT_ID)){
        newContainer = new Container(id,parent,id,null,new DIDLObject.Class("object.container"),countFiles(theFile));
      } else {
        newContainer = new Container(id,"",id,null,new DIDLObject.Class("object.container"),rootEntries.size());
      }
      newContainer.setWriteStatus(WriteStatus.UNKNOWN);
      newContainer.setSearchable(true);
      didl.addContainer(newContainer);
      count++;
    }
    return new BrowseResult(new DIDLParser().generate(didl,true), count, count, getUpdateId());

  }

  /**
   * Builds the root directories
   * 
   * @return a BrowseResult with the content for the root directories
   * 
   * @throws Exception if there is a failure building the content
   */
  public BrowseResult BuildRoots() throws Exception {
    DIDLContent didl = new DIDLContent();
    
    Iterator<String> theRoots = rootEntries.keySet().iterator();
    long numberRoots = rootEntries.size();
    while(theRoots.hasNext()){
      String nextRootKey = theRoots.next();
      RootInfo info = rootEntries.get(nextRootKey);
      int fileCount = 0;
      if (info.type == RootTypes.FILE_SYSTEM) {
        fileCount = countFiles(new File(root + info.pathPrefix));
      } else if (info.type == RootTypes.OTA){
        fileCount = otaSupport.numberChannels();
      } else if (info.type == RootTypes.CABLE){
        hdpvrSupport.numberChannels();
      }
      Container newContainer = new Container(nextRootKey,ROOT_ID,nextRootKey,null,new DIDLObject.Class("object.container"),fileCount);
      newContainer.setWriteStatus(WriteStatus.UNKNOWN); 
      newContainer.setSearchable(true);
      didl.addContainer(newContainer);
    }
    return new BrowseResult(new DIDLParser().generate(didl,true), numberRoots, numberRoots, getUpdateId());
  }

  /**
   * Counts the files in a directory
   * 
   * @param theFile the file for the directory
   * @return the number of entries in the directory
   * 
   * @throws Exception if there is problem getting the contents of the directory 
   */
  public int countFiles(File theFile) throws Exception {
    File[]  contents = theFile.listFiles(dirFilter);
    int count = 0;
    for (int i=0;i<contents.length;i++){
      if (contents[i].isDirectory()){
        count++;
      } else {
        MimeType mimeType = ContentTypes.getMimeTypeForFile(contents[i].getName());
        if (mimeType != null){
          count++;
        }
      } 
    } 
    return count;
  }
  
  
  /**
   * This method returns a unique id, it rools at the maximum positive integer value
   * @return unique integer
   */
  public static synchronized int getUpdateId(){
    lastUpdateId++;
    if (lastUpdateId <0){
      lastUpdateId = 0;
    }
    return lastUpdateId;
  }

  
  
  /**
   * Builds the list of roots that will be displayed by the server
   * 
   * @param enabledRoots HashSet containing the roots to be enabled
   */
  void buildRootEntries(HashSet<String> enabledRoots){
    if (enabledRoots.contains("Video")){
      rootEntries.put("Video", new RootInfo(RootTypes.FILE_SYSTEM,"/"));
    }
    
    if (enabledRoots.contains("New")){
      rootEntries.put("New", new RootInfo(RootTypes.FILE_SYSTEM,"/" + "download" + "/" + "done/"));
    }
    
    if (enabledRoots.contains("New(auto)")){
      rootEntries.put("New(auto)", new RootInfo(RootTypes.FILE_SYSTEM,"/" + "download" + "/" + "done/"));
    } 
    
    if (enabledRoots.contains(OTA_ROOT)){
      rootEntries.put(OTA_ROOT, new RootInfo(RootTypes.OTA,null));
    }
    
    if (enabledRoots.contains(OTA_ROOT_NESTED)){
      rootEntries.put(OTA_ROOT_NESTED, new RootInfo(RootTypes.OTA,null,true));
    }
    
    if (enabledRoots.contains(CABLE_ROOT)){
      rootEntries.put(CABLE_ROOT, new RootInfo(RootTypes.CABLE,null));
    }
    
    if (enabledRoots.contains(CABLE_ROOT_NESTED)){
      rootEntries.put(CABLE_ROOT_NESTED, new RootInfo(RootTypes.CABLE,null,true));
    }
  };
  
  
  

}

