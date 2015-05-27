/**********************************
 * CPPPrinter outputs the C++ code 
 * by traversing the C++ AST.
 *********************************/

package cpptranslator;

import java.util.Iterator;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Set;
import java.util.LinkedList;

import java.io.*;

import xtc.tree.Comment;
import xtc.tree.Node;
import xtc.tree.GNode;
import xtc.tree.Printer;
import xtc.tree.Token;
import xtc.tree.Visitor;
import xtc.util.SymbolTable;

import xtc.lang.JavaPrinter;

/**
 * A pretty printer for C++.
 */
public class CPPPrinter extends JavaPrinter {

    ArrayList<Node>        publicScopes;
    ArrayList<Node>        privateScopes; 
    String                 currentClassName;   // Name of current class
    ArrayList<Node>        methodList;
    boolean                inOutput; 
    boolean                inMethod;
    boolean                inMain;
    boolean                inMainBlock;

    // Used for printing checkNotNull
    boolean                inOutputStream;       
    boolean                inForLoopDeclaration; 
    boolean                inNestedForLoop;     
    boolean                inWhileLoop; 

    // Used for printing checkStore 
    Hashtable<String,String> smartPtrDynamicTypes = new Hashtable<String,String>();
    boolean                checkStore = false;     
    String                 varDuplicate;         // holds the variable that has the duplicate
                                                 // string literal    
                                                 
    // Used to homogenize static and dynamic types                                                    
    String                 smartPointerStaticType;    

    SymbolTable rootTable;
    String fileName;

    // holds all static field declarations declared
    // outside the main method
    // Used to print static fields
    LinkedList<String> staticFields = new LinkedList<String>();                                     

    // Used to implement vtable
    // Maps each class to its class detail
    Hashtable<String, ClassDetail>  classAndClassDetail;   

    // Used to implement outputting the ->data 
    // to the end of string types
    // Holds the return types for each method
    Hashtable<String,String>        methReturnTypes;   

    protected final Printer h_printer;          /* The printer for the h file */          
    ArrayList<String> constructorsInfo = new ArrayList<String>();
    Hashtable<String, String> existingStrings = new Hashtable<String, String>(); 
    protected boolean isConstructor;

    /**
    * Create a new C++ printer.
    */
    public CPPPrinter(Printer printer, Printer h_printer, ArrayList<Node> methodList, 
                      ArrayList<Node> publicScopes, ArrayList<Node> privateScopes, 
                      Hashtable<String, ClassDetail> classAndClassDetail, Hashtable<String,String> methReturnTypes, 
                      String currentClassName, SymbolTable rootTable, String fileName) throws IOException {

        super(printer);
        this.currentClassName    = currentClassName;    
        printer.register(this);
        this.h_printer           = h_printer;
        h_printer.register(this);
        this.classAndClassDetail = classAndClassDetail;
        this.methReturnTypes     = methReturnTypes;
        this.methodList          = methodList;
        this.privateScopes       = privateScopes;
        this.publicScopes        = publicScopes;
        inMethod                 = false;
        inMain                   = false;
        this.rootTable           = rootTable;
        this.fileName            = fileName;        
    }

    public CPPPrinter (Printer printer, Hashtable<String, ClassDetail> classAndClassDetail, 
                       Hashtable<String,String> methReturnTypes, String currentClassName,
                       LinkedList<String> staticFields, SymbolTable rootTable) {

        super(printer);
        this.currentClassName    = currentClassName;

        printer.register(this);
        h_printer                = null;
        inMain                   = true;    

        this.classAndClassDetail = classAndClassDetail;
        this.methReturnTypes     = methReturnTypes;
        this.staticFields        = staticFields;
        this.rootTable = rootTable;
    }

    /** Visit the specified declarator. */
    public void visitDeclarator(GNode n) {
        printer.p(n.getString(0));
        if (null != n.get(1)) {
            if (Token.test(n.get(1))) {
                formatDimensions(n.getString(1).length());
            } else {
                printer.p(n.getNode(1));
            }
        }   
        if(null != n.get(2) && n.getNode(2).getName().equals("StringLiteral")) {
            printer.p(" = ").p("__rt::literal(" + n.getNode(2).getString(0) + ")");
        }   else if(null != n.get(2)) {
            printer.p(" = ").p(n.getNode(2));
        }
    }

