package org.hl7.fhir.igtools.publisher;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.NPMPackageGenerator.Category;
import org.hl7.fhir.r4.model.ContactDetail;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ImplementationGuide;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.omg.IOP.TAG_ALTERNATE_IIOP_ADDRESS;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class NPMPackageGenerator {

  public enum Category {
    RESOURCE, OPENAPI, SCHEMATRON, RDF;
    
    private String getDirectory() {
      switch (this) {
      case RESOURCE: return "/package/";
      case OPENAPI: return "/openapi/";
      case SCHEMATRON: return "/xml/";
      case RDF: return "/rdf/";      
      }
      return "/";
    }
  }

  private static final boolean NO_ERRORS = true;

  private String destFile;
  private ImplementationGuide ig;
  private Set<String> created = new HashSet<String>();
  private TarArchiveOutputStream tar;
  
  public NPMPackageGenerator(String destFile, ImplementationGuide ig) throws FHIRException, IOException {
    super();
    this.destFile = destFile;
    this.ig = ig;
    start();
    buildPackageJson();
  }
  
  
  private void buildPackageJson() throws FHIRException, IOException {
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
    if (!ig.hasPackageId())
      b.append("packageId");
    if (!ig.hasVersion())
      b.append("version");
    if (!ig.hasFhirVersion())
      b.append("fhirVersion");
    if (!ig.hasLicense())
      b.append("license");
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      if (!d.hasVersion()) {
        b.append("dependsOn("+d.getUri()+").version");
      }
    }
    if (b.length() > 0)
      throw new FHIRException("In order to be published, an Implementation Guide must have values for the elements: "+b.toString());
    
    JsonObject npm = new JsonObject();
    npm.addProperty("name", ig.getPackageId());
    npm.addProperty("version", ig.getVersion());
    if (ig.hasLicense())
      npm.addProperty("license", ig.getLicense().toCode());
    npm.addProperty("canonical", ig.getUrl());
    if (ig.hasTitle())
      npm.addProperty("title", ig.getTitle());
    if (ig.hasDescription())
      npm.addProperty("description", ig.getDescription());
    JsonObject dep = new JsonObject();
    npm.add("dependencies", dep);
    dep.addProperty("hl7.fhir.core", ig.getFhirVersion());
    for (ImplementationGuideDependsOnComponent d : ig.getDependsOn()) {
      dep.addProperty(d.getPackageId(), d.getVersion());
    }
    if (ig.hasPublisher())
      npm.addProperty("author", ig.getPublisher());
    JsonArray m = new JsonArray();
    for (ContactDetail t : ig.getContact()) {
      String email = email(t.getTelecom());
      String url = url(t.getTelecom());
      if (t.hasName() & (email != null || url != null)) {
        JsonObject md = new JsonObject();
        m.add(md);
        md.addProperty("name", t.getName());
        if (email != null)
          md.addProperty("email", email);
        if (url != null)
          md.addProperty("url", url);
      }
    }
    if (m.size() > 0)
      npm.add("maintainers", m);
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String json = gson.toJson(npm);
    try {
      addFile(Category.RESOURCE, "package.json", json.getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
    }
  }


  private String url(List<ContactPoint> telecom) {
    for (ContactPoint cp : telecom) {
      if (cp.getSystem() == ContactPointSystem.URL)
        return cp.getValue();
    }
    return null;
  }


  private String email(List<ContactPoint> telecom) {
    for (ContactPoint cp : telecom) {
      if (cp.getSystem() == ContactPointSystem.EMAIL)
        return cp.getValue();
    }
    return null;
  }

  private void start() throws IOException {
    FileOutputStream fileOutputStream = new FileOutputStream(destFile);
    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
    GzipCompressorOutputStream gzipOutputStream = new GzipCompressorOutputStream(bufferedOutputStream);
    tar = new TarArchiveOutputStream(gzipOutputStream);
  }


  public void addFile(Category cat, String name, byte[] content) throws IOException {
    String path = cat.getDirectory()+name;
    if (created.contains(path)) 
      System.out.println("Duplicate package file "+path);
    else {
      created.add(path);
      TarArchiveEntry entry = new TarArchiveEntry(path);
      entry.setSize(content.length);
      tar.putArchiveEntry(entry);
      tar.write(content);
      tar.closeArchiveEntry();
    }
  }
  
  public void finish() throws IOException {
    tar.finish();
    tar.close();
  }

  public String filename() {
    return destFile;
  }
}
