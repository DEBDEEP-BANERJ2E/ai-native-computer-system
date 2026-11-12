#pragma once

#include "an32cc/ast.hpp"
#include <string>
#include <vector>
#include <unordered_map>

namespace an32cc {

/** Code generator: AST -> AN32 (RV32IM) assembly text (O5-D backend, v1).
  *
  * Simple, correct-by-construction scheme:
  *   - genExpr leaves its value in register t0
  *   - binary/compound ops spill the left operand to the machine stack
  *   - locals live at fp-relative slots (s0 = frame pointer)
  *   - globals -> .data/.bss/.rodata labels
  *   - calls: args -> a0..a7, result a0 -> t0
  *   - builtins: write()->SYS_WRITE(15), exit()->SYS_EXIT(4)
  *
  * Emits GNU-ish AN32 assembly the existing an32-as consumes.
  */
class Codegen {
public:
  // Generate full assembly for a parsed Program. Returns asm text.
  std::string generate(const Program& prog, std::string& err);

private:
  std::string out_;
  int labelCtr_ = 0;
  // per-function state
  struct FuncCtx {
    std::string name;
    int frameSize = 0;
    int nextLocal = 0;                 // byte offset for locals (fp - off)
    bool frameSized = false;
    std::unordered_map<std::string,int> locals;  // name -> fp offset
    std::vector<std::string> paramNames;
    std::unordered_map<std::string,TypePtr> localTypes;
    std::vector<std::string> breakLabels, contLabels;
    int strCtr = 0;
    std::vector<std::pair<std::string,std::string>> rodata; // label->escaped bytes
    std::unordered_map<std::string,int> globals;           // global name -> index
  };
  FuncCtx* fn_ = nullptr;
  // global symbol table
  std::unordered_map<std::string,GlobalDecl*> globals_;
  std::unordered_map<std::string,std::string> strLabels_;  // string literal -> label
  int globStrCtr_ = 0;

  void emit(const std::string& s);
  std::string freshLabel(const char* p);
  // expression -> t0
  void genExpr(const ExprPtr& e);
  void genExprNoVal(const ExprPtr& e) { genExpr(e); emit("  addi sp, sp, 4\n  lw t0, 0(sp)\n"); } // placeholder unused
  // lvalue address -> dst reg (t1)
  void genAddr(const ExprPtr& e);
  int  localOff(const std::string& name);          // returns fp offset (positive byte count)
  bool isLocal(const std::string& name);
  TypePtr typeOf(const ExprPtr& e);
  int  typeSize(const TypePtr& t);
  // statements
  void genStmt(const StmtPtr& s);
  void genBlock(const StmtPtr& s);
  void genFunc(const std::shared_ptr<FuncDecl>& f);
  void genGlobal(const std::shared_ptr<GlobalDecl>& g);
  // expression helpers
  void genBinary(const ExprPtr& e);
  void genAssign(const ExprPtr& e);
  void genCall(const ExprPtr& e);
  void genUnary(const ExprPtr& e);
  void genPostfix(const ExprPtr& e);
};

} // namespace an32cc
