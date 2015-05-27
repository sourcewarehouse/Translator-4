/****************************************************
 * HeaderPrinter prints the virtual table for a class.
 *
 * Input: One class within the test file
 * Output: One .h file containing all the vtable 
 *         information for that class
 ***************************************************/

package cpptranslator;

import java.io.*;
import java.util.*;

import xtc.lang.*;
import xtc.tree.*;
import xtc.util.*;

public class HeaderPrinter {
    String                 cn;   /* Name of current class */

    /* ClassAndClassDetail Stuff */
    ArrayList<Node>        publicMethodList;
    ArrayList<Node>        privateMethodList;
    ArrayList<Node>        methodList;
    ArrayList<String> constructorsInfo = new ArrayList<String>();
    
    protected final Printer h_printer;

    /* Used to implement vtable
       Maps each class to its class detail */
    Hashtable<String, ClassDetail>  classAndClassDetail;  

    String currentClassName;
 
    /**
     * Constructor creates a HeaderPrinter Project
     * @param cn is the class name
     * @param methodList is a list of Nodes containing all of the methods accessible by the class
     * @param publicMethodList is a list of public methods within the class
     * @param privateMethodList is a list of private methods within the class
     * @param classAndClassDetail is a symboltable alternative that contains data about the Java AST
     */
    public HeaderPrinter(String cn, ArrayList<Node> methodList, ArrayList<Node> publicMethodList, 
                         ArrayList<Node> privateMethodList, Hashtable<String, ClassDetail> classAndClassDetail, 
                         ArrayList<String> constructorsInfo, Printer h_printer, String currentClassName) {
        this.cn                  = cn;
        this.h_printer           = h_printer;
        this.classAndClassDetail = classAndClassDetail;
        this.methodList          = methodList;
        this.privateMethodList   = privateMethodList;
        this.publicMethodList    = publicMethodList;
        this.constructorsInfo    = constructorsInfo;
        this.currentClassName    = currentClassName;

    }

    /**
     * Prints out information at the top of each header file.
     */
    public void printHeaderFileIncludes() {  
        h_printer.pln("#pragma once");
        h_printer.pln("#include <iostream>");
        h_printer.pln("#include \"java_lang.h\"");
        h_printer.pln("using namespace java::lang;");
        h_printer.pln("using namespace __rt;");

        String superClass;
        if (classAndClassDetail != null && classAndClassDetail.get(currentClassName) != null ) {
            superClass = classAndClassDetail.get(currentClassName).getSuperClass();
            if (superClass != null) {
                h_printer.pln("#include \"" + superClass + ".h\"");
            }
        }
        h_printer.pln();
    }

    /**
     *Creates the the .h file
     */
    public void createVTable(){
        h_printer.p("namespace oop {\n");
        h_printer.incr();
        h_printer.indent().p("struct __" + cn+";").pln(); // struct __xyz;
        h_printer.indent().p("struct __" + cn+"_VT;").pln().pln(); // struct __xyz_VT;
        h_printer.indent().p("typedef __rt::Ptr<__" + cn+"> "+cn+";").pln().pln(); // typedef __xyz* xyz;

        h_printer.indent().p("struct  __"+cn+" {").pln().pln(); // struct __xyz {
        h_printer.indent().indent().p("__"+cn+"_VT* __vptr;").pln().pln(); // __xyz_VT* __vptr;

        /* Constructor goes here */
        for (int i = 0; i < constructorsInfo.size(); i++) {
            h_printer.indent().indent().pln("__" + cn + "(" + constructorsInfo.get(i) + ");");
        }

        ArrayList<String> classFields = printMethodLists(publicMethodList, privateMethodList, cn, classAndClassDetail);
        
        printMethodDetails(classFields);	

    }
	 
