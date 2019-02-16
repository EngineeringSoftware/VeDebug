#!/usr/bin/env python

import sys
import os
import re
import argparse

import parse
import process

__author__ = "Ben Buhse, Thomas Wei, and Zhiqiang Zhang"
__maintainer__ = "Thomas Wei"
__email__ = "thomasw219@gmail.com"
__status__ = "Development"

PRINT = False
PRINT_DIVERGENCE = True

def end_of_bb(method_info, beginning_of_bb):
    divisions = method_info[4]
    if beginning_of_bb == -1:
        return - 1
    index = divisions.index(beginning_of_bb)
    if index + 1 >= len(divisions) - 1:
        return divisions[-1]
    elif divisions[index + 1] > beginning_of_bb:
        return divisions[index + 1] - 1
    else:
        return beginning_of_bb

def is_int(s):
    try:
        int(s)
        return True
    except ValueError:
        return False

def write_intervals(output_file, intervals):
    for interval in intervals:
        output_file.write("[" + str(interval[0]) + "," + str(interval[1]) + "]" + "\n")

def complete_trace(output_file, calls_file, ids):
    INV = 0
    ID = 1

    INFO = 0
    LINES = 1
    ARGS = 2
    FID = 3

    NAME = 0
    PATH = 1
    START = 2
    END = 3
    BOXES = 4
    CLASS = 5
    OTHER = 6

    stack = []
    lines = calls_file.readlines()
    for index, line in enumerate(lines):
        tokens = [token for token in re.split(" |\n|\t", line) if token != ""]
        # Method call case
        if is_int(tokens[0]):
            info = ids[int(tokens[ID]) - 1]
            invocation_line = int(tokens[INV])
            if info[PATH] == "null":
                continue
            if PRINT:
                print "Adding Method: " + str(info)
            # Encoded as (method_info, [start of bb, end of bb, start character],
            # arguments, fid)
            if info[START] == -1:
                end = -1
            else:
                end = end_of_bb(info, info[BOXES][0])
            stack.append((info, [info[BOXES][0], end, 0], \
                        tokens[ID + 1:], int(tokens[ID])))
            output_string = ""
            for token in tokens[ID:]:
                output_string += token + " "
            output_string += "\n"
            # Static initialization special case (implicitly defined)
            if info[0] == "<clinit>":
                if invocation_line == "-1":
                    info = (info[NAME], info[PATH], -1, info[END], info[BOXES], info[CLASS], info[OTHER])
                    output_string = tokens[0] + "\n"
                    del stack[-1][ARGS][:]
            # Empty stack or implicit definition case
            if len(stack) == 1 or stack[-2][INFO][START] == -1:
                output_file.write(output_string)
                continue
            caller = stack[-2]
            begin_line = caller[LINES][0]
            old_end_line = caller[LINES][1]
            if invocation_line != -1:
                end_line = invocation_line
            else:
                end_line = begin_line
            caller[LINES][0] = end_line
            caller[LINES][2] = 0
            if begin_line <= end_line:
                intervals = [(begin_line, end_line)]
            else:
                intervals = [(end_line, end_line)]
            try:
                process.process_lines(intervals, stack[-2][INFO][PATH])
            except IndexError:
                print "Index out of bounds exception, line number out of file"
                print "Line: " + str(line)
                print "File: " + str(stack[-2][INFO][PATH])
                print "Function invoked: " + str(info)
                print "Index: " + str(index)
                print "Interval: [" + str(begin_line) + "," + str(old_end_line) + "]"
                sys.exit()
        # Return case
        elif line[0] == "-":
            if ids[int(tokens[1]) - 1][1] == "null":
                continue
            # Implicit definition
            output_string = line
            if stack[-1][INFO][START] == -1:
                output_file.write(output_string)
                stack.pop()
                continue
            intervals = [(stack[-1][LINES][0], stack[-1][LINES][1])]
            process.process_lines(intervals, stack[-1][INFO][PATH])
            stack.pop()
        # Change of basic block case
        elif line[0] == '@':
            tokens = [token for token in re.split(" |\n|\t|@|:", line) if token != ""]
            interval = stack[-1][LINES]
            # No source file case
            if ids[int(tokens[0]) - 1][PATH] == "null":
                continue
            # Normal change of basic block
            if int(tokens[0]) == stack[-1][FID]:
                intervals = [(interval[0], interval[1])]
                process.process_lines(intervals, stack[-1][INFO][PATH])
                write_intervals(output_file, intervals)
            # Exception handling
            while int(tokens[0]) != stack[-1][FID]:
                intervals = [(stack[-1][LINES][0], stack[-1][LINES][0])]
                write_intervals(output_file, intervals)
                output_string = "- " + str(stack[-1][FID]) + " "
                output_string += "Exception" + "\n"
                output_file.write(output_string)
                stack.pop()
            interval = stack[-1][LINES]
            interval[0] = int(tokens[1])
            if stack[-1][INFO][PATH] != "null":
                interval[1] = end_of_bb(stack[-1][INFO], interval[0])
            if PRINT:
                print str(stack)
            continue
        else:
            print "Unexpected occurrence in trace"
            sys.exit()
        write_intervals(output_file, intervals)
        output_file.write(output_string)
        if PRINT:
            print str(stack)

    # Handle an execution trace which ends up throwing an exception
    while len(stack) > 0:
        intervals = [(stack[-1][LINES][0], stack[-1][LINES][0])]
        write_intervals(output_file, intervals)
        output_file.write("- " + str(stack[-1][FID]) + " Exception\n")
        stack.pop()

