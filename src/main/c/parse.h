#ifndef VEDEBUG_PARSE
#define VEDEBUG_PARSE

#include <stdlib.h>
#include <stdio.h>
#include <string.h>

/*
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
typedef struct {
  int start_line;
  int end_line;
  char* path;
  char* class_name;
  char* func_name;
  char* arg_types;
  char* return_type;
} FunctionID;

typedef enum elemtype {BLOCK, CALL, RETURN, VERSION, DELTA} ElemType;

typedef struct {
  ElemType type;
  int start_line;
  int end_line;
  int fid;
  int num_args;
  char** args;
  char* ret;
} TraceElem;

int is_int(const char* string);

char* get_class_name(char* input);

void parse_file(char* filename, int* num_lines, char*** lines);

void free_file(int num_lines, char*** lines);

void concat_paths(char* base, const char* extension);

void get_function_ids(char* filename, int* num_lines, FunctionID*** ids);

void get_trace(char* filename, int* num_lines, TraceElem*** trace);

void free_trace(int* num_lines, TraceElem*** trace);

void free_function_ids(int* num_lines, FunctionID*** ids);

#endif