    /** Visit the specified class body. */
    public void visitClassBody(GNode n) {
       // check if declaring a static field outside the main method
        for (int i = 0; i < n.size(); i++ ) {
            if (n.get(i) != null &&
                n.getNode(i).getName().equals("FieldDeclaration")
                ) {
                GNode fieldDeclarationNode = n.getGeneric(i);

                if (fieldDeclarationNode.get(0) != null &&
                    fieldDeclarationNode.getNode(0).size() > 0 &&
                    fieldDeclarationNode.getNode(0).get(0) != null &&
                    fieldDeclarationNode.getNode(0).getNode(0).getString(0).equals("static")
                    ) {

                    // add the static field to the linked list to print later
                    String type = fieldDeclarationNode.getNode(1).getNode(0).getString(0);
                    String var = fieldDeclarationNode.getNode(2).getNode(0).getString(0);

                    String printLater = "";

                    // if static field is not initialized
                    if (fieldDeclarationNode.getNode(2).getNode(0).get(2) == null) {
                        printLater = "static " + type + " " + var + ";";                        
                    } else {
                        // the static field is initialized
                        GNode declaratorsNode = fieldDeclarationNode.getGeneric(2);
                        String initialization = declaratorsNode.getNode(0).getNode(2).getString(0);
                        printLater = "static " + type + " static_" + var + " = " + initialization + ";";
                    }

                    staticFields.add(printLater);
                } 
            } 
        }

        if (isOpenLine) printer.p(' ');
        printer.p("namespace oop ");
        printer.pln('{').incr();

        printDeclsAndStmts(n);

        printer.pln().indent().p("Class __" + currentClassName + "::__class() {");
        printer.pln().incr().indent().p("static Class k =");
        printer.pln().indent().p("  new __Class(__rt::literal(\"" + currentClassName + "\"), __Object::__class());");
        printer.pln().indent().p("return k;");
        printer.pln().decr().indent().p("}").pln();

        /* prints vtable definition for that class */
        printer.pln().indent().p("__" + currentClassName + "_VT __" + currentClassName + "::__vtable;").pln();

        printer.decr().indent().p('}');

        /* Adds template specialization for class literal of arrays of new class */
        printer.pln("namespace __rt");
        printer.pln('{').incr();
        printer.pln().indent().p("template<>");

        printer.pln().indent().p("java::lang::Class Array<oop::" + currentClassName + ">::__class() {");
        printer.pln().incr().indent().p("static Class k =");
        printer.pln().indent().p("  new java::lang::__Class(literal(\"[L" + currentClassName + ";\"), Array<java::lang::Object>::__class(), oop::__" + currentClassName + "::__class());");

        printer.pln().indent().p("return k;");
        printer.pln().decr().indent().p("}").pln();

        printer.pln().indent().p("template<>");

        printer.pln().indent().p("java::lang::Class Array2D<oop::" + currentClassName + ">::__class() {");
        printer.pln().incr().indent().p("static Class k =");
        printer.pln().indent().p("  new java::lang::__Class(literal(\"[[L" + currentClassName + ";\"), Array2D<java::lang::Object>::__class(), oop::__" + currentClassName + "::__class());");

        printer.pln().indent().p("return k;");
        printer.pln().decr().indent().p("}").pln();

        printer.decr().indent().p('}');
        isOpenLine    = true;
        isNested      = false;
        isIfElse      = false;


    }
  
