#include <ncurses.h>
#include <unistd.h>

#include "parse.h"
#include "control.h"

#define MAX_LINE_SIZE 1000
#define MAX_CALL_STACK_SIZE 1000
#define MAX_INPUT_SIZE 1000
#define MAX_CALL_LIST_SIZE 1000
#define PATH_BUFFER_SIZE 1000
#define VERT_DIV_NUMERATOR 3
#define VERT_DIV_DENOMINATOR 4
#define HORI_DIV_NUMERATOR 3
#define HORI_DIV_DENOMINATOR 4
#define CURRENT_LINE 1
#define CONTROL_KEY 2
#define TYPE_KEY 3
#define INHERITENCE_KEY 4

/*
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
void resize_windows(WINDOW** video, WINDOW** info, WINDOW** output, int* refresh) {
  static int rows, cols;
  int newrows, newcols;
  getmaxyx(stdscr, newrows, newcols);
  if (newrows != rows || newcols != cols) {
    *refresh = 1;
    rows = newrows; cols = newcols;
    delwin(*video); delwin(*info); delwin(*output);

    /* video window takes 3/4 of the width and 3/4 of the height of the screen from the
     * top left corner, below it is the output window and on the right quarter is the
     * info window
     */
    *video = newwin(VERT_DIV_NUMERATOR*rows/VERT_DIV_DENOMINATOR,\
            HORI_DIV_NUMERATOR*cols/HORI_DIV_DENOMINATOR, 0, 0);
    *info = newwin(rows,cols-HORI_DIV_NUMERATOR*cols/HORI_DIV_DENOMINATOR,0,\
            HORI_DIV_NUMERATOR*cols/HORI_DIV_DENOMINATOR);
    *output = newwin(rows-VERT_DIV_NUMERATOR*rows/VERT_DIV_DENOMINATOR,\
            HORI_DIV_NUMERATOR*cols/HORI_DIV_DENOMINATOR,\
            VERT_DIV_NUMERATOR*rows/VERT_DIV_DENOMINATOR,0);
    keypad(*video, true);
    mousemask(BUTTON1_PRESSED, NULL);
  }
}

//Progress bar
void display_progress(WINDOW* video,int current, int total) {
  int rows, cols;
  getmaxyx(video, rows, cols);
  int full = (cols - 2) * current / (total - 1);
  int empty = (cols - 2) - full;
  wmove(video, rows - 2, 1);
  for (int i = 0; i < full; i++) {
    waddch(video, '|');
  }
  for (int i = 0; i < empty; i++) {
    waddch(video, ' ');
  }
  return;
}

// Keywords specifying control flow
int num_control = 17;
const char* control_keywords[] = {"break", "case", "catch", "continue", "default", "do", \
    "else", "finally", "for", "goto", "if", "return", "switch", "synchronized", "throw", "try", \
    "while"};

// Keywords specifying type
int num_type = 16;
const char* type_keywords[] = {"boolean", "byte", "char", "double", "final", "float", "int", \
    "long", "new", "short", "transient", "void", "volatile", "true", "null", "false"};

// Keywords specifying inheritence relationships
int num_inheritence = 19;
const char* inheritence_keywords[] = {"abstract", "class", "enum", "extends", "implements", \
    "interface", "native", "package", "private", "protected", "public", "static", "super", \
    "this", "throws", "import", "instanceof", "assert", "strictfp"};

