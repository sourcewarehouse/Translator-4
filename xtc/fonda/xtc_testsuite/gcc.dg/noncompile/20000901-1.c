/* {{ dg-checkwhat "c-analyzer" }} */
struct foo bar[] = { {"baz"} };  /* {{ dg-error "array type has incomplete element type" }} */