    /** Visit the specified field declaration. */
    public void visitFieldDeclaration(GNode n) {

        // check if declaration is a SMART POINTER
        if (n.get(1) != null &&
            n.getNode(1).get(1) != null &&
            n.getNode(1).getNode(1).getName().equals("Dimensions")
            ) {

            String type = n.getNode(1).getNode(0).getString(0);  

            // check if assigning to argv, if so, assign to argvPtr instead of to argv
            if (n.getNode(2).getNode(0).size() >= 3 &&
                n.getNode(2).getNode(0).getNode(2).getName().equals("PrimaryIdentifier") &&   
                n.getNode(2).getNode(0).getNode(2).getString(0).equals("argv")                
                ) {
                GNode declaratorNode = n.getNode(2).getGeneric(0);
                String field = declaratorNode.getString(0);                
                printer.indent().p("__rt::Ptr<__rt::Array<").p(n.getNode(0)).p(type).p("> >").p(' ').p(field).p(" = argvPtr").p(';').pln();    
            } else {
                // not assigning to argv, so ordinary print

                // if initialzing an array of negative length, throw NegativeArraySizeException
                if (n.get(2) != null &&
                    n.getNode(2).get(0) != null &&
                    n.getNode(2).getNode(0).get(2) != null &&
                    n.getNode(2).getNode(0).getNode(2).getName().equals("NewArrayExpression")) {

                    GNode declaratorsNode = n.getGeneric(2);
                    GNode newArrayExpressionNode = declaratorsNode.getNode(0).getGeneric(2);
                    GNode concreteDimensionsNode = newArrayExpressionNode.getGeneric(1);
                    boolean isNegative = concreteDimensionsNode.getNode(0).getName().equals("UnaryExpression");
                    if (isNegative) {
                        String posLength = concreteDimensionsNode.getNode(0).getNode(1).getString(0);                        
                        String negLength = "-" + posLength;
                        printer.indent().p("if (").p(negLength); 
                        printer.p(" < 0) throw java::lang::NegativeArraySizeException();").pln();
                    }
                }

                // Map the smart pointer field to its dynamic type
                // This is used for printing checkStore for smart ptrs to arrays (in test26)
                GNode declaratorNode = n.getNode(2).getGeneric(0);
                String field = declaratorNode.getString(0);                
                String dynamicType = declaratorNode.getNode(2).getNode(0).getString(0);
                smartPtrDynamicTypes.put(field, dynamicType);

                // homogenize static and dynamic types
                // i.e. always change the dynamic type to the static type
                // this global is used in visitNewArrayExpression to homogenize
                smartPointerStaticType = type; 

                if(n.getNode(1).getNode(1).size() == 1) {
                    printer.indent().p("__rt::Ptr<__rt::Array<");
                } else {
                    printer.indent().p("__rt::Ptr<__rt::Array2D<");
                }
                printer.p(n.getNode(0)).p(type).p("> >").p(' ').p(n.getNode(2)).p(';').pln();
            }
        } else {    // not a smart pointer
            // if need to cast a field declaration
            // replace the static type with the dynamic type
            // example: in test002,
            //            Object o = a;  to
            //            A o = a;
            boolean needToUpcast = false;
            if (n.get(2) != null && 
                n.getNode(2).get(0) != null &&
                n.getNode(2).getNode(0).get(2) != null &&
                n.getNode(2).getNode(0).getNode(2).getName().equals("PrimaryIdentifier")
                ) {
                needToUpcast = true;
            }

            if (inMethod && needToUpcast) { // needs to be casted
                // get the static type of the right-hand-side variable
                String varRHS = n.getNode(2).getNode(0).getNode(2).getString(0);                
                ClassDetail currClassDetail = classAndClassDetail.get(currentClassName);
                LinkedList<String> typeInfo = currClassDetail.getFieldTypes().get(varRHS);            
                String outputStaticType = typeInfo.get(0);   // index 0 is the static type                                                      
                
                printer.indent().p(n.getNode(0)).p(outputStaticType).p(' ').p(n.getNode(2)).p(';').pln();
            } else if (inMethod) {  // no casting needed, just ordinary output       
                printer.indent().p(n.getNode(0)).p(n.getNode(1)).p(' ').p(n.getNode(2)).p(';').pln();
            }
        }   

        // Used for checkStore
        if (n.get(2) != null && n.getNode(2).get(0) != null &&
            n.getNode(2).getNode(0).get(2) != null &&
            n.getNode(2).getNode(0).getNode(2).getName().equals("StringLiteral")) {

                String var = n.getNode(2).getNode(0).getString(0);
                String stringLit = n.getNode(2).getNode(0).getNode(2).getString(0);
                existingStrings.put(stringLit,var);
        }

        isDeclaration = true;
        isOpenLine    = false;
      

        /*********** Record the field's static and dynamic types ******************/
        String staticType = n.getNode(1).getNode(0).getString(0);
        String varName = n.getNode(2).getNode(0).getString(0);
        String dynamicType = "";

        // get the dynamic type
        if (n.getNode(2).getNode(0).get(2) != null) { // field is initialized

            if (n.getNode(2).getNode(0).getNode(2).getName().equals("NewClassExpression")) {
                // declaring the field to be a new object instantiation
                // example: A a = new A(); 
                GNode newClassExpressionNode =  n.getNode(2).getNode(0).getGeneric(2);
                dynamicType = newClassExpressionNode.getNode(2).getString(0);  
            } else if (n.getNode(2).getNode(0).getNode(2).getName().equals("PrimaryIdentifier")) { 
                // assigning the field to be a reference to an existing variable
                // example: A a2 = b;             
                GNode primaryIdentiferNode = n.getNode(2).getNode(0).getGeneric(2);
                String existingVar = primaryIdentiferNode.getString(0);
                ClassDetail currClassDetail = classAndClassDetail.get(currentClassName);
                LinkedList<String> typeInfo = currClassDetail.getFieldTypes().get(existingVar);

                if (existingVar.equals("argv"))
                    dynamicType = "String[]";   // special case for args parameter in main method
                else 
                    dynamicType = typeInfo.get(0); // the field's dynamic type is the static type of the existing variable
            }
        } else { // field is not initialized
            dynamicType = null;
        }

        // record the field's type info
        LinkedList<String> types = new LinkedList<String>(); 
        types.add(staticType);      // static type goes in index 0
        types.add(dynamicType);     // dynamic types goes in index 1
        
        // get the current class' type info hashtable
        ClassDetail currClassDetail = classAndClassDetail.get(currentClassName);
        Hashtable<String, LinkedList> fieldTypes = currClassDetail.getFieldTypes();

        // add the field and its type info to the hashtable
        fieldTypes.put(varName, types);

        /*********** END Record the field's static and dynamic types ******************/
    }

