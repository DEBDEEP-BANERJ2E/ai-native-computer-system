#pragma once

#include <string>
#include <vector>
#include <cstdint>

namespace an32cc {

/** Lexer for the AN32 C-subset (O5-A frontend).
  *
  * Tokenizes C source into tokens: keywords, identifiers, integer/char/string
  * literals, operators, punctuation. Tracks line/col for diagnostics.
  */
enum class TokKind {
  END, IDENT, INT_CONST, CHAR_CONST, STRING,
  // keywords
  KW_INT, KW_VOID, KW_CHAR, KW_IF, KW_ELSE, KW_WHILE, KW_FOR,
  KW_RETURN, KW_BREAK, KW_CONTINUE, KW_SIZEOF,
  // operators / punctuation
  OP, PUNCT
};

struct Token {
  TokKind kind;
  std::string text;      // raw spelling
  int64_t ival = 0;      // for INT_CONST / CHAR_CONST
  int line = 0, col = 0;
};

class Lexer {
public:
  explicit Lexer(const std::string& src);
  std::vector<Token> tokenize();
  // returns error string or empty
  std::string error;

private:
  const std::string& src_;
  size_t pos_ = 0;
  int line_ = 1, col_ = 1;
  char peek(size_t off = 0) const;
  char get();
  Token make(TokKind k, const std::string& t);
  bool match(char c);
  bool match2(const char* s);
};

} // namespace an32cc
