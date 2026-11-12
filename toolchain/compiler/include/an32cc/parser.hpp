#pragma once

#include "an32cc/lexer.hpp"
#include "an32cc/ast.hpp"
#include <string>

namespace an32cc {

/** Recursive-descent parser for the AN32 C-subset (O5-A frontend).
  *
  *   program -> external-decl*
  *   external-decl -> func-def | global-decl
  *   statement -> block | decl | if | while | for | return | break |
  *                continue | expr-stmt
  *   expression -> assignment -> conditional -> logical -> equality ->
  *                 relational -> additive -> multiplicative -> unary ->
  *                 postfix -> primary
  */
class Parser {
public:
  Parser(const std::vector<Token>& toks);
  std::shared_ptr<Program> parse();
  std::string error;

private:
  const std::vector<Token>& t_;
  size_t p_ = 0;
  const Token& peek(size_t o=0) const { return t_[std::min(p_+o, t_.size()-1)]; }
  const Token& get() { return t_[p_++]; }
  bool at(TokKind k) const { return peek().kind==k; }
  bool atOp(const char* s) const { return peek().kind==TokKind::OP && peek().text==s; }
  bool eatOp(const char* s) { if(atOp(s)){get();return true;} return false; }
  bool eat(TokKind k) { if(at(k)){get();return true;} return false; }
  const Token& expectOp(const char* s);
  const Token& expect(TokKind k);
  bool isTypeStart() const;

  // grammar
  std::shared_ptr<Program> parseProgram();
  std::shared_ptr<GlobalDecl> parseExternal();
  TypePtr parseType();
  std::shared_ptr<FuncDecl> parseFunc(TypePtr ret, const std::string& name);
  StmtPtr parseBlock();
  StmtPtr parseStmt();
  StmtPtr parseDeclStmt();
  ExprPtr parseExpr();          // assignment
  ExprPtr parseAssign();
  ExprPtr parseCond();
  ExprPtr parseBinary(int minPrec);
  ExprPtr parseUnary();
  ExprPtr parsePostfix();
  ExprPtr parsePrimary();
  int binPrec(const std::string& op) const;
};

} // namespace an32cc