    /** Visit the specified method declaration. */
    public void visitMethodDeclaration(GNode n) {
        
        inMethod = true;
        Printer writer = null;
        Node main = n;
        if (n.getString(3).equals("main")) {
            if (!inMain) { 
                rootTable.enter(n);
                try {
                    writer = new Printer(new BufferedWriter(new OutputStreamWriter(new FileOutputStream("main.cc"), "utf-8")) );                    
                    new CPPPrinter(writer, classAndClassDetail, methReturnTypes, currentClassName, staticFields, rootTable).dispatch(main);
                } catch (IOException ex) {
                } finally {
                    try {
                        writer.close(); 
                    } catch (Exception ex) {}
                }
                rootTable.exit(n);
            } else {
                printer.p("#include <iostream>").pln();
                Set<String> classes = classAndClassDetail.keySet();
                for (String class_name : classes) {
                    printer.p("#include \"" + class_name + ".h\"").pln();
                }
                printer.pln();
                printer.p("using namespace java::lang;").pln();
                printer.p("using namespace oop;").pln().pln();

                // print out all static fields declared outside the main method (test18)
                for (int i = 0; i < staticFields.size(); i++) {
                    printer.p(staticFields.get(i)).pln();                    
                }

                // formatting
                if (staticFields.size() > 0) printer.pln();

                printer.indent().p("int ");
                printer.p("main(int argc, char *argv[])");                

                if (null != n.get(7)) {
                    isOpenLine = true;
                    inMainBlock = true;
                    printer.p(n.getNode(7)).pln();
                } else {
                    printer.pln(';');
                }
                isOpenLine = false;
            }
        } else { // method is not "main"
            rootTable.enter(n);
            printer.indent().p(n.getNode(2));
            if(currentClassName!=null) printer.p(' ').p("__" + currentClassName + "::");
            if (! "<init>".equals(n.get(3))) {
                printer.p(n.getString(3));
            }
            printer.p(n.getNode(4));
            if (null != n.get(5)) {
                printer.p(' ').p(n.getNode(5));
            }
            if (null != n.get(6)) {
                printer.p(' ').p(n.getNode(6));
            }
            if (null != n.get(7)) {
                isOpenLine = true;
                printer.p(n.getNode(7)).pln();
            } else {
                printer.pln(';');
            }
            isOpenLine = false;

            // get the return types of each non-main method  
            String methName = n.getString(3);
            String retType;
            if (n.getNode(2).getName().equals("VoidType")) { 
                retType = "Void";
            } else {
                retType = n.getNode(2).getNode(0).getString(0);
            }
            methReturnTypes.put(methName,retType);
            rootTable.exit(n);
        }
        inMethod = false;        
    }

    /** Visit the specified constructor declaration. */
    public void visitConstructorDeclaration(GNode n) { 
        rootTable.enter(n);
        String constructorParams = "";
        inMethod = true;
        printer.indent().p("__" + currentClassName + "::");
        if (null != n.get(1)) 
            printer.p(n.getNode(1));
        printer.p("__" + n.getString(2)).p(n.getNode(3));
        if(null != n.get(4)) {
            printer.p(n.getNode(4));
        }

        for (int i=0; i<n.getNode(3).size(); i++) {
            constructorParams += n.getNode(3).getNode(i).getNode(1).getNode(0).getString(0) + " " + 
                                 n.getNode(3).getNode(i).getString(3);
        if (i == n.getNode(3).size()-1) {
        } else {
            constructorParams += ", ";
        }
        }
        constructorsInfo.add(constructorParams);

        isOpenLine = true;
        isConstructor = true;
        printer.p(n.getNode(5));
        printer.p(n.getNode(6));
        isConstructor = false;
        inMethod = false;
        rootTable.exit(n);
    }

    /**
     * Prints out a constructor's initializer list
     * @param n InitList GNode
     */
    public void visitInitList(GNode n){
        printer.pln().indent().p(":");
        for (Iterator<Object> iter = n.iterator(); iter.hasNext(); ) {
            printer.p((Node)iter.next());
            if (iter.hasNext()) printer.p(",").pln();
        }
    }

    /**
     * Prints out an initializer list assignment
     * @param n ConstructorInit GNode
     */
    public void visitConstructorInit(GNode n){
        printer.p("\t" + n.getString(0) + "(" +n.getString(1) + ")");
    }

