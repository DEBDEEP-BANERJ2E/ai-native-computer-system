#include "an32cc/parser.hpp"
#include <cstdlib>

namespace an32cc {

Parser::Parser(const std::vector<Token>& toks) : t_(toks) {}

const Token& Parser::expectOp(const char* s) {
  if (!atOp(s)) { error = std::string("expected '")+s+"' got '"+peek().text+"'"; }
  return get();
}
const Token& Parser::expect(TokKind k) {
  if (!at(k)) { error = "unexpected token '"+peek().text+"'"; }
  return get();
}
bool Parser::isTypeStart() const {
  TokKind k=peek().kind;
  return k==TokKind::KW_INT||k==TokKind::KW_VOID||k==TokKind::KW_CHAR;
}

std::shared_ptr<Program> Parser::parse() {
  auto prog = parseProgram();
  return prog;
}

std::shared_ptr<Program> Parser::parseProgram() {
  auto prog = std::make_shared<Program>();
  while (!at(TokKind::END) && error.empty()) {
    auto g = parseExternal();
    if (g) prog->globals.push_back(g);
    else break;
  }
  return prog;
}

TypePtr Parser::parseType() {
  TypePtr base;
  if (eat(TokKind::KW_INT)) base = mkType(TyKind::INT);
  else if (eat(TokKind::KW_VOID)) base = mkType(TyKind::VOID);
  else if (eat(TokKind::KW_CHAR)) base = mkType(TyKind::CHAR);
  else { error="expected type"; return mkType(TyKind::INT); }
  // pointer stars
  while (eatOp("*")) base = mkType(TyKind::PTR, base);
  return base;
}

// declarator: name then suffixes (* already in type; here [N] array or (params) func)
// returns name; outputs adjusted type + optional func params
static bool parseDeclaratorSuffix(Parser& self_ref, TypePtr& ty, std::string& name,
                                  std::vector<Param>& params, bool& isFunc) {
  return false; // handled inline in parseExternal/parseDeclStmt
}

std::shared_ptr<GlobalDecl> Parser::parseExternal() {
  TypePtr ty = parseType();
  if (error.size()) return nullptr;
  // expect identifier
  if (!at(TokKind::IDENT)) { error="expected identifier"; return nullptr; }
  std::string name = get().text;

  if (atOp("(")) {
    // function definition or prototype
    auto fn = std::make_shared<FuncDecl>();
    fn->retType = ty; fn->name = name;
    get(); // '('
    if (!atOp(")")) {
      while (true) {
        TypePtr pt = parseType();
        if (!at(TokKind::IDENT)) { /* allow unnamed params */ }
        std::string pn = at(TokKind::IDENT) ? get().text : "";
        Param pr; pr.type=pt; pr.name=pn; fn->params.push_back(pr);
        if (!eatOp(",")) break;
      }
    }
    expectOp(")");
    if (eatOp(";")) { fn->isProto=true; fn->body=nullptr; }
    else fn->body = parseBlock();
    auto g = std::make_shared<GlobalDecl>();
    g->isFunc=true; g->fn=fn; g->name=name; g->type=ty;
    return g;
  } else {
    // global variable (possibly array)
    TypePtr gty = ty;
    if (atOp("[")) {
      get(); int len = at(TokKind::INT_CONST)? (int)get().ival : 0;
      expectOp("]");
      auto at2 = mkType(TyKind::ARRAY, gty); at2->arrayLen=len; gty=at2;
    }
    ExprPtr init=nullptr;
    if (eatOp("=")) init = parseAssign();
    expectOp(";");
    auto g = std::make_shared<GlobalDecl>();
    g->isFunc=false; g->type=gty; g->name=name; g->init=init;
    return g;
  }
}

StmtPtr Parser::parseBlock() {
  expectOp("{");
  auto b = std::make_shared<Stmt>(StmtKind::BLOCK);
  while (!atOp("}") && !at(TokKind::END) && error.empty())
    b->body.push_back(parseStmt());
  expectOp("}");
  return b;
}

StmtPtr Parser::parseStmt() {
  if (atOp("{")) return parseBlock();
  if (isTypeStart()) return parseDeclStmt();
  if (at(TokKind::KW_IF)) {
    get(); auto s=std::make_shared<Stmt>(StmtKind::IF);
    expectOp("("); s->cond=parseExpr(); expectOp(")");
    s->thenS=parseStmt();
    if(eat(TokKind::KW_ELSE)) s->elseS=parseStmt();
    return s;
  }
  if (at(TokKind::KW_WHILE)) {
    get(); auto s=std::make_shared<Stmt>(StmtKind::WHILE);
    expectOp("("); s->whileCond=parseExpr(); expectOp(")");
    s->body.push_back(parseStmt());
    return s;
  }
  if (at(TokKind::KW_FOR)) {
    get(); auto s=std::make_shared<Stmt>(StmtKind::FOR);
    expectOp("(");
    if (isTypeStart()) s->forInit=parseDeclStmt();
    else if (!atOp(";")) { auto e=std::make_shared<Stmt>(StmtKind::EXPR); e->expr=parseExpr(); s->forInit=e; }
    expectOp(";");
    if (!atOp(";")) s->forCond=parseExpr();
    expectOp(";");
    if (!atOp(")")) s->forPost=parseExpr();
    expectOp(")");
    s->body.push_back(parseStmt());
    return s;
  }
  if (at(TokKind::KW_RETURN)) {
    get(); auto s=std::make_shared<Stmt>(StmtKind::RETURN);
    if (!atOp(";")) s->expr=parseExpr();
    expectOp(";"); return s;
  }
  if (at(TokKind::KW_BREAK)) { get(); expectOp(";"); return std::make_shared<Stmt>(StmtKind::BREAK); }
  if (at(TokKind::KW_CONTINUE)) { get(); expectOp(";"); return std::make_shared<Stmt>(StmtKind::CONTINUE); }
  if (atOp(";")) { get(); return std::make_shared<Stmt>(StmtKind::EMPTY); }
  // expression statement
  auto s=std::make_shared<Stmt>(StmtKind::EXPR); s->expr=parseExpr(); expectOp(";"); return s;
}

StmtPtr Parser::parseDeclStmt() {
  TypePtr ty = parseType();
  // one or more declarators separated by ','
  auto first = std::make_shared<Stmt>(StmtKind::BLOCK); // wrap as block of decls? simpler: return first decl, chain via comma not supported -> handle single + loop not trivial
  // For simplicity: parse single declarator; multiple declarators -> parse as separate decl stmts is complex.
  // We'll parse one declarator per statement (C allows lists; we handle by recursion into a block is hard).
  // Instead: build a DECL stmt for the first, and if ',' follows, we still only support single -> error for now.
  if (!at(TokKind::IDENT)) { error="expected declarator name"; return first; }
  std::string name=get().text;
  TypePtr dty=ty;
  if (atOp("[")) { get(); int len=at(TokKind::INT_CONST)?(int)get().ival:0; expectOp("]"); auto a=mkType(TyKind::ARRAY,dty);a->arrayLen=len;dty=a; }
  auto s=std::make_shared<Stmt>(StmtKind::DECL);
  s->declType=dty; s->declName=name;
  if (eatOp("=")) s->declInit=parseAssign();
  expectOp(";");
  return s;
}

ExprPtr Parser::parseExpr() {
  // comma operator sits at the top (lowest precedence); call args use
  // parseAssign directly so arg-separating commas are not consumed here.
  auto l = parseAssign();
  while (eatOp(",")) { auto r=parseAssign(); auto e=std::make_shared<Expr>(ExprKind::COMMA); e->kids={l,r}; l=e; }
  return l;
}

ExprPtr Parser::parseAssign() {
  auto l = parseCond();
  if (atOp("=")||atOp("+=")||atOp("-=")||atOp("*=")||atOp("/=")||atOp("%=")||
      atOp("&=")||atOp("|=")||atOp("^=")||atOp("<<=")||atOp(">>=")) {
    std::string op=get().text;
    auto r=parseAssign();
    auto e=std::make_shared<Expr>(ExprKind::ASSIGN); e->op=op; e->kids={l,r}; return e;
  }
  return l;
}

ExprPtr Parser::parseCond() {
  auto c=parseBinary(1); // logical-or handled in binPrec
  if (eatOp("?")) {
    auto a=parseExpr(); expectOp(":"); auto b=parseCond();
    auto e=std::make_shared<Expr>(ExprKind::TERNARY); e->kids={c,a,b}; return e;
  }
  return c;
}

int Parser::binPrec(const std::string& op) const {
  if(op=="||")return 1; if(op=="&&")return 2;
  if(op=="|")return 3; if(op=="^")return 4; if(op=="&")return 5;
  if(op=="=="||op=="!=")return 6;
  if(op=="<"||op=="<="||op==">"||op==">=")return 7;
  if(op=="<<"||op==">>")return 8;
  if(op=="+"||op=="-")return 9;
  if(op=="*"||op=="/"||op=="%")return 10;
  return -1;
}

ExprPtr Parser::parseBinary(int minPrec) {
  auto l=parseUnary();
  while (true) {
    if(!at(TokKind::OP))break;
    int pr=binPrec(peek().text);
    if(pr<minPrec||pr<0)break;
    std::string op=get().text;
    auto r=parseBinary(pr+1);
    auto e=std::make_shared<Expr>(ExprKind::BINARY); e->op=op; e->kids={l,r}; l=e;
  }
  return l;
}

ExprPtr Parser::parseUnary() {
  if (atOp("+")||atOp("-")||atOp("!")||atOp("~")||atOp("&")||atOp("*")||atOp("++")||atOp("--")) {
    std::string op=get().text;
    auto e=parseUnary();
    ExprKind k;
    if(op=="&")k=ExprKind::ADDR_OF; else if(op=="*")k=ExprKind::DEREF;
    else if(op=="++")k=ExprKind::PRE_INC; else if(op=="--")k=ExprKind::PRE_DEC;
    else k=ExprKind::UNARY;
    auto n=std::make_shared<Expr>(k); n->op=op; n->kids={e}; return n;
  }
  if (at(TokKind::KW_SIZEOF)) {
    get(); expectOp("(");
    // sizeof(type) or sizeof(expr) -> INT_LIT (approx: int=4)
    if(isTypeStart()){ auto t=parseType(); /* skip declarator */ if(at(TokKind::IDENT))get(); }
    else parseUnary();
    expectOp(")");
    auto e=std::make_shared<Expr>(ExprKind::INT_LIT); e->ival=4; return e;
  }
  return parsePostfix();
}

ExprPtr Parser::parsePostfix() {
  auto e=parsePrimary();
  while (true) {
    if (eatOp("(")) {
      auto c=std::make_shared<Expr>(ExprKind::CALL); c->name=e->name; // callee name (simple)
      if(!atOp(")")){ while(true){ c->kids.push_back(parseAssign()); if(!eatOp(","))break; } }
      expectOp(")"); e=c; continue;
    }
    if (eatOp("[")) { auto i=parseExpr(); expectOp("]"); auto n=std::make_shared<Expr>(ExprKind::INDEX); n->kids={e,i}; e=n; continue; }
    if (eatOp(".")) { /* struct member unsupported */ }
    if (eatOp("++")) { auto n=std::make_shared<Expr>(ExprKind::POST_INC); n->kids={e}; e=n; continue; }
    if (eatOp("--")) { auto n=std::make_shared<Expr>(ExprKind::POST_DEC); n->kids={e}; e=n; continue; }
    if (eatOp("->")) { /* unsupported */ }
    break;
  }
  return e;
}

ExprPtr Parser::parsePrimary() {
  const Token& tk=peek();
  if (tk.kind==TokKind::INT_CONST) { get(); auto e=std::make_shared<Expr>(ExprKind::INT_LIT); e->ival=tk.ival; e->line=tk.line; return e; }
  if (tk.kind==TokKind::CHAR_CONST) { get(); auto e=std::make_shared<Expr>(ExprKind::CHAR_LIT); e->ival=tk.ival; e->line=tk.line; return e; }
  if (tk.kind==TokKind::STRING) { get(); auto e=std::make_shared<Expr>(ExprKind::STR_LIT); e->name=tk.text; e->line=tk.line; return e; }
  if (tk.kind==TokKind::IDENT) { get(); auto e=std::make_shared<Expr>(ExprKind::VAR); e->name=tk.text; e->line=tk.line; return e; }
  if (eatOp("(")) { auto e=parseExpr(); expectOp(")"); return e; }
  error="unexpected token in expression '"+tk.text+"'";
  return std::make_shared<Expr>(ExprKind::INT_LIT);
}

} // namespace an32cc
