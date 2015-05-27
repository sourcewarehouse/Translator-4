/****************************************************
 * TreeConverter converts a Java AST into a C++ AST.
 *
 * Using the visitor pattern, go thru every node in the
 * Java AST and update the node's information.
 * When all the nodes have been updated, the AST has
 * become a C++ AST.
 ****************************************************/

package cpptranslator;

import java.io.*;
import java.util.*;

import xtc.lang.*;
import xtc.parser.*;
import xtc.tree.*;
import xtc.util.*;

public class TreeConverter extends Visitor {

	/* GLOBALS */
	private ArrayList<Node> publicScopes;
    private ArrayList<Node> privateScopes;
    private ArrayList<Node> methodList;

    private ClassDetail currentClass;

    private SymbolTable rootTable;

    private Node javaAST;
    private GNode cppAST;
    private GNode currentClassNode;
    private GNode currentMethodNode;
    private GNode currentExpNode;
    private GNode currentBlockNode;
    private GNode initListNode;
    private GNode newBlockNode;
    private GNode newMethodNode;
    private GNode newExpNode;
    private GNode initMethodNode;

    private HashSet<String> localVar;

    private String classname;
    private String fileName;

    private ArrayList<ClassDetail> classList;
    private Hashtable<String, ClassDetail> classAndClassDetail;
    private Hashtable<String,String> methReturnTypes;

    private ArrayList<Node> constructors;
    private boolean isConstructor;
    private boolean constructorPresent;

    private boolean inMethod;
    /* END GLOBALS */

