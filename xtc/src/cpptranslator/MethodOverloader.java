/***********************************************
 * MethodOverloader handles method overloading.
 **********************************************/

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

/**
 * Builds an overloaded method call for the given method name and arguments
 * @author OnTheCSide
 * @version Final
 */
public class MethodOverloader {

	private String methodName;
	private GNode methodArgs;
	private String callerName;
	private boolean isStatic;
	private Hashtable<String, ClassDetail> classAndClassDetail;
	private SymbolTable rootTable;
	private String overloadedName;

	/**
	 * Constructs an instance of MethodOverloader
	 * @param methodName method call to be overloaded
	 * @param methodArgs the arguments for the method call
	 * @param callerName the object that called the method
	 * @param isStatic indicates whether the method is static
	 * @param classAndClassDetail data structure featuring each class's ClassDetail object
	 * @param rootTable this translation's symbol table at the scope as defined in visitCallExpression from CPPPrinter.java
	 * @return instance of MethodOverloader
	 */
	public MethodOverloader(String methodName, GNode methodArgs, String callerName, boolean isStatic,
	 Hashtable<String, ClassDetail> classAndClassDetail, SymbolTable rootTable){
		this.methodName = methodName;
		this.methodArgs = methodArgs;
		this.callerName = callerName;
		this.isStatic = isStatic;
		this.classAndClassDetail = classAndClassDetail;
		this.rootTable = rootTable;
		overloadedName = null;
		process();
	}

	/**
	 * @return overloadedName result of method overloading
	 */
	public String toString() {
		return overloadedName;
	}

	/**
	 * Determines arg types, caller name, and overloads method
	 */
	private void process() {

        ArrayList<String> argTypes = determineArgTypes();

        SymbValStruct callerSymbol = (SymbValStruct)rootTable.current().lookup(callerName);
        /* Overload method based on caller's type */
        if(null != callerSymbol && null != callerSymbol.getType()){
            String callerType = callerSymbol.getType();
            ClassDetail callerTypeClassDetail = classAndClassDetail.get(callerType);
            if(null != callerTypeClassDetail){
                overloadedName = overload(methodName, argTypes, callerTypeClassDetail);
            }
        }
        /* Caller name is a class type and is thus static */
        else {
            ClassDetail callerTypeClassDetail = classAndClassDetail.get(callerName);
            if (null != callerTypeClassDetail) {
                overloadedName = overload(methodName, argTypes, callerTypeClassDetail);
            }
        }

    }

    /**
     * Look up args in symbol table and determine types. 
     * If not in symbol table, determine type otherwise.
     * @return argTypes an ArrayList<String> of arg types
     */
    private ArrayList<String> determineArgTypes() {

    	SymbValStruct argSymbol = null;
        Node currentNode = null;
        int loop_size = 0;
        ArrayList<String> argTypes = new ArrayList<String>();

        /* Ignore the __this arg if not static. 
         * If static, then there is no __this arg.
         */
        if (isStatic) {loop_size = methodArgs.size();}        
        else          {loop_size = methodArgs.size() - 1;}

        /*if there are parameters */
        for (int i = 0; i < loop_size; i++) {
            currentNode = methodArgs.getNode(i);
            /* Get type of arg from SymbolTable */
            if (currentNode.getName().equals("PrimaryIdentifier") || currentNode.getName().equals("QualifiedIdentifier")) {
                String search = currentNode.getString(0);
                argSymbol = (SymbValStruct)rootTable.current().lookup(search);
                if (argSymbol != null) {
                    argTypes.add(argSymbol.getType());
                }
            } else if (currentNode.getName().equals("StringLiteral")) {
                argTypes.add("String");  
            } else if (currentNode.getName().equals("IntegerLiteral")) {
                argTypes.add("int");
            } else if (currentNode.getName().equals("FloatingPointLiteral")) {
                argTypes.add("double");
            } else if (currentNode.getName().equals("NewClassExpression")) {
                argTypes.add(currentNode.getNode(2).getString(0));
            } else if (currentNode.getName().equals("CastExpression") ) {
                argTypes.add(currentNode.getNode(0).getNode(0).getString(0));
            } else if (currentNode.getName().equals("AdditiveExpression")) {
                argTypes.add("int");
            }
        }

        return argTypes;
    }

