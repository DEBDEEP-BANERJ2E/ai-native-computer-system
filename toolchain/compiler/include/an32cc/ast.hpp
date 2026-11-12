#pragma once

#include <string>
#include <vector>
#include <memory>
#include <cstdint>

namespace an32cc {

/** AST for the AN32 C-subset (O5-A frontend).
  *
  * Tagged-node AST. Expr nodes carry kind + children/operands; Stmt nodes
  * likewise. Types are a small set: void/int/char/pointer-to-T (encoded as
  * a Type struct). Strings become rodata objects via StringLit.
  */

enum class TyKind { VOID, INT, CHAR, PTR, ARRAY, FUNC };
struct Type {
  TyKind k = TyKind::INT;
  std::shared_ptr<Type> base;   // for PTR/ARRAY element type
  int arrayLen = 0;             // for ARRAY
  bool operator==(const Type& o) const {
    if (k != o.k) return false;
    if ((k==TyKind::PTR||k==TyKind::ARRAY) && base && o.base) return *base==*o.base;
    return true;
  }
};
using TypePtr = std::shared_ptr<Type>;
inline TypePtr mkType(TyKind k, TypePtr b=nullptr){ auto t=std::make_shared<Type>(); t->k=k; t->base=b; return t; }

// ---- expressions ----
enum class ExprKind {
  INT_LIT, CHAR_LIT, STR_LIT, VAR, UNARY, BINARY, ASSIGN, CALL,
  ADDR_OF, DEREF, INDEX, POST_INC, POST_DEC, PRE_INC, PRE_DEC,
  CAST, COMMA, TERNARY
};
struct Expr {
  ExprKind kind;
  int64_t ival = 0;                 // INT_LIT/CHAR_LIT value
  std::string name;                 // VAR/CALL/STR_LIT label
  std::string op;                   // UNARY/BINARY/ASSIGN op text
  std::vector<std::shared_ptr<Expr>> kids;
  TypePtr type;                     // filled by sema/codegen
  int line = 0;
  Expr(ExprKind k):kind(k){}
};
using ExprPtr = std::shared_ptr<Expr>;

// ---- statements ----
enum class StmtKind { BLOCK, DECL, EXPR, IF, WHILE, FOR, RETURN, BREAK, CONTINUE, EMPTY };
struct Stmt {
  StmtKind kind;
  // DECL
  TypePtr declType; std::string declName; ExprPtr declInit;
  // EXPR / RETURN
  ExprPtr expr;
  // IF
  ExprPtr cond; std::shared_ptr<Stmt> thenS, elseS;
  // WHILE
  ExprPtr whileCond;
  // FOR: init is a Stmt (decl or expr), cond Expr, post Expr
  std::shared_ptr<Stmt> forInit; ExprPtr forCond, forPost;
  std::vector<std::shared_ptr<Stmt>> body;   // BLOCK
  int line = 0;
  Stmt(StmtKind k):kind(k){}
};
using StmtPtr = std::shared_ptr<Stmt>;

// ---- declarations ----
struct Param { TypePtr type; std::string name; };
struct FuncDecl {
  TypePtr retType; std::string name;
  std::vector<Param> params;
  StmtPtr body;                     // BLOCK
  bool isProto = false;
};
struct GlobalDecl {
  TypePtr type; std::string name;
  ExprPtr init;                     // optional constant init
  bool isFunc = false;
  std::shared_ptr<FuncDecl> fn;
};
struct Program {
  std::vector<std::shared_ptr<GlobalDecl>> globals;
};

} // namespace an32cc