     /**
      * @param publicstuff - public fields and methods
      * @param privatestuff - private fields and methods
      * @param cn - classname
      * @param classAndClassDetail contains info about the Java AST
      * @return ArrayList<String> of fields and methods the class contains 
      */
     private ArrayList<String> printMethodLists(ArrayList<Node> publicStuff, ArrayList<Node> privateStuff, 
                                                String cn, Hashtable<String, ClassDetail> classAndClassDetail) {

        /* Printing private */
        h_printer.pln().indent().indent().p("private:");
        h_printer.incr();
        for (int i = 0; i < privateStuff.size(); i++) {
            /* differentiate between a method and a field */
            Node n = privateStuff.get(i);
            if (n.getName().equals("FieldDeclaration")) {
                h_printer.pln().indent().indent().p(printFieldDeclaration(n));
                h_printer.p(";");
            }
            else if(n.getName().equals("MethodDeclaration")){
                h_printer.pln().indent().indent().p(grabMethodData(n, false, false, cn, classAndClassDetail));
                h_printer.p(";");
            }
        }

        /* Printing public */
        h_printer.pln().pln();
        h_printer.decr().indent().indent().p("public:").pln();
        h_printer.incr();
        ArrayList<String> classFields = new ArrayList<String>();
        ArrayList<String> parentClassFields = getSuperFields(classAndClassDetail, cn, classFields);
        for (String s : parentClassFields) {
            h_printer.indent().indent().p(s);
            h_printer.p(";").pln();
        }        
        for (int i = 0; i < publicStuff.size(); i++) {
            /* differentiate between a method and a field */
            Node n = publicStuff.get(i);
            if (n.getName().equals("FieldDeclaration")) {
                String fdStr = printFieldDeclaration(n);
                h_printer.indent().indent().p(fdStr);
                classFields.add(fdStr);
                h_printer.p(";").pln();
            }

            /* Doesnt print here... Done above - this is a mess... */
            else if (n.getName().equals("MethodDeclaration")) {
                classFields.add(grabMethodData(n,false, false, cn, classAndClassDetail));
            }     
        }

        return classFields;
    }

