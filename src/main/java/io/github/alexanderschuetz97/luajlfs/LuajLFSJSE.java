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
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Fallback impl using only JSE standard mechanisms.
 * Provides the worst compatibility but is better than nothing.
 * Locking uses java locks that only other JVMs can see (using the {@link FileLock} mechanism)
 * lfs.c uses native locks from its respective OS that non lua/java applications can see just fine.
 * This is not possible with just the JSE.
 *
 * attributes & symlinkattributes only return what information is available using JSE Standard API.
 * All other method calls will use JVM error messages instead of errno's that lfs.c uses in case of error.
 * Compatibility to JSEIOLib file descriptors relies on Reflection API and will probably break in newer java releases (8+)
 * so make sure to load this library before doing any calls to IOLib so it can install its own IOLib that does not require reflection.
 */
public class LuajLFSJSE extends LuajLFSCommon {

    protected static final Varargs ERR_UNLOCKED = err("The lock is not held by this process");

    protected LuajLFSJSE(Globals globals, LuaTable table) {
        load(globals, table);
    }

    @Override
    protected Varargs lock_dir(Varargs args) {
        File lock = new File(resolve(args.checkjstring(1)), "lockfile.lfs");
        //I am aware the JVM documentation advises against doing this but this is as good as its gonna get.
        boolean created;
        try {
            created = lock.createNewFile();
        } catch (IOException e) {
            return ioErr(e);
        }

        if (!created) {
            return ERR_FILE_EXISTS;
        }

        lock_dir_userdata userdata = new lock_dir_userdata(null);
        //GC Magic to clean it
        userdata.m_instance =  new lock_dir_cleaner(userdata, lock);

        return userdata;
    }

    @Override
    public boolean isAbsolute(String path) {
        //UNIX
        if (File.separatorChar == '/') {
            return path.startsWith("/");
        }

        //Windows

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
        Path path = resolve(args.checkjstring(1)).toPath();
        BasicFileAttributes bfa = null;

        try {
            bfa = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            return ERR_NO_SUCH_FILE_OR_DIR;
        } catch (IOException e) {
            return ioErr(e);
        }

        return mapStatResult(args.arg(2), bfa);
    }

    @Override
    protected Varargs symlinkattributes(Varargs args) {
        Path path = resolve(args.checkjstring(1)).toPath();
        BasicFileAttributes bfa = null;

        try {
            bfa = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException e) {
            return ERR_NO_SUCH_FILE_OR_DIR;
        } catch (IOException e) {
            return ioErr(e);
        }

        return mapStatResult(args.arg(2), bfa);
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



    @Override
    protected Varargs link(Varargs args) {

        Path target = resolve(args.checkjstring(1)).toPath();
        Path link = resolve(args.checkjstring(2)).toPath();
        try {
            if (args.checkboolean(3)) {
                Files.createSymbolicLink(link, target);
            } else {
                Files.createLink(link, target);
            }
        } catch (FileAlreadyExistsException e) {
            return ERR_FILE_EXISTS;
        } catch (IOException e) {
            return ioErr(e);
        }

        return LuaValue.TRUE;
    }

    protected static class LockKey {
        protected final FileChannel channel;
        protected final long start;
        protected final long len;

        public LockKey(FileChannel channel, long start, long len) {
            this.channel = Objects.requireNonNull(channel);
            this.start = start;
            this.len = len;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            LockKey lockKey = (LockKey) o;

            if (start != lockKey.start) {
                return false;
            }

            if (len != lockKey.len) {
                return false;
            }

            return channel == lockKey.channel;
        }

        @Override
        public int hashCode() {
            int result = channel.hashCode();
            result = 31 * result + (int) (start ^ (start >>> 32));
            result = 31 * result + (int) (len ^ (len >>> 32));
            return result;
        }
    }

    protected static class LockCleaner extends ReferenceQueueCleaner.CleanerRef<LuaValue> {

        private final ConcurrentMap<LockKey, LockCleaner> map;
        private final LockKey key;
        private final FileLock lock;

        public LockCleaner(LuaValue referent, ConcurrentMap<LockKey, LockCleaner> map, LockKey key, FileLock lock) {
            super(referent);
            this.map = Objects.requireNonNull(map);
            this.key = Objects.requireNonNull(key);
            this.lock = Objects.requireNonNull(lock);
        }

        @Override
        public void clean() {
            map.remove(key, this);
            try {
                lock.release();
            } catch (IOException e) {
                //DC
            }
        }
    }


    private final ConcurrentMap<LockKey, LockCleaner> fileLockTable = new ConcurrentHashMap<>();

    @Override
    protected Varargs lockExclusive(LuaValue userdata, RandomAccessFile fileDescriptor, long start, long len) {

        FileChannel channel = fileDescriptor.getChannel();
        FileLock lock;
        try {
            lock = channel.tryLock(start, len, false);
            if (lock == null) {
                return ERR_LOCK_LOCKED;
            }
        } catch (OverlappingFileLockException e) {
            return ERR_LOCK_LOCKED;
        } catch (IOException e) {
            return ioErr(e);
        }

        LockKey key = new LockKey(channel, start, len);
        LockCleaner cleaner = new LockCleaner(userdata, fileLockTable, key, lock);
        LockCleaner other = fileLockTable.put(key, cleaner);
        if (other != null) {
            other.clear();
        }

        return LuaValue.TRUE;
    }

    @Override
    protected Varargs lockShared(LuaValue userdata, RandomAccessFile fileDescriptor, long start, long len) {
        FileChannel channel = fileDescriptor.getChannel();
        FileLock lock;
        try {
            lock = channel.tryLock(start, len, true);
            if (lock == null) {
                return ERR_LOCK_LOCKED;
            }
        } catch (OverlappingFileLockException e) {
            return ERR_LOCK_LOCKED;
        } catch (IOException e) {
            return ioErr(e);
        }

        LockKey key = new LockKey(channel, start, len);
        LockCleaner cleaner = new LockCleaner(userdata, fileLockTable, key, lock);
        LockCleaner other = fileLockTable.put(key, cleaner);
        if (other != null) {
            other.clear();
        }

        return LuaValue.TRUE;
    }

    @Override
    protected Varargs lockUnlock(LuaValue userdata, RandomAccessFile fileDescriptor, long start, long len) {
        LockCleaner cleaner = fileLockTable.remove(new LockKey(fileDescriptor.getChannel(), start, len));
        if (cleaner != null) {
            cleaner.clear();
            return LuaValue.TRUE;
        }

        return ERR_UNLOCKED;


    }


}
