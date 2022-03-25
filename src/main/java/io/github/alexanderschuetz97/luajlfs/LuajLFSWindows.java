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
import io.github.alexanderschuetz97.luajfshook.api.LuaPath;
import io.github.alexanderschuetz97.luajfshook.api.LuaRandomAccessFile;
import io.github.alexanderschuetz97.nativeutils.api.NativeUtils;
import io.github.alexanderschuetz97.nativeutils.api.WindowsNativeUtil;
import io.github.alexanderschuetz97.nativeutils.api.exceptions.InvalidFileDescriptorException;
import io.github.alexanderschuetz97.nativeutils.api.exceptions.SharingViolationException;
import io.github.alexanderschuetz97.nativeutils.api.exceptions.UnknownNativeErrorException;
import io.github.alexanderschuetz97.nativeutils.api.structs.Stat;
import io.github.alexanderschuetz97.nativeutils.api.structs.Win32FileAttributeData;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class LuajLFSWindows extends LuajLFSCommon {

    private static final Varargs ERR_DIR_HARDLINK = LuaValue.varargsOf(LuaValue.NIL, LuaValue.valueOf("hard links to directories are not supported on Windows"));

    protected static final Varargs ERR_PERMISSION_DENIED = err("Permission denied", 13);

    protected static final Varargs ERR_OVERLAP = err("Overlapped I/O operation is in progress.", 997);

    protected static final Varargs ERR_BAD_HANDLE = err("The handle is invalid.", 6);

    protected static final Varargs ERR_UNLOCKED = err("The segment is already unlocked.", 158);

    protected static final Varargs ERR_BAD_FD = err("File descriptor in bad state", 77);

    private final WindowsNativeUtil util;

    protected LuajLFSWindows(LuaFileSystemHandler dirHandler, Globals globals, LuaTable lfsTable) {
        if (!NativeUtils.isWindows()) {
            throw new LuaError("OS is not Windows or cpu architecture is not supported");
        }

        util = NativeUtils.getWindowsUtil();
        load(dirHandler, globals, lfsTable);

    }

    protected boolean useWinApiForLocks(FileDescriptor descriptor) {
        /*
         * Windows has 2 ways of doing everything. It depends on how the JVM has implemented RandomAccessFile.
         * Either it uses HANDLEs or just like linux file descriptors. In case it uses HANDLES then the fd field is always -1.
         * We can without much effort support both ways. Only difference is that using HANDLES you can actually implement shared and exclusive locks,
         * however using fd's you can only have (exclusive?) locks. lfs.c always uses the fd method presumably
         * because the c based luavm always uses fd's for its IO.
         */
        if (util.getFD(descriptor) != -1) {
            return false;
        }

        return true;
    }

    @Override
    protected Varargs lock_dir(Varargs args) {
        Path path = resolve(args.checkjstring(1)).child("lockfile.lfs").toSystemPath();
        if (path == null) {
            return ERR_NOT_SUPPORTED;
        }

        long handle;
        try {
            handle = util.CreateFileA(path.toString(), 0x40000000, false, false, false, WindowsNativeUtil.CreateFileA_createMode.CREATE_ALWAYS, 0x00000080 | 0x04000000);
        } catch (UnknownNativeErrorException e) {
            return err(util.FormatMessageA((int) e.getCode()), e.getCode());
        } catch (FileAlreadyExistsException | SharingViolationException e) {
            return ERR_FILE_EXISTS;
        }

        lock_dir_object object = new lock_dir_object(handle);
        lock_dir_userdata userdata = new lock_dir_userdata(object);

        new lock_dir_cleaner(userdata, object);

        return userdata;
    }

    protected static final LuaValue LOCK_DIR_FREE = new VarArgFunction() {
        @Override
        public Varargs invoke(Varargs arg) {
            lock_dir_object object = (lock_dir_object) arg.checkuserdata(1, lock_dir_object.class);
            object.free();
            return NONE;
        }
    };

    //must not have references to anything thus static important!
    protected static class lock_dir_object {
        private final long handle;
        private boolean deleted;

        public lock_dir_object(long handle) {
            this.handle = handle;
        }

        public void free() {
            if (deleted) {
                return;
            }

            try {
                NativeUtils.getWindowsUtil().CloseHandle(handle);
            } catch (Exception e) {
                //DC
            }

            deleted = true;
        }
    }

    protected class lock_dir_userdata extends LuaUserdata {

        public lock_dir_userdata(lock_dir_object object) {
            super(object);
        }

        @Override
        public LuaValue get(LuaValue key) {
            if (FREE.eq_b(key)) {
                return LOCK_DIR_FREE;
            }
            return super.get(key);
        }
    }

    //must not have references to anything thus static important!
    protected static class lock_dir_cleaner extends ReferenceQueueCleaner.CleanerRef<lock_dir_userdata> {
        protected final lock_dir_object object;

        private lock_dir_cleaner(lock_dir_userdata referent, lock_dir_object file) {
            super(referent);
            this.object = file;
        }

        @Override
        public void clean() {
            object.free();
        }
    }

    @Override
    public boolean isAbsolute(String path) {
        if (path.length() <= 1) {
            return false;
        }

        // \\SOME-PATH-GOES-HERE\....
        if (path.startsWith("\\\\")) {
            return true;
        }

        //C:\...
        if (path.substring(1).startsWith(":\\")) {
            return true;
        }

        return false;
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
        LuaPath path = resolve(args.checkjstring(1));
        Path systemPath = path.toSystemPath();
        LuaValue arg2 = args.arg(2);

        if (systemPath == null) {
            BasicFileAttributes baf;

            try {
                baf = path.attributes();
            } catch (IOException e) {
                return ERR_IO;
            }

            return mapStatResult(arg2, baf);
        }

        Stat stat;
        try {
            stat = util._stat64(systemPath.toString());
        } catch (IllegalArgumentException e) {
            return ERR_ILLEGAL_ARGUMENTS;
        } catch (FileNotFoundException e) {
            return ERR_NO_SUCH_FILE_OR_DIR;
        } catch (UnknownNativeErrorException e) {
            return err(util.strerror_s((int) e.getCode()), (int) e.getCode());
        }

        return mapStatResult(arg2, stat);
    }

    protected long convTime(long high, long low) {
        long shift = high;
        shift <<=32;
        shift += low;
        shift /= 10000000;
        shift -= 11644473600L;
        return shift;
    }

    @Override
    protected Varargs symlinkattributes(Varargs args) {
        LuaPath path = resolve(args.checkjstring(1));
        Path systemPath = path.toSystemPath();
        LuaValue arg2 = args.arg(2);

        if (systemPath == null) {
            BasicFileAttributes baf;

            try {
                baf = path.linkAttributes();
            } catch (IOException e) {
                return ERR_IO;
            }

            return mapStatResult(arg2, baf);
        }

        String sPath = path.toString();


        try {
            Win32FileAttributeData data = util.GetFileAttributesEx(sPath);

            //IS THIS A LINK?
            if ((data.getDwFileAttributes() & 0x00000400L) == 0x00000400L) {
                //CUSTOM HANDLING FOR LINK
                if (arg2.isstring()) {
                    String str = arg2.checkjstring();
                    switch (str) {
                        case("mode"):
                            return LINK;
                        case("access"):
                            return LuaValue.valueOf(convTime(data.getFtLastAccessTimeHigh(), data.getFtLastAccessTimeLow()));
                        case("modification"):
                            return LuaValue.valueOf(convTime(data.getFtLastWriteTimeHigh(), data.getFtLastWriteTimeLow()));
                        case("change"):
                            return LuaValue.valueOf(convTime(data.getFtCreationTimeHigh(), data.getFtCreationTimeLow()));
                        case("dev"):
                        case("ino"):
                        case("nlink"):
                        case("uid"):
                        case("gid"):
                        case("rdev"):
                        case("size"):
                        case("blocks"):
                        case("blksize"):
                            return LuaValue.ZERO;
                        case("permissions"):
                        default:
                            throw new LuaError("invalid attribute name '" + str +"'");
                    }
                }

                if (!arg2.istable()) {
                    arg2 = new LuaTable();
                }

                arg2.set(DEV, LuaValue.ZERO);
                arg2.set(INO, LuaValue.ZERO);
                arg2.set(MODE, LINK);
                arg2.set(NLINK, LuaValue.ZERO);
                arg2.set(UID, LuaValue.ZERO);
                arg2.set(GID, LuaValue.ZERO);
                arg2.set(RDEV, LuaValue.ZERO);
                arg2.set(ACCESS, LuaValue.valueOf(convTime(data.getFtLastAccessTimeHigh(), data.getFtLastAccessTimeLow())));
                arg2.set(MODIFICATION, LuaValue.valueOf(convTime(data.getFtLastWriteTimeHigh(), data.getFtLastWriteTimeLow())));
                arg2.set(PERMISSIONS, LuaValue.valueOf("---------")); //TODO check
                arg2.set(CHANGE, LuaValue.valueOf(convTime(data.getFtCreationTimeHigh(), data.getFtCreationTimeLow())));
                arg2.set(SIZE, LuaValue.ZERO);
                arg2.set(BLOCKS, LuaValue.ZERO);
                arg2.set(BLKSIZE, LuaValue.ZERO);
            }
        } catch (UnknownNativeErrorException e) {
            return err(util.FormatMessageA((int) e.getCode()), e.getCode());
        }

        //NOT A LINK NORMAL STAT
        Stat stat;
        try {
            stat = util._stat64(sPath);
        } catch (IllegalArgumentException e) {
            return ERR_ILLEGAL_ARGUMENTS;
        } catch (FileNotFoundException e) {
            return ERR_NO_SUCH_FILE_OR_DIR;
        } catch (UnknownNativeErrorException e) {
            return err(util.strerror_s((int) e.getCode()), (int) e.getCode());
        }

        return mapStatResult(arg2, stat);
    }

    @Override
    protected Varargs link(Varargs args) {
        //Under windows this requires admin priviliges for some reason...
        try {
            Path target = resolve(args.checkjstring(1)).toSystemPath();
            if (target == null) {
                return ERR_NOT_SUPPORTED;
            }

            Path source = resolve(args.checkjstring(2)).toSystemPath();
            if (source == null) {
                return ERR_NOT_SUPPORTED;
            }

            if (args.optboolean(3, false)) {
                util.CreateSymbolicLinkA(source.toString(), target.toString(), Files.isDirectory(target), false);
            } else {
                if (Files.isDirectory(target)) {
                    return ERR_DIR_HARDLINK;
                }
                util.CreateHardLinkA(source.toString(), target.toString());
            }

        } catch (UnknownNativeErrorException e) {
            return err(util.FormatMessageA((int) e.getCode()), (int) e.getCode());
        }

        return LuaValue.TRUE;
    }

    @Override
    protected Varargs lockExclusive(LuaValue userdata, LuaRandomAccessFile file, long start, long len) {
        FileDescriptor fileDescriptor;
        try {
            fileDescriptor = file.getFileDescriptor();
        } catch (IOException e) {
            return ioErr(e);
        }

        if (fileDescriptor == null) {
            return ERR_NOT_SUPPORTED;
        }


        if (useWinApiForLocks(fileDescriptor)) {
            try {
                if (!util.LockFileEx(util.getHandle(fileDescriptor), true, true, start, len)) {
                    return ERR_OVERLAP;
                }

                return LuaValue.TRUE;
            } catch (InvalidFileDescriptorException e) {
                return ERR_BAD_HANDLE;
            } catch (UnknownNativeErrorException e) {
                return err(util.FormatMessageA((int) e.getCode()), e.getCode());
            }
        }


        try {
            long pos = file.getPosition();
            file.setPosition(start);
            boolean success = util._locking(util.getFD(fileDescriptor), WindowsNativeUtil._locking_Mode._LK_NBLCK, len);
            file.setPosition(pos);
            if (!success) {
               return ERR_PERMISSION_DENIED;
            }
            return LuaValue.TRUE;
        } catch (InvalidFileDescriptorException e) {
            return ERR_BAD_FD;
        } catch (UnknownNativeErrorException e) {
            return err(util.strerror_s((int) e.getCode()), e.getCode());
        } catch (IOException e) {
           return ioErr(e);
        }
    }

    @Override
    protected Varargs lockShared(LuaValue userdata, LuaRandomAccessFile file, long start, long len) {
        FileDescriptor fileDescriptor;
        try {
            fileDescriptor = file.getFileDescriptor();
        } catch (IOException e) {
            return ioErr(e);
        }

        if (fileDescriptor == null) {
            return ERR_NOT_SUPPORTED;
        }

        if (useWinApiForLocks(fileDescriptor)) {
            try {
                if (!util.LockFileEx(util.getHandle(fileDescriptor), false, true, start, len)) {
                    return ERR_OVERLAP;
                }

                return LuaValue.TRUE;
            } catch (InvalidFileDescriptorException e) {
                return ERR_BAD_HANDLE;
            } catch (UnknownNativeErrorException e) {
                return err(util.FormatMessageA((int) e.getCode()), e.getCode());
            }
        }

        try {
            long pos = file.getPosition();
            file.setPosition(start);
            boolean success = util._locking(util.getFD(fileDescriptor), WindowsNativeUtil._locking_Mode._LK_NBLCK, len);
            file.setPosition(pos);
            if (!success) {
                return ERR_PERMISSION_DENIED;
            }
            return LuaValue.TRUE;
        } catch (InvalidFileDescriptorException e) {
            return ERR_BAD_FD;
        } catch (UnknownNativeErrorException e) {
            return err(util.strerror_s((int) e.getCode()), e.getCode());

        } catch (IOException e) {
            return ioErr(e);
        }
    }

    @Override
    protected Varargs lockUnlock(LuaValue userdata, LuaRandomAccessFile file, long start, long len) {
        FileDescriptor fileDescriptor;
        try {
            fileDescriptor = file.getFileDescriptor();
        } catch (IOException e) {
            return ioErr(e);
        }

        if (fileDescriptor == null) {
            return ERR_NOT_SUPPORTED;
        }

        if (useWinApiForLocks(fileDescriptor)) {
            try {
                if (!util.UnlockFileEx(util.getHandle(fileDescriptor), start, len)) {
                    return ERR_UNLOCKED;
                }

                return LuaValue.TRUE;
            } catch (InvalidFileDescriptorException e) {
                return ERR_BAD_HANDLE;
            } catch (UnknownNativeErrorException e) {
                return err(util.FormatMessageA((int) e.getCode()), e.getCode());
            }
        }


        try {
            long pos = file.getPosition();
            file.setPosition(start);
            boolean success = util._locking(util.getFD(fileDescriptor), WindowsNativeUtil._locking_Mode._LK_UNLCK, len);
            file.setPosition(pos);
            if (!success) {
                return ERR_PERMISSION_DENIED;
            }
            return LuaValue.TRUE;
        } catch (InvalidFileDescriptorException e) {
            return ERR_BAD_FD;
        } catch (UnknownNativeErrorException e) {
            return err(util.strerror_s((int) e.getCode()), e.getCode());
        } catch (IOException e) {
            return ioErr(e);
        }
    }
}
