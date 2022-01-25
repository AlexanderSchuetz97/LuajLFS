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

import io.github.alexanderschuetz97.nativeutils.api.JVMNativeUtil;
import io.github.alexanderschuetz97.nativeutils.api.LinuxNativeUtil;
import io.github.alexanderschuetz97.nativeutils.api.NativeUtils;
import io.github.alexanderschuetz97.nativeutils.api.exceptions.InvalidFileDescriptorException;
import io.github.alexanderschuetz97.nativeutils.api.exceptions.QuotaExceededException;
import io.github.alexanderschuetz97.nativeutils.api.exceptions.UnknownNativeErrorException;
import io.github.alexanderschuetz97.nativeutils.api.structs.Stat;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.InvalidPathException;
import java.nio.file.ReadOnlyFileSystemException;

public class LuajLFSLinux extends LuajLFSCommon {



    private final LinuxNativeUtil util;

    protected LuajLFSLinux(Globals globals, LuaTable table) {
        if (!NativeUtils.isLinux()) {
            throw new LuaError("OS is not Linux or cpu architecture is not supported");
        }
        util = NativeUtils.getLinuxUtil();
        load(globals, table);
    }

    @Override
    protected Varargs lock_dir(Varargs args) {
        //This is ghetto but this is pretty much what lfs.c does
        File theLockFile = new File(resolve(args.checkjstring(1)), "lockfile.lfs");

        Varargs result = link(
                LuaValue.varargsOf(LuaValue.valueOf("lock"),
                        LuaValue.valueOf(theLockFile.getAbsolutePath()),
                        LuaValue.TRUE));


        //Was ist a success? If not return the error
        if (result.arg1().neq_b(LuaValue.TRUE)) {
            return result;
        }

        lock_dir_userdata userdata = new lock_dir_userdata(null);
        //GC Magic to clean it
        userdata.m_instance =  new lock_dir_cleaner(userdata, theLockFile);

        return userdata;
    }



    @Override
    public boolean isAbsolute(String path) {
        return path.startsWith("/");
    }

    @Override
    protected Varargs ioErr(IOException exc) {
        String msg = exc.getMessage();
        if (msg == null) {
            return INPUT_OUTPUT_ERROR;
        }

        return err("Input/output error: " + msg, 5);
    }

    @Override
    protected Varargs attributes(Varargs args) {
        File ff = resolve(args.checkjstring(1));
        LuaValue arg2 = args.arg(2);

        Stat stat;

        try {
            stat = util.stat(ff.getAbsolutePath());
        } catch (FileNotFoundException e) {
            return ERR_NO_SUCH_FILE_OR_DIR;
        } catch (InvalidPathException e) {
            return ERR_FILE_NAME_TOO_LONG;
        } catch (FileSystemLoopException e) {
            return ERR_TOO_MANY_LINKS;
        } catch (AccessDeniedException e)  {
            return ERR_PERMISSION_DENIED;
        } catch (UnknownNativeErrorException e) {
            return err(util.strerror_r((int) e.getCode()), e.getCode());
        } catch (IOException e) {
            return ERR_IO;
        }

        return mapStatResult(arg2, stat);
    }

    @Override
    protected Varargs symlinkattributes(Varargs args) {
        File ff = resolve(args.checkjstring(1));
        LuaValue arg2 = args.arg(2);

        Stat stat;

        try {
            stat = util.lstat(ff.getAbsolutePath());
        } catch (FileNotFoundException e) {
            return ERR_NO_SUCH_FILE_OR_DIR;
        } catch (InvalidPathException e) {
            return ERR_FILE_NAME_TOO_LONG;
        } catch (FileSystemLoopException e) {
            return ERR_TOO_MANY_LINKS;
        } catch (AccessDeniedException e)  {
            return ERR_PERMISSION_DENIED;
        } catch (UnknownNativeErrorException e) {
            return err(util.strerror_r((int) e.getCode()), e.getCode());
        } catch (IOException e) {
            return ERR_IO;
        }

        return mapStatResult(arg2, stat);
    }

    @Override
    protected Varargs link(Varargs args) {
        try {
            if (args.optboolean(3, false)) {
                util.symlink(resolve(args.checkjstring(1)).getAbsolutePath(), resolve(args.checkjstring(2)).getAbsolutePath());
            } else {
                util.link(resolve(args.checkjstring(1)).getAbsolutePath(), resolve(args.checkjstring(2)).getAbsolutePath());
            }
        } catch (QuotaExceededException e) {
            return ERR_QUOTA;
        } catch (FileAlreadyExistsException e) {
            return ERR_FILE_EXISTS;
        } catch (ReadOnlyFileSystemException e) {
            return ERR_READ_ONLY_FS;
        } catch (InvalidPathException e) {
            return ERR_FILE_NAME_TOO_LONG;
        } catch (IllegalArgumentException e) {
            return ERR_ILLEGAL_ARGUMENTS;
        } catch (AccessDeniedException e)  {
            return ERR_PERMISSION_DENIED;
        } catch (IOException e) {
            return ERR_IO;
        } catch (UnknownNativeErrorException e) {
            return err(util.strerror_r((int) e.getCode()), e.getCode());
        }

        return LuaValue.TRUE;
    }

    @Override
    protected Varargs lockExclusive(LuaValue userdata, RandomAccessFile fileDescriptor, long start, long len) {
        try {
            if (!util.fnctl_F_SETLK(util.getFD(fileDescriptor.getFD()), LinuxNativeUtil.fnctl_F_SETLK_Mode.F_WRLCK, start, len)) {
                return ERR_LOCK_LOCKED;
            }
        } catch (IllegalArgumentException exc) {
            return err("Invalid argument");
        } catch (InvalidFileDescriptorException e) {
            return ERR_BAD_FD;
        } catch (UnknownNativeErrorException e) {
            return err(util.strerror_r((int) e.getCode()), e.getCode());
        } catch (IOException e) {
            return ioErr(e);
        }

        return LuaValue.TRUE;
    }

    @Override
    protected Varargs lockShared(LuaValue userdata, RandomAccessFile fileDescriptor, long start, long len) {
        try {
            if (!util.fnctl_F_SETLK(util.getFD(fileDescriptor.getFD()), LinuxNativeUtil.fnctl_F_SETLK_Mode.F_RDLCK, start, len)) {
                return ERR_LOCK_LOCKED;
            }
        } catch (IllegalArgumentException exc) {
            return err("Invalid argument");
        } catch (InvalidFileDescriptorException e) {
            return err("File descriptor in bad state", 77);
        } catch (UnknownNativeErrorException e) {
            return err(util.strerror_r((int) e.getCode()), e.getCode());
        } catch (IOException e) {
            return ioErr(e);
        }

        return LuaValue.TRUE;
    }

    @Override
    protected Varargs lockUnlock(LuaValue userdata, RandomAccessFile fileDescriptor, long start, long len) {
        try {
            util.fnctl_F_SETLK(util.getFD(fileDescriptor.getFD()), LinuxNativeUtil.fnctl_F_SETLK_Mode.F_UNLCK, start, len);
        } catch (IllegalArgumentException exc) {
            return err("Invalid argument");
        } catch (InvalidFileDescriptorException e) {
            return err("File descriptor in bad state", 77);
        } catch (UnknownNativeErrorException e) {
            return err(util.strerror_r((int) e.getCode()), e.getCode());
        } catch (IOException e) {
            return ioErr(e);
        }

        return LuaValue.TRUE;
    }


}