    /**
     * Using given arg types, build an overloaded method name, and search for a match in the
     * caller's class, or in the caller's superclass(es). If no match, 'upcast' arg types one by one
     * while maintaining original types of other args, and pass those new arg types and method name into
     * this recursive function.
     * @param methodName method call to be overloaded
     * @param argTypes types of arguments for method call
     * @param callerTypeClassDetail the ClassDetail for caller's class type 
     * @return overloadedMethod resulting overloaded method call
     */
    private String overload(String methodName, ArrayList<String> argTypes, ClassDetail callerTypeClassDetail) {

    	/* Build the overloaded call */
        String overloadedCall = buildMethod(methodName, argTypes);

		/* Get a list of method names from caller type's ClassDetail */      
        ArrayList<String> callerTypeMethodNames = getMethodNames(callerTypeClassDetail);


        /* Search for the overloaded call in the list of method names */
        if (searchForMethod(overloadedCall, callerTypeMethodNames)) return overloadedCall;

        /* Search for the overloaded call in the superclass's list of method names */
        if (callerTypeClassDetail.getSuperClassDetail() != null &&
             searchForSuperMethod(overloadedCall, getMethodNames(callerTypeClassDetail.getSuperClassDetail()), callerTypeClassDetail.getSuperClassDetail() )
            ) {
            return overloadedCall;
        }

        /* Replace arg types with super types one by one to search within class's methods and super class(es)' methods */
        for (int i = 0; i < argTypes.size(); i++) {
            String original = argTypes.get(i);
            if (argTypes.get(i) != null) {
                argTypes.set(i, getSuperType(argTypes.get(i)));
                String newCall = buildMethod(methodName, argTypes);
                if (searchForMethod(newCall, callerTypeMethodNames)) {
                    return newCall;
                }
                if (callerTypeClassDetail.getSuperClassDetail() != null &&
                    searchForSuperMethod(newCall, getMethodNames(callerTypeClassDetail.getSuperClassDetail()), callerTypeClassDetail.getSuperClassDetail() )
                    ) {
                    return newCall;
                }
                argTypes.set(i, original);
            }
        }

        /* Replace arg types one by one with supertype to send into overload() method, which could eventually 
         * replace those super types with further super types to test out */
        for (int i = 0; i < argTypes.size(); i++) {
            String original = argTypes.get(i);
            if (argTypes.get(i) != null) {
                argTypes.set(i, getSuperType(argTypes.get(i)));
                if (null != overload(methodName, argTypes, callerTypeClassDetail)) {
                    return overload(methodName, argTypes, callerTypeClassDetail);
                }
                argTypes.set(i, original);
            }
        }

        return null;
    }

    /**
     * Get the list of method names from a class's ClassDetail 
     * @param classDetail a class's ClassDetail data structure
     * @return methodNames a list of methodNames from class in question
     */
    private ArrayList<String> getMethodNames(ClassDetail classDetail){
        ArrayList<Node> classMethodList = classDetail.getmethodList();
        ArrayList<String> methodNames = new ArrayList<String>();
        for (int i = 0; i < classMethodList.size(); i++) {
            methodNames.add(classMethodList.get(i).getString(3));
        }
        return methodNames;
    }

    /**
     * Look for a method in a list of method names
     * @param methodName method in question
     * @param methodNames list to search from
     * @return true if found false if not
     */
    private Boolean searchForMethod(String methodName, ArrayList<String> methodNames){
        for (int i = 0; i < methodNames.size(); i++) {
            if (methodName.equals(methodNames.get(i))) return true;
        }
        return false;
    }

    /**
     * Recursive method that searches for a method in a class's super methods
     * @param methodName method in question
     * @param methodNames methods to search from
     * @param classDetail to get super class if need be
     * @return true if found false if otherwise
     */
    private Boolean searchForSuperMethod(String methodName, ArrayList<String> methodNames, ClassDetail classDetail) {
        for (int i = 0; i < methodNames.size(); i++) {
            if (methodName.equals(methodNames.get(i))) return true;
        }

        if (classDetail.getSuperClassDetail() != null && 
            searchForSuperMethod(methodName, getMethodNames(classDetail.getSuperClassDetail()), classDetail.getSuperClassDetail() )
            ) {
            return true;
        }

        return false;
    }

    /**
     * Concatenate method name with given arg types and intermediate underscores
     * as is the properly mangled format of overloaded methods in translator
     * @param methodName method call
     * @param argTypes list of argument types to append
     * @return complete method name
     */
    private String buildMethod(String methodName, ArrayList<String> argTypes) {
        StringBuilder overloadedCall = new StringBuilder(methodName + "_");
        for(Iterator<String> iter = argTypes.iterator(); iter.hasNext(); ){
            String nextStr = (String)iter.next();
            overloadedCall.append(nextStr);
            if(iter.hasNext()) overloadedCall.append("_");
        }

        return overloadedCall.toString();
    }

    /**
     * Get the super type for a corresponding sub type
     * @param typeName the type in question
     * @return superTypeName
     */
    private String getSuperType(String typeName) {
        if      (typeName.equals("byte"))    {return "int";}
        else if (typeName.equals("int"))     {return "double";}
        else if (typeName.equals("String"))  {return "Object";}
        else if (typeName.equals("Object"))  {return null;}
        else if (null != classAndClassDetail.get(typeName) && 
                 null != classAndClassDetail.get(typeName).getSuperClass()
                 ) {
            return classAndClassDetail.get(typeName).getSuperClass();
        }

        return "Object";
    }

}