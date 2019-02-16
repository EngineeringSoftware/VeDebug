#ifndef VEDEBUG_CONTROL
#define VEDEBUG_CONTROL

#include <ncurses.h>
#include "parse.h"

/*
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
typedef struct {
  // Indexing within the trace file
  const int trace_length;
  int trace_index;

  // Total visualizable debug steps
  const int max_debug_index;
  int debug_index;

  // Line numbers within the current block of format [begin,end]
  int begin_line_number;
  int current_line_number;
  int end_line_number;

  // Stack information
  FunctionID** call_stack;
  char*** arguments_stack;
  int* num_arguments_stack;
  char** returns_stack;
  int stack_size;

} DebugState;

void move_video(FunctionID** ids, TraceElem** trace, DebugState* state, int amount);

int locked_stack_move(FunctionID** ids, TraceElem** trace, DebugState* state, int amount);

void step_out(FunctionID** ids, TraceElem** trace, DebugState* state, int reverse);

int step_into(FunctionID** ids, TraceElem** trace, DebugState* state, int reverse);

int move_to_function(FunctionID** ids, TraceElem** trace, DebugState* state, char* func_name, int reverse);

int move_to_divergence_point(FunctionID** ids, TraceElem** trace, DebugState* state, int reverse);

#endif