    /** Visit the specified class declaration. */
    public void visitClassDeclaration(GNode n) {
        rootTable.enter(fileName);
        rootTable.enter(n);
        if (null != n.get(3)) {
            printer.p(' ').p(n.getNode(3));
        }
        if (null != n.get(4)) {
            printer.p(' ').p(n.getNode(4));
        }
        isOpenLine    = true;

        printer.p(n.getNode(5)).pln();
        isDeclaration = true;
        isOpenLine    = false;
        HeaderPrinter headerPrinter = new HeaderPrinter(n.getString(1), methodList, publicScopes, privateScopes, classAndClassDetail, constructorsInfo, h_printer, currentClassName);
        headerPrinter.printHeaderFileIncludes();
        headerPrinter.createVTable();

        rootTable.exit(n);
        rootTable.exit();
    }

    /** Visit the specified extension. */
    public void visitExtension(GNode n) {
    }  

    /** Visit the specified block. */
    public void visitBlock(GNode n) {
        rootTable.enter(n);
        if(isConstructor) {
        }
        if (isOpenLine) {
            printer.p(' ');
        } else {
            printer.indent();
        }
        printer.pln('{').incr();

        // convert argv into a smart pointer
        if (inMainBlock) {
            printer.pln().indent();
            printer.p("// creating smart pointer for argv");
            printer.pln().indent();
            printer.p("__rt::Ptr<__rt::Array<String> > argvPtr = new __rt::Array<String>(argc-1);");
            printer.pln().indent();
            printer.p("for (int32_t i = 1; i < argc; i++) {");
            printer.pln().indent().indent();
            printer.p("String currString = __rt::literal(argv[i]);");
            printer.pln().indent().indent();
            printer.p("(*argvPtr)[i-1] = currString;");
            printer.pln().indent();
            printer.p("}");
            printer.pln().indent();
            printer.p("// finished creating smart pointer for argv");
            printer.pln().pln();

            inMainBlock   = false;
        }
        
        isOpenLine    = false;
        isNested      = false;
        isIfElse      = false;
        isDeclaration = false;
        isStatement   = false;

        printDeclsAndStmts(n);

        printer.decr().indent().p('}');
        isOpenLine    = true;
        isNested      = false;
        isIfElse      = false;
        rootTable.exit(n);
    }

    /** Visit the specified for statement. */
    public void visitForStatement(GNode n) {
        final boolean nested = startStatement(STMT_ANY);

        // Print checkNotNull right above FOR LOOPS iterating over arrays
        // 2D array
        GNode blockNode = n.getGeneric(1);
        if (blockNode.getNode(0).getName().equals("ForStatement")) {
            inNestedForLoop = true;

            // get the array name
            GNode nestedForStatementNode   = blockNode.getGeneric(0);
            GNode basicForControlNode      = nestedForStatementNode.getGeneric(0);
            GNode relationalExpressionNode = basicForControlNode.getGeneric(3);
            GNode selectionExpressionNode  = relationalExpressionNode.getGeneric(2);
            String arrayName               = selectionExpressionNode.getNode(0).getNode(0).getString(0);
            if (arrayName.equals("argv")) arrayName = "argvPtr";

            // print
            printer.indent().p("__rt::checkNotNull(").p(arrayName).p(");").pln();
        } else if (!inNestedForLoop) { // 1D array
            // get the array name
            GNode basicForControlNode      = n.getGeneric(0);
            GNode relationalExpressionNode = basicForControlNode.getGeneric(3);
            GNode selectionExpressionNode  = relationalExpressionNode.getGeneric(2);
            String arrayName               = selectionExpressionNode.getNode(0).getString(0);
            if (arrayName.equals("argv")) arrayName = "argvPtr";

            // print
            printer.indent().p("__rt::checkNotNull(").p(arrayName).p(");").pln();
        }

        inForLoopDeclaration = true;        
        printer.indent().p("for (").p(n.getNode(0)).p(')');
        prepareNested();        
        printer.p(n.getNode(1));        

        inForLoopDeclaration = false;
        inNestedForLoop = false;
        endStatement(nested);
    }

    /** Visit the specified while statement. */
    public void visitWhileStatement(GNode n) {
        final boolean nested = startStatement(STMT_ANY);
        inWhileLoop = true;

        printer.indent().p("while (").p(n.getNode(0)).p(')');
        prepareNested();
        printer.p(n.getNode(1));

        inWhileLoop = false;        
        endStatement(nested);
    }

