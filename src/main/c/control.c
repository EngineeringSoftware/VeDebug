#include "control.h"

#define PARSE_BUFFER_SIZE 1000

/*
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
void push_function_call(FunctionID** ids, TraceElem* call, DebugState* state) {
  state->call_stack[state->stack_size] = ids[call->fid - 1];
  state->arguments_stack[state->stack_size] = call->args;
  state->num_arguments_stack[state->stack_size] = call->num_args;
  state->returns_stack[state->stack_size] = call->ret;
  state->stack_size++;
}

void pop_function_call(DebugState* state) {
  state->stack_size--;
}

void move_video(FunctionID** ids, TraceElem** trace, DebugState* state, int amount) {
  int target_index = state->debug_index + amount;
  if (amount > 0) {
    while (state->debug_index < target_index  && \
        state->debug_index < state->max_debug_index - 1) {
      TraceElem* current = trace[state->trace_index];
      if (current->type == RETURN) {
        // Return case
        pop_function_call(state);
        state->trace_index++;
      } else if (current->type == CALL) {
        // Function call case
        push_function_call(ids, current, state);
        state->trace_index++;
      } else if (current->type == BLOCK) {
        // Move through lines case
        if (state->current_line_number == -1) {
          // Beginning line block
          state->begin_line_number = current->start_line;
          state->current_line_number = state->begin_line_number;
          state->end_line_number = current->end_line;
          state->debug_index++;
        } else if (state->current_line_number < state->end_line_number) {
          // Inside line block
          state->current_line_number++;
          state->debug_index++;
        } else {
          // Exiting line block
          state->trace_index++;
          state->current_line_number = -1;
        }
      } else {
        // Divergence point or delta info case
        state->trace_index++;
      }
    }
  } else {
    while (state->debug_index > target_index && state->debug_index > 0) {
      TraceElem* current = trace[state->trace_index];
      if (current->type == RETURN) {
        // Return case
        push_function_call(ids, current, state);
        state->trace_index--;
      } else if (current->type == CALL) {
        // Function call case
        pop_function_call(state);
        state->trace_index--;
      } else if (current->type == BLOCK) {
        // Move through lines case
        if (state->current_line_number == -1) {
          // Beginning line block
          state->begin_line_number = current->start_line;
          state->end_line_number = current->end_line;
          state->current_line_number = state->end_line_number;
          state->debug_index--;
        } else if (state->current_line_number > state->begin_line_number) {
          // Inside line block
          state->current_line_number--;
          state->debug_index--;
        } else {
          // Exiting line block
          state->trace_index--;
          state->current_line_number = -1;
        }
      } else {
        // Divergence point or delta info case
        state->trace_index--;
      }
    }
  }
}

void jump_forward(FunctionID** ids, TraceElem** trace, DebugState* state) {
  if (trace[state->trace_index]->type != BLOCK) {
    printf("Invalid jump backward");
    exit(EXIT_FAILURE);
  }
  if (state->current_line_number == -1) {
    state->debug_index++;
  }
  state->debug_index += state->end_line_number - state->current_line_number;
  if (state->debug_index == state->max_debug_index - 1) {
    state->current_line_number = state->end_line_number;
  } else {
    state->current_line_number = -1;
    state->trace_index++;
    move_video(ids, trace, state, 1);
  }
}

void jump_backward(FunctionID** ids, TraceElem** trace, DebugState* state) {
  if (trace[state->trace_index]->type != BLOCK) {
    printf("Invalid jump backward");
    exit(EXIT_FAILURE);
  }
  if (state->current_line_number == -1) {
    state->debug_index--;
  }
  state->debug_index -= state->current_line_number - state->begin_line_number;
  if (state->debug_index == 0) {
    state->current_line_number = state->begin_line_number;
  } else {
    state->current_line_number = -1;
    state->trace_index--;
    move_video(ids, trace, state, -1);
  }
}

int locked_stack_move(FunctionID** ids, TraceElem** trace, DebugState* state, int amount) {
  int locked_stack_size = state->stack_size;
  int current = 0;
  if (amount > 0) {
    while (current < amount) {
      if (state->stack_size > locked_stack_size) {
        jump_forward(ids, trace, state);
        if (state->debug_index == state->max_debug_index - 1) {
          return 1;
        }
      } else if (state->stack_size < locked_stack_size) {
        move_video(ids, trace, state, -1);
        return 0;
      } else {
        move_video(ids, trace, state, 1);
        if (state->stack_size == locked_stack_size) {
          current++;
        }
      }
    }
  } else {
    while (current > amount) {
      if (state->stack_size > locked_stack_size) {
        jump_backward(ids, trace, state);
        if (state->trace_index == 0) {
          return 1;
        }
      } else if (state->stack_size < locked_stack_size) {
        move_video(ids, trace, state, 1);
        return 0;
      } else {
        move_video(ids, trace, state, -1);
        if (state->stack_size == locked_stack_size) {
          current--;
        }
      }
    }
  }
  return 0;
}

void step_out(FunctionID** ids, TraceElem** trace, DebugState* state, int reverse) {
  int initial_stack_size = state->stack_size;
  int previous_debug_index;
  do {
    previous_debug_index = state->debug_index;
    int err = 0;
    if (!reverse) {
      err = locked_stack_move(ids, trace, state, 1);
    } else {
      err = locked_stack_move(ids, trace, state, -1);
    }
    if (err) {
      return;
    }
  } while (previous_debug_index != state->debug_index);
  if (!reverse) {
    move_video(ids, trace, state, 1);
  } else {
    move_video(ids, trace, state, -1);
  }
}

int step_into(FunctionID** ids, TraceElem** trace, DebugState* state, int reverse) {
  int found = 0;
  int initial_stack_size = state->stack_size;
  if (!reverse) {
    int temp_index = state->trace_index + 1;
    while (temp_index < state->trace_length) {
      if (trace[temp_index]->type == CALL) {
        found = 1;
        break;
      } else if (trace[temp_index]->type == RETURN) {
        break;
      }
      temp_index++;
    }
    if (!found) { return 1; }
    while (initial_stack_size == state->stack_size) {
      jump_forward(ids, trace, state);
    }
    return 0;
  } else {
    int temp_index = state->trace_index - 1;
    while (temp_index > -1) {
      char* line = trace[temp_index];
      if (trace[temp_index]->type == RETURN) {
        found = 1;
        break;
      } else if (trace[temp_index]->type == CALL) {
        break;
      }
      temp_index--;
    }
    if (!found) { return 1; }
    while (initial_stack_size == state->stack_size) {
      jump_backward(ids, trace, state);
    }
    return 0;
  }
}

int move_to_function(FunctionID** ids, TraceElem** trace, DebugState* state, char* func_name, \
    int reverse) {
  int found = 0;
  if (!reverse) {
    int temp_index = state->trace_index + 1;
    while (temp_index < state->trace_length) {
      if (trace[temp_index]->type == RETURN || trace[temp_index]->type == CALL) {
        int id = trace[temp_index]->fid;
        if (match(ids[id - 1]->func_name, func_name)) {
          found = 1;
          break;
        }
      }
      temp_index++;
    }
    if (!found) { return 1; }
    while (!match(state->call_stack[state->stack_size - 1]->func_name, func_name)) {
      jump_forward(ids, trace, state);
    }
    return 0;
  } else {
    int temp_index = state->trace_index - 1;
    while (temp_index > -1) {
      if (trace[temp_index]->type == RETURN || trace[temp_index]->type == CALL) {
        int id = trace[temp_index]->fid;
        if (match(ids[id - 1]->func_name, func_name)) {
          found = 1;
          break;
        }
      }
      temp_index--;
    }
    if (!found) { return 1; }
    while (!match(state->call_stack[state->stack_size - 1]->func_name, func_name)) {
      jump_backward(ids, trace, state);
    }
    return 0;
  }
}

/* match: search for regexp anywhere in text */
int match(char *text, char *regexp)
{
  if (regexp[0] == '^')
    return matchhere(regexp+1, text);
  do {    /* must look even if string is empty */
    if (matchhere(regexp, text))
      return 1;
  } while (*text++ != '\0');
  return 0;
}

