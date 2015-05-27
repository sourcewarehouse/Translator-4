class List{
  int item;
  List next;
  List(int item, List next){
    this.item = item;
    this.next = next;
  }
}

class Main{
  static int twice(int k){
    return 2*k;
  }
  public static void main(String[] args){
    /* -------------------------------------------------------------------- */
    System.out.println("  Some Types");
    {
      String s;
      char c;
      boolean b;
      int i;
      float f;
      int[] a;
      List l;
      s = "hello";
      System.out.println("    s is \"" + s + "\"");
      c = 'g';
      System.out.println("    c is '" + c + "'");
      b = true;
      System.out.println("    b is " + b);
      i = 2 * 7;
      System.out.println("    i is " + i);
      f = 3.141f;
      System.out.println("    f is " + f);
      a = new int[3];
      a[0] = 2;
      a[1] = 9;
      a[2] = 0;
      System.out.println("    a is {" + a[0] + ", " + a[1]
                         + ", " + a[2] + "}");
      l = new List(4, null);
      System.out.println("    l is { item=" + l.item + ", next=null }");
    }
    /* -------------------------------------------------------------------- */
    System.out.println("  Some Statements");
    {
      int i,j;
      i = 4;
      i = twice(i);
      System.out.println("    i is " + i);
      j = 1;
      while(j<1000)
        j = j + j;
      System.out.println("    j is " + j);
      if(j < 300000)
        System.out.println("    j < 300,000");
      else
        System.out.println("    j >= 300,000");
      System.out.print("    countdown");
      for(i=10; i>=1; i--)
        System.out.print(" " + i);
      System.out.println();
    }
    /* -------------------------------------------------------------------- */
  }
}
