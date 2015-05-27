/***********************************************
 * Translator is the driver for our project.
 *
 * First convert the Java AST into a C++ AST.
 * Then print C++ code according to the C++ AST
 * into output files.
 *
 * Each Java class will have its own .h and .cc
 * files.
 ***********************************************/

package cpptranslator;

import java.io.*;
import java.util.*;

import xtc.lang.*;
import xtc.parser.*;
import xtc.tree.*;
import xtc.util.*;

import xtc.Constants;

public class Translator extends xtc.util.Tool {

    TreeConverter treeConverter;    

    SymbolTable rootTable;

    Writer writer;
    Printer printer;
    Printer h_printer;

    String classname;
    String fileName;

    ArrayList<ClassDetail> classList;
    Hashtable<String, ClassDetail> classAndClassDetail;
    Hashtable<String,String> methReturnTypes;

    /** Create a new tool. */
    public Translator() {}

    public interface ICommand {
        public void run();
    }

    public String getName() { return "Translator"; }

    /**
     * Get this tool's version.  The default implementation returns
     * {@link Constants#VERSION}.
     *
     * @return The version.
     */
    public String getVersion() { return "2.0.0"; }

    /**
     * Get this tool's copyright.  The default implementation returns
     * {@link Constants#FULL_COPY}.
     *
     * @return The copyright.
     */
    public String getCopy() { return "(C) 2014 OnTheCSide with code from Robert Grimm and NYU"; }

    /** 
     * Locates java test file 
     *
     * @param name  The name of the file
     */
    public File locate(String name) throws IOException {
        File file = super.locate(name);
        if (Integer.MAX_VALUE < file.length()) {
          throw new IllegalArgumentException(file + ": file too large");
        }
        return file;
    }

    /**
     * Uses JavaFiveParser to parse test java file
     */
    public Node parse(Reader in, File file) throws IOException, ParseException {
        JavaFiveParser parser =
        new JavaFiveParser(in, file.toString(), (int) file.length());
        Result result = parser.pCompilationUnit(0);
        return (Node)parser.value(result);
    }

    /**
     * Initializes all globals by taking them from the TreeConverter.
     */
    private void initGlobals() {
        this.classList           = treeConverter.getClassList();
        this.classAndClassDetail = treeConverter.getClassAndClassDetail();
        this.methReturnTypes     = treeConverter.getMethReturnTypes();
        this.rootTable           = treeConverter.getRootTable();
        this.fileName            = treeConverter.getFileName();
    }

    /**
     * Processes each AST node using visitor pattern.
     */
    public void process(Node node) {

        /* Convert Java AST into C++ AST */
        treeConverter = new TreeConverter(node, runtime);
        initGlobals();

        ArrayList<String> filesToCompile = new ArrayList<String>();
        filesToCompile.add("main.cc");
        filesToCompile.add("java_lang.cc");
        Writer scriptWriter = null; 
        
        /* Reads through classList and prints out respective .cc and .h classes */
        for (int i = 0; i < classList.size(); i++) {
            classname = classList.get(i).getclassname();

            try {
                filesToCompile.add(classname + ".cc");

                writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(classname + ".cc"), "utf-8"));
                printer = new Printer(writer);
                printer.p("#include \"").p(classname).pln(".h\"").pln();
                h_printer = new Printer(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(classname + ".h"), "utf-8")));
                
                new CPPPrinter(printer, h_printer, classList.get(i).getmethodList(), 
                               classList.get(i).getpublicScopes(), classList.get(i).getprivateScopes(), 
                               classAndClassDetail, methReturnTypes, classname, 
                               rootTable, fileName) {}.dispatch(classList.get(i).getclassNode());

            } catch (IOException ex) {
            } finally {
                try {
                    printer.close(); 
                    h_printer.close();  
                    System.out.println("Printed " + classname + ".cc");
                    System.out.println("Printed " + classname + ".h");                  
                } catch (Exception ex) {}
            } 
        }

        /* If all generations are successful, assume main file has been printed */
        System.out.println("Printed main.cc");

        /* Generates a script 'CompileRecentTest.sh' to compile the generated files */
        try {
            scriptWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("CompileRecentTest.sh"), "utf-8"));
            scriptWriter.write("g++ -std=c++11 ");
            for (int i = 0; i < filesToCompile.size(); i++) {
                scriptWriter.write(filesToCompile.get(i) + " ");
            }
        } catch (IOException ex) {
        } finally {
            try {
                scriptWriter.close();
                System.out.println("Printed CompileRecentTest.sh");
            } catch (Exception ex) {}
        }

        /* Formatting */
        System.out.println("*****************************************************************************");
    }

    /**
     * Run the tool with the specified command line arguments.
     *
     * @param args The command line arguments.
     */
    public static void main(String[] args) {
        /* Formatting */
        System.out.println("*****************************************************************************");

        new Translator().run(args);
    }
}