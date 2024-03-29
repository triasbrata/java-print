package app;

import javax.print.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class App {
  protected PrintService[] printServices;
  protected Map<String, Integer> listArguments;

  public App() {
    this.printServices = PrintServiceLookup.lookupPrintServices(null, null);
    this.listArguments = new HashMap<String, Integer>();
    this.listArguments.put("--getPrinter", 0);
    this.listArguments.put("--print", 5);

  }

  public static void main(String[] args) throws Exception {
    try {
      ArrayList<String> argslist = new ArrayList<String>(Arrays.asList(args));
      App app = new App();
      while (argslist.size() > 0) {
        String arg = argslist.remove(0);
        ArrayList<String> value = new ArrayList<String>();
        if (arg.startsWith("--")) {
          Integer numArgs = app.numArgs(arg);
          if (numArgs == null) {
            App.exit(String.format("argument %s unidentified", arg),app);

          }
          if (numArgs > 0) {
            if (argslist.size() == 0) {
              App.exit(String.format("need value for %s", arg),app);

            }
            for (int i = 0; i < numArgs; i++) {
              if(argslist.size() == 0){
                break;
                // App.exit(String.format("%s need %i but %d found", arg, numArgs, i-1));
              }
              value.add(argslist.remove(0));
            }
          }
          app.compileArgument(arg, value);
        }
        System.exit(0);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void exit(String string, App app) {
    app.sendErrorMesssage(string, new Exception());
    System.exit(1);
  }

  private Integer numArgs(String arg) {
    for (String keyString : this.listArguments.keySet()) {
      if (keyString.equals(arg)) {
        return listArguments.get(keyString);
      }
    }
    return null;
  }

  private void compileArgument(String arg, ArrayList<String> value) {
    String methodName = this.cleanMethodName(arg);
    Method method;
    try {
      if(value.size() > 0){
        method = App.class.getMethod(methodName, ArrayList.class);
        method.invoke(this, value);
      }else{
        method = App.class.getMethod(methodName);
        method.invoke(this, (Object[]) null);
        
      }
      
      
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  private String cleanMethodName(String arg) {

    return arg.substring(2, arg.length());
  }

  public void getPrinter() {
    this.printPrinterListAsJson();
  }

  public void print(ArrayList<String> args) {
    String subArguments = args.remove(0);
    String filepath = null;
    PrintService printServiceUsage = null;
    Boolean isPrintSilent = false;
    Integer copies = 1;
    Boolean canclePrint = false;
    int orientation = PageFormat.PORTRAIT;
    if(subArguments.startsWith("-")){
      if(subArguments.equals("-printsilent")){
        isPrintSilent = true;
        subArguments = args.remove(0);
      }
      
      if (subArguments.equals("-landscape")) {
         orientation = PageFormat.LANDSCAPE;
        subArguments = args.remove(0);
      }
      if(subArguments.equals("-portrait")){
        subArguments = args.remove(0);
      } 
    }
    filepath = subArguments;

    File file = new File(filepath);
    int args_size = args.size();
    if(args_size > 0){
      printServiceUsage = this.getPrinterService(args.remove(0));
    }else{
      App.exit("printer name not found", this);
    }
    if(args.size() > 0){
      copies = Integer.parseInt(args.remove(0));
    }
    try {
      PDDocument doc = PDDocument.load(file);
      PrinterJob printerJob = PrinterJob.getPrinterJob();
      printerJob.setPrintService(printServiceUsage);
      printerJob.setCopies(copies);
      printerJob.setJobName(file.getName());
      if(doc.getNumberOfPages() < 1){
        App.exit("No page in " + file.getName(), this);
      }
      PageFormat pf = new PageFormat();
      pf.setOrientation(orientation);
      PDRectangle mediaBox = doc.getPage(0).getMediaBox();
      Paper paper = pf.getPaper();
      float width = mediaBox.getWidth();
      float height = mediaBox.getHeight();
      paper.setSize(width, height);
      paper.setImageableArea(0, 0, width, height);
      pf.setPaper(paper);
      printerJob.setPrintable(new Printable(){
      
        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
          if(doc.getNumberOfPages()  <= pageIndex){
            return Printable.NO_SUCH_PAGE;
          }
          Graphics2D graphics2 = (Graphics2D) graphics;
          try {
            new PDFRenderer(doc).renderPageToGraphics(pageIndex, graphics2);
          } catch (IOException e) {
            e.printStackTrace();
          }
          return Printable.PAGE_EXISTS;
      }}, pf);
      if(!isPrintSilent){
        if(!printerJob.printDialog()){
          canclePrint = true;
        }
      }
      
      if(!canclePrint){
        printerJob.print();
        this.sendMessageSuccess(String.format("Print %d copies %s  from %s done", copies,file.getName(), printServiceUsage.getName()));  
      }
      doc.close();
      
    } catch (IOException | PrinterException e) {
      e.printStackTrace();
	}
  }

  protected void sendMessageSuccess(String message) {
    this.printOutputMessage(message, "success", null);
  }

  public void sendErrorMesssage(String message, Exception e){
      final ByteArrayOutputStream sfps = new ByteArrayOutputStream();
    PrintStream ps;
    try {
        ps = new PrintStream(sfps, true, "UTF-8");
        e.printStackTrace(ps);
      } catch (UnsupportedEncodingException e1) {
        e1.printStackTrace();
    }
      String estacktrace = new String(sfps.toByteArray(), StandardCharsets.UTF_8);
    this.printOutputMessage(message, "error", estacktrace);
   
    
  }
  
  private void printOutputMessage(String message, String type, String error) {
    JsonObject jos = new JsonObject();
    jos.addProperty("message", message);
    jos.addProperty("type", type);
    jos.addProperty("error", error);
    System.out.println(jos.toString());
  }

  private PrintService getPrinterService(String name) {
    PrintService printServiceSelected = null;
    for (PrintService printService : this.getListPrinter()) {
      if(printService.getName().contentEquals(name)) {
        printServiceSelected = printService;
        break;
      }
    }
    if(printServiceSelected == null){
      App.exit("printer not found",this);
    }
    return printServiceSelected;
  }

  public PrintService[] getListPrinter() {
    return this.printServices;
  }

  public void printPrinterListAsJson() {
    JsonArray array = new JsonArray();
    for (PrintService printer : this.printServices) {
      JsonObject object = new JsonObject();
      object.addProperty("name", printer.getName());
      array.add(object);
    }
    System.out.print(array.toString());
  }

}