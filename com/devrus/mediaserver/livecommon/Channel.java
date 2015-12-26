// Copyright 2012-2015 the project authors as listed in the AUTHORS file.
// All rights reserved. Use of this source code is governed by the
// license that can be found in the LICENSE file.

package com.devrus.mediaserver.livecommon;

/**
 * Class used to store information about channels
 */
public class Channel {
  public String name;
  public String number;
  
  public Channel(String name, String number){
    this.name = name;
    this.number = number;
  }
}
