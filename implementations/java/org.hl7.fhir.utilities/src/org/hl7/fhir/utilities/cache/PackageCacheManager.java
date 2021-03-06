package org.hl7.fhir.utilities.cache;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;

import com.google.gson.JsonObject;

/**
 * Package cache manager
 * 
 * API:
 * 
 * constructor
 *  getPackageUrl
 *  getPackageId
 *  findPackageCache 
 *  addPackageToCache  
 * 
 * @author Grahame Grieve
 *
 */
public class PackageCacheManager {

  /**
   * info and loader for a package 
   * 
   * Packages may exist on disk in the cache, or purely in memory when they are loaded on the fly
   * 
   * Packages are contained in subfolders (see the package spec). The FHIR resources will be in "package"
   * 
   * @author Grahame Grieve
   *
   */
  public class PackageInfo {

    private String path;
    public List<String> folders = new ArrayList<String>();
    public Map<String, byte[]> content = new HashMap<String, byte[]>();
    public JsonObject npm;

    public PackageInfo(String path) {
      this.path = path;
    }
    
    /**
     * Accessing the contents of the package - get a list of files in a subfolder of the package 
     *
     * @param folder
     * @return
     * @throws IOException 
     */
    public List<String> list(String folder) throws IOException {
      List<String> res = new ArrayList<String>();
      if (path != null) {
        for (String s : new File(Utilities.path(path, folder)).list())
          res.add(s);
      } else {
        for (String s : content.keySet()) {
          if (s.startsWith(folder+"/"))
            res.add(s.substring(folder.length()+1));
        }
      }
      return res;
    }

    /**
     * get a stream that contains the contents of one of the files in a folder
     * 
     * @param folder
     * @param file
     * @return
     * @throws IOException
     */
    public InputStream load(String folder, String file) throws IOException {
      if (content.containsKey(folder+"/"+file))
        return new ByteArrayInputStream(content.get(folder+"/"+file));
      else {
        File f = new File(Utilities.path(path, folder, file));
        if (f.exists())
          return new FileInputStream(f);
        throw new IOException("Unable to find the file "+folder+"/"+file+" in the package "+name());
      }
    }

    /**
     * Handle to the package json file
     * 
     * @return
     */
    public JsonObject getNpm() {
      return npm;
    }
    
    /**
     * convenience method for getting the package name
     * @return
     */
    public String name() {
      return npm.get("name").getAsString();
    }

    /**
     * convenience method for getting the package version
     * @return
     */
    public String version() {
      return npm.get("version").getAsString();
    }

    /**
     * convenience method for getting the package fhir version
     * @return
     */
    public String fhirVersion() {
      return npm.getAsJsonObject("dependencies").get("hl7.fhir.core").getAsString();
    }

    public String description() {
      if (path != null)
        return path;
      else
        return "memory";
    }
   
  }

  public static final String PACKAGE_REGEX = "^[a-z][a-z0-9\\-]*(\\.[a-z0-9\\-]+)+[a-z0-9\\-]$";
  public static final String PACKAGE_VERSION_REGEX = "^[a-z][a-z0-9\\-]*(\\.[a-z0-9\\-]+)+[a-z0-9\\-]:[a-z0-9\\-]+(\\.[a-z0-9\\-]+)*$";

  private static final int BUFFER_SIZE = 1024;
  private static final String CACHE_VERSION = "1"; // first version

  private String cacheFolder;
  
  public PackageCacheManager(boolean userMode) throws IOException {
    if (userMode)
      cacheFolder = Utilities.path(System.getProperty("user.home"), ".fhir", "packages");
    else
      cacheFolder = Utilities.path("var", "lib", ".fhir", "packages");
    if (!(new File(cacheFolder).exists()))
      Utilities.createDirectory(cacheFolder);
    if (!(new File(Utilities.path(cacheFolder, "packages.ini")).exists()))
      TextFile.stringToFile("[urls]\r\n\r\n[local]\r\n\r\n", Utilities.path(cacheFolder, "packages.ini"));  
    IniFile ini = new IniFile(Utilities.path(cacheFolder, "packages.ini"));
    if (!CACHE_VERSION.equals(ini.getStringProperty("cache", "version")))
      clearCache();
    ini.setStringProperty("cache", "version", CACHE_VERSION, null);
    ini.save();
  }
  

