--
-- Copyright Alexander Schütz, 2021
--
-- This file is part of LuajLFS.
--
-- LuajLFS is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Lesser General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- LuajLFS is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Lesser General Public License for more details.
--
-- A copy of the GNU Lesser General Public License should be provided
-- in the COPYING & COPYING.LESSER files in top level directory of LuajLFS.
-- If not, see <https://www.gnu.org/licenses/>.
--
local debug = require("debug")
local lfs = require("lfs")
local io = require("io")
local math = require("math")
local pathSeperator = nil

local randomTab = {}
local randomTabGot = {}
local i = 1
while i < 1000 do
    local r = math.random(1, 1000000)
    while randomTabGot[r] do
        r = math.random(1, 1000000)
    end


    randomTab[i] = r
    randomTabGot[r] = true
    i = i + 1
end

local function dropFile(path, content)
    local file = io.open(path, "w+")
    if not file then
         print("Failed to open file " .. path)
         os.exit(-1)
    end

    file:write(content)
    file:close()
end

local function readFile(path)
    local file = io.open(path, "r")
    if not file then
        return nil
    end

    local v = file:read("*a")
    file:close()
    return v
end

local function spamRandomFiles()
    for i, v in ipairs(randomTab) do
        dropFile(tostring(v) .. ".file", tostring(v))
    end
end

local function spamRandomDirectories()
    for i, v in ipairs(randomTab) do
        lfs.mkdir(tostring(v))
    end
end

local function assert(check, message)
    if not check then
        print("Assert failed!")
        print(message)
        io.flush()
        local t = debug.getinfo(2)
        if t and t.currentline then
            print("line: ", t.currentline)
        end
        os.exit(-1)
    end
end

os.remove("testfolder")
lfs.rmdir("testfolder")
os.remove("testfolderlink")

print("test currentdir & chdir")
local pwd = lfs.currentdir()

assert(type(pwd) == "string", "pwd not a string")
assert(lfs.chdir("..") == true, "chdir parent failed")

local parent = lfs.currentdir()
assert(type(parent) == "string", "parent pwd ~= string")