    /** Visit the specified expression statement. */
    public void visitExpressionStatement(GNode n) {
        final boolean nested = startStatement(STMT_ANY);
        final int     prec   = enterContext(PREC_BASE);

        // Printing checkNotNull
        // If printing output stream, set flag to not print checkNotNull inside output stream
        // Instead, print checkNotNull in the line above the output stream      
        if (n.get(0) != null && 
            n.getNode(0).getName().equals("CallExpression") &&
            n.getNode(0).get(0) != null &&
            n.getNode(0).getNode(0).getName().equals("PrintOutput")
            ) {
            GNode callExpressionNode = n.getGeneric(0);
            inOutputStream = true;  // set flag

            // if accessing a vptr method, print checkNotNull
            if (callExpressionNode.getNode(0).getNode(1).getName().equals("CallExpression")) {
                GNode printOutputNode = callExpressionNode.getGeneric(0);
                GNode argumentsNode = printOutputNode.getNode(1).getGeneric(3);
                String var = "";
                if (argumentsNode.size() > 0) {
                    if (argumentsNode.getNode(0).getName().equals("SelectionExpression")) {
                        GNode primaryIdentifierNode = argumentsNode.getNode(0).getGeneric(0);
                        var = primaryIdentifierNode.getString(0);
                    } else if (argumentsNode.getNode(0).getName().equals("CallExpression")) {
                        GNode nestedCallExpressionNode = argumentsNode.getGeneric(0);
                        GNode nestedArgumentsNode = nestedCallExpressionNode.getGeneric(3);
                        var = nestedArgumentsNode.getNode(0).getString(0);
                    } else if (argumentsNode.getNode(0).getName().equals("CastExpression")) {
                        GNode primaryIdentifierNode = argumentsNode.getNode(0).getNode(1).getGeneric(0);
                        var = primaryIdentifierNode.getString(0);
                    } else {
                        GNode primaryIdentifierNode = argumentsNode.getGeneric(0);
                        var = primaryIdentifierNode.getString(0);                        
                    }

                    SymbValStruct symbStruct = (SymbValStruct)rootTable.current().lookup(var);
                    if (!symbStruct.getType().equals("int") && 
                        !symbStruct.getType().equals("double") && 
                        !symbStruct.getType().equals("byte")
                        ) {
                        printer.indent().p("__rt::checkNotNull(").p(var).p(");").pln();
                    }
                }
            }            
        } // end printing checkNotNull

        printer.indent().p(n.getNode(0)).pln(';');    

        inOutputStream = false;
        exitContext(prec);
        endStatement(nested);
        isOpenLine = false;
    }

    /** Visit the specified expression. */
    public void visitExpression(GNode n) {
        final int prec1 = startExpression(10);
        final int prec2 = enterContext();

        // Used for checkStore
        if (n.getNode(0).getName().equals("SubscriptExpression") &&
            n.getNode(2).getName().equals("StringLiteral") && 
            existingStrings.containsKey(n.getNode(2).getString(0))) {      
            checkStore = true;
            varDuplicate = existingStrings.get(n.getNode(2).getString(0));
        }

        printer.p(n.getNode(0));
        exitContext(prec2);

        if (checkStore) {
            printer.p(" " + n.getString(1) + " " + varDuplicate);
            checkStore = false;
            varDuplicate = null;
        } else {
            printer.p(' ').p(n.getString(1)).p(' ').p(n.getNode(2));
        }

        endExpression(prec1);
    }
  
    /** Visit the specified cast expression. */
    public void visitCastExpression(GNode n) {
        final int prec = startExpression(140);
        if (!inOutputStream) {
            printer.p("__rt::java_cast<").p(n.getNode(0)).p(">(").p(n.getNode(1)).p(")");
        } else {
            printer.p('(').p(n.getNode(0)).p(')').p(n.getNode(1));
        }
        endExpression(prec);
    }