  private void clearCache() throws IOException {
    for (File f : new File(cacheFolder).listFiles()) {
      if (f.isDirectory())
        FileUtils.deleteDirectory(f);
      else if (!f.getName().equals("packages.ini"))
        FileUtils.forceDelete(f);
    }    
  }


  public void recordMap(String url, String id) throws IOException {
    IniFile ini = new IniFile(Utilities.path(cacheFolder, "packages.ini"));
    ini.setStringProperty("urls", id, url, null);
    ini.save();
  }

  public String getPackageUrl(String id) throws IOException {
    IniFile ini = new IniFile(Utilities.path(cacheFolder, "packages.ini"));
    return ini.getStringProperty("urls", id); 
  }
  
  public String getPackageId(String url) throws IOException {
    IniFile ini = new IniFile(Utilities.path(cacheFolder, "packages.ini"));
    String[] ids = ini.getPropertyNames("urls");
    if (ids != null) {
      for (String id : ids) {
        if (url.equals(ini.getStringProperty("urls", id)))
          return id;
      }
    }
    return null;
  }
  
  private List<String> sorted(String[] keys) {
    List<String> names = new ArrayList<String>();
    for (String s : keys)
      names.add(s);
    Collections.sort(names);
    return names;
  }

  public PackageInfo loadPackageCacheLatest(String id) throws IOException {
    String match = null;
    List<String> l = sorted(new File(cacheFolder).list());
    for (int i = l.size()-1; i >= 0; i--) {
      String f = l.get(i);
      if (f.startsWith(id+"-")) {
        return loadPackageInfo(Utilities.path(cacheFolder, f)); 
      }
    }
    return null;    
  }
  
  public PackageInfo loadPackageCache(String id, String version) throws IOException {    
    String match = null;
    for (String f : sorted(new File(cacheFolder).list())) {
      if (f.equals(id+"-"+version)) {
        return loadPackageInfo(Utilities.path(cacheFolder, f)); 
      }
    }
    return null;
  }
  
  private PackageInfo loadPackageInfo(String path) throws IOException {
    PackageInfo pi = new PackageInfo(path);
    for (String f : sorted(new File(path).list())) {
      if (new File(Utilities.path(path, f)).isDirectory()) {
        pi.folders.add(f); 
      }
    }
    pi.npm = (JsonObject) new com.google.gson.JsonParser().parse(TextFile.fileToString(Utilities.path(path, "package", "package.json")));
    return pi;
  }

  public PackageInfo addPackageToCache(String id, String version, InputStream tgz) throws IOException {
    String packRoot = Utilities.path(cacheFolder, id+"-"+version);
    Utilities.createDirectory(packRoot);
    Utilities.clearDirectory(packRoot);
    
    GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(tgz);
    try (TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {
      TarArchiveEntry entry;

      while ((entry = (TarArchiveEntry) tarIn.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          File f = new File(Utilities.path(packRoot, entry.getName()));
          if (!f.mkdir()) 
            throw new IOException("Unable to create directory '%s', during extraction of archive contents: "+ f.getAbsolutePath());
        } else {
          int count;
          byte data[] = new byte[BUFFER_SIZE];
          String fn = Utilities.path(packRoot, entry.getName());
          String dir = Utilities.getDirectoryForFile(fn);
          if (!(new File(dir).exists()))
            Utilities.createDirectory(dir);
          
          FileOutputStream fos = new FileOutputStream(fn, false);
          try (BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER_SIZE)) {
            while ((count = tarIn.read(data, 0, BUFFER_SIZE)) != -1) {
              dest.write(data, 0, count);
            }
          }
        }
      }
    }  
    return loadPackageInfo(packRoot);
  }

  public PackageInfo addPackageToCache(InputStream tgz) throws IOException {
    PackageInfo pi = addPackageToCache("temp", "temp", tgz);
    String actual = Utilities.path(cacheFolder, pi.getNpm().get("name").getAsString()+"-"+pi.getNpm().get("version").getAsString());
    File dst = new File(actual);
    int i = 0;
    while (!(new File(pi.path).renameTo(dst))) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
      }
      i++;
      if (i == 10)
        throw new IOException("Unable to rename from "+pi.path+" to "+actual);        
    }
    pi.path = actual;
    return pi;
  }


  public PackageInfo extractLocally(String filename) {
    throw new Error("Not done yet");
  }

  public PackageInfo extractLocally(InputStream src) {
    throw new Error("Not done yet");
  }

  public String getFolder() {
    return cacheFolder;
  }
  
}
