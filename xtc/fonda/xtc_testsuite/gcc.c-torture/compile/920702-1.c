int somevar;
void
yylex ()
{
  register int result = 0;
  int num_bits = -1;

  if (((result >> -1) & 1))  // {{ dg-warning "negative" }}
    somevar = 99;
}