    /**
     * Visit the specified CallExpression and overload it if necessary
     * @param n CallExpression GNode
     */
    public void visitCallExpression(GNode n) {
        final int prec = startExpression(160);
        boolean isStatic = false;        

        /*Check if expression has a caller */
        if (null != n.get(0)) {

            // Print according to whether the call expression is for PrintOutput
            // call expression is not PrintOutput
            if (!n.getNode(0).getName().equals("PrintOutput") ) { 
                String callerName = null;

                /* Check if caller is a 'single entity', i.e. can be searched for in Symbol Table */
                if (n.getNode(0).get(0) instanceof String) {

                    callerName = n.getNode(0).getString(0);
                    SymbValStruct symbStruct = (SymbValStruct)rootTable.current().lookup(callerName);

                    /* If symbol is found, then expression is called from an instance of something.
                     * Otherwise it calls a static expression */
                    if(symbStruct == null) isStatic = true;
                }

                /*Checks if expression contains more than one argument (i.e. args other than __this)
                 * or if it doesn't, is static => select correct overloaded impl if so */
                if (n.getGeneric(3).size() > 1 || isStatic) {  

                    MethodOverloader overloading = new MethodOverloader(n.getString(2), n.getGeneric(3), callerName, isStatic, classAndClassDetail, rootTable);
                    String overloadedCall = overloading.toString(); 
                    /* If there is an overloaded method corresponding to expression,
                     * set methodname to overloaded version before printing */                
                    if(overloadedCall != null) n.set(2, overloadedCall);
                }

                // print __rt::checkNotNull for each subject variable
                if (n.get(3) != null &&
                    n.getNode(3).size() > 0 &&
                    inMain &&
                    !inOutputStream) {

                    addCheckNotNullOutsideCallExpression(n.getGeneric(3));
                }

                /* If the method is static, print using this formula */
                if (isStatic) 
                    printer.p("__").p(n.getNode(0)).p("::").p(n.getString(2)).p(n.getNode(3));
                /* Otherwise method is dynamic, so print using pointer to vtable */
                else {
                    printer.p(n.getNode(0)).p("->__vptr->");
                    printer.p(n.getNode(1)).p(n.getString(2)).p(n.getNode(3));
                }                                    
            } 
            /*Follow procedures for printing PrintOutput streams */
            else {          
                printer.p(n.getNode(0));
            }

        } 
        /* Expression has no caller, and as long as it is not 'init'
         * then it must be a static method in the current class and
         * should be printed as such */
        else if (!n.getString(2).contains("init")) {
            printer.p("__").p(currentClassName).p("::").p(n.getNode(1)).p(n.getString(2)).p(n.getNode(3));
        } 
        /* Method is 'init' and should be printed in the standard JavaPrinter style */
        else {  
            printer.p(n.getNode(1)).p(n.getString(2)).p(n.getNode(3));
        }
        endExpression(prec);
    }

    /**
     * Adds the __rt::CheckNotNull outside of method calls for affected
     * arguments. Ignores primitives.
     * @param argumentsNode the arguments for the expression in question
     * @return void
     */
    private void addCheckNotNullOutsideCallExpression(GNode argumentsNode){
        for (int i = 0; i < argumentsNode.size(); i++) {
            if (argumentsNode.getNode(i).getName().equals("PrimaryIdentifier")) {
                String var = argumentsNode.getNode(i).getString(0);
                SymbValStruct symbStructVal = (SymbValStruct)rootTable.current().lookup(var);

                if (symbStructVal != null && 
                 !symbStructVal.getType().equals("int") && 
                 !symbStructVal.getType().equals("double") && 
                 !symbStructVal.getType().equals("byte")
                 ) {
                    printer.p("__rt::checkNotNull(");
                        printer.p(var);
                        printer.p(");");
                        printer.pln().indent();
                    }
                }
            }
    }

    /** Visit the specified selection expression. */
    public void visitSelectionExpression(GNode n) {
        final int prec = startExpression(160);
        String var = null;
        SymbValStruct symbStruct = null;

        if (n.get(0) instanceof Node &&
            n.getNode(0).get(0) instanceof String
            ) {
            var = n.getNode(0).getString(0);
            symbStruct = (SymbValStruct)rootTable.current().lookup(var);
        }

        if (isConstructor && 
            n.getNode(0).getName().equals("ThisExpression")
            ) {
            printer.p(n.getString(1));
        } else if (n.getGeneric(0).get(0) instanceof String && 
                   n.getGeneric(0).getString(0).equals("argv") && 
                   n.getString(1).equals("length")
                   ) {
            printer.p("argc-1");
        } else if (n.get(0) instanceof Node &&
                   n.getNode(0).getName().equals("PrimaryIdentifier") && 
                   symbStruct != null && symbStruct.getKind().equals("class") 
                   ) {
            printer.p("__").p(n.getNode(0)).p("::").p(n.getString(1));
        } else {
            printer.p(n.getNode(0)).p("->").p(n.getString(1));
        }

        endExpression(prec);
    }

