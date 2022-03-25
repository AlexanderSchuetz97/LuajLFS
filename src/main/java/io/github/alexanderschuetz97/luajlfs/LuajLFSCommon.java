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
import io.github.alexanderschuetz97.luajfshook.impl.DefaultLuaRandomAccessFile;
import io.github.alexanderschuetz97.nativeutils.api.JVMNativeUtil;
import io.github.alexanderschuetz97.nativeutils.api.NativeUtils;
import io.github.alexanderschuetz97.nativeutils.api.structs.Stat;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class LuajLFSCommon {

    protected static final LuaValue DIRECTORY = LuaValue.valueOf("directory");
    protected static final LuaValue FILE = LuaValue.valueOf("file");
    protected static final LuaValue LINK = LuaValue.valueOf("link");
    protected static final LuaValue SOCKET = LuaValue.valueOf("socket");
    protected static final LuaValue NAMED_PIPE = LuaValue.valueOf("named pipe");
    protected static final LuaValue CHAR_DEVICE = LuaValue.valueOf("char device");
    protected static final LuaValue BLOCK_DEVICE = LuaValue.valueOf("block device");
    protected static final LuaValue OTHER = LuaValue.valueOf("other");
    protected static final LuaValue DEV = LuaValue.valueOf("dev");
    protected static final LuaValue INO = LuaValue.valueOf("ino");
    protected static final LuaValue MODE = LuaValue.valueOf("mode");
    protected static final LuaValue NLINK = LuaValue.valueOf("nlink");
    protected static final LuaValue UID = LuaValue.valueOf("uid");
    protected static final LuaValue GID = LuaValue.valueOf("gid");
    protected static final LuaValue RDEV = LuaValue.valueOf("rdev");
    protected static final LuaValue ACCESS = LuaValue.valueOf("access");
    protected static final LuaValue MODIFICATION = LuaValue.valueOf("modification");
    protected static final LuaValue PERMISSIONS = LuaValue.valueOf("permissions");
    protected static final LuaValue CHANGE = LuaValue.valueOf("change");
    protected static final LuaValue SIZE = LuaValue.valueOf("size");
    protected static final LuaValue BLOCKS = LuaValue.valueOf("blocks");
    protected static final LuaValue BLKSIZE = LuaValue.valueOf("blksize");
    protected static final LuaValue CURRENTDIR = LuaValue.valueOf("currentdir");
    protected static final LuaValue CHDIR = LuaValue.valueOf("chdir");
    protected static final LuaValue SETMODE = LuaValue.valueOf("setmode");
    protected static final LuaValue TOUCH = LuaValue.valueOf("touch");
    protected static final LuaValue DIR = LuaValue.valueOf("dir");
    protected static final LuaValue LOCK = LuaValue.valueOf("lock");
    protected static final LuaValue UNLOCK = LuaValue.valueOf("unlock");
    protected static final LuaValue RMDIR = LuaValue.valueOf("rmdir");
    protected static final LuaValue MKDIR = LuaValue.valueOf("mkdir");
    protected static final LuaValue ATTRIBUTES = LuaValue.valueOf("attributes");
    protected static final LuaValue SYMLINKATTRIBUTES = LuaValue.valueOf("symlinkattributes");
    protected static final LuaValue LOCK_DIR = LuaValue.valueOf("lock_dir");
    protected static final LuaValue FREE = LuaValue.valueOf("free");
    protected static final LuaValue NO_SUCH_FILE_OR_DIRECTORY = LuaValue.valueOf("No such file or directory");
    protected static final LuaValue INPUT_OUTPUT_ERROR = LuaValue.valueOf( "Input/output error");
    protected static final LuaValue FAILED_TO_DELETE = LuaValue.valueOf( "Failed to delete");
    protected static final LuaValue FILE_EXISTS = LuaValue.valueOf("File exists");
    protected static final Varargs ERR_NOT_SUPPORTED = err("Not supported");

    protected static final Varargs ERR_BAD_FD = err("File descriptor in bad state", 77);
    protected static final Varargs ERR_NO_SUCH_FILE_OR_DIR = err(NO_SUCH_FILE_OR_DIRECTORY, 2);
    protected static final Varargs ERR_FILE_NAME_TOO_LONG = err("Filename too long", 36);
    protected static final Varargs ERR_TOO_MANY_LINKS = err("Too many levels of symbolic links", 40);
    protected static final Varargs ERR_PERMISSION_DENIED = err("Permission denied", 13);
    protected static final Varargs ERR_IO = err(INPUT_OUTPUT_ERROR, 5);
    protected static final Varargs ERR_ILLEGAL_ARGUMENTS = err("Invalid argument", 22);
    protected static final Varargs ERR_READ_ONLY_FS = err("Read-only file system", 30);
    protected static final Varargs ERR_FILE_EXISTS = err("File exists", 17);
    protected static final Varargs ERR_QUOTA = err("Quota exceeded", 122);
    protected static final Varargs ERR_LOCK_LOCKED = err("Resource temporarily unavailable");
    protected static final LuaValue DOT = LuaValue.valueOf(".");
    protected static final LuaValue DOT_DOT = LuaValue.valueOf("..");
    protected static final LuaValue DUMMY_PERMISSIONS = LuaValue.valueOf("---------");


    protected LuaFileSystemHandler dirHandler;

    protected final JVMNativeUtil jvmu;

    protected LuajLFSCommon()  {
        jvmu = NativeUtils.isJVM() ? NativeUtils.getJVMUtil() : null;
    }

    protected void load(LuaFileSystemHandler dirHandler, Globals globals, LuaTable table) {

        this.dirHandler = dirHandler;
        if (dirHandler == null) {
            throw new LuaError("no dir handler");
        }


        table.set(CURRENTDIR, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return currentdir();
            }
        });

        table.set(CHDIR, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return chdir(args.arg1());
            }
        });

        table.set(SETMODE, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return setmode(args.arg1(), args.arg(2));
            }
        });

        table.set(TOUCH, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return touch(args);
            }
        });

        table.set(LINK, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return link(args);
            }
        });

        table.set(DIR, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return dir(args.arg1());
            }
        });

        table.set(LOCK, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return lock(args);
            }
        });

        table.set(UNLOCK, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return unlock(args);
            }
        });

        table.set(RMDIR, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return rmdir(args.arg1());
            }
        });

        table.set(MKDIR, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return mkdir(args.arg1());
            }
        });

        table.set(ATTRIBUTES, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return attributes(args);
            }
        });

        table.set(SYMLINKATTRIBUTES, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return symlinkattributes(args);
            }
        });

        table.set(LOCK_DIR, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return lock_dir(args);
            }
        });
    }

    protected abstract Varargs lock_dir(Varargs args);

    public abstract boolean isAbsolute(String path);

    public LuaPath resolve(String path) {
        return dirHandler.resolvePath(path);
    }

    protected long getTimestamp() {
        return System.currentTimeMillis();
    }

    protected abstract Varargs ioErr(IOException exc);

    protected abstract Varargs attributes(Varargs args);

    protected abstract Varargs symlinkattributes(Varargs args);

    protected abstract Varargs link(Varargs args);

    protected abstract Varargs lockExclusive(LuaValue userdata, LuaRandomAccessFile fileDescriptor, long start, long len);

    protected abstract Varargs lockShared(LuaValue userdata, LuaRandomAccessFile fileDescriptor, long start, long len);

    protected abstract Varargs lockUnlock(LuaValue userdata, LuaRandomAccessFile fileDescriptor, long start, long len);

    protected Object getField(Field field, Object instance) {
        if (jvmu != null) {
            return jvmu.FromReflectedField(field).get(instance);
        }
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (Exception exc) {
            throw new LuaError("reflection error " + exc.getMessage());
        }
    }

    /**
     * Hook your IOLib here...
     * This assumes file is either userdata of RandomAccessFile or instanceof org.luaj.vm2.lib.jse.JseIoLib.FileImpl
     * This works fine for the default globals.
     */
    protected LuaRandomAccessFile getFD(LuaValue value) {
        if (value.type() != LuaValue.TUSERDATA) {
            return null;
        }

        if (value.isuserdata(LuaRandomAccessFile.class)) {
            return (LuaRandomAccessFile) value.checkuserdata(LuaRandomAccessFile.class);
        }


        RandomAccessFile raf;
        if (value.isuserdata(RandomAccessFile.class)) {
            raf = (RandomAccessFile) value.checkuserdata(RandomAccessFile.class);
        } else {
            //Why is this private luaj....
            //org.luaj.vm2.lib.jse.JseIoLib.FileImpl
            try {
                Field f = value.getClass().getDeclaredField("file");
                raf = (RandomAccessFile) getField(f, value);
            } catch (Exception exc) {
                //Can be anything from NoSuchField or ClassCast or NPE
                return null;
            }
        }

        if (raf == null) {
            return null;
        }

        return new DefaultLuaRandomAccessFile(raf, null);


    }

    protected Varargs unlock(Varargs args) {
        LuaRandomAccessFile value = getFD(args.arg1());
        if (value == null) {
            throw new LuaError("bad argument #1 to 'unlock' (FILE* expected, got "+ args.arg1().typename() +")");
        }

        long start = args.optlong(2, 0);
        long end = args.optlong(3, 0);
        if (start < 0) {
            return err("Invalid argument");
        }

        return lockUnlock(args.arg1(), value, start, end);
    }

    protected Varargs lock(Varargs args) {
        LuaRandomAccessFile value = getFD(args.arg1());
        if (value == null) {
            throw new LuaError("bad argument #1 to 'lock' (FILE* expected, got "+ args.arg1().typename() +")");
        }

        LuaString mode = args.checkstring(2);
        long start = args.optlong(3, 0);

        long end = args.optlong(4, 0);

        switch (mode.m_length == 0 ? 0 : mode.m_bytes[mode.m_offset]) {
            case('r'):
                if (start < 0) {
                    return err("Invalid argument");
                }
                return lockShared(args.arg1(), value, start, end);
            case('w'):
                if (start < 0) {
                    return err("Invalid argument");
                }
                return lockExclusive(args.arg1(), value, start, end);
            case('u'):
                if (start < 0) {
                    return err("Invalid argument");
                }
                return lockUnlock(args.arg1(), value, start, end);
            default:
                throw new LuaError("lock: invalid mode");
        }
    }

    protected Varargs mkdir(LuaValue path) {
        LuaPath f = resolve(path.checkjstring());
        if (f.exists()) {
            return err("File exists", 17);
        }

        LuaPath parent = f.parent();

        if (parent != null && !parent.exists()) {
            return err(NO_SUCH_FILE_OR_DIRECTORY, 2);
        }


        try {
            f.mkdir();
            return LuaValue.TRUE;
        } catch (IOException e) {
            return err(INPUT_OUTPUT_ERROR, 5);
        }
    }

    protected Varargs rmdir(LuaValue path) {
        LuaPath f = resolve(path.checkjstring());
        if (!f.exists()) {
            return err(NO_SUCH_FILE_OR_DIRECTORY, 2);
        }

        if (!f.isDir()) {
            return err("Not a directory", 20);
        }

        try {
            f.walkFileTree(Integer.MAX_VALUE, false, new LuaPath.LuaFileVisitor() {

                @Override
                public FileVisitResult preVisitDirectory(LuaPath dir) throws IOException {
                    if (dir.isĹink()) {
                        dir.delete();
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(LuaPath dir) throws IOException {
                    dir.delete();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(LuaPath dir) throws IOException {
                    dir.delete();
                    return FileVisitResult.CONTINUE;

                }
            });
        } catch (IOException e) {
            return ioErr(e);
        }

        return LuaValue.TRUE;
    }

    protected Varargs touch(Varargs args) {
        String npath = args.arg1().checkjstring();

        long atime = args.narg() > 1 ? args.checklong(2) : TimeUnit.MILLISECONDS.toSeconds(getTimestamp());
        long mtime = args.optlong(3, atime);
        LuaPath ff = resolve(npath);

        try {
            ff.setFileTimes(FileTime.from(mtime, TimeUnit.SECONDS), FileTime.from(atime, TimeUnit.SECONDS), null);
        } catch (IOException e) {
            return ioErr(e);
        }

        return LuaValue.TRUE;
    }

    protected Varargs dir(LuaValue path) {
        String npath = path.checkjstring();
        LuaPath ff = resolve(npath);

        if (!ff.exists()) {
            throw new LuaError("cannot open " + npath + ": No such file or directory");
        }

        if (!ff.isDir()) {
            throw new LuaError("cannot open " + npath + ": Not a directory");
        }
        List<LuaPath> pathList;
        try {
            pathList = ff.list();
        } catch (IOException e) {
            throw new LuaError("cannot open " + npath + ": I/O error");
        }

        String[] listing = new String[pathList.size()];

        int i = 0;
        for (LuaPath p : pathList) {
            listing[i++] = p.name();
        }

        return LuaValue.varargsOf(DIR_NEXT, new dir_object_userdata(new dir_object(listing)));
    }

    protected final LuaValue DIR_NEXT = new OneArgFunction() {
        @Override
        public LuaValue call(LuaValue arg) {
            return dirNext(arg);
        }
    };

    protected final LuaValue DIR_CLOSE = new VarArgFunction() {
        @Override
        public Varargs invoke(Varargs args) {
            dirClose(args.arg1());
            return LuaValue.NONE;
        }
    };

    protected LuaValue dirNext(LuaValue dirObject) {
        dir_object obj = (dir_object) dirObject.checkuserdata(dir_object.class);
        if (obj.nextIndex < 0) {
            switch (obj.nextIndex) {
                case (-1):
                    obj.nextIndex++;
                    return DOT_DOT;
                case (-2):
                    obj.nextIndex++;
                    return DOT;
                default:
                    throw new LuaError("calling 'next' on bad self (closed directory)");
            }
        }

        if (obj.nextIndex >= obj.elements.length) {
            return LuaValue.NIL;
        }

        return LuaValue.valueOf(obj.elements[obj.nextIndex++]);
    }

    protected void dirClose(LuaValue dirObject) {
        dir_object obj = (dir_object) dirObject.checkuserdata(dir_object.class);
        obj.nextIndex = -3;
    }


    protected class dir_object {
        protected final String[] elements;
        protected int nextIndex = -2;

        public dir_object(String[] elements) {
            this.elements = elements;
        }
    }


    private static final LuaValue NEXT = LuaValue.valueOf("next");
    private static final LuaValue CLOSE = LuaValue.valueOf("close");

    protected class dir_object_userdata extends LuaUserdata {
        public dir_object_userdata(dir_object obj) {
            super(obj);
        }

        @Override
        public LuaValue get(LuaValue key) {
            if (NEXT.eq_b(key)) {
                return DIR_NEXT;
            } else if (CLOSE.eq_b(key)) {
                return DIR_CLOSE;
            } else {
                return super.get(key);
            }
        }
    }


    protected Varargs setmode(LuaValue file, LuaValue mode) {
        return LuaValue.varargsOf(LuaValue.TRUE, LuaValue.valueOf("binary"));
    }

    protected Varargs currentdir() {
        return LuaValue.valueOf(dirHandler.getWorkDirectory().toString());
    }

    protected Varargs chdir(LuaValue path) {
        String npath = path.checkjstring();
        LuaPath ff = resolve(npath);

        if (!ff.exists()) {
            return err("Unable to change working directory to '"+ npath +"'\nNo such file or directory");
        }

        if (!ff.isDir()) {
            return err("Unable to change working directory to '"+ npath +"'\nNot a directory");
        }

        try {
            dirHandler.setWorkDirectory(ff);
        } catch (IOException e) {
            return ioErr(e);
        }

        return LuaValue.TRUE;
    }

    protected static final LuaValue LOCK_DIR_FREE = new VarArgFunction() {
        @Override
        public Varargs invoke(Varargs arg) {
            lock_dir_cleaner object = (lock_dir_cleaner) arg.checkuserdata(1, lock_dir_cleaner.class);
            object.clear();
            return NONE;
        }
    };


    protected static class lock_dir_userdata extends LuaUserdata {

        public lock_dir_userdata(lock_dir_cleaner object) {
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
        private final LuaPath file;
        private boolean deleted;

        protected lock_dir_cleaner(lock_dir_userdata referent, LuaPath file) {
            super(referent);
            this.file = file;
        }

        @Override
        public void clean() {
            if (deleted) {
                return;
            }

            try {
                file.delete();
            } catch (IOException e) {
                //DONT CARE
            }
            deleted = true;
        }
    }


    protected static Varargs err(String message) {
        if (message == null) {
            message = "";
        }

        return LuaValue.varargsOf(LuaValue.NIL, LuaValue.valueOf(message));
    }

    protected static Varargs err(LuaValue message) {
        return LuaValue.varargsOf(LuaValue.NIL, message);
    }

    protected static Varargs err(String message, long code) {
        if (message == null) {
            message = "";
        }

        return LuaValue.varargsOf(LuaValue.NIL, LuaValue.valueOf(message), LuaValue.valueOf(code));
    }

    protected static Varargs err(LuaValue message, long code) {
        return LuaValue.varargsOf(LuaValue.NIL, message, LuaValue.valueOf(code));
    }

    protected LuaValue mapStatMode(Stat stat) {
        if (stat.isDir()) {
            return DIRECTORY;
        }

        if (stat.isRegularFile()) {
            return FILE;
        }

        if (stat.isSymbolicLink()) {
            return LINK;
        }

        if (stat.isSocket()) {
            return SOCKET;
        }

        if (stat.isBlockDevice()) {
            return BLOCK_DEVICE;
        }

        if (stat.isCharacterDevice()) {
            return CHAR_DEVICE;
        }

        if (stat.isFIFO()) {
            return NAMED_PIPE;
        }

        return OTHER;
    }

    protected LuaValue mapStatResult(LuaValue arg2, Stat stat) {
        if (arg2.isstring()) {
            String str = arg2.checkjstring();
            switch (str) {
                case("dev"):
                    return LuaValue.valueOf(stat.getDev());
                case("ino"):
                    return LuaValue.valueOf(stat.getIno());
                case("mode"):
                    return mapStatMode(stat);
                case("nlink"):
                    return LuaValue.valueOf(stat.getNlink());
                case("uid"):
                    return LuaValue.valueOf(stat.getUid());
                case("gid"):
                    return LuaValue.valueOf(stat.getGid());
                case("rdev"):
                    return LuaValue.valueOf(stat.getRdev());
                case("access"):
                    return LuaValue.valueOf(stat.getAtime());
                case("modification"):
                    return LuaValue.valueOf(stat.getMtime());
                case("change"):
                    return LuaValue.valueOf(stat.getCtime());
                case("size"):
                    return LuaValue.valueOf(stat.getSize());
                case("permissions"):
                    return LuaValue.valueOf(stat.getPermissions());
                case("blocks"):
                    return LuaValue.valueOf(stat.getBlocks());
                case("blksize"):
                    return LuaValue.valueOf(stat.getBlksize());
                default:
                    throw new LuaError("invalid attribute name '" + str +"'");
            }
        }

        if (!arg2.istable()) {
            arg2 = new LuaTable();
        }

        arg2.set(DEV, LuaValue.valueOf(stat.getDev()));
        arg2.set(INO, LuaValue.valueOf(stat.getIno()));
        arg2.set(MODE, mapStatMode(stat));
        arg2.set(NLINK, LuaValue.valueOf(stat.getNlink()));
        arg2.set(UID, LuaValue.valueOf(stat.getUid()));
        arg2.set(GID, LuaValue.valueOf(stat.getGid()));
        arg2.set(RDEV, LuaValue.valueOf(stat.getRdev()));
        arg2.set(ACCESS, LuaValue.valueOf(stat.getAtime()));
        arg2.set(MODIFICATION, LuaValue.valueOf(stat.getMtime()));
        arg2.set(PERMISSIONS, LuaValue.valueOf(stat.getPermissions()));
        arg2.set(CHANGE, LuaValue.valueOf(stat.getCtime()));
        arg2.set(SIZE, LuaValue.valueOf(stat.getSize()));
        arg2.set(BLOCKS, LuaValue.valueOf(stat.getBlocks()));
        arg2.set(BLKSIZE, LuaValue.valueOf(stat.getBlksize()));
        return arg2;
    }

    protected LuaValue mapStatMode(BasicFileAttributes stat) {
        if (stat.isDirectory()) {
            return DIRECTORY;
        }

        if (stat.isRegularFile()) {
            return FILE;
        }

        if (stat.isSymbolicLink()) {
            return LINK;
        }

        return OTHER;
    }

    protected LuaValue mapStatResult(LuaValue arg2, BasicFileAttributes stat) {
        if (arg2.isstring()) {
            String str = arg2.checkjstring();
            switch (str) {
                case("dev"):
                    return LuaValue.ZERO;
                case("ino"):
                    return LuaValue.ZERO;
                case("mode"):
                    return mapStatMode(stat);
                case("nlink"):
                    return LuaValue.ZERO;
                case("uid"):
                    return LuaValue.ZERO;
                case("gid"):
                    return LuaValue.ZERO;
                case("rdev"):
                    return LuaValue.ZERO;
                case("access"):
                    return LuaValue.valueOf(stat.lastAccessTime().to(TimeUnit.SECONDS));
                case("modification"):
                    return LuaValue.valueOf(stat.lastModifiedTime().to(TimeUnit.SECONDS));
                case("change"):
                    return LuaValue.valueOf(stat.lastModifiedTime().to(TimeUnit.SECONDS));
                case("size"):
                    return LuaValue.valueOf(stat.size());
                case("permissions"):
                    return DUMMY_PERMISSIONS;
                case("blocks"):
                    return LuaValue.ZERO;
                case("blksize"):
                    return LuaValue.ZERO;
                default:
                    throw new LuaError("invalid attribute name '" + str +"'");
            }
        }

        if (!arg2.istable()) {
            arg2 = new LuaTable();
        }

        arg2.set(DEV, LuaValue.ZERO);
        arg2.set(INO, LuaValue.ZERO);
        arg2.set(MODE, mapStatMode(stat));
        arg2.set(NLINK, LuaValue.ZERO);
        arg2.set(UID, LuaValue.ZERO);
        arg2.set(GID, LuaValue.ZERO);
        arg2.set(RDEV, LuaValue.ZERO);
        arg2.set(ACCESS, LuaValue.valueOf(stat.lastAccessTime().to(TimeUnit.SECONDS)));
        arg2.set(MODIFICATION, LuaValue.valueOf(stat.lastModifiedTime().to(TimeUnit.SECONDS)));
        arg2.set(PERMISSIONS, DUMMY_PERMISSIONS);
        arg2.set(CHANGE, LuaValue.valueOf(stat.lastModifiedTime().to(TimeUnit.SECONDS)));
        arg2.set(SIZE, LuaValue.valueOf(stat.size()));
        arg2.set(BLOCKS, LuaValue.ZERO);
        arg2.set(BLKSIZE, LuaValue.ZERO);
        return arg2;
    }



}