def get_return_val(lines, index):
    stack_size = 1
    try:
        while stack_size > 0:
            index += 1
            tokens = [token for token in re.split(" |\n|\t", lines[index]) if token != ""]
            if is_int(tokens[0]):
                stack_size += 1
            elif tokens[0] == "-":
                stack_size -= 1
        if len(tokens) > 2:
            return_string = ""
            for token_index, token in enumerate(tokens):
                if token_index > 1:
                    return_string += token + " "
            return return_string
        else:
            return ""
    except IndexError:
        return ""

def extrapolate_returns(output_file, input_file, ids):
    lines = input_file.readlines()
    stack = []
    for index, line in enumerate(lines):
        tokens = [token for token in re.split(" |\n|\t", line) if token != ""]
        if not is_int(tokens[0]) and line[0] != '-':
            output_string = line
        elif is_int(tokens[0]):
            val = get_return_val(lines, index)
            if val is not "":
                output_string = ""
                for token in tokens:
                    output_string += token + " "
                output_string += "| " + val + '\n'
            else:
                output_string = line
            stack.append(tokens[1:])
        else:
            output_string = tokens[0] + " " + tokens[1] + " "
            for arg in stack[-1]:
                output_string += arg + " "
            if len(tokens) > 2:
                output_string += "| "
                for token in tokens[2:]:
                    output_string += token + " "
            output_string += "\n"
            stack.pop()
        output_file.write(output_string)

def find_divergence_point(output, index, offset, lines, ids, stack, past_lines, past_ids, past_stack, verbose):
    if PRINT_DIVERGENCE:
        print "Searching for Divergence Point"
    while index < len(lines):
        if index + offset >= len(past_lines):
            output.write("* Continuing past previous execution\n")
            print "Continuing past previous execution"
            break
        line = lines[index]
        past_line = past_lines[index + offset]
        tokens = [token for token in re.split(" |\n|\t", line) if token != ""]
        past_tokens = [token for token in re.split(" |\n|\t", past_line) if token != ""]
        if is_int(tokens[0]):
            # Method Call case
            if not is_int(past_tokens[0]):
                if PRINT_DIVERGENCE:
                    print line + " at " + str(index)
                    print lines[index + 1]
                    print past_line + " at " + str(index + offset)
                    print past_lines[index + offset + 1]
                    print "Difference in method call order"
                output.write("* Difference in method call order\n")
                break
            current_method = ids[int(tokens[0]) - 1]
            past_method = past_ids[int(past_tokens[0]) - 1]
            compare_info = (current_method[0], current_method[5], current_method[6])
            past_compare_info = (past_method[0], past_method[5], past_method[6])
            if compare_info != past_compare_info:
                if PRINT_DIVERGENCE:
                    print "Difference in method call"
                    print str(compare_info)
                    print str(past_compare_info)
                output.write("* Difference in method call\n")
                break
            if verbose and (tokens[1:] != past_tokens[1:]):
                output.write("^ Arguments were previously: ")
                for token in past_tokens[1:]:
                    output.write(token + " ")
                output.write("\n")
            stack.append((current_method, current_method[2]))
            past_stack.append((past_method, past_method[2]))
        elif tokens[0] == "-":
            # Return case
            if past_tokens[0] != "-":
                if PRINT_DIVERGENCE:
                    print "Difference in return order"
                output.write("* Difference in return order\n")
                break
            if verbose and (tokens[2:] != past_tokens[2:]):
                output.write("^ Return value was previously: ")
                for token in past_tokens[2:]:
                    output.write(token + " ")
                output.write("\n")
            stack.pop()
            past_stack.pop()
        elif line[0] == '[':
            # Basic Block case
            if past_line[0] != '[':
                if PRINT_DIVERGENCE:
                    print  line + " at " + str(index) + " and " +  past_line + " at " + str(index + offset)
                    print "Difference in basic block executions"
                output.write("* Difference in basic block executions\n")
                break
            line_nums = [token for token in re.split(" |\n|\t|\[|,|\]", line) if token != ""]
            past_line_nums = [token for token in re.split(" |\n|\t|\[|,|\]", past_line) if token != ""]
            block = process.get_lines(open(stack[-1][0][1]).readlines(), int(line_nums[0]), int(line_nums[1]))
            past_block = process.get_lines(open(past_stack[-1][0][1]).readlines(), int(past_line_nums[0]), int(past_line_nums[1]))
            previous = int(line_nums[0])
            diverge = False
            for elem in block:
                try:
                    if past_block.index(elem) == 0:
                        del past_block[0]
                        previous += 1
                    else:
                        diverge = True
                        break
                except ValueError, e:
                    diverge = True
                    break
            if diverge:
                if PRINT_DIVERGENCE:
                    print str(block) + " at " + str(index) + " and " + str(past_block) + " at " + str(index + offset)
                    print "Difference in basic block content"
                output.write("[" + line_nums[0] + "," + str(previous) + "]\n")
                output.write("* Difference in basic block content\n")
                lines[index] = "[" + str(previous) + "," + str(previous) + "]\n"
                lines.insert(index + 1, "[" + str(previous + 1) + "," + str(line_nums[1]) + "]\n")
                break
        output.write(line)
        index += 1
    return (index, offset)