    /**
     * Does the bulk of the printing
     * @param classFields is a list of methods and fields in class 
     */
	private void printMethodDetails(ArrayList<String> classFields){	
        ArrayList<String> methodNames = new ArrayList<String>();
        Hashtable<String, Node> methodHash = new Hashtable<String, Node>();
        ArrayList<Node> methodsLeftToPrint = new ArrayList<Node>();
        
        for (int i = 0; i < methodList.size(); i++) {
            if (!methodList.get(i).getString(3).equals("m_hashCode") 
                    && !methodList.get(i).getString(3).equals("m_equals") 
                    && !methodList.get(i).getString(3).equals("m_getClass") 
                    && !methodList.get(i).getString(3).equals("m_toString") 
                    && !methodList.get(i).getString(3).equals("init")) {
                methodsLeftToPrint.add(methodList.get(i));
            }

            methodNames.add(methodList.get(i).getString(3));
            methodHash.put(methodList.get(i).getString(3), methodList.get(i));
        }

        /* Methods specific to xyz */
        for (int i=0; i< methodList.size();i++) {
            h_printer.indent().indent().p("static " + grabMethodData(methodList.get(i), false, false, cn, classAndClassDetail));            
            if (!grabMethodData(methodList.get(i), false, false, cn, classAndClassDetail).equals("")) {
                h_printer.p(";\n");
            }
        }

        /* Function returning the class object representing xyz */
        h_printer.indent().indent().p("static Class __class();").pln();
        h_printer.indent().indent().p("static __"+cn+"_VT  __vtable;").pln(); /* static __xyz_VT __vtable; */
        h_printer.indent().p("};").pln().pln();

        //------------------------------

        /* vtable layout for xyz */
        h_printer.indent().p("struct __" + cn + "_VT {").pln().pln(); /* struct __xyz_VT { */
        h_printer.indent().indent().p("Class __isa;").pln();
        h_printer.indent().indent().p("void (*__delete)(__" + cn + "*);").pln();            

        /* Methods specific to xyz */
        boolean hashCodePrinted = false;
        boolean equalsPrinted   = false;
        boolean getClassPrinted = false;
        boolean toStringPrinted = false;


        /* Test if function already has these, if not point to obj version */
        if (!methodNames.contains("m_hashCode"))
            h_printer.indent().indent().p("int32_t (*m_hashCode)("+cn+");").pln(); /*static int32_t hashCode(xyz); */
        else {
            h_printer.indent().indent().p(grabMethodData(methodHash.get("m_hashCode"), true, false, cn, classAndClassDetail)).p(";").pln();
            hashCodePrinted = true;
        }
        if(!methodNames.contains("m_equals"))
            h_printer.indent().indent().p("bool (*m_equals)("+cn+", Object);").pln(); /* static bool equals(xyz, Object); */
        else {
            h_printer.indent().indent().p(grabMethodData(methodHash.get("m_equals"), true, false, cn, classAndClassDetail)).p(";").pln();
            equalsPrinted = true;
        }
        if(!methodNames.contains("m_getClass"))
            h_printer.indent().indent().p("Class (*m_getClass)("+cn+");").pln(); /* static Class getClass(xyz); */
        else {
            h_printer.indent().indent().p(grabMethodData(methodHash.get("m_getClass"), true, false, cn, classAndClassDetail)).p(";").pln();
            getClassPrinted = true;
        }
        if(!methodNames.contains("m_toString"))
            h_printer.indent().indent().p("String (*m_toString)("+cn+");").pln(); /* static String toString(xyz); */
        else {
            h_printer.indent().indent().p(grabMethodData(methodHash.get("m_toString"), true, false, cn, classAndClassDetail)).p(";").pln();
            toStringPrinted = true;
        }

        ArrayList<String> parentClassFields = getSuperMethods(classAndClassDetail, cn, classFields, cn, methodList,true, false);
        for (String s : parentClassFields) {
            h_printer.indent().indent().p(s);
            h_printer.p(";").pln();
        }

        Boolean staticMethod = false;
        int staticCount = 0;
        for (int i=0; i < methodList.size(); i++) {
            Node modifiersNode = methodList.get(i).getNode(0);
            for (int j = 0; j < modifiersNode.size(); j++) {
                if (modifiersNode.getNode(j).getString(0).equals("static")) {
                    staticMethod = true;
                    staticCount++;
                    break;
                }
            }
            if (!staticMethod && !methodList.get(i).getString(3).equals("init")) {
                if(methodList.get(i).getString(3).equals("m_hashCode") && hashCodePrinted)
                    continue;
                else if(methodList.get(i).getString(3).equals("m_equals") && equalsPrinted)
                    continue;
                else if(methodList.get(i).getString(3).equals("m_getClass") && getClassPrinted)
                    continue;
                else if(methodList.get(i).getString(3).equals("m_toString") && toStringPrinted)
                    continue;
                else {
                    h_printer.indent().indent().p(grabMethodData(methodList.get(i), true, false, cn, classAndClassDetail));
                    h_printer.p(";\n");
                }
            }
        }

        h_printer.pln().indent().indent().p("__"+cn+"_VT()").pln(); /* __xyz_VT() */
        h_printer.indent().indent().indent().p(": __isa(__"+cn+"::__class()),").pln(); /* : __isa(__xyz::__class()), */
        h_printer.indent().indent().indent().p("__delete(&__rt::__delete<__" + cn + ">)");
        if (!methodNames.contains("m_hashCode")) {
            h_printer.p(",").pln();
            h_printer.indent().indent().indent().p("m_hashCode((int32_t(*)("+cn+"))&__"+getSuperClass(classAndClassDetail, cn, "m_hashCode")+"::m_hashCode)");//hashCode(&__xyz::hashCode),
        }
        else {
            h_printer.p(",").pln();
            h_printer.indent().indent().indent().p(methodHash.get("m_hashCode").getString(3) + "(&__"+cn+"::"+ methodHash.get("m_hashCode").getString(3)+")");
        }

        if (!methodNames.contains("m_equals")) {
            h_printer.p(",").pln();
            h_printer.indent().indent().indent().p("m_equals((bool(*)("+cn+", Object))&__"+getSuperClass(classAndClassDetail, cn, "m_equals")+"::m_equals)");//equals(&__xyz::equals),
        }
        else {
            h_printer.p(",");
            h_printer.indent().indent().indent().p(methodHash.get("m_equals").getString(3) + "(&__"+cn+"::"+ methodHash.get("m_equals").getString(3)+")");
        }
        if (!methodNames.contains("m_getClass")) {
            h_printer.p(",").pln();
            h_printer.indent().indent().indent().p("m_getClass((Class(*)("+cn+"))&__"+getSuperClass(classAndClassDetail, cn, "m_getClass")+"::m_getClass)");//getClass((Class(*)(xyz))&__Object::getClass)
        }
        else {
            h_printer.p(",").pln();
            h_printer.indent().indent().indent().p(methodHash.get("m_getClass").getString(3) + "(&__"+cn+"::"+ methodHash.get("m_getClass").getString(3)+")");
        }
        if (!methodNames.contains("m_toString")) {
            h_printer.p(",").pln();
            h_printer.indent().indent().indent().p("m_toString((String(*)("+cn+"))&__"+getSuperClass(classAndClassDetail, cn, "m_toString")+"::m_toString)");
        }
        else {
            h_printer.p(",").pln();
            h_printer.indent().indent().indent().p(methodHash.get("m_toString").getString(3) + "(&__"+cn+"::"+ methodHash.get("m_toString").getString(3)+")");
        }

        //toString

        ArrayList<String> parentClassFields2 = getSuperMethods(classAndClassDetail, cn, classFields, cn, methodList,false,true);
        for (String s : parentClassFields2) {
            h_printer.p(",").pln();
            h_printer.indent().indent().indent().p(s);
        }

        if (methodsLeftToPrint.size() > 0) {
            if (staticCount == 0 || (staticCount != methodsLeftToPrint.size())) {
                h_printer.p(",").pln();
            }
        }

        /* Methods specific to xyz */

        staticMethod = false;
        for (int i = 0; i < methodsLeftToPrint.size(); i++) {
            Node modifiersNode = methodsLeftToPrint.get(i).getNode(0);
            for (int j = 0; j < modifiersNode.size(); j++) {
                if (modifiersNode.getNode(j).getString(0).equals("static")) {
                    staticMethod = true;
                    break;
                }
            }
            if (!staticMethod && !methodsLeftToPrint.get(i).getString(3).equals("init")) {
                h_printer.indent().indent().indent().p(methodsLeftToPrint.get(i).getString(3) + "(&__"+cn+"::"+methodsLeftToPrint.get(i).getString(3)+")");
                if ((i != (methodsLeftToPrint.size() - 1 )) ) {
                    h_printer.p(",\n"); 
                }
            }
        }

        h_printer.p("{}");
        h_printer.pln().indent().p("};").pln();
        h_printer.p("};").pln();
    }

	   
    /**
     * @param n Node for the method declaration
     * @param wantPointer - do we want to format as a pointer for vtable
     * @param wantConstruct - do we want to format for vtable constructor
     * @param cn is className
     * @param classAndClassDetail contains Java AST details
     * @return formatted String of the method
     */
    private String grabMethodData(Node n, boolean wantPointer, boolean wantConstruct, String cn, Hashtable<String, ClassDetail> classAndClassDetail){
        
        /* Grab modifiers */
        Boolean isStatic = false;
        String methodStatic="";
        for(int i = 0; i < n.getNode(0).size(); i++){
            if(n.getNode(0).getNode(i).getString(0).equals("static")){
                isStatic = true;
                break;
            }
        }
        if(n.getNode(0).size()==2){
            methodStatic = n.getNode(0).getString(1) + " ";
            } //static
        String returnType = "";
        if(n.getNode(2)!=null){
          if(n.getNode(2).getName().equals("VoidType")){
            returnType = "void ";
          }
          else if(n.getNode(2).size() > 0)
            returnType = n.getNode(2).getNode(0).getString(0);
        }
        String methodName = n.getString(3);
        //Grab formal parameters
        ArrayList<String> formalParams = new ArrayList<String>();
        if(isStatic){
            for(int i=0;i<n.getNode(4).size();i++){
                formalParams.add(getFormalParameters(n.getNode(4).getNode(i), cn, classAndClassDetail, false));
            }
            //formalParams.add(getFormalParameters(n.getNode(4).getNode((n.getNode(4).size())), cn, classAndClassDetail, true));
        }
        else {
            for(int i=0;i<n.getNode(4).size() - 1;i++){
                formalParams.add(getFormalParameters(n.getNode(4).getNode(i), cn, classAndClassDetail, false));

            }
            formalParams.add(getFormalParameters(n.getNode(4).getNode((n.getNode(4).size() - 1)), cn, classAndClassDetail, true));
        }
        String completeName = "";
        String vt_constr = "";
        if(wantPointer){
          completeName = returnType + " (*" + methodName + ")";
        }
        else {
          completeName = methodStatic + returnType +" "+ methodName;
        }

        completeName += "(";
        
        
        if(formalParams.size() > 0){
          //size -1 cause its picking up classname as last entry wtf? dumb fix
            for(int i=0;i<formalParams.size();i++){
              if(i!=0)
                completeName += (", " + formalParams.get(i));
              else
                completeName += formalParams.get(i);
            }
        }

        completeName += ")";
        
        if(wantConstruct){
          vt_constr += (methodName+"(("+returnType+"(*)(");

          if(formalParams.size() > 0){
          //size -1 cause its picking up classname as last entry wtf? dumb fix
            for(int i=0;i<formalParams.size();i++){
              if(i!=0)
                vt_constr += (", " + formalParams.get(i));
              else
                vt_constr += formalParams.get(i);
            }
          }

          vt_constr += ("))(&__"+getSuperClass(classAndClassDetail, cn, methodName)+"::"+methodName+"))");
          return vt_constr;
        }

        return completeName;
    }