void java_print(WINDOW* win, char* string, int max_chars) {
  char buffer[MAX_LINE_SIZE];
  strcpy(buffer, string);
  char* token = strtok(buffer, " \t\n(){}[];:@='\"<>+-*%^|?&/\\,.\0");
  int chars = 0;
  int word_type = 0;
  while (chars <= max_chars) {
    word_type = 0;
    if (token != NULL) {
      // Search for token match with any of the keywords
      for (int i = 0; i < num_control && !word_type; i++) {
        if (!strcmp(token, control_keywords[i])) {
          word_type = CONTROL_KEY;
        }
      }
      for (int i = 0; i < num_type && !word_type; i++) {
        if (!strcmp(token, type_keywords[i])) {
          word_type = TYPE_KEY;
        }
      }
      for (int i = 0; i < num_inheritence && !word_type; i++) {
        if (!strcmp(token, inheritence_keywords[i])) {
          word_type = INHERITENCE_KEY;
        }
      }
    }
    while (token == NULL || (strncmp(string + chars, token, strlen(token)) && chars <= max_chars)) {
      // Advance until token is reached
      if (*(string + chars) == '\0' || *(string + chars) == 13 || *(string + chars) == 10) {
        return;
      }
      waddch(win, *(string + chars));
      chars++;
    }
    if (word_type) {
      // Color case
      wattron(win, COLOR_PAIR(word_type));
      waddnstr(win, token, max_chars - chars);
      wattroff(win, COLOR_PAIR(word_type));
    } else {
      // Non-colored case
      waddnstr(win, token, max_chars - chars);
    }
    chars += strlen(token);
    if (token != NULL) {
      token = strtok(NULL, " \t\n(){}[];:@='\"<>+-*%^|?&/\\,.\0");
    }
  }
}

void show_file(WINDOW* video, int line_num, char* file_path) {
  int rows, cols;
  getmaxyx(video, rows, cols);
  char line[MAX_LINE_SIZE];

  // Get file
  FILE* fp = fopen(file_path, "r");
  if (fp == NULL) {
    endwin();
    fprintf(stderr, "Unable to open source file %s\n", file_path);
    exit(EXIT_FAILURE);
  }

  // If the line is later on in the file, iterate to the part where it is ok to begin displaying
  int current_line_num = 1;
  int video_line = 1;
  if ((rows - 1) / 3 < line_num) {
    int advance_count = 0;
    int advance_to = line_num - ((rows - 1) / 3);
    while(fgets(line, MAX_LINE_SIZE, fp) != NULL && advance_count < advance_to) {
      advance_count++; current_line_num++;
    }
    current_line_num++;
  }

  // Find number of usable characters
  char number_buffer[10];
  sprintf(number_buffer, "%d", current_line_num + rows - 3);
  int offset = strlen(number_buffer) + 2;
  int max_chars = cols - offset - 1;

  // Display information on the window
  while (fgets(line, MAX_LINE_SIZE, fp) != NULL && video_line < rows) {
    sprintf(number_buffer, "%d ", current_line_num);
    if (current_line_num == line_num) {
      // Current execution line
      wattron(video, COLOR_PAIR(CURRENT_LINE));
      wattron(video, A_BOLD);
      wmove(video, video_line, 1);
      waddstr(video, number_buffer);
      wmove(video, video_line, offset);
      for (int i = 0; i < strlen(line) && i < max_chars; i++) {
        if (line[i] == '\r' || line[i] == '\n') {
          break;
        }
        waddch(video, line[i]);
      }
      wattroff(video, COLOR_PAIR(CURRENT_LINE));
      wattroff(video, A_BOLD);
    } else {
      // Not current execution line
      wattron(video, COLOR_PAIR(CONTROL_KEY));
      wmove(video, video_line, 1);
      waddstr(video, number_buffer);
      wattroff(video, COLOR_PAIR(CONTROL_KEY));
      wmove(video, video_line, offset);
      java_print(video, line, max_chars);
    }
    video_line++; current_line_num++;
  }
  fclose(fp);
}

// Needs to be reworked, not using for now
void handle_mouse(WINDOW* video, MEVENT event, int* indexp, int trace_length) {
  int rows, cols;
  getmaxyx(video, rows, cols);
  if (event.y != rows - 2 || event.x < 1 || event.x > cols - 2) {
    return;
  }
  *indexp = ((event.x - 1) * trace_length) / (cols - 2);
}

// Get total length of trace
int sum_lines(int trace_length, const TraceElem** trace) {
  int sum = 0;
  for (int i = 0; i < trace_length; i++) {
    if (trace[i]->type != BLOCK) { continue; }
    sum += trace[i]->end_line - trace[i]->start_line + 1;
  }
  return sum;
}

