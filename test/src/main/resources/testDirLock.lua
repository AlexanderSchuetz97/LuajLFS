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

local io = require("io")
local lfs = require("lfs")

print("locking folder " .. file)
local lock, err = lfs.lock_dir(file)
if lock then
    print("lock held press any letter plus enter to unlock")
    io.read("*n")
    lock:free()
    print("lock released")
    os.exit(0)
    return
end
print("lock failed: ", err)
os.exit(-1)