    /**
     * @param Node n representing the FormalParameters Node
     * @param cn is className
     * @param classAndClassDetail contains Java AST info
     * @param  lastOne is it last in the list of parameters
     * @return String representing a formatted list of params
     */
    private String getFormalParameters(Node n, String cn, Hashtable<String, ClassDetail> classAndClassDetail, boolean lastOne){
        String paramString="";

        //Type
        if((null != n.get(1)) && (n.get(1) instanceof Node)){
            if(n.getNode(1).size() > 0){
                String pType = n.getNode(1).getNode(0).getString(0);              
                if(lastOne && getSuperClassNames(classAndClassDetail, cn).contains(pType))
                    paramString+=cn;
                else
                    paramString+=pType;
            }
        }
        return paramString;
    }

    /**
     * @param n is Node of field
     * @return formatted string representing a field
     */
    private String printFieldDeclaration(Node n){
        String totalString = "";
        for(int i=0; i < n.getNode(0).size(); i++){
            if(n.getNode(0).getNode(i).get(0) instanceof String && n.getNode(0).getNode(i).getString(0).equals("static")){
                totalString += "const " + n.getNode(0).getNode(i).getString(0) + " ";
                break;
            }
        }
        for(int i=0;i<n.getNode(1).size();i++){
            if (n.get(1) != null && n.getNode(1).get(i) != null && n.getNode(1).getNode(i).getString(0) != "null") {
                totalString+=(n.getNode(1).getNode(i).getString(0) +" ");
            }
        }
        //Declarators
        for(int i=0;i<n.getNode(2).size();i++){
            if (n.get(2) != null && n.getNode(2).get(i) != null && n.getNode(2).getNode(i).getString(0) != "null"){                         
                totalString += (n.getNode(2).getNode(i).getString(0));
                if(n.getNode(2).getNode(i).get(2) != null ){
                    if (n.getNode(2).getNode(i).getNode(2).getName().equals("IntegerLiteral")) 
                        totalString += (" = " + n.getNode(2).getNode(i).getNode(2).getString(0) );
                    else
                        totalString += (" = " + "__rt::literal(" + n.getNode(2).getNode(i).getNode(2).getString(0) + ")");
                }

            }
        }
        return totalString;
    }

