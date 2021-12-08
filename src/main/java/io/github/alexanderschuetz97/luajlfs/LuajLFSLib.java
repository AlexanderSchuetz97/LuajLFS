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

import io.github.alexanderschuetz97.nativeutils.api.NativeUtils;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

import java.io.File;

/**
 * TwoArgFunction Lib loader for LuajLFS.
 * Load by calling {@link Globals#load(LuaValue)} with new instance of this.
 */
public class LuajLFSLib extends TwoArgFunction {

    private LuajLFSCommon lib;

    @Override
    public synchronized LuaValue call(LuaValue arg1, LuaValue env) {
        if (lib != null) {
            throw new LuaError("already loaded");
        }
        Globals globals = env.checkglobals();


        LuaTable lfsTable = new LuaTable();
        if (NativeUtils.isLinux()) {
            lib = loadLinux(globals, lfsTable);
        } else if(NativeUtils.isWindows()) {
            lib = loadWindows(globals, lfsTable);
        } else {
            lib = loadOther(globals, lfsTable);
        }

        overwriteLuaMethods(globals, lib, lfsTable);
        globals.package_.setIsLoaded("lfs", lfsTable);
        return lfsTable;
    }

    /**
     * Overwrite to add custom stuff.
     */
    protected void overwriteLuaMethods(Globals globals, LuajLFSCommon lib, LuaTable lfsTable) {
        overwriteIOLib(globals, lib);
        overwrite_os_remove(globals, lib);
        overwrite_loadfile(globals, lib);
        overwrite_dofile(globals, lib);
    }

    /**
     * Call to get the work directory lua uses currently.
     */
    public File getVirtualWorkDirectory() {
        return lib.getPwd();
    }

    /**
     * Overwrite to provide custom linux mode implementation
     */
    protected LuajLFSCommon loadLinux(Globals globals, LuaTable lfsTable) {
       return new LuajLFSLinux(globals, lfsTable);
    }

    /**
     * Overwrite to provide custom windows mode implementation
     */
    protected LuajLFSCommon loadWindows(Globals globals, LuaTable lfsTable) {
        return new LuajLFSWindows(globals, lfsTable);
    }

    /**
     * Overwrite to provide custom unsupported os mode implementation
     */
    protected LuajLFSCommon loadOther(Globals globals, LuaTable lfsTable) {
        return new LuajLFSJSE(globals, lfsTable);
    }

    /**
     * Overwrite to provide custom IOLib implementation
     */
    protected void overwriteIOLib(Globals globals, LuajLFSCommon lib) {
        LFSIoLib ioLib = new LFSIoLib(lib);
        globals.load(ioLib);
    }

    /**
     * Overwrite to provide custom os.remove implementation
     */
    protected void overwrite_os_remove(Globals globals, final LuajLFSCommon lib) {
        LuaValue os_remove = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arg) {
                java.io.File f = lib.resolve(arg.checkjstring(1));
                if (!f.exists()) {
                    return varargsOf(NIL, LuajLFSCommon.NO_SUCH_FILE_OR_DIRECTORY);
                }

                if (!f.delete()) {
                    return varargsOf(NIL, LuajLFSCommon.FAILED_TO_DELETE);
                }

                return LuaValue.TRUE;
            }
        };

        globals.get("os").set("remove", os_remove);
    }

    /**
     * Overwrite to provide custom loadfile implementation
     */
    protected void overwrite_loadfile(Globals globals, final LuajLFSCommon lib) {
        final LuaValue loadfile = globals.get("loadfile");
        //loadfile not present, not overwriting
        if (loadfile.isnil()) {
            return;
        }

        LuaValue loadfile_wrapper = new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs arg) {
                if (!arg.isstring(1)) {
                    return loadfile.invoke(arg);
                }

                LuaValue resolvedPath = valueOf(lib.resolve(arg.checkjstring(1)).getAbsolutePath());
                return loadfile.invoke(varargsOf(resolvedPath, arg.subargs(2)));
            }
        };

        globals.set("loadfile", loadfile_wrapper);
    }

    /**
     * Overwrite to provide custom loadfile implementation
     */
    protected void overwrite_dofile(Globals globals, final LuajLFSCommon lib) {
        final LuaValue dofile = globals.get("dofile");
        //dofile not present, not overwriting
        if (dofile.isnil()) {
            return;
        }

        LuaValue dofilefile_wrapper = new VarArgFunction() {

            @Override
            public Varargs invoke(Varargs arg) {
                if (!arg.isstring(1)) {
                    return dofile.invoke(arg);
                }

                LuaValue resolvedPath = valueOf(lib.resolve(arg.checkjstring(1)).getAbsolutePath());
                return dofile.invoke(varargsOf(resolvedPath, arg.subargs(2)));
            }
        };

        globals.set("dofile", dofilefile_wrapper);
    }

}
