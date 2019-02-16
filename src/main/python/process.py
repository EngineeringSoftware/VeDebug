#/usr/bin/env python

import re

__author__ = 'Ben Buhse, Thomas Wei, Zhiqiang Zang'

def replace_interval(string, interval):
    base = string[:interval[0]]
    for i in range(interval[1] - interval[0] + 1):
        base += "-"
    base += string[interval[1] + 1:]
    return base

def overwrite_comments(lines, start_index):
    in_multi = False
    current_char = start_index
    i = 0
    while i < len(lines):
        line = lines[i][current_char:]
        if not in_multi:
            single_index = line.find("//")
            multi_index = line.find("/*")
            if multi_index == -1 or (multi_index > single_index and single_index != -1):
                if single_index != -1:
                    lines[i] = replace_interval(lines[i], \
                            (single_index + current_char, len(lines[i]) - 1))
                i += 1
                current_char = 0
            elif single_index == -1 or single_index > multi_index:
                in_multi = True
                if multi_index + len("/*") == len(line):
                    i += 1
                    current_char = 0
                else:
                    current_char = multi_index + current_char + len("/*")
            else:
                print "Thomas you're a dummy"
                sys.exit()
        else:
            end_index = line.find("*/")
            if end_index == -1:
                if lines[i][:current_char].find("/*") == -1:
                    lines[i] = replace_interval(lines[i], (current_char, len(lines[i]) - 1))
                else:
                    lines[i] = replace_interval(lines[i], (current_char - 2, len(lines[i]) - 1))
                i += 1
                current_char = 0
            else:
                in_multi = False
                if lines[i][:current_char].find("/*") == -1:
                    lines[i] = replace_interval(lines[i], \
                            (current_char, end_index + current_char + 1))
                else:
                    lines[i] = replace_interval(lines[i], \
                            (current_char - 2, end_index + current_char + 1))
                if end_index + len("*/") == len(line):
                    i += 1
                    current_char = 0
                else:
                    current_char = end_index + current_char + len("*/")

def get_lines(file_lines, start_line, end_line):
    lines = []
    file_index = start_line - 1
    while file_index < end_line:
        lines.append(file_lines[file_index])
        file_index += 1
    return lines

def find_next_call(full_path, function_name, begin, end, term_char):
    begin_line = begin[0]
    begin_char = begin[1]
    end_line = end[0]
    end_char = end[1]
    file_lines = open(full_path, "r").readlines()
    lines = get_lines(file_lines, begin_line, end_line)
    overwrite_comments(lines, begin_char)

    for line_index, line in enumerate(lines):
        if line_index == len(lines) - 1 and end_char < len(line):
            line = line[:end_char]
        if line_index == 0:
            char_index = line[begin_char:].find(function_name + term_char)
            if char_index != -1:
                return (line_index + begin_line, char_index + begin_char + line[char_index:].find(term_char))
        else:
            char_index = line.find(function_name + term_char)
            if char_index != -1:
                return (line_index + begin_line, char_index + line[char_index:].find(term_char))
    return begin

ignore_chars = ['-', '/', ' ', '\n', '\t', '*', '\r']
def process_lines(intervals, path):
    begin_line = intervals[0][0]
    end_line =  intervals[0][1]
    file_lines = open(path, "r").readlines()
    lines = get_lines(file_lines, begin_line, end_line)
    overwrite_comments(lines, 0)

    del intervals[:]
    interval_begin = -1
    for index, line in enumerate(lines):
        ignore = True
        for char in line:
            if char not in ignore_chars:
                ignore = False
        if ignore and interval_begin != -1:
            intervals.append((interval_begin, begin_line + index - 1))
            interval_begin = -1
        elif not ignore and interval_begin == -1:
            interval_begin = begin_line + index
    if interval_begin != -1:
        intervals.append((interval_begin, end_line))