    /**
     * @param classAndClassDetail contains info about Java AST
     * @param cn is classname
     * @param methodName is a method name to search
     * @return name of the superclass that contains the method
     */
    private String getSuperClass(Hashtable<String, ClassDetail> classAndClassDetail, String cn, String methodName){
        ClassDetail superClassDetail = classAndClassDetail.get(cn).getSuperClassDetail();
            if (superClassDetail!=null) {
                ArrayList<Node> methodList = superClassDetail.getmethodList();
                ArrayList<String> strMethodList = new ArrayList<String>();
                for (Node n: methodList) {
                    strMethodList.add(n.getString(3));
                }

                if(strMethodList.contains(methodName)) {
                    return superClassDetail.getclassname();
                } else {
                    return getSuperClass(classAndClassDetail, superClassDetail.getclassname(), methodName);
                }
            }
        return "Object";
    }

    /**
     * @param classAndClassDetail
     * @param cn - classname
     * @param classFields - a list of fields available in cn
     * @return ArrayList of names of all of the methods accessible in superclasses
     */
    private ArrayList<String> getSuperFields(Hashtable<String, ClassDetail> classAndClassDetail, 
                              String cn, ArrayList<String> classFields) {

        ArrayList<String> additionalFields = new ArrayList<String>();
        ClassDetail superClassDetail = classAndClassDetail.get(cn).getSuperClassDetail();

        if (superClassDetail!=null) {
            ArrayList<Node> publicStuff = superClassDetail.getpublicScopes();

            for (int i = 0; i < publicStuff.size(); i++) {
                /* differentiate between a method and a field */
                Node n = publicStuff.get(i);
                String currentStr = "";
                if (n.getName().equals("FieldDeclaration")) {
                    currentStr = printFieldDeclaration(n);
                }             

                if (!currentStr.equals("") && !classFields.contains(currentStr) && !additionalFields.contains(currentStr)) {
                additionalFields.add(currentStr);
                }                      
            }

            additionalFields.addAll(getSuperFields(classAndClassDetail, superClassDetail.getclassname(), additionalFields));      
        }
        return additionalFields;
    }