def get_rebase_candidate_array(index, past_lines, past_ids, past_stack):
    initial_stack_size = len(past_stack)
    current_stack_size = initial_stack_size
    array = []
    while current_stack_size >= initial_stack_size and index < len(past_lines):
        line = past_lines[index]
        tokens = [token for token in re.split(" |\n|\t", line) if token != ""]
        if is_int(tokens[0]):
            current_stack_size += 1
        if current_stack_size == initial_stack_size:
            array.append((line, index))
        if line[0] == '-':
            current_stack_size -= 1
        index += 1
    return array

def find_reconvergence_point(output, index, offset, lines, ids, stack, past_lines, past_ids, past_stack):
    if PRINT_DIVERGENCE:
        print "Searching for Reconvergence Point"
    candidates = get_rebase_candidate_array(index + offset, past_lines, past_ids, past_stack)
    if index < len(lines) and index + offset < len(past_lines) and PRINT_DIVERGENCE:
        print "Line: " + lines[index] + " at " + str(index)
        print "Past line: " + past_lines[index + offset] + " at " + str(index + offset)
        print candidates
    start_index = index
    while index < len(lines):
        line = lines[index]
        tokens = [token for token in re.split(" |\n|\t", line) if token != ""]
        if is_int(tokens[0]):
            stack.append(ids[int(tokens[0]) - 1])
        if len(stack) < len(past_stack):
            break
        if len(stack) == len(past_stack):
            for candidate in candidates:
                past_line = candidate[0]
                past_tokens = [token for token in re.split(" |\n|\t", past_line) if token != ""]
                if is_int(tokens[0]) and is_int(past_tokens[0]):
                    current_method = ids[int(tokens[0]) - 1]
                    past_method = past_ids[int(past_tokens[0]) - 1]
                    compare_info = (current_method[0], current_method[5], current_method[6])
                    past_compare_info = (past_method[0], past_method[5], past_method[6])
                    if past_compare_info == compare_info:
                        if PRINT_DIVERGENCE:
                            print "Reconvergence point found, function call"
                        offset = candidate[1] - index
                        if index - 1 > -1 and lines[index - 1][0] == '[' and index + offset - 1 > -1 and past_lines[index - 1][0] == '[':
                            line_nums = [token for token in re.split(" |\n|\t|\[|,|\]", lines[index - 1]) if token != ""]
                            past_line_nums = [token for token in re.split(" |\n|\t|\[|,|\]", past_lines[index + offset - 1]) if token != ""]
                            stack[-1] = (stack[-1][0], int(line_nums[1]))
                            past_stack[-1] = (past_stack[-1][0], int(past_line_nums[1]))
                            print "Function call rebase"
                        output.write("* Reconvergence, like function call\n")
                        return (index, offset)
                elif line[0] == '-' and past_line[0] == '-' and not (tokens[-1] == "Exception" and past_tokens[-1] != "Exception" or tokens[-1] != "Exception" and past_tokens[-1] == "Exception"):
                    current_method = ids[int(tokens[1]) - 1]
                    past_method = past_ids[int(past_tokens[1]) - 1]
                    compare_info = (current_method[0], current_method[5], current_method[6])
                    past_compare_info = (past_method[0], past_method[5], past_method[6])
                    if past_compare_info == compare_info:
                        if PRINT_DIVERGENCE:
                            print "Reconvergence point found, return"
                        offset = candidate[1] - index
                        output.write("* Reconvergence, return\n")
                        return (index, offset)
                elif line[0] == '[' and past_line[0] == '[':
                    line_nums = [token for token in re.split(" |\n|\t|\[|,|\]", line) if token != ""]
                    past_line_nums = [token for token in re.split(" |\n|\t|\[|,|\]", past_line) if token != ""]
                    block = process.get_lines(open(stack[-1][0][1]).readlines(), int(line_nums[0]), int(line_nums[1]))
                    past_block = process.get_lines(open(past_stack[-1][0][1]).readlines(), int(past_line_nums[0]), int(past_line_nums[1]))
                    if block == past_block:
                        stack[-1] = (stack[-1][0], int(line_nums[0]))
                        past_stack[-1] = (past_stack[-1][0], int(past_line_nums[0]))
                        offset = candidate[1] - index
                        output.write("* Reconvergence, basic block match\n")
                        if PRINT_DIVERGENCE:
                            print "Reconvergence point found, basic block match"
                            print "Line nums: " + line + " in " + stack[-1][0][0]
                            print block
                            print "Trace location: " + str(index)
                            print "Reference trace location: " + str(index + offset)
                        return (index, offset)
        if line[0] == '-':
            stack.pop()
        output.write(line)
        index += 1
    while index < len(lines):
        output.write(lines[index])
        index += 1
    offset += len(lines) - start_index - 1
    return (index, offset)

