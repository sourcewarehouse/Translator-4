class A {
  private String fld;

  public A(String fld) {
    this.fld = fld;
  }

  public String getFld() {
    A b = new A("B");
    return fld;
  }
}

public class Test004 { 
  public static void main(String[] args) {
    A a = new A("A");
    System.out.println(a.getFld());
  }
}