int display_stack_trace(WINDOW* info, FunctionID** call_stack, int stack_size, int line_num) {
  int rows, cols;
  getmaxyx(info, rows, cols);
  mvwaddnstr(info, line_num, 1, "Stack Trace: ", cols - 2);
  line_num++;
  for (int i = 0; i < stack_size && line_num < rows - 1; i++) {
    // Go through each layer on the stack
    char buffer[MAX_LINE_SIZE];
    strcpy(buffer, call_stack[i]->class_name);
    strcat(buffer, ":");
    strcat(buffer, call_stack[i]->func_name);
    if (i == stack_size - 1) {
      wattron(info, COLOR_PAIR(CONTROL_KEY));
    }
    for (int j = 0; j < (strlen(buffer) / (cols - 2)) + 1; j++) {
      // Print through each row in the info window
      mvwaddnstr(info, line_num, 1, buffer + (j * (cols - 2)), cols - 2);
      line_num++;
    }
    if (i == stack_size - 1) {
      wattroff(info, COLOR_PAIR(CONTROL_KEY));
    }
  }
  return line_num + 1;
}

int display_method_info(WINDOW* win, DebugState* state, int line_num) {
  int rows, cols;
  getmaxyx(win, rows, cols);
  // Output arguments
  char** arguments = state->arguments_stack[state->stack_size - 1];
  int num_arguments = state->num_arguments_stack[state->stack_size - 1];
  mvwaddnstr(win, line_num, 1, "Arguments: ", cols - 2);
  line_num++;
  char buffer[MAX_LINE_SIZE];
  buffer[0] = '\0';
  for (int i = 0; i < num_arguments; i++) {
    strcat(buffer, " ");
    strcat(buffer, arguments[i]);
  }
  for (int j = 0; j < (strlen(buffer) / (cols - 2)) + 1; j++) {
    mvwaddnstr(win, line_num, 1, buffer + (j * (cols - 2)), cols - 2);
    line_num++;
  }
  // Output return
  char* ret_string = state->returns_stack[state->stack_size - 1];
  if (ret_string != NULL) {
    if (strcmp(ret_string, "Exception ")) {
      strcpy(buffer, "Return: ");
      strcat(buffer, ret_string);
    } else {
      strcpy(buffer, "Exception thrown");
    }
  } else {
    strcpy(buffer, "No return value");
  }
  mvwaddnstr(win, line_num, 1, buffer, cols - 2);
  return line_num + 1;
}

// Creates string, returns allocated memory, must free when using
char* collect_input(WINDOW* win) {
  // Change ncurses timeout to infinite
  wtimeout(win, -1);
  keypad(win, true);
  int y, x;
  char buffer[MAX_INPUT_SIZE];
  int num_chars = 0;
  int c = wgetch(win);
  // Collect input into buffer, exiting on newline input
  while (c != '\n') {
    if (c == KEY_BACKSPACE) {
      if (num_chars > 0) {
        num_chars--;
        getyx(win, y, x);
        wmove(win, y, x - 1);
        waddch(win, ' ');
        wmove(win, y, x - 1);
      }
    } else {
      buffer[num_chars] = c;
      waddch(win, c);
      num_chars++;
    }
    c = wgetch(win);
  }
  buffer[num_chars] = '\0';
  char* string = (char*) malloc((num_chars + 1) * sizeof(char));
  strcpy(string, buffer);
  return string;
}

// Creates window, displays prompt, and collects input from user in new window
char* query_user(char* prompt) {
  int rows, cols;
  getmaxyx(stdscr, rows, cols);
  WINDOW* win = newwin(3, cols / 2, 0, 0);
  box(win, 0, 0);
  wmove(win, 1, 1);
  waddstr(win, prompt);
  waddstr(win, ": ");
  return collect_input(win);
}

void function_search_move(FunctionID** ids, char** trace, DebugState* state, int reverse) {
  // Maintains previous search with static pointer so the default search mechanism can be used
  static char* fsearch = NULL;
  char buffer[MAX_LINE_SIZE];
  if (!reverse) {
    strcpy(buffer, "Search forward");
  } else {
    strcpy(buffer, "Search backward");
  }
  if (fsearch != NULL) {
    strcat(buffer, " (default: ");
    strcat(buffer, fsearch);
    strcat(buffer, ")");
  }
  char* new_message = query_user(buffer);
  // Process user input
  if (strcmp(new_message, "")) {
    if (fsearch != NULL) {
      free(fsearch);
    }
    fsearch = new_message;
  } else {
    free(new_message);
    if (fsearch == NULL) {
      return;
    }
  }
  if (!reverse) {
    move_to_function(ids, trace, state, fsearch, 0);
  } else {
    move_to_function(ids, trace, state, fsearch, 1);
  }
}