/* matchhere: search for regexp at beginning of text */
int matchhere(char *regexp, char *text)
{
  if (regexp[0] == '\0')
    return 1;
  if (regexp[1] == '*')
    return matchstar(regexp[0], regexp+2, text);
  if (regexp[0] == '$' && regexp[1] == '\0')
    return *text == '\0';
  if (*text!='\0' && (regexp[0]=='.' || regexp[0]==*text))
    return matchhere(regexp+1, text+1);
  return 0;
}

/* matchstar: search for c*regexp at beginning of text */
int matchstar(int c, char *regexp, char *text)
{
  do {    /* a * matches zero or more instances */
    if (matchhere(regexp, text))
      return 1;
  } while (*text != '\0' && (*text++ == c || c == '.'));
  return 0;
}

int move_to_divergence_point(FunctionID** ids, TraceElem** trace, DebugState* state, int reverse) {
  int increment = 0;
  if (!reverse) {
    increment = 1;
  } else {
    increment = -1;
  }
  int temp_index = state->trace_index;
  int found = 0;
  while (temp_index > -1 && temp_index < state->trace_length) {
    if (trace[temp_index]->type == VERSION) {
      found = 1;
      break;
    }
    temp_index += increment;
  }
  if (!found) { return 1; }
  int previous_index = state->debug_index;
  while (increment * (temp_index - state->trace_index) > 0) {
    if (!reverse) {
      jump_forward(ids, trace, state);
    } else {
      jump_backward(ids, trace, state);
    }
    if (previous_index == state->debug_index) {
      return 1;
    } else {
      previous_index = state->debug_index;
    }
  }
  move_video(ids, trace, state, -1 * increment);
  return 0;
}