    /** Visit the specified subscript expression. */
    public void visitSubscriptExpression(GNode n) {

        // Accessing 1D array
        if (n.getNode(0).get(0) instanceof String) {

            // only print checkNotNull when appropriate
            if (!inOutput &&
                !inForLoopDeclaration &&
                !inWhileLoop) {                
                printer.p("__rt::checkNotNull(" + n.getNode(0).getString(0) + ");\n");
            }

            // Used to print checkStore in a self-written test. Doesn't seem to be needed for the 39 tests
            if (checkStore) {
                printer.indent().p("__rt::checkStore(").p(n.getNode(0).getString(0)).p(", ").p(varDuplicate).p(");").pln();
            } 

            // Print checkStore inside for loop (test26)
            if (inForLoopDeclaration && 
                !inNestedForLoop &&
                !n.getNode(0).getString(0).equals("argv") &&
                smartPtrDynamicTypes.size() > 0 &&
                !smartPointerStaticType.equals("Object")
                ) {
                // get the dynamic type of the array
                String arrayName = n.getNode(0).getString(0);
                String dynamicType = smartPtrDynamicTypes.get(arrayName);
                String tempArray = dynamicType.toLowerCase(); 

                // print intialization of temp array using the array's dynamic type
                printer.p("__rt::Ptr<__rt::Array<").p(dynamicType).p("> > ");
                printer.p(tempArray);
                printer.p(" = new __rt::Array<").p(dynamicType).p(">(1);");
                printer.pln().indent();

                // print checkStore using the temp array
                printer.p("__rt::checkStore(").p(tempArray).p(" , __");                
                printer.p(smartPointerStaticType).p("::init(new __");
                printer.p(smartPointerStaticType).p("(i),(i)) );");
                printer.pln().indent();
            }

            // Print the subscript expression for the 1D array     
            // get info
            String arrayName = n.getNode(0).getString(0);
            if (arrayName.equals("argv")) arrayName = "argvPtr";
            String idx = n.getNode(1).getString(0);

            // formatting
            if (!inOutput && !inForLoopDeclaration && !inWhileLoop) printer.indent();

            // print
            printer.p("(*" + arrayName + ")[" + idx + "]");             
                           
        } else if (n.getNode(0).getName().equals("SubscriptExpression")) {
            // Accessing 2D array
            // get info
            String arrayName = n.getNode(0).getNode(0).getString(0);
            if (arrayName.equals("argv")) arrayName = "argvPtr";
            String idx1 = n.getNode(0).getNode(1).getString(0);
            String idx2 = n.getNode(1).getString(0);

            // print
            printer.p("(*").p(arrayName).p(")[").p(idx1).p("]").p("[").p(idx2).p("]");   
        }
    }

    /** Visit the specified this expression. */
    public void visitThisExpression(GNode n) {
        final int prec = startExpression(160);
        if (null != n.get(0)) printer.p(n.getNode(0)).p('.');
        printer.p("__this");
        endExpression(prec);
    }

    /** Visit the specified primary identifier. */
    public void visitPrimaryIdentifier(GNode n) {
        final int prec = startExpression(160);

        if (n.get(0) instanceof Node) {
            if (n.getGeneric(0).hasName("LocalClassFieldReference")) {
                printer.p("__this->" + n.getGeneric(0).getString(0));
            }
        } else {
            printer.p(n.getString(0));            
        }

        endExpression(prec);
    }   

    /** Visit the specified new class expression. */
    public void visitNewClassExpression(GNode n) {
        final int prec = startExpression(160);
        if (null != n.get(0)) printer.p(n.getNode(0)).p('.');
        printer.p("__").p(n.getNode(2).getString(0)).p("::init(");
        printer.p("new __");
        if (null != n.get(1)) printer.p(n.getNode(1)).p(' ');
        printer.p(n.getNode(2)).p(n.getNode(3));
        if (null != n.get(4)) {
            prepareNested();
            printer.p(n.getNode(4));
        }
        if(n.getNode(3).size() > 0) printer.p(",").p(n.getNode(3));
        printer.p(")");
        endExpression(prec);
    }

    /** Visit the specified new array expression. */
    public void visitNewArrayExpression(GNode n) {
        final int prec = startExpression(160);

        // Use homogenized type (static type instead of dynamic type)
        printer.p("new ");
        if(n.getGeneric(1).size() == 1) {
            printer.p("__rt::Array<");
        } else {
            printer.p("__rt::Array2D<");
        }
        printer.p(smartPointerStaticType);
        printer.p(">").p("("); 

        printer.p(n.getGeneric(1).getGeneric(0));
        if(n.getGeneric(1).size() > 1) {
            printer.p(",").p(n.getGeneric(1).getGeneric(1));
        }
        printer.p(")");
        if (null != n.get(3)) printer.p(' ').p(n.getNode(3));
        printer.p(n.getNode(2));
        endExpression(prec);
    }

    /** Visit the specified primitive type. */
    public void visitPrimitiveType(GNode n) {
        if(n.getString(0).equals("byte")){
            printer.p("char");
        }
        else
        printer.p(n.getString(0));
    } 

    /** Visit the specified string literal. */
    public void visitStringLiteral(GNode n) {
        final int prec = startExpression(160);
        printer.p("__rt::literal(" + n.getString(0) + ")");
        endExpression(prec);
    }

    /** Used for printing out std::cout calls */
    public void visitPrintOutput(GNode n) {
        inOutput = true;
        Node currentNode;
        for (Iterator<Object> iter = n.iterator(); iter.hasNext();) {
            currentNode = (Node)iter.next();
            printer.p(currentNode);
            if (iter.hasNext()) printer.p(" << ");
        }
        inOutput = false;
    }

    /** Prints what's passed into cout */
    public void visitPrintBound(GNode n) {
        printer.p(n.getString(0));
    }

}