local myfolder = pwd:sub(#parent+1)
assert((parent .. myfolder) == pwd, "parent and pwd dont share same base path")

pathSeperator = myfolder:sub(1, 1)
myfolder = myfolder:sub(2)

assert(pathSeperator == "/" or pathSeperator == "\\", "path seperator not / or \\")

if pathSeperator == "/" then
    assert(pwd:sub(1, 1) == "/", "pwd does not start with /")
end

assert(pwd:sub(#pwd, 1) ~= pathSeperator, "pwd ends with path seperator")

assert(lfs.chdir(myfolder) == true, "chdir to pwd failed")
assert(lfs.currentdir() == pwd, "did not end up in pwd after going back to pwd from parent")
assert(lfs.chdir("this_hopefully_does_not_exist") == nil, "chdir to non existant directory succeeded")

print("test currentdir & chdir & mkdir")
assert(lfs.mkdir("testfolder") == true, "failed to create testfolder")
assert(type(lfs.attributes("testfolder")) == "table", "failed to stat testfolder")
assert(lfs.chdir("testfolder") == true, "chdir to testfolder failed")
local testfolder = pwd .. pathSeperator .. "testfolder"
assert(lfs.currentdir() == testfolder, "base path of testfolder and pwd does not match")

assert(lfs.chdir(parent) == true, "chdir to parent failed")
assert(lfs.currentdir() == parent, "not in parent dir")

assert(lfs.chdir(testfolder) == true, "chdir to testfolder failed")
assert(lfs.currentdir() == testfolder, "not in testfolder dir")

assert(lfs.chdir(".." .. pathSeperator .. "testfolder") == true, "chdir to self failed")
assert(lfs.currentdir() == testfolder, "not in testfolder dir")

assert(lfs.chdir(".." .. pathSeperator .. "testfolder" .. pathSeperator) == true, "chdir to self failed")
assert(lfs.currentdir() == testfolder, "not in testfolder dir")

assert(lfs.chdir(".") == true, "chdir to self failed")
assert(lfs.currentdir() == testfolder, "not in testfolder dir")

print("test lfs.dir via nextFunc and lfs.attributes for file+dir while iterating")
spamRandomFiles()
spamRandomDirectories()


local got = {}
local gotOrdered = {}
for file in lfs.dir(".") do
    got[file] = true
    gotOrdered[#gotOrdered+1] = file
end

assert(got["."] == true, "lfs.dir missing . dir")
assert(got[".."] == true, "lfs.dir missing .. dir")
-- +2 for . and ..
assert(#gotOrdered == (#randomTab * 2) + 2, "file count mismatch")
for i,v in ipairs(randomTab) do
    local base = tostring(v)
    local file = base .. ".file"
    assert(got[file], "file " .. file .. " is missing")
    assert(got[base], "dir " .. base .. " is missing")

    local attrDir, attrDirErr = lfs.attributes(base)
    if not attrDir then
        print(attrDirErr)
    end
    assert(type(attrDir) == "table", "stat on dir " .. base .. " failed")

    local attrFile = lfs.attributes(file)
    assert(type(attrFile) == "table", "stat on file " .. base .. " failed")

    assert(attrFile.mode == "file", "file.mode != file")
    assert(attrDir.mode == "directory", "directory.mode != directory")
    assert(attrFile.size == #base, "file length mismatch")

    assert(attrFile.mode == lfs.attributes(file, "mode"), "mode on file explicit call mismatch")
    assert(attrFile.size == lfs.attributes(file, "size"), "size on file explicit call mismatch")
    assert(attrDir.mode == lfs.attributes(base, "mode"), "mode on dir explicit call mismatch")
    assert(readFile(file) == base, "file content mismatch")
end

print("test explicit iter.next produces same result as nextFunc")
assert(lfs.chdir(pwd) == true, "chdir failed")
local nextFunc, iter = lfs.dir("." .. pathSeperator .. "testfolder" .. pathSeperator)
local gotOrdered2 = {}
while true do
    local n = iter:next()
    if n == nil then
        break
    end

    gotOrdered2[#gotOrdered2+1] = n
end
iter:close()

assert(lfs.chdir("testfolder") == true, "chdir failed")
assert(#gotOrdered == #gotOrdered2, "nextFunc and iter:next produced different number of results")
for i,v in ipairs(gotOrdered) do
    assert(v == gotOrdered2[i], "element #" .. i .. " mismatch in nextFunc and iter:next")
end

print("test simlink to folder")
local succ, err = lfs.link(".",".." .. pathSeperator .. "testfolderlink", true)

assert(succ == true, "creating simlink failed: " .. tostring(err))

local gotOrdered3 = {}
for file in lfs.dir(".." .. pathSeperator .. "testfolderlink") do
    gotOrdered3[#gotOrdered3 + 1]  = file
end

for i,v in ipairs(gotOrdered) do
    assert(v == gotOrdered3[i], "element #" .. i .. " mismatch in direct & simlink listing: " .. v .. " | " .. gotOrdered3[i])
end

for i,v in ipairs(randomTab) do
    assert(readFile(".." .. pathSeperator .. "testfolderlink" .. pathSeperator .. tostring(v) .. ".file") == tostring(v), "simlink file read mismatch")
end

print("test delete simlink via os.remove")
assert(os.remove(".." .. pathSeperator .. "testfolderlink") == true, "os.remove failed to delete simlink")

for file in lfs.dir("..") do
    assert(file ~= "testfolderlink", "os.remove succeeded but simlink is still there")
end

print("test rmdir on multi level directory")
assert(lfs.chdir(randomTab[10]) == true, "chdir failed")
spamRandomFiles()
assert(lfs.chdir("..") == true, "chdir failed")

assert(lfs.chdir(randomTab[33]) == true, "chdir failed")
spamRandomFiles()
assert(lfs.chdir("..") == true, "chdir failed")

assert(lfs.chdir(randomTab[45]) == true, "chdir failed")
spamRandomFiles()
assert(lfs.chdir("..") == true, "chdir failed")

assert(lfs.chdir(pwd) == true, "chdir failed")
assert(lfs.rmdir("testfolder") == true, "failed to delete testfolder")

print("test stat on non exist")
local succ, err = lfs.attributes("testfolder")
assert(succ == nil, "testfolder stat success even tho deleted")
assert(err == "No such file or directory", "testfolder failed stat error message mismatch: " .. tostring(err))

print("test iter.next on closed iter")
local next, iter = lfs.dir(".")
iter:close()
local succ, err = pcall(iter.next, iter)
assert(succ == false, "iter.next on closed iter succeeded")
assert(err == "calling 'next' on bad self (closed directory)", "iter.next on closed iter error message mismatch")

print("test os.remove use relative path")
assert(lfs.mkdir("testfolder") == true, "mkdir failed")
assert(lfs.chdir("testfolder") == true, "chdir failed")
spamRandomFiles()
local todel = tostring(randomTab[69]) .. ".file"
assert(os.remove(todel) == true, "os.remove failed")
for file in lfs.dir(".") do
    assert(todel ~= file, "os.remove did not delete the file")
end
assert(lfs.chdir("..") == true, "chdir failed")

print("test rmdir on file")
assert(lfs.rmdir("testfolder") == true, "rmdir on folder failed")
dropFile("testfolder", "hello world")
local succ, err = lfs.rmdir("testfolder")
assert(succ == nil, "rmdir on file succeeded")
assert(err == "Not a directory", "rmdir on file error message mismatch")
assert(os.remove("testfolder") == true, "os.remove on file failed")


print("success!")

os.exit(0)
