#include "an32cc/codegen.hpp"
#include <sstream>
#include <cstring>

namespace an32cc {

void Codegen::emit(const std::string& s) { out_ += s; }
std::string Codegen::freshLabel(const char* p) {
  return std::string(".") + p + std::to_string(labelCtr_++);
}

// syscall numbers (O3 ABI)
enum { SC_EXIT=4, SC_BRK=5, SC_SBRK=6, SC_YIELD=9, SC_OPEN=13, SC_READ=14, SC_WRITE=15, SC_CLOSE=16, SC_GETPID=17 };

// decode a C string-literal token (raw "..." text) to bytes + NUL terminator
static std::vector<int> decodeString(const std::string& raw) {
  std::vector<int> out;
  // strip surrounding quotes
  size_t i = (raw.size()>=2 && raw.front()=='\"') ? 1 : 0;
  size_t end = (raw.size()>=2 && raw.back()=='\"') ? raw.size()-1 : raw.size();
  for (; i<end; ++i) {
    char c = raw[i];
    if (c=='\\' && i+1<end) {
      char n = raw[++i];
      switch(n){
        case 'n': out.push_back('\n'); break;
        case 't': out.push_back('\t'); break;
        case 'r': out.push_back('\r'); break;
        case '0': out.push_back(0); break;
        case '\\': out.push_back('\\'); break;
        case '\"': out.push_back('\"'); break;
        case '\'': out.push_back('\''); break;
        case 'x': {
          int v=0; int d=0;
          while(i+1<end && d<2){ char h=raw[i+1]; int hv = (h>='0'&&h<='9')?h-'0':(h>='a'&&h<='f')?h-'a'+10:(h>='A'&&h<='F')?h-'A'+10:-1; if(hv<0)break; v=v*16+hv; i++; d++; }
          out.push_back(v&0xff); break;
        }
        default: out.push_back(n); break;
      }
    } else out.push_back((unsigned char)c);
  }
  out.push_back(0); // NUL terminator
  return out;
}

int Codegen::localOff(const std::string& n) { auto it=fn_->locals.find(n); return it==fn_->locals.end()?-1:it->second; }
bool Codegen::isLocal(const std::string& n) { return fn_ && fn_->locals.count(n); }

TypePtr Codegen::typeOf(const ExprPtr& e) {
  if (!e) return mkType(TyKind::INT);
  switch(e->kind){
    case ExprKind::STR_LIT: return mkType(TyKind::PTR, mkType(TyKind::CHAR));
    case ExprKind::VAR: {
      if (isLocal(e->name) && fn_->localTypes.count(e->name)) return fn_->localTypes[e->name];
      if (globals_.count(e->name)) return globals_[e->name]->type;
      return mkType(TyKind::INT);
    }
    case ExprKind::INDEX: case ExprKind::DEREF: {
      auto base = (e->kind==ExprKind::INDEX)? typeOf(e->kids[0]) : typeOf(e->kids[0]);
      if (base && base->k==TyKind::PTR && base->base) return base->base;
      if (base && base->k==TyKind::ARRAY) return base->base;
      return mkType(TyKind::INT);
    }
    case ExprKind::ADDR_OF: return mkType(TyKind::PTR, typeOf(e->kids[0]));
    default: return mkType(TyKind::INT);
  }
}
int Codegen::typeSize(const TypePtr& t) {
  if (!t) return 4;
  switch(t->k){
    case TyKind::VOID: return 0; case TyKind::CHAR: return 1;
    case TyKind::INT: case TyKind::PTR: case TyKind::FUNC: return 4;
    case TyKind::ARRAY: return typeSize(t->base)*std::max(1,t->arrayLen);
  }
  return 4;
}

// ---- expression generation (result in t0) ----
void Codegen::genExpr(const ExprPtr& e) {
  if (!e) { emit("  li t0, 0\n"); return; }
  switch(e->kind){
    case ExprKind::INT_LIT: case ExprKind::CHAR_LIT:
      emit("  li t0, " + std::to_string(e->ival) + "\n"); break;
    case ExprKind::STR_LIT: {
      auto it = strLabels_.find(e->name);
      std::string lab;
      if (it==strLabels_.end()) {
        lab = ".Lstr" + std::to_string(globStrCtr_++);
        strLabels_[e->name]=lab;
        fn_->rodata.push_back({lab, e->name});
      } else lab=it->second;
      emit("  la t0, " + lab + "\n"); break;
    }
    case ExprKind::VAR: {
      if (isLocal(e->name)) {
        int off=localOff(e->name); int sz=typeSize(typeOf(e));
        if (sz==1) emit("  lbu t0, -"+std::to_string(off)+"(s0)\n");
        else emit("  lw t0, -"+std::to_string(off)+"(s0)\n");
      } else {
        emit("  la t1, "+e->name+"\n  lw t0, 0(t1)\n");
      }
      break;
    }
    case ExprKind::UNARY: genUnary(e); break;
    case ExprKind::ADDR_OF: genAddr(e->kids[0]); emit("  mv t0, t1\n"); break;
    case ExprKind::DEREF: {
      genExpr(e->kids[0]); // pointer in t0
      int sz = typeSize(typeOf(e));
      if (sz==1) emit("  lbu t0, 0(t0)\n"); else emit("  lw t0, 0(t0)\n");
      break;
    }
    case ExprKind::INDEX: {
      // a[i] = *(a + i*scale); result type size
      genExpr(e->kids[0]); emit("  addi sp,sp,-4\n  sw t0,0(sp)\n"); // base
      genExpr(e->kids[1]);                          // index in t0
      emit("  lw t1, 0(sp)\n  addi sp,sp,4\n");      // base -> t1
      int scale = typeSize(typeOf(e));               // element size
      if (scale!=1){ emit("  slli t0, t0, "+std::to_string(scale==4?2:0)+"\n"); }
      emit("  add t1, t1, t0\n");
      if (typeSize(typeOf(e))==1) emit("  lbu t0, 0(t1)\n"); else emit("  lw t0, 0(t1)\n");
      break;
    }
    case ExprKind::BINARY: genBinary(e); break;
    case ExprKind::ASSIGN: genAssign(e); break;
    case ExprKind::CALL: genCall(e); break;
    case ExprKind::COMMA: {
      genExpr(e->kids[0]);   // discard left (result stays in t0, overwritten)
      genExpr(e->kids[1]); break;
    }
    case ExprKind::TERNARY: {
      genExpr(e->kids[0]);
      std::string lElse=freshLabel("Lte"), lEnd=freshLabel("Lte");
      emit("  beqz t0, "+lElse+"\n");
      genExpr(e->kids[1]); emit("  j "+lEnd+"\n"+lElse+":\n");
      genExpr(e->kids[2]); emit(lEnd+":\n"); break;
    }
    case ExprKind::POST_INC: case ExprKind::POST_DEC:
    case ExprKind::PRE_INC: case ExprKind::PRE_DEC: genPostfix(e); break;
  }
}

void Codegen::genBinary(const ExprPtr& e) {
  const std::string& op=e->op;
  // logical && / || : short-circuit to 0/1
  if (op=="&&"||op=="||") {
    std::string lRhs=freshLabel("Lrhs"), lEnd=freshLabel("Lend");
    genExpr(e->kids[0]);
    if(op=="&&"){
      // if lhs==0 -> result 0 (jump end with t0=0); else eval rhs -> t0=(rhs!=0)
      emit("  beqz t0, "+lEnd+"\n");
      genExpr(e->kids[1]); emit("  snez t0, t0\n  j "+lEnd+"\n");
      emit(lEnd+":\n"); // t0 already 0 on the taken path? beqz leaves t0=0 -> good
      // but need explicit 0: beqz path t0==0 already -> ok
    } else {
      // if lhs!=0 -> result 1; else eval rhs -> t0=(rhs!=0)
      emit("  bnez t0, "+lRhs+"\n");
      genExpr(e->kids[1]); emit("  snez t0, t0\n  j "+lEnd+"\n");
      emit(lRhs+":\n  li t0, 1\n"+lEnd+":\n");
    }
    return;
  }
  genExpr(e->kids[0]); emit("  addi sp,sp,-4\n  sw t0,0(sp)\n"); // left -> stack
  genExpr(e->kids[1]);                                        // right -> t0
  emit("  lw t1, 0(sp)\n  addi sp,sp,4\n");                    // left->t1, right->t0
  if (op=="+") emit("  add t0, t1, t0\n");
  else if(op=="-") emit("  sub t0, t1, t0\n");
  else if(op=="*") emit("  mul t0, t1, t0\n");
  else if(op=="/") emit("  div t0, t1, t0\n");
  else if(op=="%") emit("  rem t0, t1, t0\n");
  else if(op=="&") emit("  and t0, t1, t0\n");
  else if(op=="|") emit("  or t0, t1, t0\n");
  else if(op=="^") emit("  xor t0, t1, t0\n");
  else if(op=="<<") emit("  sll t0, t1, t0\n");
  else if(op==">>") emit("  srl t0, t1, t0\n");
  else if(op=="<") emit("  slt t0, t1, t0\n");
  else if(op=="<=") { emit("  slt t0, t0, t1\n  xori t0, t0, 1\n"); } // !(right<left)
  else if(op==">") { emit("  slt t0, t0, t1\n"); }                     // right<left
  else if(op==">=") { emit("  slt t0, t1, t0\n  xori t0, t0, 1\n"); } // !(left<right)
  else if(op=="==") { emit("  xor t0, t1, t0\n  seqz t0, t0\n"); }
  else if(op=="!=") { emit("  xor t0, t1, t0\n  snez t0, t0\n"); }
  else emit("  add t0, t1, t0\n"); // fallback
}

void Codegen::genAssign(const ExprPtr& e) {
  const std::string& op=e->op;
  // compute lvalue address -> t2 (preserve across rhs eval via stack)
  genAddr(e->kids[0]);
  emit("  addi sp,sp,-4\n  sw t1,0(sp)\n");   // save addr
  if (op=="=") {
    genExpr(e->kids[1]);                        // value -> t0
  } else {
    // compound: load current, combine with rhs
    emit("  lw t1, 0(sp)\n");                   // addr -> t1 (peek, keep on stack)
    int sz=typeSize(typeOf(e->kids[0]));
    if(sz==1) emit("  lbu t0, 0(t1)\n"); else emit("  lw t0, 0(t1)\n"); // current -> t0
    emit("  addi sp,sp,4\n");                   // pop addr (discard)
    // push current, eval rhs
    emit("  addi sp,sp,-4\n  sw t0,0(sp)\n");
    genExpr(e->kids[1]);                        // rhs -> t0
    emit("  lw t1,0(sp)\n  addi sp,sp,4\n");    // current -> t1, rhs -> t0
    std::string base=op.substr(0,op.size()-1);
    if(base=="+")emit("  add t0,t1,t0\n"); else if(base=="-")emit("  sub t0,t1,t0\n");
    else if(base=="*")emit("  mul t0,t1,t0\n"); else if(base=="/")emit("  div t0,t1,t0\n");
    else if(base=="%")emit("  rem t0,t1,t0\n"); else if(base=="&")emit("  and t0,t1,t0\n");
    else if(base=="|")emit("  or t0,t1,t0\n"); else if(base=="^")emit("  xor t0,t1,t0\n");
    else if(base=="<<")emit("  sll t0,t1,t0\n"); else if(base==">>")emit("  srl t0,t1,t0\n");
    // recompute addr for store
    genAddr(e->kids[0]); emit("  mv t1,t1\n");
    // t1=addr? genAddr puts in t1 already
  }
  // store t0 -> [addr]. addr currently: for "=" path it's on stack top; pop to t1
  if (op=="=") {
    emit("  lw t1,0(sp)\n  addi sp,sp,4\n");
  }
  int sz=typeSize(typeOf(e->kids[0]));
  if(sz==1) emit("  sb t0, 0(t1)\n"); else emit("  sw t0, 0(t1)\n");
}

void Codegen::genCall(const ExprPtr& e) {
  // builtin syscalls
  if (e->name=="write"||e->name=="read"||e->name=="open"||e->name=="close"||
      e->name=="getpid"||e->name=="exit"||e->name=="brk"||e->name=="sbrk"||e->name=="yield") {
    int sc=0;
    if(e->name=="write")sc=SC_WRITE; else if(e->name=="read")sc=SC_READ;
    else if(e->name=="open")sc=SC_OPEN; else if(e->name=="close")sc=SC_CLOSE;
    else if(e->name=="getpid")sc=SC_GETPID; else if(e->name=="exit")sc=SC_EXIT;
    else if(e->name=="brk")sc=SC_BRK; else if(e->name=="sbrk")sc=SC_SBRK;
    else if(e->name=="yield")sc=SC_YIELD;
    // evaluate args right-to-left onto stack, then into a0..a5
    int n=e->kids.size();
    for(int i=n-1;i>=0;i--){ genExpr(e->kids[i]); emit("  addi sp,sp,-4\n  sw t0,0(sp)\n"); }
    for(int i=0;i<n && i<6;i++){ emit("  lw a"+std::to_string(i)+",0(sp)\n  addi sp,sp,4\n"); }
    emit("  li a7, "+std::to_string(sc)+"\n  ecall\n  mv t0, a0\n");
    return;
  }
  // normal call: args -> a0..a7
  int n=e->kids.size();
  for(int i=n-1;i>=0;i--){ genExpr(e->kids[i]); emit("  addi sp,sp,-4\n  sw t0,0(sp)\n"); }
  for(int i=0;i<n && i<8;i++){ emit("  lw a"+std::to_string(i)+",0(sp)\n  addi sp,sp,4\n"); }
  emit("  jal ra, "+e->name+"\n  mv t0, a0\n");
}

void Codegen::genUnary(const ExprPtr& e) {
  const std::string& op=e->op;
  genExpr(e->kids[0]);
  if(op=="-") emit("  sub t0, x0, t0\n");
  else if(op=="!") emit("  seqz t0, t0\n");
  else if(op=="~") emit("  not t0, t0\n");
  else if(op=="+") {} // nop
}

void Codegen::genPostfix(const ExprPtr& e) {
  // lvalue ++/-- : load, add/sub, store; result old(POST) or new(PRE)
  genAddr(e->kids[0]); emit("  mv t2, t1\n");   // addr -> t2
  int sz=typeSize(typeOf(e->kids[0]));
  if(sz==1) emit("  lbu t0, 0(t2)\n"); else emit("  lw t0, 0(t2)\n"); // current -> t0
  bool pre = (e->kind==ExprKind::PRE_INC||e->kind==ExprKind::PRE_DEC);
  bool inc = (e->kind==ExprKind::POST_INC||e->kind==ExprKind::PRE_INC);
  emit("  mv t1, t0\n");                        // save current -> t1
  emit("  addi t0, t0, "+std::string(inc?"1":"-1")+"\n");   // new -> t0
  if(sz==1) emit("  sb t0, 0(t2)\n"); else emit("  sw t0, 0(t2)\n"); // store new
  if(pre) emit("  mv t0, t0\n");                 // result new (already t0)
  else emit("  mv t0, t1\n");                    // result old
}

void Codegen::genAddr(const ExprPtr& e) {
  // compute lvalue address -> t1
  switch(e->kind){
    case ExprKind::VAR: {
      if (isLocal(e->name)) { int off=localOff(e->name); emit("  addi t1, s0, -"+std::to_string(off)+"\n"); }
      else emit("  la t1, "+e->name+"\n");
      break;
    }
    case ExprKind::DEREF: genExpr(e->kids[0]); emit("  mv t1, t0\n"); break;
    case ExprKind::INDEX: {
      genExpr(e->kids[0]); emit("  addi sp,sp,-4\n  sw t0,0(sp)\n");
      genExpr(e->kids[1]);
      emit("  lw t1,0(sp)\n  addi sp,sp,4\n");
      int scale=typeSize(typeOf(e->kids[0]->kind==ExprKind::VAR? e->kids[0] : e));
      // element size = size of pointee; approximate via typeOf(e) result... use index expr type base
      scale = typeSize(typeOf(e)); // result elem size? actually want base elem size
      // simpler: scale by typeSize of the indexed type's base
      auto bt = typeOf(e->kids[0]);
      int esz = (bt->k==TyKind::PTR||bt->k==TyKind::ARRAY) ? typeSize(bt->base) : 4;
      if(esz!=1){ int sh = esz==4?2:(esz==2?1:0); if(sh) emit("  slli t0,t0,"+std::to_string(sh)+"\n"); }
      emit("  add t1, t1, t0\n"); break;
    }
    default: emit("  li t1, 0\n"); break;
  }
}

// ---- statements ----
void Codegen::genStmt(const StmtPtr& s) {
  if(!s)return;
  switch(s->kind){
    case StmtKind::BLOCK: genBlock(s); break;
    case StmtKind::DECL: {
      // allocate local slot
      int sz=typeSize(s->declType); sz=(sz+3)&~3;
      fn_->nextLocal+=sz;
      int off=fn_->nextLocal;
      fn_->locals[s->declName]=off;
      fn_->localTypes[s->declName]=s->declType;
      if(s->declInit){
        genExpr(s->declInit);
        emit("  sw t0, -"+std::to_string(off)+"(s0)\n");
      } else {
        emit("  sw x0, -"+std::to_string(off)+"(s0)\n");
      }
      break;
    }
    case StmtKind::EXPR: genExpr(s->expr); break; // result discarded in t0
    case StmtKind::RETURN:
      if(s->expr) genExpr(s->expr); else emit("  li t0, 0\n");
      emit("  mv a0, t0\n  j "+fn_->name+"_epilogue\n"); break;
    case StmtKind::IF: {
      std::string lElse=freshLabel("Lelse"), lEnd=freshLabel("Lendif");
      genExpr(s->cond);
      emit("  beqz t0, "+lElse+"\n");
      genStmt(s->thenS);
      if(s->elseS){ emit("  j "+lEnd+"\n"+lElse+":\n"); genStmt(s->elseS); emit(lEnd+":\n"); }
      else emit(lElse+":\n");
      break;
    }
    case StmtKind::WHILE: {
      std::string lTop=freshLabel("Lwhile"), lEnd=freshLabel("Lwend");
      fn_->breakLabels.push_back(lEnd); fn_->contLabels.push_back(lTop);
      emit(lTop+":\n");
      genExpr(s->whileCond); emit("  beqz t0, "+lEnd+"\n");
      for(auto&b:s->body)genStmt(b);
      emit("  j "+lTop+"\n"+lEnd+":\n");
      fn_->breakLabels.pop_back(); fn_->contLabels.pop_back();
      break;
    }
    case StmtKind::FOR: {
      std::string lTop=freshLabel("Lfor"), lEnd=freshLabel("Lford"), lCont=freshLabel("Lforc");
      fn_->breakLabels.push_back(lEnd); fn_->contLabels.push_back(lCont);
      if(s->forInit)genStmt(s->forInit);
      emit(lTop+":\n");
      if(s->forCond){genExpr(s->forCond);emit("  beqz t0, "+lEnd+"\n");}
      for(auto&b:s->body)genStmt(b);
      emit(lCont+":\n");
      if(s->forPost)genExpr(s->forPost); // result discarded in t0
      emit("  j "+lTop+"\n"+lEnd+":\n");
      fn_->breakLabels.pop_back(); fn_->contLabels.pop_back();
      break;
    }
    case StmtKind::BREAK:
      emit("  j "+(fn_->breakLabels.empty()?"0":fn_->breakLabels.back())+"\n"); break;
    case StmtKind::CONTINUE:
      emit("  j "+(fn_->contLabels.empty()?"0":fn_->contLabels.back())+"\n"); break;
    case StmtKind::EMPTY: break;
  }
}
void Codegen::genBlock(const StmtPtr& s){ for(auto&x:s->body)genStmt(x); }

void Codegen::genFunc(const std::shared_ptr<FuncDecl>& f) {
  FuncCtx ctx; ctx.name=f->name; fn_=&ctx;
  // generate body into temp buffer to size frame
  std::string saved=out_; out_="";
  // spill params to locals: assign offsets first
  for(size_t i=0;i<f->params.size() && i<8;i++){
    int sz=typeSize(f->params[i].type); sz=(sz+3)&~3;
    ctx.nextLocal+=sz; int off=ctx.nextLocal;
    ctx.locals[f->params[i].name]=off; ctx.localTypes[f->params[i].name]=f->params[i].type;
    ctx.paramNames.push_back(f->params[i].name);
  }
  // param spill code (a0..a7 -> local slots)
  std::string pro="";
  for(size_t i=0;i<f->params.size() && i<8;i++){
    int off=ctx.locals[f->params[i].name];
    pro+="  sw a"+std::to_string(i)+", -"+std::to_string(off)+"(s0)\n";
  }
  // body
  if(f->body) for(auto&st:f->body->body) genStmt(st);
  // default return
  emit("  li a0, 0\n");
  // frame = locals + saved(ra,s0) + slack, 16-byte aligned
  int frame = (ctx.nextLocal + 16 + 15) & ~15;
  ctx.frameSize = frame;
  std::string ep=f->name+"_epilogue";
  emit(ep+":\n");
  emit("  lw ra, "+std::to_string(frame-4)+"(sp)\n");
  emit("  lw s0, "+std::to_string(frame-8)+"(sp)\n");
  emit("  addi sp, sp, "+std::to_string(frame)+"\n");
  emit("  ret\n");
  std::string body=out_; out_=saved;
  emit(".globl "+f->name+"\n"+f->name+":\n");
  emit("  addi sp, sp, -"+std::to_string(frame)+"\n");
  emit("  sw ra, "+std::to_string(frame-4)+"(sp)\n");
  emit("  sw s0, "+std::to_string(frame-8)+"(sp)\n");
  emit("  addi s0, sp, "+std::to_string(frame-8)+"\n"); // s0 -> saved-s0 slot
  emit(pro);
  emit(body);
  // rodata for this function's strings -> .byte lists (decoded C escapes)
  if(!ctx.rodata.empty()){
    emit(".rodata\n");
    for(auto&pr:ctx.rodata){
      emit(pr.first+":\n");
      auto bytes = decodeString(pr.second);
      emit("  .byte ");
      for(size_t i=0;i<bytes.size();i++){ if(i)emit(","); emit(std::to_string((int)bytes[i])); }
      emit("\n");
    }
    emit(".text\n");
  }
  fn_=nullptr;
}

void Codegen::genGlobal(const std::shared_ptr<GlobalDecl>& g) {
  if(g->isFunc){ genFunc(g->fn); return; }
  globals_[g->name]=(GlobalDecl*)g.get();
  int sz=typeSize(g->type);
  if(g->init){
    emit(".data\n.globl "+g->name+"\n"+g->name+":\n  .word "+std::to_string(g->init->ival)+"\n.text\n");
  } else {
    emit(".bss\n.globl "+g->name+"\n.align 2\n"+g->name+":\n  .space "+std::to_string(std::max(4,sz))+"\n.text\n");
  }
}

std::string Codegen::generate(const Program& prog, std::string& err) {
  out_=""; labelCtr_=0; globStrCtr_=0; strLabels_.clear(); globals_.clear(); fn_=nullptr;
  emit("# AN32 generated code\n.text\n");
  // globals first (functions may reference them)
  for(auto&g:prog.globals) if(!g->isFunc) genGlobal(g);
  // functions
  for(auto&g:prog.globals) if(g->isFunc) genFunc(g->fn);
  if(err.size()) return "";
  return out_;
}

} // namespace an32cc
