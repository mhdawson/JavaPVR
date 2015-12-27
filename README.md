# JavaPVR

This project contains 3 main components

* Recorder which records shows from HDHomerun devices on the local network in mpeg2
* Upnp server which serves shows to DNLA clients
* cleaner which strips commercials from shows and compresses them to a reasonable size

# Recorder

The recorder uses data in the XMLTV fromat from [Shedules Direct](www.schedulesdirect.org) 
to shedule recordings and then record them using IP network based HDHomeRun devices from
silicondust (and others like HDPVR if you want). The recorder simply takes a list of 
show names and then finds/schedules all "new" episodes for that show. Guide data is
downloaded nightly and used to update the currently scheduled recordings

# Cleaner

The cleaner walks a top level directory recursively looking for recorded content. 
For each show it finds it uses [comskip](www.kaashoek.com/comskip) to identify
commercials in the show and the simplyl cuts out the byte ranges corresponding
to the commercials.  This works because the mpe2 format is resiliant to parts
of the streams being removed.  Once the commercials are stripped out the 
cleaner them compersses the show using [handbrake](https://handbrake.fr) and
places them in the final directory for viewing

# UPnP server

The UPnP server servers the recorder shows to DLNA clients.  It has a few extra
features like identifying which shows have already been watched.  I use multiple
instances of the server and have each family member use a different instance so
that we can each see which shows we have already watched/not watched. In addition
the server provides a "virtual" directory for new content where only those
shows which have not already been watched show up

# TODO

Much more documentation on how to configure/build/setup 



 
