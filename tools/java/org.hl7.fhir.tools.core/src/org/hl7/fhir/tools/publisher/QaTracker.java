package org.hl7.fhir.tools.publisher;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.definitions.model.Definitions;
import org.hl7.fhir.definitions.model.ElementDefn;
import org.hl7.fhir.definitions.model.ResourceDefn;
import org.hl7.fhir.instance.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.instance.validation.ValidationMessage;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Utilities;

public class QaTracker {

  private class SnapShot {
    private int resources; 
    private int types; 
    private int packs; 
    private int paths;
    private int bindings;
    private int codelists;
    private int valuesets;
    private int codes;
    
    private int hints;
    private int warnings;
  }
  
  
  private SnapShot current = new SnapShot();
  
  private Map<Date, SnapShot> records = new HashMap<Date, SnapShot>();
  
  public void countDefinitions(Definitions definitions) throws Exception {
    current.resources = definitions.getResources().size();
    current.types = definitions.getResources().size() + definitions.getTypes().size() + definitions.getStructures().size()  
         + definitions.getShared().size() + definitions.getPrimitives().size()+ definitions.getInfrastructure().size();
    current.packs = definitions.getPackList().size();
    
    for (ResourceDefn r : definitions.getResources().values())
      countPaths(r.getRoot());
    for (ElementDefn e : definitions.getTypes().values())
      countPaths(e);
    for (ElementDefn e : definitions.getStructures().values())
      countPaths(e);
    for (String e : definitions.getShared())
      countPaths(definitions.getElementDefn(e));
    for (ElementDefn e : definitions.getInfrastructure().values())
      countPaths(e);
    
//    current.bindings = definitions.getBindings().size();
//    for (BindingSpecification bs : definitions.getBindings().values())
//      if (bs.getBinding() == Binding.CodeList) {
//        current.codelists++;
//        current.codes = current.codes + bs.getCodes().size();
//      }
//      else if (bs.getBinding() == Binding.ValueSet)
//        current.valuesets++;
  }

  private void countPaths(ElementDefn e) {
    current.paths++;
    for (ElementDefn c : e.getElements())
      countPaths(c);
  }

  public String report(PageProcessor page, List<ValidationMessage> errors) throws Exception {
    StringBuilder s = new StringBuilder();
    s.append("<h2>Build Stats</h2>\r\n");
    s.append("<table class=\"grid\">\r\n");
    s.append(" <tr><td>resources</td><td>"+Integer.toString(current.resources)+"</td></tr>\r\n");
    s.append(" <tr><td>types</td><td>"+Integer.toString(current.types)+"</td></tr>\r\n");
    s.append(" <tr><td>packs</td><td>"+Integer.toString(current.packs)+"</td></tr>\r\n");
    s.append(" <tr><td>paths</td><td>"+Integer.toString(current.paths)+"</td></tr>\r\n");
    s.append(" <tr><td>bindings</td><td>"+Integer.toString(current.bindings)+"</td></tr>\r\n");
    s.append(" <tr><td>codelists</td><td>"+Integer.toString(current.codelists)+"</td></tr>\r\n");
    s.append(" <tr><td>valuesets</td><td>"+Integer.toString(current.valuesets)+"</td></tr>\r\n");
    s.append(" <tr><td>codes</td><td>"+Integer.toString(current.codes)+"</td></tr>\r\n");
    s.append(" <tr><td>hints</td><td>"+Integer.toString(current.hints)+"</td></tr>\r\n");
    s.append(" <tr><td>warnings</td><td>"+Integer.toString(current.warnings)+"</td></tr>\r\n");
    s.append("</table>\r\n");
    
    String xslt = Utilities.path(page.getFolders().rootDir, "implementations", "xmltools", "WarningsToQA.xslt");
    s.append(Utilities.saxonTransform(page.getFolders().dstDir + "work-group-warnings.xml", xslt));
    
    return s.toString();
    
  }
  public void commit(String rootDir) {
    Calendar c = new GregorianCalendar();
    c.set(Calendar.HOUR_OF_DAY, 0); //anything 0 - 23
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    Date d = c.getTime();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    String n = sdf.format(d);
    IniFile ini = new IniFile(rootDir+"records.cache");
    ini.setIntegerProperty("resources", n, current.resources, null);   
    ini.setIntegerProperty("types", n, current.types, null);   
    ini.setIntegerProperty("profiles", n, current.packs, null); // need to maintain the old word here   
    ini.setIntegerProperty("paths", n, current.paths, null);   
    ini.setIntegerProperty("bindings", n, current.bindings, null);   
    ini.setIntegerProperty("codelists", n, current.codelists, null);   
    ini.setIntegerProperty("valuesets", n, current.valuesets, null);   
    ini.setIntegerProperty("codes", n, current.codes, null);   
    ini.setIntegerProperty("hints", n, current.hints, null);   
    ini.setIntegerProperty("warnings", n, current.warnings, null);   
    ini.save();
  }

  public void setCounts(int e, int w, int i) {
    current.hints = i;
    current.warnings = w;
  }

}