def compare_traces(output, pretrace, ids, past_pretrace, past_ids, verbose):
    lines = pretrace.readlines()
    past_lines = past_pretrace.readlines()
    index = 0
    offset = 0 # From pretrace to past_pretrace
    # Encoded as name, class name, start line, other info (args, return, etc.), most recent reference point
    stack = []
    past_stack = []
    num_divergences = 0
    old_offset = 0
    total_difference = 0
    while index < len(lines):
        index, offset = find_divergence_point(output, index, offset, lines, ids, stack, past_lines, past_ids, past_stack, verbose)
        index, offset = find_reconvergence_point(output, index, offset, lines, ids, stack, past_lines, past_ids, past_stack)
        if offset != old_offset:
            total_difference += abs(old_offset - offset)
            num_divergences += 1
            old_offset = offset
    data = open("data.txt", "a")
    data.write(str(num_divergences) + "\n" + str(total_difference) + "\n")

def main():
    parser = argparse.ArgumentParser(description="Trace completion/comparison for the vedebug tool")
    parser.add_argument("-v", "--verbose", action="store_true", help="show more detailed output")
    parser.add_argument("trace_path", help="path to the the output of the trace")
    # Now we support multiple src roots split by colon.
    parser.add_argument("src_roots", help="paths to the root of the source code that was instrumented")
    parser.add_argument("comparison_trace_path", nargs="?", help="path to the previous trace to compare to")
    args = vars(parser.parse_args())
    try:
        calls = open(os.path.join(args['trace_path'], "MethodCalls.txt"), "r")
    except Exception, e:
        print "Unable to open trace file"
        sys.exit()
    try:
        ids = open(os.path.join(args['trace_path'], "MethodIDs.txt"), "r")
    except Exception, e:
        print "Unable to open method id file"
        sys.exit()
    pretrace_path = os.path.join(args['trace_path'], "pretrace.txt")
    pretrace = open(pretrace_path, "w+")

    lookup = parse.parse_ids(ids, args['src_roots'], args['trace_path'])
    ids.close()
    # Whether we change "args['src_roots']" here depends on whether the
    # argument is really used in the complete_trace function. For now,
    # the answer is no.
    complete_trace(pretrace, calls, lookup)
    calls.close()
    pretrace.close()
    pretrace_read = open(pretrace_path, "r")

    if args['comparison_trace_path'] != None:
        try:
            past_ids = open(os.path.join(args['comparison_trace_path'], "MethodIDs.txt"), "r")
        except Exception, e:
            print "Unable to open method id file"
            sys.exit()
        data = open("data.txt", "w")
        data.close()
        past_lookup = parse.parse_ids(past_ids, args['comparison_trace_path'], args['comparison_trace_path'])
        past_ids.close()
        past_pretrace_read = open(os.path.join(args['comparison_trace_path'], "pretrace.txt"), "r")
        processed_pretrace = open(os.path.join(args['trace_path'], "processed_pretrace.txt"), "w+")
        compare_traces(processed_pretrace, pretrace_read, lookup, past_pretrace_read, past_lookup, args['verbose'])
        past_pretrace_read.close()
        pretrace_read.close()
        processed_pretrace.close()
        pretrace_read = open(os.path.join(args['trace_path'], "processed_pretrace.txt"), "r")

    trace = open(os.path.join(args['trace_path'], "trace.txt"), "w+")
    extrapolate_returns(trace, pretrace_read, lookup)
    trace.close()

if __name__ == "__main__":
    main()
    print "[INFO] trace completion succeeded."
