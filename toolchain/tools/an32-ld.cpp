#include "an32asm/objectfile.hpp"
#include "an32asm/linker.hpp"
#include "an32exe/exeformat.hpp"
#include <fstream>
#include <iostream>
#include <vector>
#include <string>

void usage(const char* p){
  std::cout<<"Usage: "<<p<<" [options] <input.o>... -o <output.exe>\n"
           <<"  Links AN32OBJ objects into an AN32EXE executable.\n"
           <<"  --text-base <addr>   VA for executable sections (default 0x10000)\n"
           <<"  --data-base <addr>   VA for writable/data sections (default 0x20000)\n"
           <<"  --entry <sym>        entry symbol (default _start)\n";
}

static uint32_t parseAddr(const std::string& s){
  return (uint32_t)std::stoul(s,nullptr,0);
}

int main(int argc,char**argv){
  std::vector<std::string> objs; std::string out;
  an32asm::Linker L;
  for(int i=1;i<argc;i++){
    std::string a=argv[i];
    if(a=="-o"){ if(i+1<argc) out=argv[++i]; }
    else if(a=="--text-base"){ if(i+1<argc)L.textBase=parseAddr(argv[++i]); }
    else if(a=="--data-base"){ if(i+1<argc)L.dataBase=parseAddr(argv[++i]); }
    else if(a=="--rodata-base"){ if(i+1<argc)L.rodataBase=parseAddr(argv[++i]); }
    else if(a=="--entry"){ if(i+1<argc)L.entrySymbol=argv[++i]; }
    else if(a=="-h"||a=="--help"){usage(argv[0]);return 0;}
    else objs.push_back(a);
  }
  if(objs.empty()||out.empty()){usage(argv[0]);return 1;}
  for(auto&o:objs){
    an32asm::AssemblerObject obj;
    if(!an32asm::an32obj::read_object(o,obj)){std::cerr<<"an32-ld: cannot read "<<o<<"\n";return 1;}
    L.add_object(std::move(obj));
  }
  std::string err;
  auto img=L.link(err);
  if(!img){std::cerr<<"an32-ld: "<<err<<"\n";return 1;}
  if(!an32exe::write_exe(*img,out)){std::cerr<<"an32-ld: cannot write "<<out<<"\n";return 1;}
  std::cout<<"an32-ld: wrote "<<out<<" (entry=0x"<<std::hex<<img->entry<<std::dec<<")\n";
  return 0;
}
