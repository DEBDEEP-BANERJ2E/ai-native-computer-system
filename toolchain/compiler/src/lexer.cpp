#include "an32cc/lexer.hpp"
#include <cctype>
#include <cstdlib>

namespace an32cc {

Lexer::Lexer(const std::string& src) : src_(src) {}

char Lexer::peek(size_t off) const {
  return (pos_ + off < src_.size()) ? src_[pos_ + off] : '\0';
}
char Lexer::get() {
  char c = peek();
  if (c) { pos_++; if (c=='\n'){line_++;col_=1;} else col_++; }
  return c;
}
bool Lexer::match(char c) { if (peek()==c){get();return true;} return false; }
bool Lexer::match2(const char* s) {
  size_t n = std::char_traits<char>::length(s);
  if (pos_+n<=src_.size() && src_.compare(pos_,n,s)==0) {
    for(size_t i=0;i<n;i++) get(); return true;
  }
  return false;
}
Token Lexer::make(TokKind k, const std::string& t) {
  Token tk; tk.kind=k; tk.text=t; tk.line=line_; tk.col=col_;
  if(k==TokKind::INT_CONST||k==TokKind::CHAR_CONST) tk.ival=std::strtoll(t.c_str(),nullptr,0);
  return tk;
}

static const char* KEYWORDS[] = {
  "int","void","char","if","else","while","for","return","break","continue","sizeof"
};
static TokKind KW[] = {
  TokKind::KW_INT,TokKind::KW_VOID,TokKind::KW_CHAR,TokKind::KW_IF,TokKind::KW_ELSE,
  TokKind::KW_WHILE,TokKind::KW_FOR,TokKind::KW_RETURN,TokKind::KW_BREAK,
  TokKind::KW_CONTINUE,TokKind::KW_SIZEOF
};

std::vector<Token> Lexer::tokenize() {
  std::vector<Token> out;
  while (true) {
    // skip whitespace + comments
    while (true) {
      char c = peek();
      if (c==' '||c=='\t'||c=='\r'||c=='\n'||c=='\v'||c=='\f') { get(); continue; }
      if (c=='/' && peek(1)=='/') { while(peek()&&peek()!='\n') get(); continue; }
      if (c=='/' && peek(1)=='*') { get();get(); while(peek()&&!(peek()=='*'&&peek(1)=='/')) get(); if(peek()){get();get();} continue; }
      break;
    }
    char c = peek();
    if (!c) { out.push_back(make(TokKind::END,"")); break; }

    // identifiers / keywords
    if (std::isalpha((unsigned char)c) || c=='_') {
      std::string t; while(std::isalnum((unsigned char)peek())||peek()=='_') t+=get();
      TokKind k=TokKind::IDENT;
      for(size_t i=0;i<sizeof(KEYWORDS)/sizeof(KEYWORDS[0]);i++)
        if(t==KEYWORDS[i]){k=KW[i];break;}
      out.push_back(make(k,t)); continue;
    }
    // integer constants (dec/hex/oct) + suffixes
    if (std::isdigit((unsigned char)c)) {
      std::string t;
      if (c=='0'&&(peek(1)=='x'||peek(1)=='X')) { t+=get();t+=get(); while(std::isxdigit((unsigned char)peek()))t+=get(); }
      else { while(std::isdigit((unsigned char)peek()))t+=get(); }
      while(std::isalpha((unsigned char)peek()))t+=get(); // u/l suffix
      out.push_back(make(TokKind::INT_CONST,t)); continue;
    }
    // char literal
    if (c=='\'') {
      get(); std::string t="'";
      if(peek()=='\\'){t+=get();t+=get();} else t+=get();
      if(peek()=='\'')t+=get();
      out.push_back(make(TokKind::CHAR_CONST,t)); continue;
    }
    // string literal
    if (c=='\"') {
      get(); std::string t="\"";
      while(peek()&&peek()!='\"'){ if(peek()=='\\'){t+=get();t+=get();} else t+=get(); }
      if(peek()=='\"')t+=get();
      out.push_back(make(TokKind::STRING,t)); continue;
    }
    // multi-char operators
    static const char* ops3[]={">>=","<<=","..."};
    static const char* ops2[]={"==","!=","<=",">=","&&","||","++","--","+=","-=","*=","/=","%=","<<",">>","&=","|=","^=","->"};
    bool done=false;
    for(auto o:ops2) if(match2(o)){out.push_back(make(TokKind::OP,std::string(o)));done=true;break;}
    if(done)continue;
    for(auto o:ops3) if(match2(o)){out.push_back(make(TokKind::OP,std::string(o)));done=true;break;}
    if(done)continue;
    // single-char ops / punct
    std::string one(1,get());
    if(std::string("+-*/%=<>!&|^~?:;,.()[]{}").find(one[0])!=std::string::npos)
      out.push_back(make(TokKind::OP,one));
    else { error="unexpected char '"+one+"'"; out.push_back(make(TokKind::END,"")); break; }
  }
  return out;
}

} // namespace an32cc
