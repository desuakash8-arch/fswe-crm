package com.klu.crmbackend.service;
import com.klu.crmbackend.model.*;
import com.klu.crmbackend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class CsvService {
    private final com.klu.crmbackend.service.CrmNotifications events;

    private final CustomerService customers;private final CustomerRepository customerRepo;private final LeadRepository leads;private final RecordAccessService access;private final RecordQuery query;
    public CsvService(CustomerService customers,CustomerRepository customerRepo,LeadRepository leads,RecordAccessService access,RecordQuery query, com.klu.crmbackend.service.CrmNotifications events){
        this.events=events;this.customers=customers;this.customerRepo=customerRepo;this.leads=leads;this.access=access;this.query=query;}
    public static List<List<String>> parse(String csv) {
        if(csv==null || csv.length()>1_000_000)throw new IllegalArgumentException("CSV must be at most 1 MB");
        csv=csv.replaceFirst("^\\uFEFF","");var rows=new ArrayList<List<String>>();var row=new ArrayList<String>();var cell=new StringBuilder();boolean quoted=false,closed=false;
        for(int i=0;i<csv.length();i++) {
            char c=csv.charAt(i);
            if(quoted) {if(c=='"'){if(i+1<csv.length()&&csv.charAt(i+1)=='"'){cell.append('"');i++;}else{quoted=false;closed=true;}}else cell.append(c);}
            else if(c=='"' && cell.isEmpty() && !closed)quoted=true;
            else if(c==',' || c=='\n' || c=='\r') {
                row.add(cell.toString());cell.setLength(0);closed=false;
                if(c!=','){if(c=='\r' && i+1<csv.length() && csv.charAt(i+1)=='\n')i++;if(row.stream().anyMatch(v->!v.isBlank()))rows.add(List.copyOf(row));row.clear();}
            } else {if(closed || c=='"')throw new IllegalArgumentException("Invalid CSV quoting");cell.append(c);}
        }
        if(quoted)throw new IllegalArgumentException("Unclosed CSV quote");
        if(!cell.isEmpty() || !row.isEmpty() || closed){row.add(cell.toString());rows.add(row);}if(rows.size()>501)throw new IllegalArgumentException("Import at most 500 rows at a time");return rows;
    }
    public static String cell(String value) {String v=value==null?"":value;String trimmed=v.stripLeading();if(!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0))>=0 || v.startsWith("\t") || v.startsWith("\r"))v="'"+v;return "\""+v.replace("\"","\"\"")+"\"";}
    public record RowResult(int row,String name,String email,String status,String message) {}
    @Transactional public Object importCsv(String kind,String csv,boolean commit) {
        if(!List.of("customers","leads").contains(kind))throw new IllegalArgumentException("Import supports customers and leads");
        var rows=parse(csv);if(rows.isEmpty())throw new IllegalArgumentException("CSV is empty");var header=rows.remove(0).stream().map(v->v.trim().toLowerCase(Locale.ROOT)).toList();
        if(!header.contains("name") || !header.contains("email") || new HashSet<>(header).size()!=header.size())throw new IllegalArgumentException("CSV requires unique headers including name,email");
        var results=new ArrayList<RowResult>();var valid=new ArrayList<Map<String,String>>();var seen=new HashSet<String>();var actor=access.actor();
        int number=1;
        for(var row:rows){number++;String name="",email="";try {
            if(row.size()!=header.size())throw new IllegalArgumentException("Column count does not match header");
            var data=new HashMap<String,String>();for(int i=0;i<header.size();i++)data.put(header.get(i),row.get(i));
            name=Rules.text(data.get("name"),"Name",true,255);email=Rules.email(data.get("email"));
            if(email==null)throw new IllegalArgumentException("Email is required for duplicate detection during import");data.put("email",email);data.put("name",name);
            String finalEmail=email;
            boolean duplicate=kind.equals("customers")?!customerRepo.findAll(RecordQuery.<Customer>visible(actor,"assignedTo").and((r,q,cb)->cb.equal(cb.lower(r.get("email")),finalEmail))).isEmpty():!leads.findAll(RecordQuery.<Lead>visible(actor,"assignedTo").and((r,q,cb)->cb.equal(cb.lower(r.get("email")),finalEmail))).isEmpty();
            if(duplicate || !seen.add(email)){results.add(new RowResult(number,name,email,"SKIPPED","Duplicate email"));continue;}
            if(kind.equals("customers"))Rules.customer(customer(data));else Rules.lead(lead(data));valid.add(data);results.add(new RowResult(number,name,email,"READY","Valid"));
        }catch(IllegalArgumentException ex){results.add(new RowResult(number,name,email,"ERROR",ex.getMessage()));}}
        boolean errors=results.stream().anyMatch(r->r.status().equals("ERROR"));
        if(commit && errors)throw new IllegalArgumentException("Fix invalid rows before importing. No rows were imported.");
        if(commit)for(var data:valid){if(kind.equals("customers"))customers.createCustomer(customer(data),null);else {var l=lead(data);l.setAssignedTo(actor);leads.save(l);events.created(l);}}
        if(commit && !valid.isEmpty())events.event(actor,"IMPORT","CRM import completed",valid.size()+" "+kind+" imported.","Reason: You committed a validated CSV import.\nImported: "+valid.size()+"\nSkipped: "+results.stream().filter(r->r.status().equals("SKIPPED")).count(),null);
        return Map.of("rows",results,"ready",valid.size(),"imported",commit?valid.size():0,"hasErrors",errors);
    }
    private Customer customer(Map<String,String> d){var c=new Customer();c.setName(d.get("name"));c.setEmail(d.get("email"));c.setPhone(d.get("phone"));c.setCompany(d.get("company"));c.setAddress(d.get("address"));c.setStatus(d.getOrDefault("status","ACTIVE"));return c;}
    private Lead lead(Map<String,String>d){var l=new Lead();l.setName(d.get("name"));l.setEmail(d.get("email"));l.setPhone(d.get("phone"));l.setCompany(d.get("company"));l.setSource(d.get("source"));l.setStatus(d.getOrDefault("status","NEW"));Rules.lead(l);if("CONVERTED".equals(l.getStatus()))throw new IllegalArgumentException("Import new leads, then use Convert");return l;}
    @Transactional(readOnly=true) public String export(String kind) {
        if(!List.of("customers","leads").contains(kind))throw new IllegalArgumentException("Export supports customers and leads");
        var text=new StringBuilder("name,email,phone,company,status\r\n");var first=query.page(kind,0,100,"","ALL","",false,null);
        if(first.totalElements()>10000)throw new IllegalArgumentException("Export is limited to 10,000 records");
        for(int p=0;p<Math.max(1,first.totalPages());p++)for(Object o:(p==0?first:query.page(kind,p,100,"","ALL","",false,null)).content()){
            String[] data=o instanceof Customer c?new String[]{c.getName(),c.getEmail(),c.getPhone(),c.getCompany(),c.getStatus()}:new String[]{((Lead)o).getName(),((Lead)o).getEmail(),((Lead)o).getPhone(),((Lead)o).getCompany(),((Lead)o).getStatus()};
            text.append(Arrays.stream(data).map(CsvService::cell).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
        }return text.toString();
    }
}
