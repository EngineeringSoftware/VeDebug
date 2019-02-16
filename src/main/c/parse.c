#include "parse.h"

#define MAX_LINE_SIZE 1000
#define MAX_NUM_ARGS 500

/*
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
// Checks if all the chars in a string are digits
int is_int(const char* string) {
  for (int i = 0; *(string + i); i++) {
    if (*(string + i) < '0' || *(string + i) > '9') {
      return 0;
    }
  }
  return 1;
}

// This function destroys the old string in the same way strtok does
// be sure to buffer
char* get_class_name(char* input) {
  char* token = strtok(input, "/$");
  char* prev = token;
  while (token != NULL) {
    prev = token;
    token = strtok(NULL, "/$");
  }
  return prev;
}

void parse_file(char* filename, int* num_lines, char*** lines) {
  FILE* fp;
  fp = fopen(filename, "r");
  char line[MAX_LINE_SIZE];

  if (fp == NULL) {
    fprintf(stderr, "Unable to open file at %s\n", filename);
    exit(EXIT_FAILURE);
  }

  // Get length of the file in lines
  int count = 0;
  while (fgets(line, MAX_LINE_SIZE, fp) != NULL) {
    count++;
  }
  *num_lines = count;

  // Allocate array for lines
  char** array = (char**) malloc(count * sizeof(char*));
  fseek(fp, 0, SEEK_SET);
  for (int i = 0; i < count; i++) {
    // Populate array
    fgets(line, MAX_LINE_SIZE, fp);
    int length = 0;
    while (line[length] && line[length] != '\n') {
      length++;
    }
    array[i] = (char*) malloc((length + 1) * sizeof(char));
    for (int j = 0; j < length; j++) {
      array[i][j] = line[j];
    }
    array[i][length] = '\0';
  }
  *lines = array;
  fclose(fp);
  return;
}

void free_file(int num_lines, char*** lines) {
  for (int i = 0; i < num_lines; i++) {
    free((*lines)[i]);
  }
  free(*lines);
  return;
}

void concat_paths(char* base, const char* extension) {
  int i = 0;
  while (base[i]) {
    if (base[i] == '/' && base[i + 1] == '\0') {
      base[i] = '\0';
      break;
    } else { i++; }
  }
  if (extension[0] != '/') {
    strcat(base, "/");
  }
  strcat(base, extension);
}

void get_function_ids(char* filename, int* num_lines, FunctionID*** ids) {
  char** id_strings;
  char token_buffer[MAX_LINE_SIZE];
  parse_file(filename, num_lines, &id_strings);
  *ids = (FunctionID**) malloc(*num_lines * sizeof(FunctionID*));
  for (int i = 0; i < *num_lines; i++) {
    strcpy(token_buffer, id_strings[i]);
    (*ids)[i] = (FunctionID*) malloc(sizeof(FunctionID));
    FunctionID* current = (*ids)[i];

    char* token = strtok(token_buffer, " \t\n");
    // First token is the id number, encoded as line number in this structure

    token = strtok(NULL, " \t\n");
    // Second token is start line number
    if (!strcmp(token, "null")) { current->start_line = -1; }
    else { current->start_line = atoi(token); }

    token = strtok(NULL, " \t\n");
    // Third token is end line number
    current->end_line = atoi(token);

    token = strtok(NULL, " \t\n");
    // Fourth token is the path
    current->path = (char*) malloc((strlen(token) + 1) * sizeof(char));
    strcpy(current->path, token);

    token = strtok(NULL, " \t\n");
    // Fifth token is the class name
    current->class_name = (char*) malloc((strlen(token) + 1) * sizeof(char));
    strcpy(current->class_name, token);

    token = strtok(NULL, " \t\n");
    // Sixth token is the function name
    current->func_name = (char*) malloc((strlen(token) + 1) * sizeof(char));
    strcpy(current->func_name, token);

    token = strtok(NULL, " \t\n");
    // Seventh token is the types of the arguments
    current->arg_types= (char*) malloc((strlen(token) + 1) * sizeof(char));
    strcpy(current->arg_types, token);

    token = strtok(NULL, " \t\n");
    // Eigth token is the type of the return
    current->return_type= (char*) malloc((strlen(token) + 1) * sizeof(char));
    strcpy(current->return_type, token);
  }
  free_file(*num_lines, &id_strings);
}

void get_trace(char* filename, int* num_lines, TraceElem*** trace) {
  char** trace_strings;
  char token_buffer[MAX_LINE_SIZE];
  char* temp[MAX_NUM_ARGS];
  parse_file(filename, num_lines, &trace_strings);
  // Allocate array for trace
  *trace = (TraceElem**) malloc(*num_lines * sizeof(TraceElem*));
  for (int i = 0; i < *num_lines; i++) {
    strcpy(token_buffer, trace_strings[i]);
    (*trace)[i] = (TraceElem*) malloc(sizeof(TraceElem));
    TraceElem* current = (*trace)[i];
    char* token = strtok(token_buffer, " \t\n");
    if (token[0] == '[') {
      // Store information for block
      current->type = BLOCK;
      strcpy(token_buffer, trace_strings[i]);
      current->start_line = atoi(strtok(token_buffer, " [,]\n"));
      current->end_line = atoi(strtok(NULL, " [,]\n"));
    } else if (token[0] == '-' || is_int(token)) {
      // Similar data stored for function calls and returns so they are groupted together
      if (is_int(token)) {
        // Function call
        current->type = CALL;
        current->fid = atoi(token);
      } else {
        // Function return
        current->type = RETURN;
        current->fid = atoi(strtok(NULL, " \t\n"));
      }
      int num_args = 0;
      token = strtok(NULL, " \t\n");
      current->ret = NULL;
      // Read all tokens for arguments, storing into buffer while counting number as well
      while (token != NULL) {
        if (!strcmp(token, "|")) {
          current->ret = (char*) malloc((strlen(token + strlen("|") + 1) + 1) * sizeof(char));
          strcpy(current->ret, token + strlen("|") + 1);
          break;
        }
        temp[num_args] = (char*) malloc((strlen(token) + 1) * sizeof(char));
        strcpy(temp[num_args], token);
        num_args++;
        token = strtok(NULL, " \t\n");
      }
      current->num_args = num_args;
      current->args = (char**) malloc(num_args * sizeof(char*));
      // Move strings from buffer into new args list
      for (int i = 0; i < num_args; i++) {
        current->args[i] = temp[i];
      }
    } else if (token[0] == '*') {
      // Change in version information, store in return data location
      current->type = VERSION;
      current->ret = (char*) malloc((strlen(token + strlen("*") + 1) + 1) * sizeof(char));
      strcpy(current->ret, token + strlen("*") + 1);
    } else if (token[0] == '^') {
      // Delta between current and previous versions
      current->type = DELTA;
      current->ret = (char*) malloc((strlen(token + strlen("^") + 1) + 1) * sizeof(char));
      strcpy(current->ret, token + strlen("*") + 1);
    } else {
      printf("Unexpected Occurrance in trace\n");
      exit(EXIT_FAILURE);
    }
  }
  free_file(*num_lines, &trace_strings);
}

void free_trace(int* num_lines, TraceElem*** trace) {
  for (int i = 0; i < *num_lines; i++) {
    TraceElem* current = (*trace)[i];
    if (current->type == CALL || current->type == RETURN) {
      for (int j = 0; j < current->num_args; j++) {
        free(current->args[j]);
      }
      free(current->args);
      if (current->ret != NULL) {
        free(current->ret);
      }
    } else if (current->type == VERSION) {
      free(current->ret);
    }
    free(current);
  }
  free(*trace);
}

void free_function_ids(int* num_lines, FunctionID*** ids) {
  for (int i = 0; i < *num_lines; i++) {
    FunctionID* current = (*ids)[i];
    free(current->path);
    free(current->class_name);
    free(current->func_name);
    free(current->arg_types);
    free(current->return_type);
    free(current);
  }
  free(*ids);
}