int get_stack_size_offset(const TraceElem** trace, int start_index, int end_index) {
  int stack_size = 0;
  int min_stack_size = 0;
  for (int i = start_index; i <= end_index; i++) {
    if (trace[i]->type == RETURN) {
      stack_size--;
    } else if (trace[i]->type == CALL) {
      stack_size++;
    }
    if (stack_size < min_stack_size) {
      min_stack_size = stack_size;
    }
  }
  if (min_stack_size < 0) {
    return -1 * min_stack_size;
  } else {
    return 0;
  }
}

// TODO: clean up, standardize output
void show_call_list(WINDOW* win, FunctionID** ids, const TraceElem** trace, DebugState* state, int row) {
  char buffer[MAX_LINE_SIZE];
  char class_buffer[MAX_LINE_SIZE];
  int rows, cols;
  int start_index, end_index;
  getmaxyx(win, rows, cols);
  int num_previous = 0, num_next = 0;
  int temp_index = state->trace_index - 1;
  while (temp_index > -1 && num_previous < (rows - row) / 2 - 2) {
    if (trace[temp_index]->type != BLOCK) {
      num_previous++;
    }
    temp_index--;
  }
  start_index = temp_index + 1;
  temp_index = state->trace_index + 1;
  while (temp_index < state->trace_length && num_next < (rows - row) - num_previous) {
    if (trace[temp_index]->type != BLOCK) {
      num_next++;
    }
    temp_index++;
  }
  end_index = temp_index - 1;
  mvwprintw(win, row, 1, "Execution Trace:");
  row++;
  int stack_size = get_stack_size_offset(trace, start_index, end_index);
  for (int i = start_index; i <= end_index; i++) {
    wmove(win, row, 1);
    for (int i = 0; i < stack_size && i < cols - 2; i++) {
      waddch(win, '-');
    }
    if (i == state->trace_index) {
      wattron(win, COLOR_PAIR(CURRENT_LINE));
      wattron(win, A_BOLD);
      waddnstr(win, "current", cols - 2 - stack_size);
      wattroff(win, COLOR_PAIR(CURRENT_LINE));
      wattroff(win, A_BOLD);
    } else if (trace[i]->type == BLOCK) {
      continue;
    } else if (trace[i]->type == RETURN) {
      strcpy(class_buffer, ids[trace[i]->fid - 1]->class_name);
      char* class_name = get_class_name(class_buffer);
      strcat(class_name, ":");
      strcat(class_name, ids[trace[i]->fid - 1]->func_name);
      if (trace[i]->ret != NULL && !strcmp(trace[i]->ret, "Exception ")) {
        strcpy(buffer, "exception thrown from ");
      } else {
        strcpy(buffer, "return from ");
      }
      strcat(buffer, class_name);
      waddnstr(win, buffer, cols - 2 - stack_size);
      stack_size--;
    } else if (trace[i]->type == CALL) {
      strcpy(class_buffer, ids[trace[i]->fid - 1]->class_name);
      char* class_name = get_class_name(class_buffer);
      strcat(class_name, ":");
      strcat(class_name, ids[trace[i]->fid - 1]->func_name);
      strcpy(buffer, "-call to ");
      strcat(buffer, class_name);
      waddnstr(win, buffer, cols - 2 - stack_size);
      stack_size++;
    } else if (trace[i]->type == VERSION) {
      wattron(win, COLOR_PAIR(CONTROL_KEY));
      strcpy(buffer, trace[i]->ret);
      waddnstr(win, buffer, cols - 2 - stack_size);
      wattroff(win, COLOR_PAIR(CONTROL_KEY));
    } else if (trace[i]->type == DELTA) {
      wattron(win, COLOR_PAIR(TYPE_KEY));
      strcpy(buffer, trace[i]->ret);
      waddnstr(win, buffer, cols - 2 - stack_size);
      wattroff(win, COLOR_PAIR(TYPE_KEY));
    } else{
      endwin();
      printf("Unexpected occurrence in trace\n");
      exit(EXIT_FAILURE);
    }
    row++;
  }
}

