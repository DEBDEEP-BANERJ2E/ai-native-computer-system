#include "an32cc/lexer.hpp"
#include "an32cc/parser.hpp"
#include "an32cc/codegen.hpp"
#include <fstream>
#include <sstream>
#include <iostream>
#include <string>

void usage(const char* p){
  std::cout<<"Usage: "<<p<<" <input.c> [-o output.s] [-S]\n"
           <<"  Compiles AN32 C-subset to AN32 assembly.\n"
           <<"  -o <file>  output assembly (default: stdout)\n"
           <<"  -S        stop after assembly generation (default)\n";
}

int main(int argc,char**argv){
  std::string in,out; bool toFile=false;
  for(int i=1;i<argc;i++){
    std::string a=argv[i];
    if(a=="-o"){ if(i+1<argc){out=argv[++i];toFile=true;} }
    else if(a=="-h"||a=="--help"){usage(argv[0]);return 0;}
    else if(a[0]!='-') in=a;
  }
  if(in.empty()){usage(argv[0]);return 1;}
  std::ifstream f(in); if(!f){std::cerr<<"an32cc: cannot open "<<in<<"\n";return 1;}
  std::stringstream ss; ss<<f.rdbuf(); std::string src=ss.str();

  an32cc::Lexer lx(src);
  auto toks=lx.tokenize();
  if(!lx.error.empty()){std::cerr<<"an32cc: lex error: "<<lx.error<<"\n";return 1;}

  an32cc::Parser ps(toks);
  auto prog=ps.parse();
  if(!ps.error.empty()){std::cerr<<"an32cc: parse error: "<<ps.error<<"\n";return 1;}

  an32cc::Codegen cg;
  std::string err;
  std::string asmText=cg.generate(*prog,err);
  if(!err.empty()){std::cerr<<"an32cc: codegen error: "<<err<<"\n";return 1;}

  if(toFile){ std::ofstream o(out); o<<asmText; }
  else std::cout<<asmText;
  return 0;
}
