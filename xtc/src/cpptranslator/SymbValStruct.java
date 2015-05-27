/******************************************************************
 * A class representing the value object in the symbol table, 
 * corresponds with a name within it's scope
 *
 * Contains 3 String fields: kind, type, and other
 * Kind is "fun" (function), "par" (parameter), "var" (variable), "sup" (superclass)
 * Type is the type of the variable (ex. int) or the input to ouput for the function (int -> bool)
 * Other contains other modifiers such as extern, const, auto
 *****************************************************************/

package cpptranslator;

public class SymbValStruct {

	private String kind;		/* Can be one of: "fun", "par", "var", "sup"         */
	private String type;		/* Type of variable or input/ouput types of function */
	private String other;		/* Any other modifier fields                         */

	/* Default constructor */
	public SymbValStruct() {
		kind  = null;
		type  = null;
		other = null;
	}

	/* Constructor with 3 parameters */
	public SymbValStruct(String kind, String type, String other) {
		this.kind  = kind;
		this.type  = type;
		this.other = other;
	}
	
	/* Getters */	
	public String getKind()  { return kind; }
	public String getType()  { return type; }
	public String getOther() { return other; }

	/* Setters */
	public void setKind(String k)  { kind = k; }
	public void setType(String t)  { type = t; }
	public void setOther(String o) { other = o; }

}