    /**
     * Constructor that runs the tree conversion using the visitor pattern.
     *
     * @param node    The root node of the Java AST
     * @param Runtime The runtime console
     */
	public TreeConverter(Node node, final xtc.util.Runtime runtime) {

		/* Creates C++ AST from Java AST */
        new Visitor() {

            public void visitCompilationUnit(GNode n) {
                //cppAST = GNode.create("cppAST");
                cppAST = n;
                classList = new ArrayList<ClassDetail>();
                classAndClassDetail = new Hashtable<String, ClassDetail>();
                methReturnTypes = new Hashtable<String,String>();

                rootTable = new SymbolTable();
                fileName = JavaEntities.fileNameToScopeName(n.getLocation().file);
                rootTable.enter(fileName);
                rootTable.mark(n);
                visit(n);
                n = cppAST;

                rootTable.setScope(rootTable.root());
                runtime.console().flush();
            } 

            public void visitClassDeclaration(GNode n) {
                inMethod = false;
                currentClass = new ClassDetail(n.getString(1));

                String currClassName = n.getString(1);
                rootTable.enter(currClassName);
                SymbValStruct val = new SymbValStruct("class", null, null);
                rootTable.current().addDefinition(currClassName, val);
                rootTable.mark(n);

                currentClassNode = n;

                // check if this class extends a superclass
                if (n.get(3) != null && n.getNode(3).getName().equals("Extension")) {
                    String superClassName = n.getNode(3).getNode(0).getNode(0).getString(0);                  
                    ClassDetail superClassDetail = classAndClassDetail.get(superClassName);

                    currentClass.setSuperClass(superClassName);
                    currentClass.setSuperClassDetail(superClassDetail);
                }

                visit(n);
                rootTable.exit();

                n = currentClassNode;
                GNode classBodyCopy = n.ensureVariable(n.getGeneric(5));
                if (!constructorPresent) {
                  classBodyCopy.add(newMethodNode);
                  constructorPresent = true;
                }
                classBodyCopy.add(initMethodNode);
                n.set(5, classBodyCopy);

                currentClass.setclassNode(n);
                classList.add(currentClass);

                // map the current class name to the class' classDetails

                classAndClassDetail.put(currClassName, currentClass);
                
            }

            public void visitClassBody(GNode n) {
                constructors = new ArrayList<Node>();
                constructorPresent = false;
                visit(n);

                int constCount = 0;

                /* Find constructor node and set it to new constructor node */
                for (int i = 0; i<n.size(); i++) {
                    if (n.get(i) instanceof Node) {
                        if (n.getNode(i).getName().equals("ConstructorDeclaration")) {
                            n.set(i, constructors.get(constCount));
                            constCount++;
                            constructorPresent = true;
                        }
                    }
                }

                /* If no constructor, add an implicit one as well as an init method */
                if (!constructorPresent) {
                  newMethodNode = addConstructor(n);
                  constructors.add(newMethodNode);
                  initMethodNode = addInitMethod(newMethodNode.getGeneric(6), currentClass.getSuperClass(), newMethodNode.getGeneric(3));
                  currentClass.getmethodList().add(initMethodNode);
                  currentClass.getpublicScopes().add(initMethodNode);
                }
            }

            private GNode addConstructor(GNode n) {
                GNode nCopy = GNode.create("ConstructorDeclaration", GNode.create("Modifiers", GNode.create("Modifier", "public")),
                              null, currentClass.getclassname(), GNode.create("FormalParameters"), null, 
                              GNode.create("InitList", GNode.create("ConstructorInit", "__vptr", "&__vtable")), GNode.create("Block"));
                return nCopy;
            }

            /**
            * Traverses ConstructorDeclaration node to retrieve unique variables used
            */
            public void visitConstructorDeclaration(GNode n) {
                isConstructor = true;

                currentMethodNode = n;
                currentMethodNode.set(5, n.ensureVariable(n.getGeneric(5)));

                rootTable.enter(n.getString(2));
                rootTable.mark(n);

                localVar = new HashSet<String>();

                visit(n);

                rootTable.exit();

                n = currentMethodNode;

                localVar = null;

                n = currentMethodNode;

                /* Adds an init method to global initMethodNode for placement in class body */
                initMethodNode = addInitMethod(currentMethodNode.getGeneric(5), currentClass.getSuperClass(), currentMethodNode.getGeneric(3));
                currentClass.getmethodList().add(initMethodNode);
                currentClass.getpublicScopes().add(initMethodNode);

                /* Places Init List in constructor */
                newMethodNode = n.ensureVariable(GNode.create(currentMethodNode));
                newMethodNode.add(null);
                newMethodNode.set(6, GNode.create("Block"));
                newMethodNode.set(5, initListNode); 

                currentMethodNode = GNode.create(newMethodNode);
                constructors.add(newMethodNode); // Keep a list of all constructors, there could be more then one

                n = currentMethodNode;

                isConstructor = false;
            } 

            /**
            * Traverses MethodDeclaration node to retriee unique variables used
            *
            * @param n The GNode
            */
            public void visitMethodDeclaration(GNode n) throws IOException {
                inMethod = true;
                currentMethodNode = n;
                boolean isPublic = true;

                /* adds a return statement to main, since in c++ main methods return int */
                if (currentMethodNode.getString(3).equals("main")) {
                    GNode blockCopy = currentMethodNode.getGeneric(7).ensureVariable(currentMethodNode.getGeneric(7));
                    GNode returnNode = GNode.create("ReturnStatement");
                    returnNode.add(GNode.create("IntegerLiteral").add("0"));
                    blockCopy.add(returnNode); 
                    currentMethodNode.set(7, blockCopy);
                } else {
                    String newName = methodNameMangler(n);
                    currentMethodNode.set(3, newName);
                }

                rootTable.enter(n.getString(3));
                rootTable.mark(n);

                String modifiers = getModifiers(n.getGeneric(0));
                String type = getParamTypes(n.getGeneric(4));
                SymbValStruct val = new SymbValStruct("fun", type, modifiers);
                rootTable.current().addDefinition(n.getString(3), val);

                /* Add 'this' parameter to all methods */
                GNode paramsCopy = currentMethodNode.ensureVariable(currentMethodNode.getGeneric(4));
                if (!modifiers.contains("static")) {
                    GNode thisParam = GNode.create("FormalParameter", 
                    GNode.create("Modifiers"), 
                    GNode.create("Type", 
                    GNode.create("QualifiedIdentifier", currentClassNode.getString(1)), null), null, "__this", null);
                    paramsCopy.add(thisParam);
                }

                currentMethodNode.set(4, paramsCopy);

                localVar = new HashSet<String>();

                visit(n);

                rootTable.exit();

                localVar = null;
                n = currentMethodNode;

                /* If not 'main', adds method info to ClassDetail and public or private scopes */
                if ( !(n.getString(3).equals("main")) ) {
                    for (int i=0; i < n.getGeneric(0).size(); i++) {
                        String modifier = n.getGeneric(0).getGeneric(i).getString(0);
                        if (modifier.equals("private")) {
                            currentClass.getprivateScopes().add(n);
                            isPublic = false;
                        } 
                    }
                    currentClass.getmethodList().add(n);

                    if (isPublic) {
                        currentClass.getpublicScopes().add(n);
                    }
                }
                inMethod = false;
            }

            /**
             * Mangles method name declaration with arg types for method overloading
             *
             * @param n The GNode
             * @return mangled name
             */
            public String methodNameMangler(GNode n) {
                /* Add "m_" to front of method */
                final StringBuilder newName = new StringBuilder("m_");
                newName.append(currentMethodNode.getString(3));

                /* If there are arguments for method, append them to method name */
                if (n.getNode(4).size() > 0) {
                    newName.append("_");
                    Node currentNode = null;

                    /* For each arg, get the type and append it to method name */
                    for (Iterator<Object> iter = n.getNode(4).iterator(); iter.hasNext(); ) {
                        currentNode = (Node)iter.next();

                        new Visitor() {
                            public void visitType(GNode n) {
                                newName.append(n.getNode(0).getString(0));
                            }

                            public void visit(Node n) {
                                for (Object o : n) {
                                    // The scope belongs to the for loop!
                                    if (o instanceof Node) { dispatch((Node) o); }
                                }
                            }
                        }.dispatch(currentNode);

                        if (iter.hasNext()) { newName.append('_'); }
                    } 
                }

                return newName.toString();
            }   

            /**
             * Add an 'init' method to the class body
             *
             * @param block a GNode for the block of the class's constructor
             * @param superClassName for use in adding the superclass's init call 
             * @param args a GNode for the args of the class's constructor to add to the init call
             */
            public GNode addInitMethod(GNode block, String superClassName, GNode args) {
                boolean superCallPresent = false;
                ArrayList<GNode> argsToAdd = new ArrayList<GNode>();

                /* Checks if there is a call to super() explicit in the constructor body.
                 * If so, gather its arguments to add to the superclass's init call
                 */
                if (block.size() > 0 && block.get(0) instanceof Node 
                        && block.getNode(0).getName().equals("ExpressionStatement") 
                        && block.getNode(0).get(0) instanceof Node 
                        && block.getNode(0).getNode(0).getName().equals("CallExpression") 
                        && block.getNode(0).getNode(0).get(2) instanceof String 
                        && (block.getNode(0).getNode(0).getString(2).equals("super") 
                                || block.getNode(0).getNode(0).getString(2).equals("m_super")) ) {

                    superCallPresent = true;
                    for (int i = 0; i < block.getNode(0).getNode(0).getNode(3).size(); i++) {
                        argsToAdd.add(block.getNode(0).getNode(0).getNode(3).getGeneric(i));
                    }
                }

                /* Add the superInitCall to the current class's init method's body */
                GNode blockCopy = createSuperInitCall(superClassName, superCallPresent, argsToAdd, block);

                /* Build GNode for init method declaration */
                GNode initMethodNode = GNode.create("MethodDeclaration", GNode.create("Modifiers", GNode.create("Modifier", "public")), 
                                       null, GNode.create("Type", GNode.create("QualifiedIdentifier", currentClass.getclassname()), null), 
                                       "init", GNode.create("FormalParameters", GNode.create("FormalParameter", 
                                       GNode.create("Modifiers"), GNode.create("Type", GNode.create("QualifiedIdentifier", 
                                       currentClass.getclassname()), null), null,  "__this", null)), null, null, blockCopy);

                /* Make the formal parameters a variable GNode */
                GNode argsCopy = initMethodNode.ensureVariable(initMethodNode.getGeneric(4));
                for (int l = 0; l < args.size(); l++) {
                    argsCopy.add(args.getNode(l));
                }

                initMethodNode.set(4, argsCopy);

                return initMethodNode;
            }

            /**
             * Creates an ExpressionStatement GNode for the call to a class's superclass's init method, and adds it to the constructor block.
             *
             * @param superClassName super class to init
             * @param superCallPresent if there is an explicit super() call already
             * @param argsToAdd the args from a superclass's constructor to add to the super init call
             * @param block reference to the subclass's constructor block
             * @return blockCopy a new copy of the constructor's block
             */
            private GNode createSuperInitCall(String superClassName, boolean superCallPresent, ArrayList<GNode> argsToAdd, GNode block) {
                GNode superClassInitCall = null;

                /* Build Expression Statement GNode */
                if (superClassName != null) {
                    superClassInitCall = superClassInitCall.ensureVariable(GNode.create("ExpressionStatement", 
                                         GNode.create("CallExpression", null, null, "__" + superClassName + 
                                         "::init", GNode.create("Arguments",            
                                         GNode.create("PrimaryIdentifier", "__this")))));
                } else {
                    superClassInitCall = superClassInitCall.ensureVariable(GNode.create("ExpressionStatement", 
                                         GNode.create("CallExpression", null, null, "__Object::init", GNode.create("Arguments",
                                         GNode.create("PrimaryIdentifier", "__this")))));
                }

                /* Add args from super() call to super class init call */
                int i = 0;
                if (superCallPresent) {
                    i = 1;
                    GNode argsCopy = superClassInitCall.ensureVariable(superClassInitCall.getGeneric(0).getGeneric(3));
                    for (int m = 0; m < argsToAdd.size(); m++) {
                        argsCopy.add(argsToAdd.get(m));
                    }
                    superClassInitCall.getNode(0).set(3, argsCopy);
                } else {
                    i = 0;
                }

                /* Add superclass init call to block Copy*/
                GNode blockCopy = GNode.create("Block"); 
                blockCopy.add(superClassInitCall);

                /* If there's a super() call present, skip the first node in the block before adding the rest to the copy */
                for (int j = i; j < block.size(); j++) {
                    blockCopy.add(block.getGeneric(j));
                }

                /* Add return statement */
                blockCopy.add(GNode.create("ReturnStatement", GNode.create("PrimaryIdentifier", "__this")));

                return blockCopy;
            }

            public void visitFormalParameters(GNode n) {
                if (localVar != null) {
                    for (int i=  0; i < n.size(); i++) {
                        GNode param = n.getGeneric(i);
                        String modifier = getModifiers(param.getGeneric(0));
                        String type = getType(param.getGeneric(1));
                        String name = param.getString(3);
                        SymbValStruct val = new SymbValStruct("par", type, modifier);
                        rootTable.current().addDefinition(name, val);
                        localVar.add(name);
                    }
                }
            }

            /**
             * Gets all of the modifiers concatenated into a string deliminated by spaces
             *
             * @return all of the modifiers concatenated into a string deliminated by spaces
             */
            private String getModifiers(GNode n) {
                String mods = "";
                for (int i = 0; i < n.size(); i++) {
                  mods+=n.getNode(i).getString(0);
                }
                return mods;
            }

            /**
             * @return type from Type Node, "" if Type is blank
             */
            private String getType(GNode n) {
                if (n.size() != 0) { return n.getGeneric(0).getString(0); }
                return "";
            }

            /**
             * @param n is a FormalParameters Node
             * @return Concatenates the types of all the parameters delimited by a space
             */
            private String getParamTypes(GNode n) { 
                String output = "";
                for (int i = 0; i < n.size(); i++) {
                    output += (getType(n.getGeneric(i).getGeneric(1)) + " ");
                }        
                return output.trim();   /* Remove the trailing whitespace */
            }

            public void visitFieldDeclaration(GNode n) {
                visit(n);
                if (!inMethod) {
                    boolean isPublic = true;

                    for (int i = 0; i < n.getGeneric(0).size(); i++) {
                        String modifier = n.getGeneric(0).getGeneric(i).getString(0);
                        if (modifier.equals("private")) {
                            currentClass.getprivateScopes().add(n);
                            isPublic = false;
                        }             
                    }

                    //classDetail stuff
                    currentClass.getallScopes().add(n);

                    if (isPublic) {
                        currentClass.getpublicScopes().add(n);
                    }
                }

                String modifier = getModifiers(n.getGeneric(0));
                String type = getType(n.getGeneric(1));

                SymbValStruct val = new SymbValStruct("var", type, modifier);
                rootTable.current().addDefinition(n.getNode(2).getNode(0).getString(0), val);
            }

            /**
             * Checks to make sure declared variable is inside a method, i.e. arraylist is null.
             * Adds variables to list, prints them
             */ 
            public void visitDeclarator(GNode n) {
                if (localVar != null) {
                    String name = n.getString(0);
                    localVar.add(name);
                }
                visit(n);
            }

            /**
             * Checks to make sure variable hasn't been declared, or wasn't an argument
             * to determine if it's unique. This ensures printing of global variables used inside methods.
             */
            public void visitPrimaryIdentifier(GNode n) {
                String name = n.getString(0);   

                if (n.getString(0).equals("args")) {
                    n.set(0, "argv");
                }

                boolean found = false;

                for (int i = 0; i < currentClass.getallScopes().size(); i++) {
                    Node m = currentClass.getallScopes().get(i);
                    if (null != localVar) {
                        if (m.getGeneric(2).getGeneric(0).getString(0).equals(name) 
                                && !localVar.contains(name)) {

                            GNode newNode = GNode.create("LocalClassFieldReference");

                            newNode.add(name);
                            n.set(0, newNode);
                            found = true;
                            break;
                        }
                    }
                }

                if (!found && (currentClass.getSuperClassDetail() != null) ) {
                    checkSuperClass(n, currentClass.getSuperClassDetail(), name);
                }
            }

            public void checkSuperClass(GNode n, ClassDetail classDetail, String name) {
                boolean found = false;
                for (int i = 0; i < classDetail.getallScopes().size(); i++) {
                    Node m = classDetail.getallScopes().get(i);
                    if (null != localVar) {
                        if (m.getGeneric(2).getGeneric(0).getString(0).equals(name) 
                                && !localVar.contains(name)) {

                            GNode newNode = GNode.create("LocalClassFieldReference");

                            newNode.add(name);
                            n.set(0, newNode);
                            found = true;
                            break;
                        }
                    }

                    if (!found && (classDetail.getSuperClassDetail() != null)) {
                        checkSuperClass(n, classDetail.getSuperClassDetail(), name);
                    }
                }
            }

            public void visitExpressionStatement(GNode n) {
                currentExpNode = n;
                visit(n);

                if (n.getNode(0).getName().equals("CallExpression") 
                        && (n.getNode(0).size() > 2) ) {

                    if ( (n.getNode(0).getString(2).equals("print")) 
                              || (n.getNode(0).getString(2).equals("println")) ) {
                        n.set(0, newExpNode);
                    }
                }
            }

            public void visitBlock(GNode n) {
                rootTable.enter(rootTable.freshName("block"));
                rootTable.mark(n);

                if (isConstructor) {
                    initListNode = GNode.create("InitList");
                    GNode vtableNode = GNode.create("ConstructorInit");
                    vtableNode.add("__vptr"); vtableNode.add("&__vtable");
                    initListNode.add(vtableNode);   
                }

                visit(n);

                rootTable.exit();

                for (int i = 0; i < n.size(); i++) {
                    if (n.getNode(i).hasName("ConstructorDeclaration")) {
                        n.set(i, newMethodNode);
                    }
                }
            }

            public void visitCallExpression(GNode n) {

                if (n.getString(2).equals("print")) {
                    newExpNode = GNode.create("CallExpression");
                    newExpNode.add(0, printHelper((GNode)n.getNode(3), false));
                    visit(n);
                } else if (n.getString(2).equals("println")) {
                    newExpNode = GNode.create("CallExpression");
                    newExpNode.add(printHelper((GNode)n.getNode(3), true));
                    visit(n);
                } else {
                    currentExpNode = n;

                    final StringBuilder newName = new StringBuilder("m_");
                    newName.append(currentExpNode.getString(2));

                    currentExpNode.set(2, newName.toString());

                    visit(n);
                    currentExpNode = n;

                    GNode argsCopy = n.ensureVariable(n.getGeneric(3));
                    if (n.get(0) != null) {
                        if (n.getNode(0).get(0) instanceof String) {
                            SymbValStruct symbStruct = (SymbValStruct)rootTable.current().lookup(n.getNode(0).getString(0));

                            if (symbStruct == null) {
                            } else { 
                                argsCopy.add(n.getNode(0));
                            }

                        } else {
                            argsCopy.add(n.getNode(0));
                        }

                        currentExpNode.set(3, argsCopy);
                    }           
                }

                n = currentExpNode;
            } 

            /**
             * Converts System.out.print/println to std::cout
             *
             * @param n is the node of the print statement n.getnode(3) in visitCallExpression
             * @param newLine - true for System.out.println, false for System.out.print 
             */
            private GNode printHelper(GNode n, boolean newLine) {
                /* Create the start of a stream by adding std::cout */
                GNode printOutput = GNode.create("PrintOutput");
                printOutput.add(0, GNode.create("PrintBound").add(0, "std::cout"));
                  
                /* Append the arguments as nodes - the stuff to be printed */
                for (int i = 0; i < n.size(); i++) {
                    if (n.get(i) != null) printOutput.add(n.get(i));
                }
                  
                if (newLine) {
                    printOutput.add(GNode.create("PrintBound").add(0, "std::endl"));
                }
                return printOutput;
            }

            public void visitSubscriptExpression(GNode n) {
                if (n.getNode(0).get(0) instanceof String) {
                    String primIden = n.getGeneric(0).getString(0);
                    if (primIden.equals("args")) {
                        n.getGeneric(0).set(0, "argv");
                    } 
                }
            } 

            public void visitPrimitiveType(GNode n) {
                if (n.getString(0).equals("int")) {
                    n.set(0, "int32_t");
                } else if (n.getString(0).equals("boolean")) {
                    n.set(0, "bool");
                }
            }

            /**
             * Helper method to print unique variables to console
             */      
            public void visit(Node n) {
                for (Object o : n) {
                    // The scope belongs to the for loop!
                    if (o instanceof Node) { dispatch((Node) o); }
                }
            }
        }.dispatch(node);
	}

	/* Getters */
	public SymbolTable 						getRootTable() 			 { return this.rootTable; }
	public String 							getFileName() 	  		 { return this.fileName; }
	public ArrayList<ClassDetail> 			getClassList() 			 { return this.classList; }
    public Hashtable<String, ClassDetail> 	getClassAndClassDetail() { return this.classAndClassDetail; }
    public Hashtable<String,String> 		getMethReturnTypes() 	 { return this.methReturnTypes; }

}