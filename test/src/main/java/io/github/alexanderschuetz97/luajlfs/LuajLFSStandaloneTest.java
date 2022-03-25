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


import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.InputStreamReader;

public class LuajLFSStandaloneTest {

    public static void main(String[] args) {
        Globals globals = JsePlatform.debugGlobals();
        globals.load(new LuajLFSLib());
        if (args.length == 0) {
            globals.load(new InputStreamReader(LuajLFSStandaloneTest.class.getResourceAsStream("/test.lua")), "test.lua").call();
            return;
        }

        LuaValue[] params = new LuaValue[args.length-1];
        for (int i = 1; i < args.length; i++) {
            params[i-1] = LuaValue.valueOf(args[i]);
        }
        switch (args[0]) {
            case("testLock.lua"):
                globals.load(new InputStreamReader(LuajLFSStandaloneTest.class.getResourceAsStream("/testLock.lua")), "testLock.lua").invoke(params);
                break;
            case("testDirLock.lua"):
                globals.load(new InputStreamReader(LuajLFSStandaloneTest.class.getResourceAsStream("/testDirLock.lua")), "testDirLock.lua").invoke(params);
                break;
            default:
                System.out.println("no such test" + args[0]);
                System.exit(-1);
                return;
        }






    }
}
