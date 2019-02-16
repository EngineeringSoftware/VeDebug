#!/usr/bin/env python

import os
import shutil
import re
import sys

__author__ = 'Ben Buhse, Thomas Wei, Zhiqiang Zang'

def get_all_files(root):
    all_files = []
    for path, directories, files in os.walk(root):
        for f in files:
            all_files.append(os.path.join(path, f))
    return all_files

def complete_segmentation(method_info, data_root, file_path):
    if method_info[1] == "null":
        return
    try:
        file_name = os.path.splitext(file_path)[0].replace("/", "-") + "BB"
        bb_file = open(os.path.join(data_root, file_name), "r")
    except Exception, e:
        print "Unable to open basic block file " + os.path.join(data_root, file_name)
        sys.exit()
    start_line = method_info[4][0]
    end_line = method_info[4][1]
    divisions = method_info[4]
    for line in bb_file:
        if int(line) < start_line:
            continue
        elif int(line) > end_line:
            break
        divisions.append(int(line))
    divisions.remove(start_line)
    divisions.sort()

# Now we support multiple src_root paths split by colon.
def parse_ids(id_file, src_roots, data_root):
    METHOD_NAME = 5
    START_LINE = 1
    END_LINE = 2
    CLASS_NAME = 4
    FILE_PATH = 3
    output = open(os.path.join(data_root, "CompletedMethodIDs.txt"), "w+")

    # Items in "files" and those in "src_root_paths" correspond to
    # each other one by one.
    files = []
    src_root_paths = []
    for src_root_path in src_roots.split(":"):
        temp_files = get_all_files(src_root_path)
        files += temp_files
        src_root_paths += [src_root_path] * len(temp_files)

    lines = id_file.readlines()
    lookup = []
    for index, line in enumerate(lines):
        tokens = line.split(" ")
        found = False
        for f, src_root_path in zip(files, src_root_paths):
            if tokens[FILE_PATH] in f:
                found = True
                path_from_src = os.path.relpath(f, start=src_root_path)
                break
        rel_path = "null"
        if not found:
            print "Unable to find source file: " + tokens[FILE_PATH]
            path_from_src = "null"
            store_path = "null"
        else:
            rel_path = os.path.join("src", path_from_src)
            store_path = os.path.join(data_root, rel_path)
            if not os.path.exists(os.path.dirname(store_path)):
                try:
                    os.makedirs(os.path.dirname(store_path))
                except OSError as exc: # Guard against race condition
                    if exc.errno != errno.EEXIST:
                        raise
            if not os.path.exists(store_path):
                shutil.copyfile(os.path.join(src_root_path, path_from_src), store_path)
        # Encoded as name, path (from root), start line, end line, basic blocks, class name, other info (args, return, etc.)
        if tokens[START_LINE] == "null" or int(tokens[START_LINE]) > int(tokens[END_LINE]) or not found:
            tokens[START_LINE] = -1
        method_info = (tokens[METHOD_NAME], store_path, int(tokens[START_LINE]), \
                    int(tokens[END_LINE]), [int(tokens[START_LINE]), int(tokens[END_LINE])], tokens[CLASS_NAME], tokens[6:])
        complete_segmentation(method_info, data_root, tokens[FILE_PATH])
        lookup.append(method_info)
        for index, token in enumerate(tokens):
            if index == 3:
                output.write(rel_path + " ")
            elif index == len(tokens) - 1:
                output.write(str(token))
            else:
                output.write(str(token) + " ")
    return lookup
