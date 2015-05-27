/********************************************************
 * ClassDetail records information about a Java class.
 *
 * It is the system we use to manage Java's
 * inheritance in C++.
 ********************************************************/

package cpptranslator;

import java.util.*;
import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Printer;
import xtc.tree.Visitor;
import xtc.util.SymbolTable;

public class ClassDetail {

    private String          classname;                  /* Name of this class       */
    private Node            classNode;                  /* the ClassDeclaration node for this class */               
    private ArrayList<Node> methodList;                 /* a list containing MethodDeclaration nodes found in the class */
    private ArrayList<Node> publicScopes;               /* public MethodDeclaration and FieldDeclaration nodes */
    private ArrayList<Node> privateScopes;              /* private MethodDeclaration and FieldDeclaration nodes */
    private ArrayList<Node> allScopes;                  /* all fields in the class */
    private String          superClass;                 /* Name of super class      */
    private ClassDetail     superClassDetail;           /* Super class' ClassDetail */

    private Hashtable<String, LinkedList> fieldTypes;  

    public ClassDetail(String classname) {
        this.classname      = classname;
        this.methodList     = new ArrayList<Node>();
        this.publicScopes   = new ArrayList<Node>();
        this.privateScopes  = new ArrayList<Node>();
        this.allScopes      = new ArrayList<Node>();
        this.fieldTypes     = new Hashtable<String, LinkedList>();
    }

    /* Getters */
    public String          getclassname()                {return this.classname;}
    public ArrayList<Node> getmethodList()               {return this.methodList;}
    public ArrayList<Node> getpublicScopes()             {return this.publicScopes;}
    public ArrayList<Node> getprivateScopes()            {return this.privateScopes;}
    public ArrayList<Node> getallScopes()                {return this.allScopes;}
    public Node            getclassNode()                {return this.classNode;}
    public String          getSuperClass()               {return this.superClass;}
    public ClassDetail     getSuperClassDetail()         {return this.superClassDetail;}
    public Hashtable<String, LinkedList> getFieldTypes() {return this.fieldTypes;}

    /* Setters */
    public void    setclassNode(Node classNode)          {this.classNode = classNode;}
    public void    setSuperClass(String superClass)      {this.superClass = superClass;}
    public void    setSuperClassDetail(ClassDetail superClassDetail) {this.superClassDetail = superClassDetail;}
}