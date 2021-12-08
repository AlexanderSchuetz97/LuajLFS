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
local args = {...}
local file = args[1]
local mode = args[2]
local start = args[3]
local len = args[4]

if not start then
    start = 0
end

if not len then
    len = 0
end

local io = require("io")
local lfs = require("lfs")

print("locking " .. file .. " mode " .. mode .. " start " .. start .. " len " .. len)
local fmode = mode
if (fmode == "w") then
    fmode = "a"
end
local file = io.open(file, fmode)
if not file then
    print("failed to open file")
    os.exit(-1)
    return
end

local succ, err = lfs.lock(file, mode, start, len)
if succ then
    print("lock held press any letter plus enter to unlock")
    io.read("*n")
    succ, err = lfs.lock(file, "u", start, len)
    file:close()
    if not succ then
        print("unlock failed: ", err)
        os.exit(-1)
        return
    end

    print("unlocked!")
    os.exit(0)
    return
end
file:close()
print("lock failed: ", err)
os.exit(-1)