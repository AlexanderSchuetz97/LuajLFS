//
// Copyright Alexander Schütz, 2021
//
// This file is part of LuajLFS.
//
// LuajLFS is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LuajLFS is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// A copy of the GNU Lesser General Public License should be provided
// in the COPYING & COPYING.LESSER files in top level directory of LuajLFS.
// If not, see <https://www.gnu.org/licenses/>.
//
package io.github.alexanderschuetz97.luajlfs;

import io.github.alexanderschuetz97.luajfshook.api.LuaFileSystemHandler;
import io.github.alexanderschuetz97.luajfshook.api.LuajFSHook;
import io.github.alexanderschuetz97.nativeutils.api.NativeUtils;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

import java.io.File;
import java.nio.file.Path;

/**
 * TwoArgFunction Lib loader for LuajLFS.
 * Load by calling {@link Globals#load(LuaValue)} with new instance of this.
 */
public class LuajLFSLib extends TwoArgFunction {

    private LuajLFSCommon lib;

    private LuaFileSystemHandler handler;

    @Override
    public synchronized LuaValue call(LuaValue arg1, LuaValue env) {
        if (lib != null) {
            throw new LuaError("already loaded");
        }
        Globals globals = env.checkglobals();
        handler = createFileSystemHandler(globals);
        if (handler == null) {
            throw new LuaError("no fs handler");
        }

        LuaTable lfsTable = new LuaTable();
        if (NativeUtils.isLinux()) {
            lib = loadLinux(handler, globals, lfsTable);
        } else if(NativeUtils.isWindows()) {
            lib = loadWindows(handler, globals, lfsTable);
        } else {
            lib = loadOther(handler, globals, lfsTable);
        }


        globals.package_.setIsLoaded("lfs", lfsTable);
        return lfsTable;
    }

    protected LuaFileSystemHandler createFileSystemHandler(Globals globals) {
        return LuajFSHook.getOrInstall(globals);
    }

    public LuaFileSystemHandler getFileSystemHandler() {
        return handler;
    }

    /**
     * Get the current work directory
     * @deprecated use getFileSystemHandler
     */
    @Deprecated
    public File getVirtualWorkDirectory() {
        Path syspath = getFileSystemHandler().getWorkDirectory().toSystemPath();
        if (syspath == null) {
            //FALLBACK
            return new File(".");
        }
        return syspath.toFile();
    }

    /**
     * Overwrite to provide custom linux mode implementation
     */
    protected LuajLFSCommon loadLinux(LuaFileSystemHandler handler, Globals globals, LuaTable lfsTable) {
       return new LuajLFSLinux(handler, globals, lfsTable);
    }

    /**
     * Overwrite to provide custom windows mode implementation
     */
    protected LuajLFSCommon loadWindows(LuaFileSystemHandler handler, Globals globals, LuaTable lfsTable) {
        return new LuajLFSWindows(handler, globals, lfsTable);
    }

    /**
     * Overwrite to provide custom unsupported os mode implementation
     */
    protected LuajLFSCommon loadOther(LuaFileSystemHandler handler, Globals globals, LuaTable lfsTable) {
        return new LuajLFSJSE(handler, globals, lfsTable);
    }
}