    /**
     * @param classAndClassDetail
     * @param cn - classname
     * @param classFields - a list of methods already added
     * @param root - root class name (same as first instance of cn)
     * @param methodList - list of methods in cn, no duplicates
     * @param wantPointer - do we want method format as a pointer for the vtable?
     * @param wantConstruct - do we want method format as part of the constructor for the vtable?
     * @return ArrayList of names of all of the methods accessible in superclasses
     */
    private ArrayList<String> getSuperMethods(Hashtable<String, ClassDetail> classAndClassDetail, 
                                               String cn, ArrayList<String> classFields, String root, 
                                               ArrayList<Node> methodList,boolean wantPointer, boolean wantConstruct) {
        ArrayList<String> additionalFields = new ArrayList<String>();
        ClassDetail superClassDetail = classAndClassDetail.get(cn).getSuperClassDetail();
        if (superClassDetail!=null) {
            ArrayList<Node> publicStuff = superClassDetail.getpublicScopes();
            boolean alreadyPrinted = false;
            for (int i = 0; i < publicStuff.size(); i++) {
                alreadyPrinted = false;
                /* differentiate between a method and a field */
                Node n = publicStuff.get(i);
                String currentStr = "";             
                if (n.getName().equals("MethodDeclaration")) {
                    currentStr = grabMethodData(n,wantPointer, wantConstruct, root, classAndClassDetail);
                }

                String uniqueMethod = null;

                for (int j = 0; j < methodList.size(); j++) {
                    uniqueMethod = methodList.get(j).getString(3);
                        
                    if (currentStr.contains(uniqueMethod)) {
                        alreadyPrinted = true;
                        break;
                    }
                }

                if (alreadyPrinted) {
                    continue;
                }

                if (!currentStr.equals("") && !classFields.contains(currentStr) && !additionalFields.contains(currentStr)) {
                    if (!currentStr.contains("m_hashCode") 
                            && !currentStr.contains("m_equals")
                            && !currentStr.contains("m_getClass")
                            && !currentStr.contains("m_toString")
                            &&!currentStr.contains("init")) {

                        additionalFields.add(currentStr);
                    }
                }                                    
            }
            additionalFields.addAll(getSuperMethods(classAndClassDetail, superClassDetail.getclassname(), additionalFields, cn, methodList,wantPointer, wantConstruct));     
        }
        return additionalFields;
    }

    /**
     * @param classAndClassDetail is the Hashtable contain details about the Java AST
     * @param cn is the classname
     * @return ArrayList<String> name of all superclasses of given class name
     */
    private ArrayList<String> getSuperClassNames(Hashtable<String, ClassDetail> classAndClassDetail, String cn) {
        ArrayList<String> superClassNames = new ArrayList<String>();  

        ClassDetail superClassDetail = classAndClassDetail.get(cn).getSuperClassDetail();
        if (superClassDetail != null) {
            superClassNames.add(superClassDetail.getclassname());
            superClassNames.addAll(getSuperClassNames(classAndClassDetail, superClassDetail.getclassname()));
        }
        return superClassNames;
    }
}