// Creates a new window and displays controls in it
void show_help_window() {
  int rows, cols;
  getmaxyx(stdscr, rows, cols);
  WINDOW* win = newwin(3 * rows / 4, 3 * cols / 4, rows / 8, cols / 8);
  mvwprintw(win, 1, 1, "Ctl+n = step forward | Ctl+p = step backward | space = pause | Ctl+y = reverse | Ctl+f = step over forward | Ctl+b = step over backward");
  mvwprintw(win, 3, 1, "Alt+f = step into next function | Alt+b = step into previous function | Ctl+e = step out forward | Ctl+a = step out backward");
  mvwprintw(win, 5, 1, "Ctl+s = search for function forward | Ctl+r = search for function backward | Alt+Shift+> = speed up | Alt+Shift+< = slow down");
  mvwprintw(win, 7, 1, "d = go to next divergence point | r = go to previous divergence point");
  mvwprintw(win, 9, 1, "Press any key to return");
  box(win, 0, 0);
  // Set ncurses character read timeout to infinity
  wtimeout(win, -1);
  keypad(win, true);
  int c = wgetch(win);
}

int main(int argc, char** argv) {
  if (argc != 2) {
    fprintf(stderr, "Error: expects path to .vedebug folder as argument\n");
    exit(EXIT_FAILURE);
  }
  char file_buffer[PATH_BUFFER_SIZE];

  // Get trace information
  strcpy(file_buffer, argv[1]);
  concat_paths(file_buffer, "trace.txt");
  int trace_length;
  TraceElem** trace;
  get_trace(file_buffer, &trace_length, &trace);

  // Get method information
  strcpy(file_buffer, argv[1]);
  concat_paths(file_buffer, "CompletedMethodIDs.txt");
  int num_ids;
  FunctionID** ids;
  get_function_ids(file_buffer, &num_ids, &ids);

  // ncurses initalization for the screen, input, and colors
  initscr();
  raw();
  noecho();
  start_color();
  init_pair(CURRENT_LINE, COLOR_GREEN, COLOR_BLACK);
  init_pair(CONTROL_KEY, COLOR_YELLOW, COLOR_BLACK);
  init_pair(TYPE_KEY, COLOR_CYAN, COLOR_BLACK);
  init_pair(INHERITENCE_KEY, COLOR_RED, COLOR_BLACK);
  MEVENT event;
  int c = 0;

  // Initalize windows
  WINDOW* video = newwin(0,0,0,0);
  WINDOW* info = newwin(0,0,0,0);
  WINDOW* output = newwin(0,0,0,0);

  // Initalize debugger state
  DebugState state = {trace_length, 0, sum_lines(trace_length, trace), -1, -1, -1, -1, \
      (FunctionID**) malloc(MAX_CALL_STACK_SIZE * sizeof(FunctionID*)), \
      (char***) malloc(MAX_CALL_STACK_SIZE * sizeof(char**)), \
      (int*) malloc(MAX_CALL_STACK_SIZE * sizeof(int)), \
      (char**) calloc(MAX_CALL_STACK_SIZE, sizeof(char*)), 0};
  move_video(ids, trace, &state, 1);

  // Control variables
  int pause = 1;
  int reverse = 0;
  int refresh = 1;
  int wait = 400; // milliseconds

  while (1) {
    // Output current state
    resize_windows(&video, &info, &output, &refresh);
    if (refresh) {
      werase(video); werase(info); werase(output);
      if (state.stack_size < 1) {
        fprintf(stderr, "Error: call stack underflow\n");
        exit(EXIT_FAILURE);
      } else if (state.stack_size > MAX_CALL_STACK_SIZE) {
        fprintf(stderr, "Error: call stack overflow\n");
        exit(EXIT_FAILURE);
      }

      // Display code and progress bar on video window
      strcpy(file_buffer, argv[1]);
      concat_paths(file_buffer, state.call_stack[state.stack_size - 1]->path);
      show_file(video, state.current_line_number, file_buffer);
      display_progress(video, state.debug_index, state.max_debug_index - 1);

      // Display stack and call list/script on info window
      int info_rows, info_cols;
      getmaxyx(info, info_rows, info_cols);
      int info_line = display_stack_trace(info, state.call_stack, state.stack_size, 1);
      if (info_line > info_rows / 2) {
        show_call_list(info, ids, trace, &state, info_line);
      } else {
        show_call_list(info, ids, trace, &state, info_rows / 2);
      }

      display_method_info(output, &state, 1);
      box(video,0,0); box(info,0,0); box(output,0,0);
      // TODO Standardize output in some smart way
      mvwprintw(output, 5, 1, "Index: %d/%d | ", state.debug_index, state.max_debug_index - 1);
      wprintw(output, "Speed: %f | ", 1 / (((float) wait) / 1000));
      if (pause) { wprintw(output, "Paused | "); }
      if (reverse) { wprintw(output, "Reverse Mode |"); }
      mvwprintw(output, 6, 1, "Press Ctl-h for help");
      // mvwprintw(output, 7, 1, "Trace Index: %d/%d", state.trace_index, state.trace_length - 1);
      // mvwprintw(output, 7, 1, "KEY NAME : %s - %d", keyname(c),c);
      wrefresh(video); wrefresh(info); wrefresh(output);
    }

    // Handle key presses/go to next state
    wtimeout(video, wait);
    c = wgetch(video);
    refresh = 1;
    switch (c) {
      case KEY_MOUSE:
        if (getmouse(&event) == OK) {
          // handle_mouse(&video, event, &index, trace_length);
        }
        break;
      case 'r':
        move_to_divergence_point(ids, trace, &state, 1);
        break;
      case 'd':
        move_to_divergence_point(ids, trace, &state, 0);
        break;
      case 8:
        // Ctl-h
        show_help_window();
        break;
      case 16:
        // Ctl-p
        move_video(ids, trace, &state, -1);
        break;
      case 14:
        // Ctl-n
        move_video(ids, trace, &state, 1);
        break;
      case 2:
        // Ctl-b
        locked_stack_move(ids, trace, &state, -1);
        break;
      case 6:
        // Ctl-f
        locked_stack_move(ids, trace, &state, 1);
        break;
      case 1:
        // Ctl-e
        step_out(ids, trace, &state, 1);
        break;
      case 5:
        // Ctl-a
        step_out(ids, trace, &state, 0);
        break;
      case ' ':
        // Space
        pause = !pause;
        break;
      case 25:
        // Ctl-y
        reverse = !reverse;
        break;
      case 19:
        // Ctl-s
        function_search_move(ids, trace, &state, 0);
        break;
      case 18:
        // Ctl-r
        function_search_move(ids, trace, &state, 1);
        break;
      case 27:
        // Escape sequence (also alt)
        wtimeout(video, 100);
        c = wgetch(video);
        switch (c) {
          case '>':
            if (wait > 25) { wait /= 2; }
            break;
          case '<':
            if (wait < 256000) { wait *= 2; }
            break;
          case 'f':
            step_into(ids, trace, &state, 0);
            break;
          case 'b':
            step_into(ids, trace, &state, 1);
            break;
        }
        break;
      case -1:
        // Timeout case
        if (!reverse) {
          if (!pause) {
            move_video(ids, trace, &state, 1);
          } else {
            refresh = 0;
          }
        } else {
          if (!pause) {
            move_video(ids, trace, &state, -1);
          } else {
            refresh = 0;
          }
        }
        break;
    }
    // Control-z, Control-c
    if (c == 26 || c == 3) {
      break;
    }
  }
  delwin(video); delwin(info); delwin(output);
  endwin();

  // To keep valgrind from bugging me
  // TODO: move somewhere else
  free(state.call_stack);
  free(state.arguments_stack);
  free(state.num_arguments_stack);
  free(state.returns_stack);
  free_trace(&trace_length, &trace);
  free_function_ids(&num_ids, &ids);
  return 0;
}
